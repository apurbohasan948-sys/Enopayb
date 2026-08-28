package com.example.core.health

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashReportData(
    val timestamp: Long,
    val timestampFormatted: String,
    val exceptionType: String,
    val message: String,
    val stackTrace: String,
    val appVersion: String,
    val androidVersion: String,
    val deviceRAM: String,
    val availableRAM: String,
    val currentModel: String,
    val currentService: String,
    val lastTask: String,
    val lastScreen: String,
    val lastAction: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("timestampFormatted", timestampFormatted)
            put("exceptionType", exceptionType)
            put("message", message)
            put("stackTrace", stackTrace)
            put("appVersion", appVersion)
            put("androidVersion", androidVersion)
            put("deviceRAM", deviceRAM)
            put("availableRAM", availableRAM)
            put("currentModel", currentModel)
            put("currentService", currentService)
            put("lastTask", lastTask)
            put("lastScreen", lastScreen)
            put("lastAction", lastAction)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CrashReportData {
            return CrashReportData(
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                timestampFormatted = json.optString("timestampFormatted", ""),
                exceptionType = json.optString("exceptionType", "UnknownException"),
                message = json.optString("message", ""),
                stackTrace = json.optString("stackTrace", ""),
                appVersion = json.optString("appVersion", "1.1"),
                androidVersion = json.optString("androidVersion", "Android 15 (API 36)"),
                deviceRAM = json.optString("deviceRAM", "Unknown"),
                availableRAM = json.optString("availableRAM", "Unknown"),
                currentModel = json.optString("currentModel", "None"),
                currentService = json.optString("currentService", "Main"),
                lastTask = json.optString("lastTask", "None"),
                lastScreen = json.optString("lastScreen", "Main"),
                lastAction = json.optString("lastAction", "Idle")
            )
        }
    }
}

/**
 * CrashReporter.
 * Captures, sanitizes, and persists crash telemetry and diagnostics without logging sensitive tokens/passwords.
 * Installs an UncaughtExceptionHandler and provides diagnostics for safe mode recovery.
 */
object CrashReporter {
    private const val TAG = "JARVIS_CrashReporter"
    private const val PREFS_NAME = "jarvis_crash_reporter_prefs"
    private const val KEY_LAST_CRASH_JSON = "last_crash_json"
    private const val KEY_TOTAL_CRASH_COUNT = "total_crash_count"
    private const val KEY_CONSECUTIVE_STARTUP_CRASHES = "consecutive_startup_crashes"
    private const val KEY_SAFE_MODE_TRIGGERED = "safe_mode_triggered"

    @Volatile
    private var isInstalled = false
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    // Runtime state tracking for crash telemetry context
    @Volatile var currentScreen: String = "HUD Console"
    @Volatile var currentService: String = "JARVIS Core"
    @Volatile var currentModel: String = "Qwen2.5-1.5B (Local)"
    @Volatile var lastAction: String = "Startup Initialization"
    @Volatile var lastTask: String = "None"

