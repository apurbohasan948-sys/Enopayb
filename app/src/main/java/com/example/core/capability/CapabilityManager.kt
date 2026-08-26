package com.example.core.capability

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.device.JarvisDeviceManager

enum class CapabilityStatus {
    GRANTED,
    DENIED,
    UNSUPPORTED,
    REQUIRES_SETUP
}

data class CapabilityItem(
    val id: String,
    val name: String,
    val description: String,
    val status: CapabilityStatus,
    val requiredPermission: String,
    val setupAction: String,
    val isCrucial: Boolean = false
)

class CapabilityManager(
    private val context: Context,
    private val deviceManager: JarvisDeviceManager = JarvisDeviceManager(context)
) {

    fun getAllCapabilities(): List<CapabilityItem> {
        val list = mutableListOf<CapabilityItem>()

        // 1. Accessibility Service
        val accessibilityEnabled = JarvisAccessibilityService.isAccessibilityEnabled(context)
        list.add(
            CapabilityItem(
                id = "ACCESSIBILITY",
                name = "Accessibility Controller",
                description = "Enables screen reading, gesture clicks, and app UI navigation",
                status = if (accessibilityEnabled) CapabilityStatus.GRANTED else CapabilityStatus.REQUIRES_SETUP,
                requiredPermission = "BIND_ACCESSIBILITY_SERVICE",
                setupAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                isCrucial = true
            )
        )

        // 2. Microphone
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityItem(
                id = "MICROPHONE",
                name = "Microphone & Voice Input",
                description = "Speech recognition and conversational wake-word input",
                status = if (micGranted) CapabilityStatus.GRANTED else CapabilityStatus.DENIED,
                requiredPermission = Manifest.permission.RECORD_AUDIO,
                setupAction = "PERMISSION_REQUEST"
            )
        )

        // 3. Contacts
        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityItem(
                id = "CONTACTS",
                name = "Contacts Resolution",
                description = "Resolving contact names to phone numbers and WhatsApp targets",
                status = if (contactsGranted) CapabilityStatus.GRANTED else CapabilityStatus.DENIED,
                requiredPermission = Manifest.permission.READ_CONTACTS,
                setupAction = "PERMISSION_REQUEST"
            )
        )

        // 4. Phone
        val phoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityItem(
                id = "PHONE",
                name = "Direct Telephony Calls",
                description = "Placing verified voice calls on user behalf",
                status = if (phoneGranted) CapabilityStatus.GRANTED else CapabilityStatus.DENIED,
                requiredPermission = Manifest.permission.CALL_PHONE,
                setupAction = "PERMISSION_REQUEST"
            )
        )

        // 5. SMS
        val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityItem(
                id = "SMS",
                name = "SMS Messenger",
                description = "Preparing and sending SMS messages",
                status = if (smsGranted) CapabilityStatus.GRANTED else CapabilityStatus.DENIED,
                requiredPermission = Manifest.permission.SEND_SMS,
                setupAction = "PERMISSION_REQUEST"
            )
        )

        // 6. Notifications
        val notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        list.add(
            CapabilityItem(
                id = "NOTIFICATIONS",
                name = "System Notifications",
                description = "Foreground execution alerts and task status updates",
                status = if (notifGranted) CapabilityStatus.GRANTED else CapabilityStatus.DENIED,
                requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "NONE",
                setupAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            )
        )

        // 7. Overlay (SYSTEM_ALERT_WINDOW)
        val overlayGranted = Settings.canDrawOverlays(context)
        list.add(
            CapabilityItem(
                id = "OVERLAY",
                name = "HUD & Overlay View",
                description = "Visual floating HUD and on-screen targeting overlay",
                status = if (overlayGranted) CapabilityStatus.GRANTED else CapabilityStatus.REQUIRES_SETUP,
                requiredPermission = Manifest.permission.SYSTEM_ALERT_WINDOW,
                setupAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            )
        )

        // 8. Device Admin
        val isAdmin = deviceManager.isDeviceAdminActive()
        list.add(
            CapabilityItem(
                id = "DEVICE_ADMIN",
                name = "Device Administration",
                description = "Screen lock and enterprise device management policy enforcement",
                status = if (isAdmin) CapabilityStatus.GRANTED else CapabilityStatus.REQUIRES_SETUP,
                requiredPermission = "BIND_DEVICE_ADMIN",
                setupAction = "DEVICE_ADMIN_INTENT"
            )
        )

        // 9. Device Owner
        val isOwner = deviceManager.isDeviceOwner()
        list.add(
            CapabilityItem(
                id = "DEVICE_OWNER",
                name = "Device Owner Status",
                description = "Fully managed device enterprise authority",
                status = if (isOwner) CapabilityStatus.GRANTED else CapabilityStatus.UNSUPPORTED,
                requiredPermission = "dpm set-device-owner",
                setupAction = "PROVISIONING_REQUIRED"
            )
        )

        // 10. Default Assistant
        val isAssistant = isDefaultAssistant()
        list.add(
            CapabilityItem(
                id = "DEFAULT_ASSISTANT",
                name = "Default Android Assistant",
                description = "System default voice interaction and assistant key binding",
                status = if (isAssistant) CapabilityStatus.GRANTED else CapabilityStatus.REQUIRES_SETUP,
                requiredPermission = "ROLE_ASSISTANT",
                setupAction = Settings.ACTION_VOICE_INPUT_SETTINGS
            )
        )

        return list
    }

    fun isDefaultAssistant(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) ?: false
            } else {
                val currentAssistant = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
                currentAssistant?.contains(context.packageName) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getSetupIntent(capabilityId: String): Intent {
        return when (capabilityId) {
            "ACCESSIBILITY" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
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
            "DEVICE_ADMIN" -> deviceManager.getAdminActivationIntent()
            "DEFAULT_ASSISTANT" -> Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
