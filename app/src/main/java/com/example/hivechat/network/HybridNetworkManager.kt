package com.example.hivechat.network

import android.content.Context
import android.util.Log
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import kotlinx.coroutines.flow.StateFlow

class HybridNetworkManager(private val context: Context) {

    companion object {
        private const val TAG = "HybridNetworkManager"
    }

    private val normalNetworkManager = NetworkManager(context)
    private var wifiDirectManager: WiFiDirectManager? = null

    private var _networkMode = NetworkMode.NORMAL_WIFI
    val networkMode: NetworkMode get() = _networkMode

    val devices: StateFlow<List<Device>> = normalNetworkManager.devices
    val isDiscovering: StateFlow<Boolean> = normalNetworkManager.isDiscovering
    val messages: StateFlow<Map<String, List<Message>>> = normalNetworkManager.messages
    val connectionStatus: StateFlow<String> = normalNetworkManager.connectionStatus
    val unreadMessages: StateFlow<Map<String, Int>> = normalNetworkManager.unreadMessages

    fun initialize(deviceName: String) {
        normalNetworkManager.initialize(deviceName)
        Log.d(TAG, "HybridNetworkManager initialized with normal WiFi")
    }

    fun startDiscovery() {
        when (_networkMode) {
            NetworkMode.NORMAL_WIFI -> {
                normalNetworkManager.startDiscovery()
            }
            NetworkMode.WIFI_DIRECT -> {
                wifiDirectManager?.startDiscovery()
            }
        }
    }

    fun stopDiscovery() {
        normalNetworkManager.stopDiscovery()
        wifiDirectManager?.stopDiscovery()
    }

    fun switchToWiFiDirect(deviceName: String) {
        if (wifiDirectManager == null) {
            wifiDirectManager = WiFiDirectManager(context)
            wifiDirectManager?.register()
        }
        _networkMode = NetworkMode.WIFI_DIRECT
        Log.d(TAG, "Switched to WiFi Direct mode")
    }

    fun switchToNormalWiFi() {
        wifiDirectManager?.cleanup()
        wifiDirectManager = null
        _networkMode = NetworkMode.NORMAL_WIFI
        Log.d(TAG, "Switched to Normal WiFi mode")
    }

    fun isNetworkRestricted(): Boolean {
        val status = connectionStatus.value
        return status.contains("❌") && status.contains("All ports blocked")
    }

    fun sendMessage(deviceId: String, text: String) {
        normalNetworkManager.sendMessage(deviceId, text)
    }

    fun clearUnreadMessages(deviceId: String) {
        normalNetworkManager.clearUnreadMessages(deviceId)
    }

    fun cleanup() {
        normalNetworkManager.cleanup()
        wifiDirectManager?.cleanup()
    }
}

enum class NetworkMode {
    NORMAL_WIFI,
    WIFI_DIRECT
}