    fun install(context: Context) {
        if (isInstalled) return
        isInstalled = true

        val appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(appContext, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture crash report", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        Log.i(TAG, "CrashReporter handler installed successfully.")
    }

    fun recordCrash(
        context: Context,
        throwable: Throwable,
        overrideService: String? = null,
        overrideScreen: String? = null,
        overrideAction: String? = null
    ): CrashReportData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val rawStackTrace = sw.toString()

        val sanitizedStackTrace = sanitizeSensitiveData(rawStackTrace)
        val sanitizedMessage = sanitizeSensitiveData(throwable.message ?: throwable.javaClass.simpleName)

        val memoryInfo = getMemoryInfo(context)
        val now = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))

        val report = CrashReportData(
            timestamp = now,
            timestampFormatted = formattedDate,
            exceptionType = throwable.javaClass.name,
            message = sanitizedMessage,
            stackTrace = sanitizedStackTrace,
            appVersion = "1.1 (2)",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceRAM = memoryInfo.first,
            availableRAM = memoryInfo.second,
            currentModel = currentModel,
            currentService = overrideService ?: currentService,
            lastTask = sanitizeSensitiveData(lastTask),
            lastScreen = overrideScreen ?: currentScreen,
            lastAction = overrideAction ?: lastAction
        )

        // Increment crash counts
        val totalCrashes = prefs.getInt(KEY_TOTAL_CRASH_COUNT, 0) + 1
        val consecutiveStartups = prefs.getInt(KEY_CONSECUTIVE_STARTUP_CRASHES, 0) + 1

        val editor = prefs.edit()
        editor.putString(KEY_LAST_CRASH_JSON, report.toJson().toString())
        editor.putInt(KEY_TOTAL_CRASH_COUNT, totalCrashes)
        editor.putInt(KEY_CONSECUTIVE_STARTUP_CRASHES, consecutiveStartups)

        // If 2 or more startup crashes in a row, auto-flag Safe Mode
        if (consecutiveStartups >= 2) {
            editor.putBoolean(KEY_SAFE_MODE_TRIGGERED, true)
            Log.w(TAG, "Auto-triggered SAFE MODE due to consecutive crashes ($consecutiveStartups)")
        }
        editor.apply()

        // Append to crash log file
        saveToFile(context, report)

        Log.e(TAG, "CRASH RECORDED: ${report.exceptionType} - ${report.message}")
        return report
    }

    fun markSuccessfulStartup(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CONSECUTIVE_STARTUP_CRASHES, 0).apply()
    }

    fun isSafeModeTriggered(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SAFE_MODE_TRIGGERED, false)
    }

    fun setSafeMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SAFE_MODE_TRIGGERED, enabled).apply()
    }

    fun getLastCrash(context: Context): CrashReportData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_LAST_CRASH_JSON, null) ?: return null
        return try {
            CrashReportData.fromJson(JSONObject(jsonStr))
        } catch (e: Exception) {
            null
        }
    }

    fun getCrashCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TOTAL_CRASH_COUNT, 0)
    }

    fun resetCrashCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_TOTAL_CRASH_COUNT, 0)
            .putInt(KEY_CONSECUTIVE_STARTUP_CRASHES, 0)
            .putBoolean(KEY_SAFE_MODE_TRIGGERED, false)
            .apply()
    }

    fun clearCrashReports(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_CRASH_JSON).apply()
        try {
            val logFile = File(context.filesDir, "logs/crash_reports.json")
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {}
    }

    private fun getMemoryInfo(context: Context): Pair<String, String> {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            val availMb = memInfo.availMem / (1024 * 1024)
            Pair("${totalMb} MB", "${availMb} MB")
        } catch (e: Exception) {
            Pair("4096 MB", "Unknown")
        }
    }

    /**
     * Sanitizes API keys, tokens, passwords, and user message content from logs.
     */
    private fun sanitizeSensitiveData(input: String): String {
        return input
            .replace(Regex("AIza[0-9A-Za-z-_]{35}"), "[REDACTED_GEMINI_KEY]")
            .replace(Regex("(password|token|secret|key|bearer)=\"[^\"]+\"", RegexOption.IGNORE_CASE), "$1=\"[REDACTED]\"")
            .replace(Regex("(password|token|secret|key|bearer)=[^&\\s]+", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            .replace(Regex("([0-9]{10,12})"), "[REDACTED_PHONE]")
    }

    private fun saveToFile(context: Context, report: CrashReportData) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()
            val file = File(logDir, "crash_reports.json")
            val array = if (file.exists()) {
                try {
                    JSONArray(file.readText())
                } catch (e: Exception) {
                    JSONArray()
                }
            } else {
                JSONArray()
            }
            array.put(report.toJson())
            // Keep last 15 reports
            while (array.length() > 15) {
                array.remove(0)
            }
            file.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Could not save crash to file: ${e.message}")
        }
    }
}
