package com.example.core.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.DeviceCapabilityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class CapabilityDescriptor(
    val id: String,
    val name: String,
    val category: String,
    val available: Boolean,
    val permission: String,
    val enabled: Boolean,
    val restricted: Boolean,
    val reason: String,
    val setupAction: String? = null
) {
    fun toEntity(): DeviceCapabilityEntity {
        return DeviceCapabilityEntity(
            id = id,
            name = name,
            category = category,
            available = available,
            permission = permission,
            enabled = enabled,
            restricted = restricted,
            reason = reason,
            lastChecked = System.currentTimeMillis()
        )
    }
}

class DeviceCapabilityManager(
    private val context: Context,
    private val jarvisDao: JarvisDao? = null,
    private val deviceManager: JarvisDeviceManager = JarvisDeviceManager(context)
) {

    private val _capabilities = MutableStateFlow<Map<String, CapabilityDescriptor>>(emptyMap())
    val capabilities: StateFlow<Map<String, CapabilityDescriptor>> = _capabilities.asStateFlow()

    init {
        refreshCapabilities()
    }

    fun getCapability(id: String): CapabilityDescriptor {
        val upperId = id.trim().uppercase()
        return _capabilities.value[upperId] ?: evaluateCapability(upperId)
    }

    fun isCapabilityAvailable(id: String): Boolean {
        val cap = getCapability(id)
        return cap.available && cap.enabled && !cap.restricted
    }

    fun getAllCapabilities(): List<CapabilityDescriptor> {
        return evaluateAllCapabilities()
    }

    fun refreshCapabilities(): List<CapabilityDescriptor> {
        val list = evaluateAllCapabilities()
        val map = list.associateBy { it.id }
        _capabilities.value = map
        return list
    }

    suspend fun syncCapabilitiesToDatabase() {
        if (jarvisDao == null) return
        val list = refreshCapabilities()
        val entities = list.map { it.toEntity() }
        jarvisDao.insertDeviceCapabilities(entities)
    }

    suspend fun detectCapabilities(): List<CapabilityDescriptor> = withContext(Dispatchers.IO) {
        val list = refreshCapabilities()
        if (jarvisDao != null) {
            val entities = list.map { it.toEntity() }
            jarvisDao.insertDeviceCapabilities(entities)
        }
        list
    }

    private fun evaluateAllCapabilities(): List<CapabilityDescriptor> {
        return listOf(
            evaluateCapability("APP_LAUNCH"),
            evaluateCapability("APP_INFO"),
            evaluateCapability("ACCESSIBILITY"),
            evaluateCapability("SCREEN_READING"),
            evaluateCapability("SCREEN_INTERACTION"),
            evaluateCapability("OVERLAY"),
            evaluateCapability("MICROPHONE"),
            evaluateCapability("CAMERA"),
            evaluateCapability("FLASHLIGHT"),
            evaluateCapability("TELEPHONY"),
            evaluateCapability("CONTACTS"),
            evaluateCapability("SMS"),
            evaluateCapability("NOTIFICATIONS"),
            evaluateCapability("MEDIA_CONTROL"),
            evaluateCapability("VOLUME_CONTROL"),
            evaluateCapability("BATTERY_STATUS"),
            evaluateCapability("NETWORK_STATUS"),
            evaluateCapability("BLUETOOTH"),
            evaluateCapability("LOCATION_STATUS"),
            evaluateCapability("DEVICE_SETTINGS"),
            evaluateCapability("FILE_ACCESS"),
            evaluateCapability("INSTALLATION_STATUS")
        )
    }

    fun evaluateCapability(id: String): CapabilityDescriptor {
        val pm = context.packageManager
        return when (id.uppercase()) {
            "APP_LAUNCH" -> {
                CapabilityDescriptor(
                    id = "APP_LAUNCH",
                    name = "Application Launch",
                    category = "SYSTEM_CONTROL",
                    available = true,
                    permission = "NONE",
                    enabled = true,
                    restricted = false,
                    reason = "PackageManager and explicit Activity Launch Intents are operational."
                )
            }
            "APP_INFO" -> {
                CapabilityDescriptor(
                    id = "APP_INFO",
                    name = "Application Information & Metadata",
                    category = "SYSTEM_CONTROL",
                    available = true,
                    permission = "QUERY_ALL_PACKAGES",
                    enabled = true,
                    restricted = false,
                    reason = "Installed application querying and details resolution are operational."
                )
            }
            "ACCESSIBILITY" -> {
                val isEnabled = JarvisAccessibilityService.isAccessibilityEnabled(context)
                val isConnected = JarvisAccessibilityService.instance != null
                CapabilityDescriptor(
                    id = "ACCESSIBILITY",
                    name = "Accessibility Hands Controller",
                    category = "UI_AUTOMATION",
                    available = true,
                    permission = "BIND_ACCESSIBILITY_SERVICE",
                    enabled = isEnabled,
                    restricted = !isEnabled,
                    reason = if (isEnabled && isConnected) "Accessibility service is active and connected."
                    else if (isEnabled) "Accessibility service is enabled in settings but binding."
                    else "Accessibility service is disabled. Requires user activation in Android Accessibility Settings.",
                    setupAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            }
            "SCREEN_READING" -> {
                val isEnabled = JarvisAccessibilityService.isAccessibilityEnabled(context)
                val rootAvail = JarvisAccessibilityService.instance?.rootInActiveWindow != null
                CapabilityDescriptor(
                    id = "SCREEN_READING",
                    name = "Screen Node Hierarchy & UI Reading",
                    category = "VISION_AND_READING",
                    available = true,
                    permission = "BIND_ACCESSIBILITY_SERVICE",
                    enabled = isEnabled,
                    restricted = !isEnabled,
                    reason = if (isEnabled && rootAvail) "Active window view tree is inspectable."
                    else if (isEnabled) "Accessibility is active; waiting for foreground window focus."
                    else "Requires Accessibility Service activation to parse UI nodes.",
                    setupAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            }
            "SCREEN_INTERACTION" -> {
                val isEnabled = JarvisAccessibilityService.isAccessibilityEnabled(context)
                CapabilityDescriptor(
                    id = "SCREEN_INTERACTION",
                    name = "Screen Clicks, Gestures & Swiping",
                    category = "UI_AUTOMATION",
                    available = true,
                    permission = "BIND_ACCESSIBILITY_SERVICE",
                    enabled = isEnabled,
                    restricted = !isEnabled,
                    reason = if (isEnabled) "Touch injection via Accessibility Actions and Gestures is ready."
                    else "Requires Accessibility Service activation for node clicks and swipe gestures.",
                    setupAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            }
            "OVERLAY" -> {
                val canDraw = Settings.canDrawOverlays(context)
                CapabilityDescriptor(
                    id = "OVERLAY",
                    name = "Visual HUD & Floating Overlay",
                    category = "UI_SYSTEM",
                    available = true,
                    permission = Manifest.permission.SYSTEM_ALERT_WINDOW,
                    enabled = canDraw,
                    restricted = !canDraw,
                    reason = if (canDraw) "Overlay permission granted."
                    else "Overlay permission required to render floating HUD.",
                    setupAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                )
            }
            "MICROPHONE" -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                CapabilityDescriptor(
                    id = "MICROPHONE",
                    name = "Microphone & Speech Input",
                    category = "HARDWARE_INPUT",
                    available = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
                    permission = Manifest.permission.RECORD_AUDIO,
                    enabled = granted,
                    restricted = !granted,
                    reason = if (granted) "Audio recording permission active." else "RECORD_AUDIO permission not granted.",
                    setupAction = "PERMISSION_REQUEST"
                )
            }
            "CAMERA" -> {
                val hasCam = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                CapabilityDescriptor(
                    id = "CAMERA",
                    name = "Camera Optical Sensor",
                    category = "HARDWARE_INPUT",
                    available = hasCam,
                    permission = Manifest.permission.CAMERA,
                    enabled = hasCam && granted,
                    restricted = !granted,
                    reason = if (!hasCam) "No camera hardware detected." else if (granted) "Camera permission active." else "CAMERA permission not granted.",
                    setupAction = "PERMISSION_REQUEST"
                )
            }
            "FLASHLIGHT" -> {
                val hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
                CapabilityDescriptor(
                    id = "FLASHLIGHT",
                    name = "Camera LED Flashlight",
                    category = "HARDWARE_CONTROL",
                    available = hasFlash,
                    permission = "NONE",
                    enabled = hasFlash,
                    restricted = !hasFlash,
                    reason = if (hasFlash) "Flashlight hardware is operational via CameraManager." else "No camera flash hardware available on this device."
                )
            }
            "TELEPHONY" -> {
                val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                val callGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                CapabilityDescriptor(
                    id = "TELEPHONY",
                    name = "Telephony & Voice Calls",
                    category = "COMMUNICATION",
                    available = hasTelephony,
                    permission = Manifest.permission.CALL_PHONE,
                    enabled = hasTelephony && callGranted,
                    restricted = !callGranted,
                    reason = if (!hasTelephony) "No cellular telephony hardware." else if (callGranted) "Direct call placing enabled." else "CALL_PHONE permission not granted (Intent dialer available as fallback).",
                    setupAction = "PERMISSION_REQUEST"
                )
            }
            "CONTACTS" -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                CapabilityDescriptor(
                    id = "CONTACTS",
                    name = "Contacts Resolution",
                    category = "DATA_ACCESS",
                    available = true,
                    permission = Manifest.permission.READ_CONTACTS,
                    enabled = granted,
                    restricted = !granted,
                    reason = if (granted) "Contacts provider accessible." else "READ_CONTACTS permission required for contact lookup.",
                    setupAction = "PERMISSION_REQUEST"
                )
            }
            "SMS" -> {
                val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                CapabilityDescriptor(
                    id = "SMS",
                    name = "SMS Messenger",
                    category = "COMMUNICATION",
                    available = hasTelephony,
                    permission = Manifest.permission.SEND_SMS,
                    enabled = hasTelephony && granted,
                    restricted = !granted,
                    reason = if (!hasTelephony) "No SMS hardware available." else if (granted) "Direct SMS dispatch available." else "SEND_SMS permission not granted (SMS intent available as fallback).",
                    setupAction = "PERMISSION_REQUEST"
                )
            }
            "NOTIFICATIONS" -> {
                val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                CapabilityDescriptor(
                    id = "NOTIFICATIONS",
                    name = "System Notification Alerts",
                    category = "SYSTEM_COMMUNICATION",
                    available = true,
                    permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "NONE",
                    enabled = granted,
                    restricted = !granted,
                    reason = if (granted) "System notifications are active." else "Notifications blocked by user or system policy.",
                    setupAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                )
            }
            "MEDIA_CONTROL" -> {
                CapabilityDescriptor(
                    id = "MEDIA_CONTROL",
                    name = "Universal Media Player Control",
                    category = "MEDIA_AND_AUDIO",
                    available = true,
                    permission = "MODIFY_AUDIO_SETTINGS",
                    enabled = true,
                    restricted = false,
                    reason = "AudioManager media key dispatch and volume stream management are operational."
                )
            }
            "VOLUME_CONTROL" -> {
                CapabilityDescriptor(
                    id = "VOLUME_CONTROL",
                    name = "Audio Volume & Ringer Control",
                    category = "MEDIA_AND_AUDIO",
                    available = true,
                    permission = "MODIFY_AUDIO_SETTINGS",
                    enabled = true,
                    restricted = false,
                    reason = "AudioManager volume stream adjustment is operational."
                )
            }
            "BATTERY_STATUS" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val isSupported = bm != null
                CapabilityDescriptor(
                    id = "BATTERY_STATUS",
                    name = "Battery & Power Monitor",
                    category = "HARDWARE_STATUS",
                    available = isSupported,
                    permission = "NONE",
                    enabled = isSupported,
                    restricted = false,
                    reason = "BatteryManager charge level and power metrics are accessible."
                )
            }
            "NETWORK_STATUS" -> {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val isSupported = cm != null
                CapabilityDescriptor(
                    id = "NETWORK_STATUS",
                    name = "Network Connectivity Status",
                    category = "HARDWARE_STATUS",
                    available = isSupported,
                    permission = Manifest.permission.ACCESS_NETWORK_STATE,
                    enabled = isSupported,
                    restricted = false,
                    reason = "ConnectivityManager Wi-Fi/Cellular/VPN capabilities are inspectable."
                )
            }
            "BLUETOOTH" -> {
                val hasBt = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
                val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                CapabilityDescriptor(
                    id = "BLUETOOTH",
                    name = "Bluetooth State & Settings",
                    category = "HARDWARE_STATUS",
                    available = hasBt,
                    permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH,
                    enabled = hasBt && granted,
                    restricted = !granted,
                    reason = if (!hasBt) "No Bluetooth hardware detected." else if (granted) "Bluetooth status inspectable." else "BLUETOOTH_CONNECT permission required on Android 12+.",
                    setupAction = Settings.ACTION_BLUETOOTH_SETTINGS
                )
            }
            "LOCATION_STATUS" -> {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val gpsEnabled = try { lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true } catch (e: Exception) { false }
                val netEnabled = try { lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true } catch (e: Exception) { false }
                CapabilityDescriptor(
                    id = "LOCATION_STATUS",
                    name = "Location Status & Providers",
                    category = "HARDWARE_STATUS",
                    available = lm != null,
                    permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                    enabled = gpsEnabled || netEnabled,
                    restricted = !(gpsEnabled || netEnabled),
                    reason = if (gpsEnabled || netEnabled) "Location provider is enabled." else "Location providers are turned off.",
                    setupAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS
                )
            }
            "DEVICE_SETTINGS" -> {
                CapabilityDescriptor(
                    id = "DEVICE_SETTINGS",
                    name = "System & Application Settings Navigation",
                    category = "SYSTEM_CONTROL",
                    available = true,
                    permission = "NONE",
                    enabled = true,
                    restricted = false,
                    reason = "System settings Intent routers (Wi-Fi, Bluetooth, Display, Sound, Apps, Accessibility) are operational."
                )
            }
            "FILE_ACCESS" -> {
                val isAvailable = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
                CapabilityDescriptor(
                    id = "FILE_ACCESS",
                    name = "Local Storage & Media Access",
                    category = "DATA_ACCESS",
                    available = isAvailable,
                    permission = "READ_EXTERNAL_STORAGE / SAF",
                    enabled = isAvailable,
                    restricted = false,
                    reason = "App private storage, Cache directories, and SAF Storage Access Framework are operational."
                )
            }
            "INSTALLATION_STATUS" -> {
                CapabilityDescriptor(
                    id = "INSTALLATION_STATUS",
                    name = "Package Installation & State Inspector",
                    category = "SYSTEM_CONTROL",
                    available = true,
                    permission = "QUERY_ALL_PACKAGES",
                    enabled = true,
                    restricted = false,
                    reason = "Package installation checks and uninstall prompt launchers are operational."
                )
            }
            else -> {
                CapabilityDescriptor(
                    id = id.uppercase(),
                    name = id,
                    category = "CUSTOM",
                    available = false,
                    permission = "UNKNOWN",
                    enabled = false,
                    restricted = true,
                    reason = "Capability '$id' not recognized by DeviceCapabilityManager."
                )
            }
        }
    }

    fun getSetupIntent(capabilityId: String): Intent {
        return when (capabilityId.uppercase()) {
            "ACCESSIBILITY", "SCREEN_READING", "SCREEN_INTERACTION" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "OVERLAY" -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            "NOTIFICATIONS" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "BLUETOOTH" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "LOCATION_STATUS" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "DEVICE_ADMIN" -> deviceManager.getAdminActivationIntent()
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
