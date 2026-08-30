package com.example.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

data class SettingsNavResult(
    val success: Boolean,
    val targetSetting: String,
    val action: String,
    val message: String,
    val error: String? = null
)

class SettingsNavigator(private val context: Context) {
    private val TAG = "JARVIS_SettingsNav"

    fun openSetting(target: String, packageName: String? = null): SettingsNavResult {
        val upper = target.trim().uppercase()
        val (action, uri) = when {
            upper.contains("WIFI") || upper.contains("WI-FI") || upper.contains("INTERNET") ->
                Pair(Settings.ACTION_WIFI_SETTINGS, null)
            upper.contains("BLUETOOTH") || upper.contains("BT") ->
                Pair(Settings.ACTION_BLUETOOTH_SETTINGS, null)
            upper.contains("SOUND") || upper.contains("VOLUME") || upper.contains("AUDIO") ->
                Pair(Settings.ACTION_SOUND_SETTINGS, null)
            upper.contains("DISPLAY") || upper.contains("BRIGHTNESS") || upper.contains("SCREEN") ->
                Pair(Settings.ACTION_DISPLAY_SETTINGS, null)
            upper.contains("BATTERY") || upper.contains("POWER") ->
                Pair(Settings.ACTION_BATTERY_SAVER_SETTINGS, null)
            upper.contains("ACCESSIBILITY") ->
                Pair(Settings.ACTION_ACCESSIBILITY_SETTINGS, null)
            upper.contains("NOTIFICATION") || upper.contains("NOTIF") ->
                Pair(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, null)
            upper.contains("LOCATION") || upper.contains("GPS") ->
                Pair(Settings.ACTION_LOCATION_SOURCE_SETTINGS, null)
            upper.contains("DATE") || upper.contains("TIME") ->
                Pair(Settings.ACTION_DATE_SETTINGS, null)
            upper.contains("APP") || upper.contains("APPLICATION") || !packageName.isNullOrBlank() -> {
                val pkg = packageName ?: context.packageName
                Pair(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            }
            upper.contains("SECURITY") || upper.contains("LOCK") ->
                Pair(Settings.ACTION_SECURITY_SETTINGS, null)
            upper.contains("ABOUT") || upper.contains("DEVICE_INFO") ->
                Pair(Settings.ACTION_DEVICE_INFO_SETTINGS, null)
            else ->
                Pair(Settings.ACTION_SETTINGS, null)
        }

        return try {
            val intent = Intent(action).apply {
                if (uri != null) data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SettingsNavResult(
                success = true,
                targetSetting = target,
                action = action,
                message = "Opened system settings for '$target'."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open setting $target with action $action", e)
            try {
                // Fallback to general settings
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                SettingsNavResult(
                    success = true,
                    targetSetting = target,
                    action = Settings.ACTION_SETTINGS,
                    message = "Opened general Settings page (fallback from $target)."
                )
            } catch (fallbackError: Exception) {
                SettingsNavResult(
                    success = false,
                    targetSetting = target,
                    action = action,
                    message = "Failed to launch settings for '$target': ${e.message}",
                    error = e.localizedMessage
                )
            }
        }
    }
}
