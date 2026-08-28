package com.example.data.local.preference

import android.content.Context
import android.content.SharedPreferences

class JarvisPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis_ai_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model_id"
        private const val KEY_GEMINI_TEMPERATURE = "gemini_temperature"
        private const val KEY_SYSTEM_PROMPT = "gemini_system_prompt"
        private const val KEY_DEFAULT_LANGUAGE = "jarvis_default_language"

        // Phase 7 Learning & Privacy Keys
        private const val KEY_LEARNING_ENABLED = "learning_enabled"
        private const val KEY_GEMINI_TEACHER_ENABLED = "gemini_teacher_enabled"
        private const val KEY_CLOUD_FALLBACK_ENABLED = "cloud_fallback_enabled"
        private const val KEY_STORE_EXPERIENCES_ENABLED = "store_experiences_enabled"
        private const val KEY_STORE_TRAINING_DATA_ENABLED = "store_training_data_enabled"
        private const val KEY_AUTO_SKILL_CREATION_ENABLED = "auto_skill_creation_enabled"
        private const val KEY_PRIVACY_FILTERING_ENABLED = "privacy_filtering_enabled"

        // Emergency Stabilization & Safe Mode Keys
        private const val KEY_SAFE_MODE_ENABLED = "safe_mode_enabled"
        private const val KEY_VISION_ENABLED = "vision_enabled"
        private const val KEY_LOCAL_MODEL_ENABLED = "local_model_enabled"
        private const val KEY_GEMINI_SERVICE_ENABLED = "gemini_service_enabled"
        private const val KEY_ACCESSIBILITY_SERVICE_ENABLED = "accessibility_service_enabled"
        private const val KEY_AUTONOMOUS_WORKERS_ENABLED = "autonomous_workers_enabled"
        private const val KEY_SERVICE_CRASH_PREFIX = "svc_crash_"

        const val DEFAULT_MODEL = "gemini-3.5-flash"
        const val DEFAULT_TEMPERATURE = 0.4f
        const val DEFAULT_SYSTEM_PROMPT = "You are J.A.R.V.I.S., a personal AI assistant. Be direct, concise, and helpful."

        private val DEPRECATED_MODELS = setOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-pro",
            "gemini-2.0-flash-thinking",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-pro"
        )
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var geminiModel: String
        get() {
            val saved = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
            return if (saved in DEPRECATED_MODELS) {
                // Auto-migrate old deprecated model to supported gemini-3.5-flash
                prefs.edit().putString(KEY_GEMINI_MODEL, DEFAULT_MODEL).apply()
                DEFAULT_MODEL
            } else {
                saved
            }
        }
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value.trim()).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_GEMINI_TEMPERATURE, DEFAULT_TEMPERATURE)
        set(value) = prefs.edit().putFloat(KEY_GEMINI_TEMPERATURE, value).apply()

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value.trim()).apply()

    var defaultLanguage: String
        get() = prefs.getString(KEY_DEFAULT_LANGUAGE, "EN") ?: "EN"
        set(value) = prefs.edit().putString(KEY_DEFAULT_LANGUAGE, value).apply()

    // Phase 7 Learning Preferences
    var isLearningEnabled: Boolean
        get() = prefs.getBoolean(KEY_LEARNING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LEARNING_ENABLED, value).apply()

    var isGeminiTeacherEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEMINI_TEACHER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GEMINI_TEACHER_ENABLED, value).apply()

    var isCloudFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_FALLBACK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_FALLBACK_ENABLED, value).apply()

    var isStoreExperiencesEnabled: Boolean
        get() = prefs.getBoolean(KEY_STORE_EXPERIENCES_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_STORE_EXPERIENCES_ENABLED, value).apply()

    var isStoreTrainingDataEnabled: Boolean
        get() = prefs.getBoolean(KEY_STORE_TRAINING_DATA_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_STORE_TRAINING_DATA_ENABLED, value).apply()

    var isAutoSkillCreationEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SKILL_CREATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SKILL_CREATION_ENABLED, value).apply()

    var isPrivacyFilteringEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_FILTERING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_FILTERING_ENABLED, value).apply()

    var isGeminiTeacherAllowed: Boolean
        get() = isGeminiTeacherEnabled
        set(value) { isGeminiTeacherEnabled = value }

    // Emergency Safe Mode & Component Isolation
    var isSafeModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAFE_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SAFE_MODE_ENABLED, value).apply()

    /**
     * Vision is OFF by default after Phase 10 isolation to guarantee startup stability.
     */
    var isVisionEnabled: Boolean
        get() = prefs.getBoolean(KEY_VISION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VISION_ENABLED, value).apply()

    var isLocalModelEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_MODEL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCAL_MODEL_ENABLED, value).apply()

    var isGeminiServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEMINI_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GEMINI_SERVICE_ENABLED, value).apply()

    var isAccessibilityServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_SERVICE_ENABLED, value).apply()

    var isAutonomousWorkersEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTONOMOUS_WORKERS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTONOMOUS_WORKERS_ENABLED, value).apply()

    // Service crash & restart isolation (Crash >= 3 -> temporary shutdown)
    fun getServiceCrashCount(serviceName: String): Int {
        return prefs.getInt(KEY_SERVICE_CRASH_PREFIX + serviceName, 0)
    }

    fun incrementServiceCrashCount(serviceName: String): Int {
        val next = getServiceCrashCount(serviceName) + 1
        prefs.edit().putInt(KEY_SERVICE_CRASH_PREFIX + serviceName, next).apply()
        return next
    }

    fun resetServiceCrashCount(serviceName: String) {
        prefs.edit().putInt(KEY_SERVICE_CRASH_PREFIX + serviceName, 0).apply()
    }

    fun isServiceDisabledDueToCrashes(serviceName: String, threshold: Int = 3): Boolean {
        if (isSafeModeEnabled) return true
        return getServiceCrashCount(serviceName) >= threshold
    }

    fun resetAllServiceCrashCounts() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_SERVICE_CRASH_PREFIX) }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }
}
