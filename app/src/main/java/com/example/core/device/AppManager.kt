package com.example.core.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AppRegistryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AppMetadata(
    val packageName: String,
    val applicationLabel: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val launchIntentAvailable: Boolean,
    val category: String = "Application"
) {
    fun toEntity(): AppRegistryEntity {
        return AppRegistryEntity(
            packageName = packageName,
            applicationLabel = applicationLabel,
            versionName = versionName,
            versionCode = versionCode,
            isSystemApp = isSystemApp,
            launchIntentAvailable = launchIntentAvailable,
            category = category,
            lastScannedAt = System.currentTimeMillis()
        )
    }
}

data class AppLaunchResult(
    val success: Boolean,
    val packageName: String,
    val applicationLabel: String,
    val message: String,
    val error: String? = null
)

class AppManager(
    private val context: Context,
    private val jarvisDao: JarvisDao? = null
) {
    private val TAG = "JARVIS_AppManager"

    private val _installedApps = MutableStateFlow<List<AppMetadata>>(emptyList())
    val installedApps: StateFlow<List<AppMetadata>> = _installedApps.asStateFlow()

    private val commonAliases = mapOf(
        "yt" to "YouTube",
        "youtube" to "YouTube",
        "chrome" to "Chrome",
        "browser" to "Chrome",
        "google chrome" to "Chrome",
        "whatsapp" to "WhatsApp",
        "wa" to "WhatsApp",
        "camera" to "Camera",
        "settings" to "Settings",
        "system settings" to "Settings",
        "clock" to "Clock",
        "alarm" to "Clock",
        "calculator" to "Calculator",
        "calc" to "Calculator",
        "maps" to "Maps",
        "google maps" to "Maps",
        "photos" to "Photos",
        "gallery" to "Photos",
        "play store" to "Google Play Store",
        "store" to "Google Play Store",
        "messages" to "Messages",
        "sms" to "Messages",
        "phone" to "Phone",
        "dialer" to "Phone",
        "contacts" to "Contacts",
        "files" to "Files",
        "file manager" to "Files",
        "gmail" to "Gmail",
        "mail" to "Gmail",
        "email" to "Gmail"
    )

    init {
        scanInstalledAppsSync()
    }

    fun scanInstalledAppsSync(): List<AppMetadata> {
        val pm = context.packageManager
        val list = mutableListOf<AppMetadata>()
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(0L)
            } else {
                0
            }

            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(flags as PackageManager.PackageInfoFlags)
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags as Int)
            }

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val label = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                }

                list.add(
                    AppMetadata(
                        packageName = pkg.packageName,
                        applicationLabel = label,
                        versionName = pkg.versionName ?: "1.0",
                        versionCode = vCode,
                        isSystemApp = isSystem,
                        launchIntentAvailable = launchIntent != null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed packages", e)
        }

        val sorted = list.sortedBy { it.applicationLabel.lowercase() }
        _installedApps.value = sorted
        return sorted
    }

    suspend fun syncAppRegistry() = withContext(Dispatchers.IO) {
        val apps = scanInstalledAppsSync()
        if (jarvisDao != null) {
            val entities = apps.map { it.toEntity() }
            jarvisDao.insertRegisteredApps(entities)
        }
    }

    suspend fun scanInstalledApps(): List<AppMetadata> = withContext(Dispatchers.IO) {
        val apps = scanInstalledAppsSync()
        if (jarvisDao != null) {
            val entities = apps.map { it.toEntity() }
            jarvisDao.insertRegisteredApps(entities)
        }
        apps
    }

    fun findApp(query: String): AppMetadata? {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return null

        val currentList = _installedApps.value.ifEmpty { scanInstalledAppsSync() }

        // 1. Direct package match
        val byPackage = currentList.firstOrNull { it.packageName.equals(trimmed, ignoreCase = true) }
        if (byPackage != null) return byPackage

        // 2. Exact label match
        val byExactLabel = currentList.firstOrNull { it.applicationLabel.equals(trimmed, ignoreCase = true) }
        if (byExactLabel != null) return byExactLabel

        // 3. Alias check
        val aliasTarget = commonAliases[trimmed]
        if (aliasTarget != null) {
            val byAlias = currentList.firstOrNull { it.applicationLabel.contains(aliasTarget, ignoreCase = true) }
            if (byAlias != null) return byAlias
        }

        // 4. Starts-with label match
        val byPrefix = currentList.firstOrNull { it.applicationLabel.lowercase().startsWith(trimmed) }
        if (byPrefix != null) return byPrefix

        // 5. Contains label match (prefer launchable apps)
        val byContains = currentList.filter { it.applicationLabel.lowercase().contains(trimmed) }
        val launchable = byContains.firstOrNull { it.launchIntentAvailable }
        if (launchable != null) return launchable
        if (byContains.isNotEmpty()) return byContains.first()

        // 6. Contains package match
        return currentList.firstOrNull { it.packageName.lowercase().contains(trimmed) }
    }

    fun openApp(queryOrPackage: String): AppLaunchResult {
        val app = findApp(queryOrPackage)
        if (app == null) {
            return AppLaunchResult(
                success = false,
                packageName = queryOrPackage,
                applicationLabel = queryOrPackage,
                message = "Application '$queryOrPackage' is not installed on this device.",
                error = "APP_NOT_INSTALLED"
            )
        }

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
        if (launchIntent == null) {
            return AppLaunchResult(
                success = false,
                packageName = app.packageName,
                applicationLabel = app.applicationLabel,
                message = "Application '${app.applicationLabel}' does not expose a main launch activity.",
                error = "NO_LAUNCH_INTENT"
            )
        }

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(launchIntent)
            AppLaunchResult(
                success = true,
                packageName = app.packageName,
                applicationLabel = app.applicationLabel,
                message = "Successfully launched '${app.applicationLabel}' (${app.packageName})."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch application ${app.packageName}", e)
            AppLaunchResult(
                success = false,
                packageName = app.packageName,
                applicationLabel = app.applicationLabel,
                message = "Failed to launch application '${app.applicationLabel}': ${e.message}",
                error = e.localizedMessage
            )
        }
    }

    fun openAppSettings(queryOrPackage: String): AppLaunchResult {
        val app = findApp(queryOrPackage)
        val targetPackage = app?.packageName ?: queryOrPackage.trim()
        val targetLabel = app?.applicationLabel ?: targetPackage

        return try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$targetPackage")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLaunchResult(
                success = true,
                packageName = targetPackage,
                applicationLabel = targetLabel,
                message = "Opened Application Settings for '$targetLabel'."
            )
        } catch (e: Exception) {
            AppLaunchResult(
                success = false,
                packageName = targetPackage,
                applicationLabel = targetLabel,
                message = "Failed to open settings for '$targetLabel': ${e.message}",
                error = e.localizedMessage
            )
        }
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun requestUninstall(queryOrPackage: String): Intent {
        val app = findApp(queryOrPackage)
        val targetPackage = app?.packageName ?: queryOrPackage.trim()
        return Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$targetPackage")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
