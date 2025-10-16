package com.example.hivechat.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.hivechat.model.Device
import com.example.hivechat.model.DiscoveryPacket
import com.example.hivechat.model.Message
import com.example.hivechat.model.MessagePacket
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*

class NetworkManager(private val context: Context) {

    companion object {
        private const val TAG = "NetworkManager"
        private const val DISCOVERY_PORT = 9999
        private val MESSAGE_PORTS = listOf(443, 80, 8080, 53, 8888, 9090, 5353, 49152)
        private const val DISCOVERY_DURATION = 30000L
        private const val SOCKET_TIMEOUT = 5000
        private const val STALE_DEVICE_THRESHOLD = 35000L
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var myDeviceId = ""
    private var myDeviceName = ""
    private var activeMessagePort = 0

    private var serverSocket: ServerSocket? = null
    private val activeConnections = mutableMapOf<String, Socket>()
    private var multicastLock: WifiManager.MulticastLock? = null

    private var discoveryJob: Job? = null
    private var listenerJob: Job? = null
    private var cleanupJob: Job? = null

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages

    private val _connectionStatus = MutableStateFlow("Initializing...")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _unreadMessages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadMessages: StateFlow<Map<String, Int>> = _unreadMessages

    fun initialize(deviceName: String) {
        myDeviceName = deviceName
        myDeviceId = getDeviceId()
        acquireMulticastLock()
        startMessageServerWithFallback()
        startDeviceCleanup()
        Log.d(TAG, "NetworkManager initialized: $myDeviceName ($myDeviceId)")
    }

    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("HiveChatLock").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Multicast lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    private fun startMessageServerWithFallback() {
        scope.launch {
            var serverStarted = false
            for (port in MESSAGE_PORTS) {
                try {
                    serverSocket = ServerSocket(port)
                    serverSocket?.soTimeout = 0
                    activeMessagePort = port
                    serverStarted = true
                    _connectionStatus.value = "✅ Server running on port $port"
                    Log.d(TAG, "✅ Server started on port $port")
                    startAcceptingConnections()
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Port $port failed: ${e.message}")
                    _connectionStatus.value = "Trying port $port..."
                }
            }
            if (!serverStarted) {
                _connectionStatus.value = "❌ Network restricted - All ports blocked"
                Log.e(TAG, "CRITICAL: Could not start server on any port")
            }
        }
    }

    private suspend fun startAcceptingConnections() {
        withContext(Dispatchers.IO) {
            try {
                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            launch { handleIncomingConnection(socket) }
                        }
                    } catch (e: SocketTimeoutException) {
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Server socket error: ${e.message}")
            }
        }
    }

    // -------------------- DISCOVERY --------------------

