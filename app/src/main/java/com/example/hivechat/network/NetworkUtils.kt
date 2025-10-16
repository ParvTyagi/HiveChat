package com.example.hivechat.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.*

/**
 * Network utility functions for HiveChat
 */
object NetworkUtils {

    private const val TAG = "NetworkUtils"

    /**
     * Get the device's local IP address
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && addr is java.net.Inet4Address) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
        }
        return null
    }

    /**
     * Get WiFi hotspot IP address (usually 192.168.43.1)
     */
    fun getHotspotIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                // Hotspot interface names vary by manufacturer
                if (intf.name.contains("ap") ||
                    intf.name.contains("wlan") ||
                    intf.name.contains("softap")) {

                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting hotspot IP: ${e.message}")
        }
        return "192.168.43.1" // Default hotspot IP
    }

    /**
     * Get WiFi information
     */
    fun getWifiInfo(context: Context): WifiInfo? {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo

            if (wifiInfo != null) {
                WifiInfo(
                    ssid = wifiInfo.ssid.replace("\"", ""),
                    bssid = wifiInfo.bssid,
                    ipAddress = intToIp(wifiInfo.ipAddress),
                    linkSpeed = wifiInfo.linkSpeed,
                    rssi = wifiInfo.rssi
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi info: ${e.message}")
            null
        }
    }

    /**
     * Convert integer IP to string format
     */
    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    /**
     * Check if IP address is reachable
     */
    suspend fun isReachable(ipAddress: String, timeout: Int = 1000): Boolean {
        return try {
            val address = InetAddress.getByName(ipAddress)
            address.isReachable(timeout)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking reachability: ${e.message}")
            false
        }
    }

    /**
     * Scan local network for active hosts (simplified version)
     */
    fun scanLocalNetwork(
        baseIp: String,
        onHostFound: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        Thread {
            try {
                val ipPrefix = baseIp.substringBeforeLast(".")
                for (i in 1..254) {
                    val testIp = "$ipPrefix.$i"
                    try {
                        val address = InetAddress.getByName(testIp)
                        if (address.isReachable(100)) {
                            onHostFound(testIp)
                        }
                    } catch (e: Exception) {
                        // Continue scanning
                    }
                }
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Network scan error: ${e.message}")
                onComplete()
            }
        }.start()
    }

    /**
     * Generate unique device ID based on device info
     */
    fun generateDeviceId(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val macAddress = wifiInfo.macAddress ?: "unknown"

            // Create MD5 hash of MAC address
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(macAddress.toByteArray())
            hash.joinToString("") { "%02x".format(it) }.take(8)
        } catch (e: Exception) {
            // Fallback to random ID
            UUID.randomUUID().toString().take(8)
        }
    }

    /**
     * Format bytes to human-readable size
     */
    fun formatFileSize(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            bytes < kb -> "$bytes B"
            bytes < mb -> "%.2f KB".format(bytes / kb)
            bytes < gb -> "%.2f MB".format(bytes / mb)
            else -> "%.2f GB".format(bytes / gb)
        }
    }

    /**
     * Calculate file checksum (MD5)
     */
    fun calculateChecksum(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val fis = FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }

            fis.close()
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating checksum: ${e.message}")
            null
        }
    }

    /**
     * Get network interface information
     */
    fun getNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val interfaces = mutableListOf<NetworkInterfaceInfo>()

        try {
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in networkInterfaces) {
                val addresses = Collections.list(intf.inetAddresses)
                    .filter { it is java.net.Inet4Address }
                    .map { it.hostAddress ?: "" }

                if (addresses.isNotEmpty()) {
                    interfaces.add(
                        NetworkInterfaceInfo(
                            name = intf.name,
                            displayName = intf.displayName,
                            addresses = addresses,
                            isUp = intf.isUp,
                            isLoopback = intf.isLoopback
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network interfaces: ${e.message}")
        }

        return interfaces
    }

    /**
     * Validate IP address format
     */
    fun isValidIpAddress(ip: String): Boolean {
        val ipPattern = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        return ipPattern.matches(ip)
    }

    /**
     * Get subnet mask for IP address
     */
    fun getSubnetMask(ipAddress: String): String {
        return when {
            ipAddress.startsWith("192.168") -> "255.255.255.0"
            ipAddress.startsWith("10.") -> "255.0.0.0"
            ipAddress.startsWith("172.") -> "255.255.0.0"
            else -> "255.255.255.0"
        }
    }

    /**
     * Calculate broadcast address
     */
    fun getBroadcastAddress(ipAddress: String): String? {
        return try {
            val ip = ipAddress.split(".").map { it.toInt() }
            val subnet = getSubnetMask(ipAddress).split(".").map { it.toInt() }

            val broadcast = ip.zip(subnet).map { (ipPart, subnetPart) ->
                ipPart or (255 - subnetPart)
            }

            broadcast.joinToString(".")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating broadcast address: ${e.message}")
            null
        }
    }

    /**
     * Data classes for network information
     */
    data class WifiInfo(
        val ssid: String,
        val bssid: String,
        val ipAddress: String,
        val linkSpeed: Int,
        val rssi: Int
    ) {
        fun getSignalStrength(): String {
            return when {
                rssi >= -50 -> "Excellent"
                rssi >= -60 -> "Good"
                rssi >= -70 -> "Fair"
                else -> "Weak"
            }
        }
    }

    data class NetworkInterfaceInfo(
        val name: String,
        val displayName: String,
        val addresses: List<String>,
        val isUp: Boolean,
        val isLoopback: Boolean
    )
}

/**
 * Extension functions for network operations
 */

/**
 * Send file over output stream with progress callback
 */
fun OutputStream.sendFile(
    file: File,
    onProgress: ((Int) -> Unit)? = null
) {
    try {
        val fileSize = file.length()
        val buffer = ByteArray(8192)
        var totalBytesRead = 0L

        FileInputStream(file).use { fis ->
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                onProgress?.invoke(((totalBytesRead * 100) / fileSize).toInt())
                bytesRead = fis.read(buffer)
            }
            flush()
        }
    } catch (e: Exception) {
        Log.e("NetworkUtils", "Error sending file: ${e.message}")
        throw e
    }
}

