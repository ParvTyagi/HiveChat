package com.example.hivechat.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WiFiHotspotManager(private val context: Context) {

    private val TAG = "WiFiHotspotManager"
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _hotspotState = MutableStateFlow<HotspotState>(HotspotState.Disabled)
    val hotspotState: StateFlow<HotspotState> get() = _hotspotState

    sealed class HotspotState {
        object Disabled : HotspotState()
        data class Enabled(val ssid: String, val password: String) : HotspotState()
        data class Error(val message: String) : HotspotState()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun startHotspot(): Boolean {
        return createLocalOnlyHotspot()
    }

    fun stopHotspot(): Boolean {
        // Implement stopping logic if needed for your Android version
        _hotspotState.value = HotspotState.Disabled
        return true
    }

    @RequiresPermission(
        allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ]
    )
    @Suppress("DEPRECATION")
    private fun createLocalOnlyHotspot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }

                wifiManager.startLocalOnlyHotspot(
                    object : WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                            super.onStarted(reservation)
                            reservation?.let {
                                val config = it.wifiConfiguration
                                _hotspotState.value = HotspotState.Enabled(
                                    ssid = config?.SSID ?: "Unknown",
                                    password = config?.preSharedKey ?: ""
                                )
                                Log.d(TAG, "Local hotspot started: ${config?.SSID}")
                                startServer()
                            }
                        }

                        override fun onFailed(reason: Int) {
                            super.onFailed(reason)
                            val error = when (reason) {
                                ERROR_GENERIC -> "Generic error"
                                ERROR_INCOMPATIBLE_MODE -> "Incompatible mode"
                                ERROR_NO_CHANNEL -> "No channel available"
                                ERROR_TETHERING_DISALLOWED -> "Tethering not allowed"
                                else -> "Unknown error"
                            }
                            Log.e(TAG, "Hotspot failed: $error")
                            _hotspotState.value = HotspotState.Error(error)
                        }
                    },
                    Handler(context.mainLooper)
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "Local hotspot error: ${e.message}")
                _hotspotState.value = HotspotState.Error("Failed to create hotspot: ${e.message}")
                false
            }
        } else {
            false
        }
    }

    private fun startServer() {
        // TODO: implement server logic if needed
    }
}
