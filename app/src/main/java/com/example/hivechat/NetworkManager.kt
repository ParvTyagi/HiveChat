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
        private const val MESSAGE_PORT = 8888
        private const val DISCOVERY_DURATION = 10000L // 10 seconds
        private const val BROADCAST_INTERVAL = 500L // Broadcast every 0.5 seconds during discovery
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Device info
    private var myDeviceId = ""
    private var myDeviceName = ""

    // Discovered devices
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    // Discovery state
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    // Messages
    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages

    // Active connections
    private val activeConnections = mutableMapOf<String, Socket>()

    // Server socket for incoming connections
    private var serverSocket: ServerSocket? = null

    // Discovery jobs
    private var discoveryBroadcastJob: Job? = null
    private var discoveryListenerJob: Job? = null
    private var discoveryTimeoutJob: Job? = null

    // Multicast lock for UDP broadcast
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Initialize the network manager
     */
    fun initialize(deviceName: String) {
        myDeviceName = deviceName
        myDeviceId = getDeviceId()

        // Acquire multicast lock
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("HiveChatLock").apply {
            acquire()
        }

        // Start message server (always running to receive messages)
        startMessageServer()

        Log.d(TAG, "NetworkManager initialized: $myDeviceName ($myDeviceId)")
    }

    /**
     * Get a unique device ID
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
     * Start device discovery (manual trigger)
     */
    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.d(TAG, "Discovery already in progress")
            return
        }

        _isDiscovering.value = true
        Log.d(TAG, "Starting discovery for $DISCOVERY_DURATION ms")

        // Clear old devices
        _devices.value = emptyList()

        // Start broadcasting and listening
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

        Log.d(TAG, "Stopping discovery")

        // Cancel discovery jobs
        discoveryBroadcastJob?.cancel()
        discoveryListenerJob?.cancel()
        discoveryTimeoutJob?.cancel()

        discoveryBroadcastJob = null
        discoveryListenerJob = null
        discoveryTimeoutJob = null

        _isDiscovering.value = false
    }

    /**
     * Broadcast presence to network (UDP) - runs only during discovery
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
                            port = MESSAGE_PORT
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
                        Log.d(TAG, "Broadcast sent: $myDeviceName")

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
                Log.d(TAG, "Broadcast socket closed")
            }
        }
    }

    /**
     * Listen for device broadcasts (UDP) - runs only during discovery
     */
    private fun startDiscoveryListener() {
        discoveryListenerJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket.broadcast = true
                socket.soTimeout = 1000 // 1 second timeout for checking cancellation
                val buffer = ByteArray(1024)

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
                            Log.d(TAG, "Device discovered: ${device.name} at ${device.ipAddress}")
                        }

                    } catch (e: SocketTimeoutException) {
                        // Normal timeout, continue loop
                    } catch (e: Exception) {
                        if (isActive && _isDiscovering.value) {
                            Log.e(TAG, "Discovery listener error: ${e.message}")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Discovery socket error: ${e.message}")
            } finally {
                socket?.close()
                Log.d(TAG, "Discovery listener closed")
            }
        }
    }

    /**
     * Update the list of discovered devices
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
     * Start TCP server to receive messages (always running)
     */
    private fun startMessageServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(MESSAGE_PORT)
                Log.d(TAG, "Message server started on port $MESSAGE_PORT")

                while (isActive) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            launch { handleIncomingConnection(socket) }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Server accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error: ${e.message}")
            }
        }
    }

    /**
     * Handle incoming TCP connection
     */
    private suspend fun handleIncomingConnection(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (isActive) {
                    val line = reader.readLine() ?: break
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
                    Log.d(TAG, "Message received from ${message.senderName}: ${message.text}")
                }

                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Incoming connection error: ${e.message}")
            }
        }
    }

    /**
     * Send a message to a device
     */
    fun sendMessage(deviceId: String, text: String) {
        scope.launch {
            try {
                val device = _devices.value.find { it.id == deviceId } ?: return@launch

                // Get or create connection
                val socket = activeConnections.getOrPut(deviceId) {
                    Socket().apply {
                        connect(InetSocketAddress(device.ipAddress, device.port), 5000)
                        Log.d(TAG, "Connected to ${device.name}")
                    }
                }

                // Create message packet
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

                // Send message
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(gson.toJson(packet))

                // Add to local messages
                addMessage(deviceId, message)
                Log.d(TAG, "Message sent to ${device.name}: $text")

            } catch (e: Exception) {
                Log.e(TAG, "Send message error: ${e.message}")
                // Remove failed connection
                activeConnections.remove(deviceId)?.close()
            }
        }
    }

    /**
     * Add a message to the conversation
     */
    private fun addMessage(deviceId: String, message: Message) {
        val currentMessages = _messages.value.toMutableMap()
        val deviceMessages = currentMessages[deviceId]?.toMutableList() ?: mutableListOf()
        deviceMessages.add(message)
        currentMessages[deviceId] = deviceMessages
        _messages.value = currentMessages
    }

    /**
     * Get messages for a specific device
     */
    fun getMessagesForDevice(deviceId: String): List<Message> {
        return _messages.value[deviceId] ?: emptyList()
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopDiscovery()
        scope.cancel()
        serverSocket?.close()
        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        multicastLock?.release()
        Log.d(TAG, "NetworkManager cleaned up")
    }
}