package com.example.core.vision

import android.graphics.Bitmap

data class VisualAnalysisResult(
    val success: Boolean,
    val elements: List<VisualElement>,
    val description: String,
    val providerName: String,
    val rawJson: String? = null,
    val error: String? = null,
    val latencyMs: Long = 0
)

/**
 * Interface for Vision Providers (Local, Gemini, and Hybrid).
 */
interface VisionProvider {
    val providerName: String
    val isMultimodalSupported: Boolean

    suspend fun analyzeScreenshot(
        bitmap: Bitmap?,
        prompt: String,
        semanticGoal: String? = null,
        appPackage: String? = null,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): VisualAnalysisResult
}