/**
 * Receive file from input stream with progress callback
 */
fun InputStream.receiveFile(
    outputFile: File,
    fileSize: Long,
    onProgress: ((Int) -> Unit)? = null
) {
    try {
        val buffer = ByteArray(8192)
        var totalBytesRead = 0L

        FileOutputStream(outputFile).use { fos ->
            var bytesRead = read(buffer)
            while (totalBytesRead < fileSize && bytesRead != -1) {
                fos.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                onProgress?.invoke(((totalBytesRead * 100) / fileSize).toInt())
                bytesRead = read(buffer)
            }
            fos.flush()
        }
    } catch (e: Exception) {
        Log.e("NetworkUtils", "Error receiving file: ${e.message}")
        throw e
    }
}

/**
 * Message protocol for structured communication
 */
object MessageProtocol {

    // Message types
    const val TYPE_TEXT = "TEXT"
    const val TYPE_FILE = "FILE"
    const val TYPE_IMAGE = "IMAGE"
    const val TYPE_SYSTEM = "SYSTEM"
    const val TYPE_PING = "PING"
    const val TYPE_PONG = "PONG"

    // Separators
    private const val FIELD_SEPARATOR = "|:|"
    private const val LINE_END = "\n"

    /**
     * Encode message to protocol format
     * Format: TYPE|:|SENDER|:|CONTENT|:|TIMESTAMP\n
     */
    fun encodeMessage(
        type: String,
        sender: String,
        content: String,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        return "$type$FIELD_SEPARATOR$sender$FIELD_SEPARATOR$content$FIELD_SEPARATOR$timestamp$LINE_END"
    }

    /**
     * Decode message from protocol format
     */
    fun decodeMessage(encodedMessage: String): DecodedMessage? {
        return try {
            val parts = encodedMessage.trim().split(FIELD_SEPARATOR)
            if (parts.size >= 4) {
                DecodedMessage(
                    type = parts[0],
                    sender = parts[1],
                    content = parts[2],
                    timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                )
            } else null
        } catch (e: Exception) {
            Log.e("MessageProtocol", "Error decoding message: ${e.message}")
            null
        }
    }

    /**
     * Create text message
     */
    fun createTextMessage(sender: String, text: String): String {
        return encodeMessage(TYPE_TEXT, sender, text)
    }

    /**
     * Create file transfer message
     */
    fun createFileMessage(
        sender: String,
        fileName: String,
        fileSize: Long,
        checksum: String
    ): String {
        val content = "$fileName;$fileSize;$checksum"
        return encodeMessage(TYPE_FILE, sender, content)
    }

    /**
     * Create system message
     */
    fun createSystemMessage(content: String): String {
        return encodeMessage(TYPE_SYSTEM, "System", content)
    }

    /**
     * Create ping message for connection check
     */
    fun createPingMessage(sender: String): String {
        return encodeMessage(TYPE_PING, sender, "ping")
    }

    /**
     * Create pong response
     */
    fun createPongMessage(sender: String): String {
        return encodeMessage(TYPE_PONG, sender, "pong")
    }

    data class DecodedMessage(
        val type: String,
        val sender: String,
        val content: String,
        val timestamp: Long
    )

