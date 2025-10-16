package com.example.hivechat.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo as AndroidWpsInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WiFiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WiFiDirectManager"
        private const val SERVER_PORT = 8888
        private const val SOCKET_TIMEOUT = 5000
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private var myDeviceId = ""
    private var myDeviceName = ""
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isGroupOwner = false

    private val _discoveredPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiP2pDevice>> = _discoveredPeers

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages

    private val _connectionStatus = MutableStateFlow("WiFi Direct Initializing...")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    private val _unreadMessages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadMessages: StateFlow<Map<String, Int>> = _unreadMessages

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        _connectionStatus.value = "WiFi Direct enabled"
                        Log.d(TAG, "WiFi Direct enabled")
                    } else {
                        _connectionStatus.value = "WiFi Direct disabled"
                        Log.d(TAG, "WiFi Direct disabled")
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (checkLocationPermission()) {
                        wifiP2pManager?.requestPeers(channel) { peerList ->
                            val peers = peerList.deviceList.toList()
                            _discoveredPeers.value = peers

                            // Convert to Device list
                            val deviceList = peers.map { peer ->
                                Device(
                                    id = peer.deviceAddress,
                                    name = peer.deviceName,
                                    ipAddress = "",
                                    port = SERVER_PORT,
                                    lastSeen = System.currentTimeMillis()
                                )
                            }
                            _devices.value = deviceList

                            Log.d(TAG, "Peers discovered: ${peers.size}")
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO,
                            NetworkInfo::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }

                    if (networkInfo?.isConnected == true) {
                        wifiP2pManager?.requestConnectionInfo(channel) { info ->
                            handleConnectionInfo(info)
                        }
                    } else {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _connectionStatus.value = "Disconnected"
                        closeConnections()
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                            WifiP2pDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    }
                    Log.d(TAG, "This device: ${device?.deviceName}")
                }
            }
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    fun initialize(deviceName: String) {
        myDeviceName = deviceName
        myDeviceId = getDeviceId()
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        Log.d(TAG, "WiFiDirectManager initialized: $myDeviceName ($myDeviceId)")
    }

    fun register() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter)
            }
            Log.d(TAG, "Receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver: ${e.message}")
        }
    }

    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ])
    fun startDiscovery() {
        if (!checkLocationPermission()) {
            _connectionStatus.value = "Location permission required"
            return
        }

        _isDiscovering.value = true
        _connectionStatus.value = "🔍 Discovering WiFi Direct peers..."

        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started")
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                val error = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "WiFi Direct not supported"
                    WifiP2pManager.ERROR -> "Internal error"
                    WifiP2pManager.BUSY -> "System busy"
                    else -> "Unknown error ($reason)"
                }
                _connectionStatus.value = "Discovery failed: $error"
                Log.e(TAG, "Discovery failed: $error")
            }
        })
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        wifiP2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery stopped")
                val deviceCount = _discoveredPeers.value.size
                _connectionStatus.value = if (deviceCount > 0) {
                    "✅ Found $deviceCount peer(s)"
                } else {
                    "No peers found"
                }
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Stop discovery failed: $reason")
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        if (!checkLocationPermission()) return

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = AndroidWpsInfo.PBC
        }

        _connectionStatus.value = "Connecting to ${device.deviceName}..."

        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionState.value = ConnectionState.CONNECTING
                Log.d(TAG, "Connection initiated to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                _connectionState.value = ConnectionState.DISCONNECTED
                val error = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "WiFi Direct not supported"
                    WifiP2pManager.ERROR -> "Connection error"
                    WifiP2pManager.BUSY -> "System busy"
                    else -> "Unknown error ($reason)"
                }
                _connectionStatus.value = "Connection failed: $error"
                Log.e(TAG, "Connect failed: $error")
            }
        })
    }

    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null) return

        isGroupOwner = info.isGroupOwner
        val groupOwnerAddress = info.groupOwnerAddress?.hostAddress

        Log.d(TAG, "Connected - Group Owner: $isGroupOwner, Address: $groupOwnerAddress")

        if (isGroupOwner) {
            _connectionState.value = ConnectionState.CONNECTED
            _connectionStatus.value = "✅ Connected as Group Owner"
            startServer()
        } else {
            _connectionState.value = ConnectionState.CONNECTED
            _connectionStatus.value = "✅ Connected as Client"
            connectToServer(groupOwnerAddress)
        }
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                Log.d(TAG, "Server started on port $SERVER_PORT")

                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept()
                        client?.let { socket ->
                            clientSocket = socket
                            launch { handleClient(socket) }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Server accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    private fun connectToServer(address: String?) {
        if (address == null) return

        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(address, SERVER_PORT), SOCKET_TIMEOUT)
                clientSocket = socket
                Log.d(TAG, "Connected to server at $address:$SERVER_PORT")
                handleClient(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Client connection error: ${e.message}")
                _connectionStatus.value = "Connection error: ${e.message}"
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    try {
                        val message = gson.fromJson(line, MessageData::class.java)
                        val msg = Message(
                            id = message.id,
                            text = message.text,
                            senderName = message.senderName,
                            senderId = message.senderId,
                            timestamp = message.timestamp,
                            isMine = false
                        )
                        addMessage(message.senderId, msg)
                        incrementUnreadCount(message.senderId)
                        Log.d(TAG, "Message received: ${message.text}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handler error: ${e.message}")
            } finally {
                reader?.close()
            }
        }
    }

    fun sendMessage(deviceId: String, text: String) {
        scope.launch {
            try {
                val socket = clientSocket
                if (socket == null || socket.isClosed) {
                    Log.e(TAG, "No active connection")
                    return@launch
                }

                val message = Message(
                    text = text,
                    senderName = myDeviceName,
                    senderId = myDeviceId,
                    isMine = true
                )

                val messageData = MessageData(
                    id = message.id,
                    text = message.text,
                    senderName = message.senderName,
                    senderId = message.senderId,
                    timestamp = message.timestamp
                )

                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(gson.toJson(messageData))

                addMessage(deviceId, message)
                Log.d(TAG, "Message sent: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }

    fun disconnect() {
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectionStatus.value = "Disconnected"
                Log.d(TAG, "Disconnected")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason")
            }
        })
        closeConnections()
    }

    private fun closeConnections() {
        clientSocket?.close()
        clientSocket = null
        serverSocket?.close()
        serverSocket = null
    }

    private fun addMessage(deviceId: String, message: Message) {
        val currentMessages = _messages.value.toMutableMap()
        val deviceMessages = currentMessages[deviceId]?.toMutableList() ?: mutableListOf()
        deviceMessages.add(message)
        currentMessages[deviceId] = deviceMessages
        _messages.value = currentMessages
    }

    private fun incrementUnreadCount(deviceId: String) {
        val currentUnread = _unreadMessages.value.toMutableMap()
        currentUnread[deviceId] = (currentUnread[deviceId] ?: 0) + 1
        _unreadMessages.value = currentUnread
    }

    fun clearUnreadMessages(deviceId: String) {
        val currentUnread = _unreadMessages.value.toMutableMap()
        currentUnread.remove(deviceId)
        _unreadMessages.value = currentUnread
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

    private fun checkLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun cleanup() {
        try {
            stopDiscovery()
            disconnect()
            scope.cancel()
            context.unregisterReceiver(receiver)
            Log.d(TAG, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    private data class MessageData(
        val id: String,
        val text: String,
        val senderName: String,
        val senderId: String,
        val timestamp: Long
    )
}