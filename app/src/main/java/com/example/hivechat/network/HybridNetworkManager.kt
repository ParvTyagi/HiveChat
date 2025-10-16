package com.example.hivechat.network

import android.content.Context
import android.util.Log
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HybridNetworkManager(private val context: Context) {

    companion object {
        private const val TAG = "HybridNetworkManager"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val normalNetworkManager = NetworkManager(context)
    private var wifiDirectManager: WiFiDirectManager? = null

    private val _networkMode = MutableStateFlow(NetworkMode.NORMAL_WIFI)
    val networkMode: StateFlow<NetworkMode> = _networkMode

    // Combined flows that switch based on mode
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
        normalNetworkManager.initialize(deviceName)

        // Observe normal WiFi manager flows
        observeNormalWifi()

        Log.d(TAG, "HybridNetworkManager initialized with normal WiFi")
        _connectionStatus.value = "Normal WiFi initialized"
    }

    private fun observeNormalWifi() {
        scope.launch {
            normalNetworkManager.devices.collect { deviceList ->
                if (_networkMode.value == NetworkMode.NORMAL_WIFI) {
                    _devices.value = deviceList
                }
            }
        }

        scope.launch {
            normalNetworkManager.isDiscovering.collect { discovering ->
                if (_networkMode.value == NetworkMode.NORMAL_WIFI) {
                    _isDiscovering.value = discovering
                }
            }
        }

        scope.launch {
            normalNetworkManager.messages.collect { messageMap ->
                if (_networkMode.value == NetworkMode.NORMAL_WIFI) {
                    _messages.value = messageMap
                }
            }
        }

        scope.launch {
            normalNetworkManager.connectionStatus.collect { status ->
                if (_networkMode.value == NetworkMode.NORMAL_WIFI) {
                    _connectionStatus.value = status
                }
            }
        }

        scope.launch {
            normalNetworkManager.unreadMessages.collect { unread ->
                if (_networkMode.value == NetworkMode.NORMAL_WIFI) {
                    _unreadMessages.value = unread
                }
            }
        }
    }

    private fun observeWiFiDirect() {
        val wifiDirect = wifiDirectManager ?: return

        scope.launch {
            wifiDirect.devices.collect { deviceList ->
                if (_networkMode.value == NetworkMode.WIFI_DIRECT) {
                    _devices.value = deviceList
                }
            }
        }

        scope.launch {
            wifiDirect.isDiscovering.collect { discovering ->
                if (_networkMode.value == NetworkMode.WIFI_DIRECT) {
                    _isDiscovering.value = discovering
                }
            }
        }

        scope.launch {
            wifiDirect.messages.collect { messageMap ->
                if (_networkMode.value == NetworkMode.WIFI_DIRECT) {
                    _messages.value = messageMap
                }
            }
        }

        scope.launch {
            wifiDirect.connectionStatus.collect { status ->
                if (_networkMode.value == NetworkMode.WIFI_DIRECT) {
                    _connectionStatus.value = status
                }
            }
        }

        scope.launch {
            wifiDirect.unreadMessages.collect { unread ->
                if (_networkMode.value == NetworkMode.WIFI_DIRECT) {
                    _unreadMessages.value = unread
                }
            }
        }
    }

    fun startDiscovery() {
        when (_networkMode.value) {
            NetworkMode.NORMAL_WIFI -> {
                normalNetworkManager.startDiscovery()
            }
            NetworkMode.WIFI_DIRECT -> {
                wifiDirectManager?.startDiscovery()
            }
        }
    }

    fun stopDiscovery() {
        when (_networkMode.value) {
            NetworkMode.NORMAL_WIFI -> {
                normalNetworkManager.stopDiscovery()
            }
            NetworkMode.WIFI_DIRECT -> {
                wifiDirectManager?.stopDiscovery()
            }
        }
    }

    fun switchToWiFiDirect(deviceName: String) {
        Log.d(TAG, "Switching to WiFi Direct mode...")

        // Stop normal WiFi operations
        normalNetworkManager.stopDiscovery()

        // Initialize WiFi Direct if needed
        if (wifiDirectManager == null) {
            wifiDirectManager = WiFiDirectManager(context).apply {
                initialize(deviceName)
                register()
            }
            observeWiFiDirect()
        }

        // Switch mode
        _networkMode.value = NetworkMode.WIFI_DIRECT
        _connectionStatus.value = "Switched to WiFi Direct mode"

        // Clear previous data
        _devices.value = emptyList()

        Log.d(TAG, "Switched to WiFi Direct mode")
    }

    fun switchToNormalWiFi() {
        Log.d(TAG, "Switching to Normal WiFi mode...")

        // Stop WiFi Direct operations
        wifiDirectManager?.stopDiscovery()
        wifiDirectManager?.disconnect()
        wifiDirectManager?.cleanup()
        wifiDirectManager = null

        // Switch mode
        _networkMode.value = NetworkMode.NORMAL_WIFI
        _connectionStatus.value = "Switched to Normal WiFi mode"

        // Clear previous data
        _devices.value = emptyList()

        Log.d(TAG, "Switched to Normal WiFi mode")
    }

    fun isNetworkRestricted(): Boolean {
        if (_networkMode.value != NetworkMode.NORMAL_WIFI) {
            return false
        }

        val status = normalNetworkManager.connectionStatus.value
        return status.contains("❌") && status.contains("All ports blocked")
    }

    fun sendMessage(deviceId: String, text: String) {
        when (_networkMode.value) {
            NetworkMode.NORMAL_WIFI -> {
                normalNetworkManager.sendMessage(deviceId, text)
            }
            NetworkMode.WIFI_DIRECT -> {
                wifiDirectManager?.sendMessage(deviceId, text)
            }
        }
    }

    fun clearUnreadMessages(deviceId: String) {
        when (_networkMode.value) {
            NetworkMode.NORMAL_WIFI -> {
                normalNetworkManager.clearUnreadMessages(deviceId)
            }
            NetworkMode.WIFI_DIRECT -> {
                wifiDirectManager?.clearUnreadMessages(deviceId)
            }
        }
    }

    fun getMyDeviceId(): String {
        return normalNetworkManager.getMyDeviceId()
    }

    fun connectToWiFiDirectPeer(deviceAddress: String) {
        val peer = wifiDirectManager?.discoveredPeers?.value?.find {
            it.deviceAddress == deviceAddress
        }

        if (peer != null) {
            wifiDirectManager?.connectToPeer(peer)
        } else {
            Log.e(TAG, "Peer not found: $deviceAddress")
        }
    }

    fun disconnectWiFiDirect() {
        wifiDirectManager?.disconnect()
    }

    fun cleanup() {
        normalNetworkManager.cleanup()
        wifiDirectManager?.cleanup()
        wifiDirectManager = null
        scope.cancel()
        Log.d(TAG, "HybridNetworkManager cleaned up")
    }
}

enum class NetworkMode {
    NORMAL_WIFI,
    WIFI_DIRECT
}