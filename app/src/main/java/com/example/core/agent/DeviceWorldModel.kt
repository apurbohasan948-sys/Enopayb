package com.example.core.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.capability.CapabilityManager
import com.example.core.vision.UnifiedScreen

data class DeviceStateSnapshot(
    val foregroundPackage: String,
    val currentActivity: String,
    val totalScreenNodes: Int,
    val isAccessibilityActive: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isNetworkConnected: Boolean,
    val networkType: String,
    val activeTask: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class DeviceWorldModel(
    private val context: Context,
    private val capabilityManager: CapabilityManager = CapabilityManager(context)
) {
    var latestSnapshot: DeviceStateSnapshot = createSnapshot()
        private set

    fun refresh(currentTask: String? = null, liveScreen: UnifiedScreen? = null): DeviceStateSnapshot {
        val diag = JarvisAccessibilityService.getDiagnostics(context)
        val batteryStatus = getBatteryInfo()
        val networkInfo = getNetworkInfo()

        val foregroundApp = liveScreen?.packageName ?: if (diag.currentPackage.isNotBlank()) diag.currentPackage else context.packageName
        val activityName = diag.currentPackage.substringAfterLast(".", "MainActivity")
        val totalNodes = liveScreen?.totalNodes ?: diag.totalNodes

        val snapshot = DeviceStateSnapshot(
            foregroundPackage = foregroundApp,
            currentActivity = activityName,
            totalScreenNodes = totalNodes,
            isAccessibilityActive = diag.isConnected,
            batteryLevel = batteryStatus.first,
            isCharging = batteryStatus.second,
            isNetworkConnected = networkInfo.first,
            networkType = networkInfo.second,
            activeTask = currentTask
        )
        latestSnapshot = snapshot
        return snapshot
    }

    private fun createSnapshot(): DeviceStateSnapshot {
        return DeviceStateSnapshot(
            foregroundPackage = context.packageName,
            currentActivity = "MainActivity",
            totalScreenNodes = 0,
            isAccessibilityActive = JarvisAccessibilityService.isServiceActive.value,
            batteryLevel = 100,
            isCharging = false,
            isNetworkConnected = true,
            networkType = "WIFI",
            activeTask = null
        )
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 85
            Pair(percent, isCharging)
        } catch (e: Exception) {
            Pair(85, false)
        }
    }

    private fun getNetworkInfo(): Pair<Boolean, String> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return Pair(false, "DISCONNECTED")
            val caps = cm.getNetworkCapabilities(network) ?: return Pair(false, "DISCONNECTED")
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "ACTIVE"
            }
            Pair(true, type)
        } catch (e: Exception) {
            Pair(true, "UNKNOWN")
        }
    }
}