    fun startDiscovery() {
        if (_isDiscovering.value) return
        if (activeMessagePort == 0) {
            _connectionStatus.value = "❌ Cannot discover: Server not running"
            Log.e(TAG, "Cannot start discovery: Server not running")
            return
        }

        _isDiscovering.value = true
        _connectionStatus.value = "🔍 Discovering devices..."
        _devices.value = emptyList()
        Log.d(TAG, "Discovery started")

        // Start periodic discovery bursts
        discoveryJob = scope.launch {
            while (isActive && _isDiscovering.value) {
                sendDiscoveryBurst()
                delay(10000L) // wait 10s between bursts
            }
        }

        // Start listener
        startDiscoveryListener()

        // Optional timeout
        scope.launch {
            delay(DISCOVERY_DURATION)
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        if (!_isDiscovering.value) return
        discoveryJob?.cancel()
        listenerJob?.cancel()
        _isDiscovering.value = false
        val count = _devices.value.size
        _connectionStatus.value = if (count > 0) "✅ Found $count device(s)" else "No devices found"
        Log.d(TAG, "Discovery stopped - Found $count devices")
    }

    private suspend fun sendDiscoveryBurst() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            repeat(3) { // send 3 packets quickly
                val packet = DiscoveryPacket(myDeviceId, myDeviceName, activeMessagePort)
                val json = gson.toJson(packet)
                val data = json.toByteArray()
                val datagramPacket = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                socket.send(datagramPacket)
                Log.d(TAG, "Discovery burst sent: $myDeviceName")
                delay(500L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery burst error: ${e.message}")
        } finally {
            socket?.close()
        }
    }

    private fun startDiscoveryListener() {
        listenerJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket.soTimeout = 2000
                val buffer = ByteArray(2048)
                while (isActive && _isDiscovering.value) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val json = String(packet.data, 0, packet.length)
                        val dp = gson.fromJson(json, DiscoveryPacket::class.java)
                        if (dp.deviceId != myDeviceId) {
                            val device = Device(dp.deviceId, dp.deviceName, packet.address.hostAddress ?: "", dp.port, System.currentTimeMillis())
                            updateDeviceList(device)
                            Log.d(TAG, "Device found: ${device.name}")
                        }
                    } catch (e: SocketTimeoutException) {
                        // No packet received, continue
                    } catch (e: Exception) {
                        Log.e(TAG, "Listener error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery listener socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun updateDeviceList(device: Device) {
        val current = _devices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index >= 0) current[index] = device else current.add(device)
        _devices.value = current
    }

    private fun startDeviceCleanup() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                val active = _devices.value.filter { now - it.lastSeen < STALE_DEVICE_THRESHOLD }
                if (active.size != _devices.value.size) _devices.value = active
            }
        }
    }

    // -------------------- MESSAGING --------------------

    private suspend fun handleIncomingConnection(socket: Socket) {
        withContext(Dispatchers.IO) {
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val clientAddr = socket.inetAddress.hostAddress ?: "unknown"
                Log.d(TAG, "Client connected: $clientAddr")
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    try {
                        val mp = gson.fromJson(line, MessagePacket::class.java)
                        val message = Message(mp.messageId, mp.text, mp.senderName, mp.senderId, mp.timestamp, false)
                        addMessage(mp.senderId, message)
                        incrementUnread(mp.senderId)
                        Log.d(TAG, "Message received: ${message.text}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
            } finally {
                reader?.close()
                socket.close()
            }
        }
    }

    fun sendMessage(deviceId: String, text: String) {
        scope.launch {
            try {
                val device = _devices.value.find { it.id == deviceId } ?: return@launch
                val socket = activeConnections.getOrPut(deviceId) { connectToDevice(device) }
                val message = Message(text = text, senderName = myDeviceName, senderId = myDeviceId, isMine = true)
                val packet = MessagePacket(message.id, message.text, message.senderName, message.senderId, message.timestamp)
                PrintWriter(socket.getOutputStream(), true).println(gson.toJson(packet))
                addMessage(deviceId, message)
                Log.d(TAG, "Message sent to ${device.name}: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
                activeConnections.remove(deviceId)?.close()
            }
        }
    }

    private fun connectToDevice(device: Device): Socket {
        val portsToTry = listOf(device.port) + MESSAGE_PORTS.filter { it != device.port }
        for (port in portsToTry) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(device.ipAddress, port), SOCKET_TIMEOUT)
                Log.d(TAG, "✅ Connected to ${device.name} on port $port")
                return socket
            } catch (e: Exception) {
                Log.w(TAG, "Failed port $port for ${device.name}: ${e.message}")
            }
        }
        throw Exception("Could not connect to ${device.name} on any port")
    }

    private fun addMessage(deviceId: String, message: Message) {
        val current = _messages.value.toMutableMap()
        val msgs = current[deviceId]?.toMutableList() ?: mutableListOf()
        msgs.add(message)
        current[deviceId] = msgs
        _messages.value = current
    }

    private fun incrementUnread(deviceId: String) {
        val current = _unreadMessages.value.toMutableMap()
        current[deviceId] = (current[deviceId] ?: 0) + 1
        _unreadMessages.value = current
    }

    fun clearUnreadMessages(deviceId: String) {
        val current = _unreadMessages.value.toMutableMap()
        current.remove(deviceId)
        _unreadMessages.value = current
    }

    fun getMyDeviceId(): String = myDeviceId

    fun cleanup() {
        stopDiscovery()
        cleanupJob?.cancel()
        scope.cancel()
        serverSocket?.close()
        serverSocket = null
        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        multicastLock?.release()
        multicastLock = null
        Log.d(TAG, "NetworkManager cleaned up")
    }
}
