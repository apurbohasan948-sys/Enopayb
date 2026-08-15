package com.example.core.vision

import android.graphics.Bitmap
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hybrid Vision Provider.
 * Combines local heuristic visual intelligence, past experience cache from Room DB,
 * and intelligent Gemini Multimodal Vision fallback when local identification lacks confidence.
 */
class HybridVisionProvider(
    private val localVisionProvider: LocalVisionProvider,
    private val geminiVisionProvider: GeminiVisionProvider,
    private val repository: JarvisRepository? = null
) : VisionProvider {

    override val providerName: String = "JARVIS Hybrid Vision Engine"
    override val isMultimodalSupported: Boolean = true

    var isCloudVisionEnabled: Boolean = true

    override suspend fun analyzeScreenshot(
        bitmap: Bitmap?,
        prompt: String,
        semanticGoal: String?,
        appPackage: String?,
        screenWidth: Int,
        screenHeight: Int
    ): VisualAnalysisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val pkg = appPackage.orEmpty()
        val targetRole = semanticGoal?.let { SemanticTarget.normalizeIntent(it) }

        // Step 1: Run Local Vision Provider (Heuristic layout & visual geometry anchors)
        val localResult = localVisionProvider.analyzeScreenshot(
            bitmap = bitmap,
            prompt = prompt,
            semanticGoal = semanticGoal,
            appPackage = appPackage,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        // Step 2: Check Learning / Experience Database for verified past successes
        val learnedElements = mutableListOf<VisualElement>()
        if (repository != null && pkg.isNotBlank() && targetRole != null) {
            try {
                val experiences = repository.getVisualExperiencesForRole(pkg, targetRole)
                for (exp in experiences) {
                    learnedElements.add(
                        VisualElement(
                            semanticRole = exp.semanticRole,
                            visualDescription = "${exp.visualDescription} (Learned)",
                            bounds = android.graphics.Rect(exp.boundsLeft, exp.boundsTop, exp.boundsRight, exp.boundsBottom),
                            confidence = exp.confidence,
                            source = "EXPERIENCE_DB"
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Check if we have high-confidence local or learned elements matching our target
        val hasConfidentLocalMatch = targetRole != null && (
                learnedElements.any { it.semanticRole == targetRole && it.confidence >= 0.90f } ||
                localResult.elements.any { it.semanticRole == targetRole && it.confidence >= 0.90f }
        )

        // Step 3: If confident or cloud vision is disabled, return aggregated local/learned results
        if (hasConfidentLocalMatch || !isCloudVisionEnabled || geminiVisionProvider.getEffectiveApiKey().isBlank()) {
            val aggregated = (learnedElements + localResult.elements).distinctBy { "${it.semanticRole}_${it.bounds}" }
            return@withContext VisualAnalysisResult(
                success = aggregated.isNotEmpty(),
                elements = aggregated,
                description = "Hybrid Vision: Resolved via Local Layout Intelligence & Experience DB (${aggregated.size} elements)",
                providerName = providerName,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // Step 4: If local confidence is insufficient and Gemini Vision is enabled & configured, use Gemini
        if (bitmap != null) {
            val cloudResult = geminiVisionProvider.analyzeScreenshot(
                bitmap = bitmap,
                prompt = prompt,
                semanticGoal = semanticGoal,
                appPackage = appPackage,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )

            if (cloudResult.success && cloudResult.elements.isNotEmpty()) {
                val merged = (cloudResult.elements + learnedElements + localResult.elements)
                    .distinctBy { "${it.semanticRole}_${it.bounds}" }
                return@withContext VisualAnalysisResult(
                    success = true,
                    elements = merged,
                    description = "Hybrid Vision: Resolved via Gemini Cloud Vision (${cloudResult.elements.size} cloud + ${localResult.elements.size} local)",
                    providerName = providerName,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // Fallback to local
        val aggregated = (learnedElements + localResult.elements).distinctBy { "${it.semanticRole}_${it.bounds}" }
        VisualAnalysisResult(
            success = aggregated.isNotEmpty(),
            elements = aggregated,
            description = "Hybrid Vision: Fallback to Local (${aggregated.size} elements)",
            providerName = providerName,
            latencyMs = System.currentTimeMillis() - startTime
        )
    }
}
