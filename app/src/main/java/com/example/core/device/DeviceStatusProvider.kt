package com.example.core.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.example.core.accessibility.JarvisAccessibilityService
import org.json.JSONObject
import java.io.File

data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean,
    val statusDescription: String,
    val isPowerSaveMode: Boolean
)

data class NetworkStatus(
    val isConnected: Boolean,
    val connectionType: String,
    val isWifiEnabled: Boolean
)

data class VolumeStatus(
    val mediaVolume: Int,
    val maxMediaVolume: Int,
    val ringVolume: Int,
    val maxRingVolume: Int,
    val alarmVolume: Int,
    val maxAlarmVolume: Int,
    val notificationVolume: Int,
    val maxNotificationVolume: Int,
    val ringerMode: String
)

data class StorageStatus(
    val totalInternalBytes: Long,
    val availableInternalBytes: Long,
    val totalInternalGb: Double,
    val availableInternalGb: Double,
    val freePercent: Int
)

data class DisplayStatus(
    val isScreenOn: Boolean,
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int
)

data class DeviceStatusReport(
    val battery: BatteryStatus,
    val network: NetworkStatus,
    val volume: VolumeStatus,
    val storage: StorageStatus,
    val display: DisplayStatus,
    val currentForegroundApp: String,
    val isBluetoothEnabled: Boolean,
    val isLocationEnabled: Boolean,
    val isFlashlightSupported: Boolean,
    val isAccessibilityActive: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSummaryText(): String {
        return buildString {
            append("• Battery: ${battery.level}% (${if (battery.isCharging) "Charging" else "On Battery"})\n")
            append("• Network: ${if (network.isConnected) network.connectionType else "Disconnected"}\n")
            append("• Storage: %.1f GB free / %.1f GB total (${storage.freePercent}%% free)\n".format(storage.availableInternalGb, storage.totalInternalGb))
            append("• Media Volume: ${volume.mediaVolume}/${volume.maxMediaVolume} (Ringer: ${volume.ringerMode})\n")
            append("• Active App: $currentForegroundApp\n")
            append("• Accessibility Hands: ${if (isAccessibilityActive) "Active" else "Disabled"}")
        }
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("currentForegroundApp", currentForegroundApp)
            put("isAccessibilityActive", isAccessibilityActive)
            put("battery", JSONObject().apply {
                put("level", battery.level)
                put("isCharging", battery.isCharging)
                put("status", battery.statusDescription)
                put("powerSave", battery.isPowerSaveMode)
            })
            put("network", JSONObject().apply {
                put("isConnected", network.isConnected)
                put("type", network.connectionType)
                put("wifiEnabled", network.isWifiEnabled)
            })
            put("storage", JSONObject().apply {
                put("availableGb", storage.availableInternalGb)
                put("totalGb", storage.totalInternalGb)
                put("freePercent", storage.freePercent)
            })
            put("volume", JSONObject().apply {
                put("media", volume.mediaVolume)
                put("maxMedia", volume.maxMediaVolume)
                put("ringerMode", volume.ringerMode)
            })
            put("bluetoothEnabled", isBluetoothEnabled)
            put("locationEnabled", isLocationEnabled)
            put("flashlightSupported", isFlashlightSupported)
        }
    }
}

class DeviceStatusProvider(private val context: Context) {

    fun getDeviceStatus(): DeviceStatusReport {
        return DeviceStatusReport(
            battery = getBatteryStatus(),
            network = getNetworkStatus(),
            volume = getVolumeStatus(),
            storage = getStorageStatus(),
            display = getDisplayStatus(),
            currentForegroundApp = getCurrentForegroundApp(),
            isBluetoothEnabled = isBluetoothEnabled(),
            isLocationEnabled = isLocationEnabled(),
            isFlashlightSupported = isFlashlightSupported(),
            isAccessibilityActive = JarvisAccessibilityService.isAccessibilityEnabled(context)
        )
    }

    fun getBatteryStatus(): BatteryStatus {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.let { intent ->
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) (rawLevel * 100) / scale else -1
        } ?: run {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val statusDesc = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> if (isCharging) "Charging" else "Normal"
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = pm?.isPowerSaveMode == true

        return BatteryStatus(
            level = level.coerceIn(0, 100),
            isCharging = isCharging,
            statusDescription = statusDesc,
            isPowerSaveMode = isPowerSave
        )
    }

    fun getNetworkStatus(): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var isConnected = false
        var connectionType = "None"

        if (cm != null) {
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps != null) {
                isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                connectionType = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Mobile Data"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth Tethering"
                    else -> "Connected"
                }
            }
        }

        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiEnabled = wm?.isWifiEnabled == true

        return NetworkStatus(
            isConnected = isConnected,
            connectionType = connectionType,
            isWifiEnabled = isWifiEnabled
        )
    }

    fun getVolumeStatus(): VolumeStatus {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am == null) {
            return VolumeStatus(5, 15, 5, 15, 5, 15, 5, 15, "Normal")
        }

        val mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxMedia = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ringVol = am.getStreamVolume(AudioManager.STREAM_RING)
        val maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        val alarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val notifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val maxNotif = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

        val ringerMode = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }

        return VolumeStatus(
            mediaVolume = mediaVol,
            maxMediaVolume = maxMedia,
            ringVolume = ringVol,
            maxRingVolume = maxRing,
            alarmVolume = alarmVol,
            maxAlarmVolume = maxAlarm,
            notificationVolume = notifVol,
            maxNotificationVolume = maxNotif,
            ringerMode = ringerMode
        )
    }

    fun getStorageStatus(): StorageStatus {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availBytes = availableBlocks * blockSize

            val totalGb = totalBytes.toDouble() / (1024 * 1024 * 1024)
            val availGb = availBytes.toDouble() / (1024 * 1024 * 1024)
            val freePct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0

            StorageStatus(
                totalInternalBytes = totalBytes,
                availableInternalBytes = availBytes,
                totalInternalGb = totalGb,
                availableInternalGb = availGb,
                freePercent = freePct
            )
        } catch (e: Exception) {
            StorageStatus(0L, 0L, 0.0, 0.0, 0)
        }
    }

    fun getDisplayStatus(): DisplayStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenOn = pm?.isInteractive == true
        val metrics = context.resources.displayMetrics
        return DisplayStatus(
            isScreenOn = isScreenOn,
            widthPixels = metrics.widthPixels,
            heightPixels = metrics.heightPixels,
            densityDpi = metrics.densityDpi
        )
    }

    fun getCurrentForegroundApp(): String {
        return JarvisAccessibilityService.currentForegroundApp.value
    }

    fun isBluetoothEnabled(): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    fun isLocationEnabled(): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (e: Exception) {
            false
        }
    }

    fun isFlashlightSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
}
