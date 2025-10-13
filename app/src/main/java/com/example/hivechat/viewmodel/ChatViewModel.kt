package com.example.hivechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import com.example.hivechat.network.NetworkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val networkManager = NetworkManager(application)

    // User name
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    // Discovered devices
    val devices: StateFlow<List<Device>> = networkManager.devices

    // Discovery state
    val isDiscovering: StateFlow<Boolean> = networkManager.isDiscovering

    // All messages
    val allMessages: StateFlow<Map<String, List<Message>>> = networkManager.messages

    // Currently selected device
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    /**
     * Set user name and initialize network
     */
    fun setUserName(name: String) {
        _userName.value = name
        networkManager.initialize(name)

        // Save name to preferences
        val prefs = getApplication<Application>().getSharedPreferences("HiveChat", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("user_name", name).apply()
    }

    /**
     * Get saved user name
     */
    fun getSavedUserName(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("HiveChat", android.content.Context.MODE_PRIVATE)
        return prefs.getString("user_name", null)
    }

    /**
     * Clear saved user name (for logout)
     */
    fun clearUserName() {
        val prefs = getApplication<Application>().getSharedPreferences("HiveChat", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("user_name").apply()
        _userName.value = ""
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
     * Select a device to chat with
     */
    fun selectDevice(device: Device) {
        _selectedDevice.value = device
    }

    /**
     * Clear selected device
     */
    fun clearSelectedDevice() {
        _selectedDevice.value = null
    }

    /**
     * Send a message to the selected device
     */
    fun sendMessage(text: String) {
        val device = _selectedDevice.value ?: return
        viewModelScope.launch {
            networkManager.sendMessage(device.id, text)
        }
    }

    /**
     * Get messages for a specific device
     */
    fun getMessagesForDevice(deviceId: String): List<Message> {
        return networkManager.getMessagesForDevice(deviceId)
    }

    override fun onCleared() {
        super.onCleared()
        networkManager.cleanup()
    }
}