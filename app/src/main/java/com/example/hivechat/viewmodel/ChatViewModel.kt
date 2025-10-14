package com.example.hivechat.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import com.example.hivechat.network.NetworkManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val networkManager = NetworkManager(context)

    // User data
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    // Devices
    val devices: StateFlow<List<Device>> = networkManager.devices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isDiscovering: StateFlow<Boolean> = networkManager.isDiscovering
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val connectionStatus: StateFlow<String> = networkManager.connectionStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Messages
    val allMessages: StateFlow<Map<String, List<Message>>> = networkManager.messages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val unreadMessages: StateFlow<Map<String, Int>> = networkManager.unreadMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Selected device for chat
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    /**
     * Set user name and initialize network
     */
    fun setUserName(name: String) {
        _userName.value = name
        saveUserName(name)
        networkManager.initialize(name)
    }

    /**
     * Save user name to preferences
     */
    private fun saveUserName(name: String) {
        context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
            .edit()
            .putString("user_name", name)
            .apply()
    }

    /**
     * Get saved user name from preferences
     */
    fun getSavedUserName(): String? {
        return context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
            .getString("user_name", null)
    }

    /**
     * Clear user name from preferences
     */
    fun clearUserName() {
        _userName.value = ""
        context.getSharedPreferences("HiveChat", Context.MODE_PRIVATE)
            .edit()
            .remove("user_name")
            .apply()
    }

    /**
     * Start device discovery
     */
    fun startDiscovery() {
        networkManager.startDiscovery()
    }

    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        networkManager.stopDiscovery()
    }

    /**
     * Select a device for chat
     */
    fun selectDevice(device: Device) {
        _selectedDevice.value = device
        // Clear unread messages when entering chat
        networkManager.clearUnreadMessages(device.id)
    }

    /**
     * Clear selected device
     */
    fun clearSelectedDevice() {
        _selectedDevice.value = null
    }

    /**
     * Send message to selected device
     */
    fun sendMessage(text: String) {
        val device = _selectedDevice.value ?: return
        networkManager.sendMessage(device.id, text)
    }

    /**
     * Cleanup on ViewModel clear
     */
    override fun onCleared() {
        super.onCleared()
        networkManager.cleanup()
    }
}