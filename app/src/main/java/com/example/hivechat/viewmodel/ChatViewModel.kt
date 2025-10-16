package com.example.hivechat.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import com.example.hivechat.network.HybridNetworkManager
import com.example.hivechat.network.NetworkMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val hybridNetworkManager = HybridNetworkManager(application)

    // User name management
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName

    // Device management
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice

    // WiFi Direct dialog
    private val _showWiFiDirectDialog = MutableStateFlow(false)
    val showWiFiDirectDialog: StateFlow<Boolean> = _showWiFiDirectDialog

    // Network flows
    val devices = hybridNetworkManager.devices
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isDiscovering = hybridNetworkManager.isDiscovering
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val connectionStatus = hybridNetworkManager.connectionStatus
        .stateIn(viewModelScope, SharingStarted.Lazily, "Initializing...")

    val unreadMessages = hybridNetworkManager.unreadMessages
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val allMessages = hybridNetworkManager.messages
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val networkMode = hybridNetworkManager.networkMode
        .stateIn(viewModelScope, SharingStarted.Lazily, NetworkMode.NORMAL_WIFI)

    init {
        // Load saved user name
        val savedName = getSavedUserName()
        if (savedName != null) {
            _userName.value = savedName
            hybridNetworkManager.initialize(savedName)
        }

        // Auto periodic discovery
        viewModelScope.launch {
            while (true) {
                if (_userName.value.isNotEmpty()) {
                    hybridNetworkManager.startDiscovery()
                    delay(15000L) // wait 15s before next auto-discovery
                } else {
                    delay(5000L)
                }
            }
        }

        // Monitor network restrictions and show WiFi Direct dialog
        viewModelScope.launch {
            hybridNetworkManager.connectionStatus.collect { status ->
                if (hybridNetworkManager.isNetworkRestricted() &&
                    networkMode.value == NetworkMode.NORMAL_WIFI) {
                    _showWiFiDirectDialog.value = true
                }
            }
        }
    }

    // -------------------- USER NAME --------------------

    fun setUserName(name: String) {
        _userName.value = name
        saveUserName(name)
        hybridNetworkManager.initialize(name)
    }

    fun getSavedUserName(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
        return prefs.getString("user_name", null)
    }

    private fun saveUserName(name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
        prefs.edit().putString("user_name", name).apply()
    }

    fun clearUserName() {
        _userName.value = ""
        val prefs = getApplication<Application>().getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
        prefs.edit().remove("user_name").apply()
    }

    // -------------------- DEVICE --------------------

    fun selectDevice(device: Device) {
        _selectedDevice.value = device
        hybridNetworkManager.clearUnreadMessages(device.id)
    }

    fun clearSelectedDevice() {
        _selectedDevice.value = null
    }

    // -------------------- DISCOVERY --------------------

    fun startDiscovery() {
        hybridNetworkManager.startDiscovery()
    }

    fun stopDiscovery() {
        hybridNetworkManager.stopDiscovery()
    }

    // -------------------- MESSAGING --------------------

    fun sendMessage(text: String) {
        val device = _selectedDevice.value ?: return
        hybridNetworkManager.sendMessage(device.id, text)
    }

    // -------------------- WIFI DIRECT --------------------

    fun switchToWiFiDirect() {
        val name = _userName.value
        if (name.isNotEmpty()) {
            hybridNetworkManager.switchToWiFiDirect(name)
            _showWiFiDirectDialog.value = false
        }
    }

    fun switchToNormalWiFi() {
        hybridNetworkManager.switchToNormalWiFi()
    }

    fun dismissWiFiDirectDialog() {
        _showWiFiDirectDialog.value = false
    }

    fun connectToWiFiDirectPeer(deviceAddress: String) {
        hybridNetworkManager.connectToWiFiDirectPeer(deviceAddress)
    }

    fun disconnectWiFiDirect() {
        hybridNetworkManager.disconnectWiFiDirect()
    }

    override fun onCleared() {
        super.onCleared()
        hybridNetworkManager.cleanup()
    }
}
