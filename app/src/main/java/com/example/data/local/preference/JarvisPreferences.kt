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

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }
}