    data class FileInfo(
        val fileName: String,
        val fileSize: Long,
        val checksum: String
    ) {
        companion object {
            fun fromContent(content: String): FileInfo? {
                return try {
                    val parts = content.split(";")
                    if (parts.size >= 3) {
                        FileInfo(
                            fileName = parts[0],
                            fileSize = parts[1].toLong(),
                            checksum = parts[2]
                        )
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}

/**
 * Connection helper for managing socket connections
 */
class ConnectionHelper {

    companion object {
        /**
         * Test connection to host
         */
        fun testConnection(host: String, port: Int, timeout: Int = 3000): Boolean {
            return try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), timeout)
                socket.close()
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Get available port in range
         */
        fun getAvailablePort(startPort: Int = 8000, endPort: Int = 9000): Int? {
            for (port in startPort..endPort) {
                try {
                    java.net.ServerSocket(port).use {
                        return port
                    }
                } catch (e: Exception) {
                    // Port in use, try next
                }
            }
            return null
        }

        /**
         * Create server socket with retry
         */
        fun createServerSocket(
            preferredPort: Int,
            maxRetries: Int = 5
        ): java.net.ServerSocket? {
            var port = preferredPort
            var attempts = 0

            while (attempts < maxRetries) {
                try {
                    return java.net.ServerSocket(port)
                } catch (e: Exception) {
                    port++
                    attempts++
                }
            }
            return null
        }

        /**
         * Send data with retry
         */
        fun sendWithRetry(
            socket: java.net.Socket,
            data: ByteArray,
            maxRetries: Int = 3
        ): Boolean {
            var attempts = 0

            while (attempts < maxRetries) {
                try {
                    socket.getOutputStream().write(data)
                    socket.getOutputStream().flush()
                    return true
                } catch (e: Exception) {
                    attempts++
                    if (attempts >= maxRetries) {
                        Log.e("ConnectionHelper", "Send failed after $maxRetries attempts")
                        return false
                    }
                    Thread.sleep(100 * attempts.toLong())
                }
            }
            return false
        }
    }
}

/**
 * Performance monitor for network operations
 */
class NetworkPerformanceMonitor {
    private var startTime: Long = 0
    private var totalBytesSent: Long = 0
    private var totalBytesReceived: Long = 0
    private var messagesSent: Int = 0
    private var messagesReceived: Int = 0

    fun start() {
        startTime = System.currentTimeMillis()
        totalBytesSent = 0
        totalBytesReceived = 0
        messagesSent = 0
        messagesReceived = 0
    }

    fun recordSent(bytes: Long) {
        totalBytesSent += bytes
        messagesSent++
    }

    fun recordReceived(bytes: Long) {
        totalBytesReceived += bytes
        messagesReceived++
    }

    fun getStatistics(): NetworkStatistics {
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

        return NetworkStatistics(
            elapsedSeconds = elapsedTime,
            bytesSent = totalBytesSent,
            bytesReceived = totalBytesReceived,
            messagesSent = messagesSent,
            messagesReceived = messagesReceived,
            sendRate = if (elapsedTime > 0) totalBytesSent / elapsedTime else 0.0,
            receiveRate = if (elapsedTime > 0) totalBytesReceived / elapsedTime else 0.0
        )
    }

    data class NetworkStatistics(
        val elapsedSeconds: Double,
        val bytesSent: Long,
        val bytesReceived: Long,
        val messagesSent: Int,
        val messagesReceived: Int,
        val sendRate: Double,
        val receiveRate: Double
    ) {
        fun getSendRateFormatted(): String =
            NetworkUtils.formatFileSize(sendRate.toLong()) + "/s"

        fun getReceiveRateFormatted(): String =
            NetworkUtils.formatFileSize(receiveRate.toLong()) + "/s"

        fun getTotalDataFormatted(): String =
            NetworkUtils.formatFileSize(bytesSent + bytesReceived)
    }
}

/**
 * Keep-alive manager for maintaining connections
 */
class KeepAliveManager(
    private val socket: java.net.Socket,
    private val intervalMs: Long = 30000 // 30 seconds
) {
    private var keepAliveThread: Thread? = null
    private var isRunning = false

    fun start() {
        isRunning = true
        keepAliveThread = Thread {
            while (isRunning && socket.isConnected && !socket.isClosed) {
                try {
                    // Send ping
                    val ping = MessageProtocol.createPingMessage("KeepAlive")
                    socket.getOutputStream().write(ping.toByteArray())
                    socket.getOutputStream().flush()

                    Thread.sleep(intervalMs)
                } catch (e: Exception) {
                    Log.e("KeepAlive", "Keep-alive failed: ${e.message}")
                    break
                }
            }
        }
        keepAliveThread?.start()
    }

    fun stop() {
        isRunning = false
        keepAliveThread?.interrupt()
        keepAliveThread = null
    }
}

/**
 * Connection timeout handler
 */
class TimeoutHandler(
    private val timeoutMs: Long = 60000, // 1 minute
    private val onTimeout: () -> Unit
) {
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var timeoutThread: Thread? = null
    private var isRunning = false

    fun start() {
        isRunning = true
        timeoutThread = Thread {
            while (isRunning) {
                try {
                    Thread.sleep(5000) // Check every 5 seconds

                    val elapsed = System.currentTimeMillis() - lastActivityTime
                    if (elapsed > timeoutMs) {
                        onTimeout()
                        break
                    }
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        timeoutThread?.start()
    }

    fun resetTimeout() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun stop() {
        isRunning = false
        timeoutThread?.interrupt()
        timeoutThread = null
    }
}