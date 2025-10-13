package com.example.hivechat.model

import java.util.UUID

/**
 * Represents a discovered device on the network
 */
data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ipAddress: String,
    val port: Int = 8888,
    val lastSeen: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0 // Add this for unread messages
)

/**
 * Represents a chat message
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val senderName: String,
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false
)

/**
 * Network packet for device discovery (UDP)
 */
data class DiscoveryPacket(
    val deviceId: String,
    val deviceName: String,
    val port: Int = 8888
)

/**
 * Network packet for messages (TCP)
 */
data class MessagePacket(
    val messageId: String,
    val text: String,
    val senderName: String,
    val senderId: String,
    val timestamp: Long
)
