package com.example.core.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

data class AppPackageMapping(
    val appName: String,
    val packageName: String,
    val launchActivity: String? = null,
    val lastVerified: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f
)

/**
 * AppResolver.
 * Dynamically resolves natural language app names to installed packages and activities
 * using Android PackageManager. Refreshes mappings on demand and caches verified bindings.
 */
class AppResolver(private val context: Context) {
    companion object {
        private const val TAG = "JARVIS_AppResolver"

        // Common known standard package mappings as fast fallbacks
        private val COMMON_PACKAGES = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "settings" to "com.android.settings",
            "setting" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "calc" to "com.google.android.calculator",
            "gallery" to "com.google.android.apps.photos",
            "photos" to "com.google.android.apps.photos",
            "photo" to "com.google.android.apps.photos",
            "camera" to "com.google.android.GoogleCamera",
            "whatsapp" to "com.whatsapp",
            "messages" to "com.google.android.apps.messaging",
            "message" to "com.google.android.apps.messaging",
            "sms" to "com.google.android.apps.messaging",
            "phone" to "com.google.android.dialer",
            "dialer" to "com.google.android.dialer",
            "call" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "contact" to "com.google.android.contacts",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "map" to "com.google.android.apps.maps",
            "play store" to "com.android.vending",
            "store" to "com.android.vending",
            "files" to "com.google.android.apps.nbu.files",
            "clock" to "com.google.android.deskclock"
        )
    }

    private val dynamicMappings = mutableMapOf<String, AppPackageMapping>()

    /**
     * Resolves app name to installed package mapping.
     */
    fun resolveApp(requestedAppName: String): AppPackageMapping? {
        val cleanName = requestedAppName.trim().lowercase()
        val pm = context.packageManager

        // 1. Check verified cached mapping
        dynamicMappings[cleanName]?.let { cached ->
            if (isPackageInstalled(cached.packageName, pm)) {
                return cached
            }
        }

        // 2. Query PackageManager for launchable applications matching label
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var bestMatch: AppPackageMapping? = null
        var highestScore = 0f

        for (app in installedApps) {
            val isLaunchable = pm.getLaunchIntentForPackage(app.packageName) != null
            if (!isLaunchable) continue

            val label = try {
                pm.getApplicationLabel(app).toString().lowercase()
            } catch (e: Exception) {
                ""
            }

            val pkg = app.packageName.lowercase()

            val score = when {
                label == cleanName -> 1.0f
                label.contains(cleanName) -> 0.85f
                pkg.contains(cleanName) -> 0.75f
                cleanName.contains(label) && label.length > 2 -> 0.70f
                else -> 0f
            }

            if (score > highestScore) {
                highestScore = score
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                val activity = launchIntent?.component?.className
                bestMatch = AppPackageMapping(
                    appName = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    launchActivity = activity,
                    lastVerified = System.currentTimeMillis(),
                    confidence = score
                )
            }
        }

        if (bestMatch != null && highestScore >= 0.70f) {
            dynamicMappings[cleanName] = bestMatch
            Log.d(TAG, "Resolved '$requestedAppName' via PackageManager to ${bestMatch.packageName} (score: $highestScore)")
            return bestMatch
        }

        // 3. Fallback to common aliases
        val fallbackPkg = COMMON_PACKAGES[cleanName]
            ?: COMMON_PACKAGES.entries.firstOrNull { cleanName.contains(it.key) }?.value

        if (fallbackPkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(fallbackPkg)
            val isInstalled = isPackageInstalled(fallbackPkg, pm)
            val mapping = AppPackageMapping(
                appName = requestedAppName,
                packageName = fallbackPkg,
                launchActivity = launchIntent?.component?.className,
                lastVerified = System.currentTimeMillis(),
                confidence = if (isInstalled) 1.0f else 0.95f
            )
            dynamicMappings[cleanName] = mapping
            return mapping
        }

        Log.w(TAG, "Could not resolve app '$requestedAppName' to installed package")
        return null
    }

    private fun isPackageInstalled(packageName: String, pm: PackageManager): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getAllKnownMappings(): List<AppPackageMapping> {
        return dynamicMappings.values.toList()
    }
}
