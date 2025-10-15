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

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val networkManager = HybridNetworkManager(context)

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _networkMode = MutableStateFlow(NetworkMode.NORMAL_WIFI)
    val networkMode: StateFlow<NetworkMode> = _networkMode.asStateFlow()

    val devices: StateFlow<List<Device>> = networkManager.devices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isDiscovering: StateFlow<Boolean> = networkManager.isDiscovering
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val connectionStatus: StateFlow<String> = networkManager.connectionStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val allMessages: StateFlow<Map<String, List<Message>>> = networkManager.messages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val unreadMessages: StateFlow<Map<String, Int>> = networkManager.unreadMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    private val _showWiFiDirectDialog = MutableStateFlow(false)
    val showWiFiDirectDialog: StateFlow<Boolean> = _showWiFiDirectDialog.asStateFlow()

    init {
        viewModelScope.launch {
            connectionStatus.collect { status ->
                if (networkManager.isNetworkRestricted() &&
                    _networkMode.value == NetworkMode.NORMAL_WIFI) {
                    _showWiFiDirectDialog.value = true
                }
            }
        }
    }

    fun setUserName(name: String) {
        _userName.value = name
        saveUserName(name)
        networkManager.initialize(name)
    }

    private fun saveUserName(name: String) {
        context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
            .edit()
            .putString("user_name", name)
            .apply()
    }

    fun getSavedUserName(): String? {
        return context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
            .getString("user_name", null)
    }

    fun clearUserName() {
        _userName.value = ""
        context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
            .edit()
            .remove("user_name")
            .apply()
    }

    fun startDiscovery() {
        networkManager.startDiscovery()
    }

    fun stopDiscovery() {
        networkManager.stopDiscovery()
    }

    fun switchToWiFiDirect() {
        networkManager.switchToWiFiDirect(_userName.value)
        _networkMode.value = NetworkMode.WIFI_DIRECT
        _showWiFiDirectDialog.value = false
    }

    fun switchToNormalWiFi() {
        networkManager.switchToNormalWiFi()
        _networkMode.value = NetworkMode.NORMAL_WIFI
        _showWiFiDirectDialog.value = false
    }

    fun dismissWiFiDirectDialog() {
        _showWiFiDirectDialog.value = false
    }

    fun selectDevice(device: Device) {
        _selectedDevice.value = device
        networkManager.clearUnreadMessages(device.id)
    }

    fun clearSelectedDevice() {
        _selectedDevice.value = null
    }

    fun sendMessage(text: String) {
        val device = _selectedDevice.value ?: return
        networkManager.sendMessage(device.id, text)
    }

    override fun onCleared() {
        super.onCleared()
        networkManager.cleanup()
    }
}