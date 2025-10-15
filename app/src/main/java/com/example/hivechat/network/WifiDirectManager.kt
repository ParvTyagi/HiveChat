package com.example.hivechat.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.net.wifi.WpsInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WiFiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WiFiDirectManager"
        const val SERVICE_TYPE = "_hivechat._tcp"
    }

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private val receiver: WiFiDirectBroadcastReceiver
    private val intentFilter = IntentFilter()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    init {
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, context.mainLooper, null)
        receiver = WiFiDirectBroadcastReceiver(this)
        intentFilter.apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }

    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.d(TAG, "Discovery already in progress")
            return
        }
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = true
                Log.d(TAG, "✅ WiFi Direct discovery started")
            }
            override fun onFailure(reasonCode: Int) {
                _isDiscovering.value = false
                Log.e(TAG, "❌ Discovery failed: ${getErrorMessage(reasonCode)}")
            }
        })
    }

    fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = false
                Log.d(TAG, "Discovery stopped")
            }
            override fun onFailure(reasonCode: Int) {
                Log.e(TAG, "Failed to stop discovery: ${getErrorMessage(reasonCode)}")
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "✅ Connecting to ${device.deviceName}")
            }
            override fun onFailure(reasonCode: Int) {
                Log.e(TAG, "❌ Connection failed: ${getErrorMessage(reasonCode)}")
            }
        })
    }

    fun disconnect() {
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected")
            }
            override fun onFailure(reasonCode: Int) {
                Log.e(TAG, "Failed to disconnect: ${getErrorMessage(reasonCode)}")
            }
        })
    }

    fun register() {
        context.registerReceiver(receiver, intentFilter)
        Log.d(TAG, "WiFi Direct receiver registered")
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "WiFi Direct receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}")
        }
    }

    fun requestPeers() {
        manager?.requestPeers(channel) { peerList ->
            val deviceList = peerList.deviceList.toList()
            _peers.value = deviceList
            Log.d(TAG, "Peers updated: ${deviceList.size} devices found")
            deviceList.forEach {
                Log.d(TAG, "  - ${it.deviceName} (${it.deviceAddress})")
            }
        }
    }

    fun requestConnectionInfo() {
        manager?.requestConnectionInfo(channel) { info ->
            _connectionInfo.value = info
            if (info.groupFormed) {
                val role = if (info.isGroupOwner) "Group Owner" else "Client"
                Log.d(TAG, "✅ Connected as $role")
                Log.d(TAG, "Group Owner IP: ${info.groupOwnerAddress?.hostAddress}")
            }
        }
    }

    private fun getErrorMessage(reasonCode: Int): String {
        return when (reasonCode) {
            WifiP2pManager.ERROR -> "Internal error"
            WifiP2pManager.P2P_UNSUPPORTED -> "WiFi Direct not supported on this device"
            WifiP2pManager.BUSY -> "System is busy, try again"
            WifiP2pManager.NO_SERVICE_REQUESTS -> "No service requests"
            else -> "Unknown error ($reasonCode)"
        }
    }

    fun cleanup() {
        stopDiscovery()
        disconnect()
        unregister()
        Log.d(TAG, "WiFi Direct manager cleaned up")
    }
}

class WiFiDirectBroadcastReceiver(
    private val manager: WiFiDirectManager
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.d("WiFiDirect", "P2P state changed: ${if (isEnabled) "Enabled" else "Disabled"}")
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d("WiFiDirect", "Peer list changed")
                manager.requestPeers()
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                Log.d("WiFiDirect", "Connection changed")
                manager.requestConnectionInfo()
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Log.d("WiFiDirect", "This device changed")
            }
        }
    }
}