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
        private const val BROADCAST_INTERVAL = 1000L
        private const val SOCKET_TIMEOUT = 5000
        private const val STALE_DEVICE_THRESHOLD = 35000L
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Device information
    private var myDeviceId = ""
    private var myDeviceName = ""
    private var activeMessagePort = 0

    // Network components
    private var serverSocket: ServerSocket? = null
    private val activeConnections = mutableMapOf<String, Socket>()
    private var multicastLock: WifiManager.MulticastLock? = null

    // Discovery jobs
    private var discoveryBroadcastJob: Job? = null
    private var discoveryListenerJob: Job? = null
    private var discoveryTimeoutJob: Job? = null
    private var deviceCleanupJob: Job? = null

    // State flows
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

    /**
     * Initialize the network manager
     */
    fun initialize(deviceName: String) {
        myDeviceName = deviceName
        myDeviceId = getDeviceId()

        // Acquire multicast lock for UDP broadcasts
        acquireMulticastLock()

        // Start message server with port fallback
        startMessageServerWithFallback()

        // Start periodic device cleanup
        startDeviceCleanup()

        Log.d(TAG, "NetworkManager initialized: $myDeviceName ($myDeviceId)")
    }

    /**
     * Get or create unique device ID
     */
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    /**
     * Acquire multicast lock for UDP
     */
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

    /**
     * Try multiple ports until one works
     */
    private fun startMessageServerWithFallback() {
        scope.launch {
            var serverStarted = false

            for (port in MESSAGE_PORTS) {
                try {
                    serverSocket = ServerSocket(port)
                    serverSocket?.soTimeout = 0 // No timeout
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

    /**
     * Accept incoming TCP connections
     */
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
                        // Normal timeout, continue
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server socket error: ${e.message}")
                }
            }
        }
    }

    /**
     * Start device discovery
     */
    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.d(TAG, "Discovery already in progress")
            return
        }

        if (activeMessagePort == 0) {
            _connectionStatus.value = "❌ Cannot discover: Server not running"
            Log.e(TAG, "Cannot start discovery: Server not running")
            return
        }

        _isDiscovering.value = true
        _connectionStatus.value = "🔍 Discovering devices..."
        _devices.value = emptyList()
        Log.d(TAG, "Discovery started")

        startDiscoveryBroadcast()
        startDiscoveryListener()

        // Auto-stop after duration
        discoveryTimeoutJob = scope.launch {
            delay(DISCOVERY_DURATION)
            stopDiscovery()
        }
    }

    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        if (!_isDiscovering.value) return

        discoveryBroadcastJob?.cancel()
        discoveryListenerJob?.cancel()
        discoveryTimeoutJob?.cancel()

        _isDiscovering.value = false

        val deviceCount = _devices.value.size
        _connectionStatus.value = if (deviceCount > 0) {
            "✅ Found $deviceCount device(s)"
        } else {
            "No devices found"
        }

        Log.d(TAG, "Discovery stopped - Found $deviceCount devices")
    }

    /**
     * Broadcast presence via UDP
     */
    private fun startDiscoveryBroadcast() {
        discoveryBroadcastJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true

                while (isActive && _isDiscovering.value) {
                    try {
                        val packet = DiscoveryPacket(
                            deviceId = myDeviceId,
                            deviceName = myDeviceName,
                            port = activeMessagePort
                        )
                        val json = gson.toJson(packet)
                        val data = json.toByteArray()

                        val datagramPacket = DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("255.255.255.255"),
                            DISCOVERY_PORT
                        )

                        socket.send(datagramPacket)
                        Log.d(TAG, "Broadcast sent: $myDeviceName on port $activeMessagePort")

                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Broadcast error: ${e.message}")
                        }
                    }

                    delay(BROADCAST_INTERVAL)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Broadcast socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * Listen for device broadcasts via UDP
     */
    private fun startDiscoveryListener() {
        discoveryListenerJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket.broadcast = true
                socket.soTimeout = 2000
                val buffer = ByteArray(2048)

                while (isActive && _isDiscovering.value) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val json = String(packet.data, 0, packet.length)
                        val discoveryPacket = gson.fromJson(json, DiscoveryPacket::class.java)

                        // Ignore own broadcasts
                        if (discoveryPacket.deviceId != myDeviceId) {
                            val device = Device(
                                id = discoveryPacket.deviceId,
                                name = discoveryPacket.deviceName,
                                ipAddress = packet.address.hostAddress ?: "",
                                port = discoveryPacket.port,
                                lastSeen = System.currentTimeMillis()
                            )

                            updateDeviceList(device)
                            Log.d(TAG, "Device found: ${device.name} at ${device.ipAddress}:${device.port}")
                        }

                    } catch (e: SocketTimeoutException) {
                        // Normal timeout
                    } catch (e: Exception) {
                        if (isActive && _isDiscovering.value) {
                            Log.e(TAG, "Listener error: ${e.message}")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Discovery socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * Update discovered devices list
     */
    private fun updateDeviceList(newDevice: Device) {
        val currentDevices = _devices.value.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.id == newDevice.id }

        if (existingIndex >= 0) {
            currentDevices[existingIndex] = newDevice
        } else {
            currentDevices.add(newDevice)
        }

        _devices.value = currentDevices
    }

    /**
     * Periodically remove stale devices
     */
    private fun startDeviceCleanup() {
        deviceCleanupJob = scope.launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds

                val now = System.currentTimeMillis()
                val activeDevices = _devices.value.filter {
                    now - it.lastSeen < STALE_DEVICE_THRESHOLD
                }

                if (activeDevices.size != _devices.value.size) {
                    _devices.value = activeDevices
                    Log.d(TAG, "Removed stale devices. Active: ${activeDevices.size}")
                }
            }
        }
    }

    /**
     * Handle incoming TCP connection
     */
    private suspend fun handleIncomingConnection(socket: Socket) {
        withContext(Dispatchers.IO) {
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val clientAddress = socket.inetAddress.hostAddress ?: "unknown"

                Log.d(TAG, "Client connected: $clientAddress")

                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break

                    try {
                        val messagePacket = gson.fromJson(line, MessagePacket::class.java)

                        val message = Message(
                            id = messagePacket.messageId,
                            text = messagePacket.text,
                            senderName = messagePacket.senderName,
                            senderId = messagePacket.senderId,
                            timestamp = messagePacket.timestamp,
                            isMine = false
                        )

                        addMessage(messagePacket.senderId, message)
                        incrementUnreadCount(messagePacket.senderId)
                        Log.d(TAG, "Message received from ${message.senderName}: ${message.text}")

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

    /**
     * Send message to a device
     */
    fun sendMessage(deviceId: String, text: String) {
        scope.launch {
            try {
                val device = _devices.value.find { it.id == deviceId }
                if (device == null) {
                    Log.e(TAG, "Device not found: $deviceId")
                    return@launch
                }

                // Get or create connection
                val socket = activeConnections.getOrPut(deviceId) {
                    connectToDevice(device)
                }

                // Create message
                val message = Message(
                    text = text,
                    senderName = myDeviceName,
                    senderId = myDeviceId,
                    isMine = true
                )

                val packet = MessagePacket(
                    messageId = message.id,
                    text = message.text,
                    senderName = message.senderName,
                    senderId = message.senderId,
                    timestamp = message.timestamp
                )

                // Send via TCP
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(gson.toJson(packet))

                // Add to local messages
                addMessage(deviceId, message)
                Log.d(TAG, "Message sent to ${device.name}: $text")

            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
                activeConnections.remove(deviceId)?.close()
            }
        }
    }

    /**
     * Connect to a device with port fallback
     */
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

    /**
     * Add message to conversation
     */
    private fun addMessage(deviceId: String, message: Message) {
        val currentMessages = _messages.value.toMutableMap()
        val deviceMessages = currentMessages[deviceId]?.toMutableList() ?: mutableListOf()
        deviceMessages.add(message)
        currentMessages[deviceId] = deviceMessages
        _messages.value = currentMessages
    }

    /**
     * Increment unread message count
     */
    private fun incrementUnreadCount(deviceId: String) {
        val currentUnread = _unreadMessages.value.toMutableMap()
        currentUnread[deviceId] = (currentUnread[deviceId] ?: 0) + 1
        _unreadMessages.value = currentUnread
    }

    /**
     * Clear unread messages for a device
     */
    fun clearUnreadMessages(deviceId: String) {
        val currentUnread = _unreadMessages.value.toMutableMap()
        currentUnread.remove(deviceId)
        _unreadMessages.value = currentUnread
    }

    /**
     * Get device ID
     */
    fun getMyDeviceId(): String = myDeviceId

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopDiscovery()
        deviceCleanupJob?.cancel()
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