package com.example.core.model

import android.content.Context
import com.example.core.vision.GeminiVisionProvider
import com.example.core.vision.LocalVisionProvider
import com.example.core.vision.VisionProvider
import com.example.data.local.preference.JarvisPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ModelProviderManager.
 * Central coordinator and repository for all model and vision providers in JARVIS.
 * Ensures all app components communicate through abstract provider interfaces
 * rather than hardcoding direct cloud model dependencies.
 */
class ModelProviderManager(
    val context: Context,
    private val preferences: JarvisPreferences? = null
) {
    val localModelProvider = LocalModelProvider(context)
    val geminiModelProvider = GeminiModelProvider()
    val localVisionProvider = LocalVisionProvider()
    val geminiVisionProvider = GeminiVisionProvider()
    val fallbackProvider = FallbackProvider()

    private val _activeTextProvider = MutableStateFlow<ModelProvider>(localModelProvider)
    val activeTextProvider: StateFlow<ModelProvider> = _activeTextProvider.asStateFlow()

    private val _activeVisionProvider = MutableStateFlow<VisionProvider>(localVisionProvider)
    val activeVisionProvider: StateFlow<VisionProvider> = _activeVisionProvider.asStateFlow()

    init {
        // Synchronize initial preferences
        preferences?.let { prefs ->
            if (prefs.geminiApiKey.isNotBlank() && prefs.geminiApiKey != "MY_GEMINI_API_KEY") {
                geminiModelProvider.runtimeApiKey = prefs.geminiApiKey
                geminiVisionProvider.runtimeApiKey = prefs.geminiApiKey
            }
            geminiModelProvider.selectedModel = prefs.geminiModel
            geminiModelProvider.temperature = prefs.temperature
        }
    }

    fun updateGeminiApiKey(key: String) {
        geminiModelProvider.runtimeApiKey = key
        geminiVisionProvider.runtimeApiKey = key
    }

    fun updateGeminiModel(model: String) {
        geminiModelProvider.selectedModel = model
    }

    fun updateGeminiTemperature(temp: Float) {
        geminiModelProvider.temperature = temp
    }

    fun selectTextProvider(type: ActiveModelType): ModelProvider {
        val provider = when (type) {
            ActiveModelType.LOCAL_GGUF_CPU, ActiveModelType.LOCAL_SLM -> localModelProvider
            ActiveModelType.GEMINI_CLOUD_TEACHER, ActiveModelType.GEMINI_FLASH -> geminiModelProvider
            ActiveModelType.HYBRID_SUPERVISED -> HybridModelProvider(localModelProvider, geminiModelProvider)
        }
        _activeTextProvider.value = provider
        return provider
    }

    fun getFallbackProvider(): ModelProvider = fallbackProvider
}
