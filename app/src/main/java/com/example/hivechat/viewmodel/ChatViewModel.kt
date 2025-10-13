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

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    val devices: StateFlow<List<Device>> = networkManager.devices
    val isDiscovering: StateFlow<Boolean> = networkManager.isDiscovering
    val allMessages: StateFlow<Map<String, List<Message>>> = networkManager.messages

    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    // Map to store unread message counts per device
    private val _unreadMessages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadMessages: StateFlow<Map<String, Int>> = _unreadMessages.asStateFlow()

    private var previousMessageCounts = mutableMapOf<String, Int>()

    init {
        // Monitor incoming messages and update unread counts
        viewModelScope.launch {
            allMessages.collect { messageMap ->
                val currentDevice = _selectedDevice.value
                messageMap.forEach { (deviceId, messages) ->
                    val previousCount = previousMessageCounts[deviceId] ?: 0
                    val currentCount = messages.size

                    // Check if there are new messages
                    if (currentCount > previousCount) {
                        val newMessages = messages.drop(previousCount)
                        // Only increment unread if not currently viewing this device's chat
                        if (deviceId != currentDevice?.id) {
                            // Count messages that are from the other user (not sent by me)
                            val unreadCount = newMessages.count { message ->
                                message.senderId != _userName.value
                            }
                            if (unreadCount > 0) {
                                incrementUnreadBy(deviceId, unreadCount)
                            }
                        }
                    }

                    previousMessageCounts[deviceId] = currentCount
                }
            }
        }
    }

    fun setUserName(name: String) {
        _userName.value = name
        networkManager.initialize(name)

        val prefs = getApplication<Application>()
            .getSharedPreferences("HiveChat", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("user_name", name).apply()
    }

    fun getSavedUserName(): String? {
        val prefs = getApplication<Application>()
            .getSharedPreferences("HiveChat", android.content.Context.MODE_PRIVATE)
        return prefs.getString("user_name", null)
    }

    fun clearUserName() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("HiveChat", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("user_name").apply()
        _userName.value = ""
    }

    fun startDiscovery() {
        networkManager.startDiscovery()
    }

    fun stopDiscovery() {
        networkManager.stopDiscovery()
    }

    fun startDiscoveryLimited(durationMs: Long = 10_000L) {
        startDiscovery()
        viewModelScope.launch {
            kotlinx.coroutines.delay(durationMs)
            stopDiscovery()
        }
    }

    fun selectDevice(device: Device) {
        _selectedDevice.value = device
        // Reset unread count when opening chat
        _unreadMessages.value = _unreadMessages.value.toMutableMap().also {
            it[device.id] = 0
        }
    }

    fun clearSelectedDevice() {
        _selectedDevice.value = null
    }

    fun sendMessage(text: String) {
        val device = _selectedDevice.value ?: return
        viewModelScope.launch {
            networkManager.sendMessage(device.id, text)
        }
    }

    private fun incrementUnreadBy(deviceId: String, count: Int) {
        _unreadMessages.value = _unreadMessages.value.toMutableMap().also {
            it[deviceId] = (it[deviceId] ?: 0) + count
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkManager.cleanup()
    }
}