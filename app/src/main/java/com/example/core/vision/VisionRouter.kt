package com.example.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.core.model.CloudUsagePolicy
import com.example.core.vision.ocr.OCRProvider

enum class VisionTier {
    ACCESSIBILITY_TREE,
    SEMANTIC_UI_NODES,
    LOCAL_OCR,
    LOCAL_VISION_MODEL,
    GEMINI_VISION_FALLBACK
}

data class VisionRouteResult(
    val tierUsed: VisionTier,
    val elements: List<VisualElement>,
    val confidence: Float,
    val providerDescription: String,
    val latencyMs: Long
)

/**
 * VisionRouter.
 * Implements an intelligent 5-tier vision resolution pipeline:
 * Tier 1: Accessibility Tree (0ms / No-AI text & bounds)
 * Tier 2: Semantic UI Nodes & Spatial heuristics
 * Tier 3: Local OCR (On-device text extraction)
 * Tier 4: Local Vision Model / Icon understanding (Magnifier -> Search, Mic -> Voice, Gear -> Settings)
 * Tier 5: Gemini Vision Fallback (Only if local confidence < 0.65 and policy allows)
 */
class VisionRouter(
    val localVisionProvider: LocalVisionProvider,
    val geminiVisionProvider: GeminiVisionProvider,
    val cloudUsagePolicy: CloudUsagePolicy? = null
) {
    /**
     * Routes vision requests through the 5-tier hierarchy.
     */
    suspend fun resolveVision(
        bitmap: Bitmap?,
        prompt: String,
        semanticGoal: String?,
        appPackage: String?,
        accessibilityElements: List<ScreenElement> = emptyList(),
        ocrElements: List<VisualElement> = emptyList(),
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): VisionRouteResult {
        val startTime = System.currentTimeMillis()
        val target = semanticGoal?.let { SemanticTarget.normalizeIntent(it) }

        // Tier 1: Direct Accessibility Match (Lowest cost, highest accuracy for standard text buttons)
        if (target != null) {
            val matchingAcc = accessibilityElements.firstOrNull { elem ->
                elem.semanticRole == target || (elem.text != null && elem.text.contains(semanticGoal, ignoreCase = true))
            }
            if (matchingAcc != null && matchingAcc.bounds != null) {
                val element = VisualElement(
                    semanticRole = matchingAcc.semanticRole,
                    visualDescription = "Accessibility Node: ${matchingAcc.text ?: matchingAcc.contentDescription ?: matchingAcc.viewId}",
                    bounds = matchingAcc.bounds,
                    confidence = 0.98f,
                    source = "ACCESSIBILITY_TREE"
                )
                return VisionRouteResult(
                    tierUsed = VisionTier.ACCESSIBILITY_TREE,
                    elements = listOf(element),
                    confidence = 0.98f,
                    providerDescription = "Accessibility Tree (0 AI Cost)",
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // Tier 2 & 3: Local OCR & Spatial Anchors
        if (ocrElements.isNotEmpty() && target != null) {
            val matchingOcr = ocrElements.firstOrNull {
                it.semanticRole == target || it.visualDescription.contains(semanticGoal, ignoreCase = true)
            }
            if (matchingOcr != null) {
                return VisionRouteResult(
                    tierUsed = VisionTier.LOCAL_OCR,
                    elements = listOf(matchingOcr),
                    confidence = matchingOcr.confidence,
                    providerDescription = "Local OCR Engine",
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // Tier 4: Local Vision Provider (Heuristics & Icon Recognition)
        val localResult = localVisionProvider.analyzeScreenshot(
            bitmap = bitmap,
            prompt = prompt,
            semanticGoal = semanticGoal,
            appPackage = appPackage,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        if (localResult.success && localResult.elements.isNotEmpty()) {
            val bestConfidence = localResult.elements.maxOfOrNull { it.confidence } ?: 0.80f
            if (bestConfidence >= 0.70f) {
                return VisionRouteResult(
                    tierUsed = VisionTier.LOCAL_VISION_MODEL,
                    elements = localResult.elements,
                    confidence = bestConfidence,
                    providerDescription = localVisionProvider.providerName,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // Tier 5: Gemini Vision Fallback (Only if policy allows and local confidence is insufficient)
        val (isCloudAllowed, _) = cloudUsagePolicy?.isCloudRequestPermitted(isVision = true) ?: Pair(true, "")
        if (isCloudAllowed && geminiVisionProvider.getEffectiveApiKey().isNotBlank() && bitmap != null) {
            cloudUsagePolicy?.recordCloudRequest()
            val geminiResult = geminiVisionProvider.analyzeScreenshot(
                bitmap = bitmap,
                prompt = prompt,
                semanticGoal = semanticGoal,
                appPackage = appPackage,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
            if (geminiResult.success && geminiResult.elements.isNotEmpty()) {
                return VisionRouteResult(
                    tierUsed = VisionTier.GEMINI_VISION_FALLBACK,
                    elements = geminiResult.elements,
                    confidence = geminiResult.elements.maxOfOrNull { it.confidence } ?: 0.90f,
                    providerDescription = geminiVisionProvider.providerName,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // Return local result as safe fallback
        return VisionRouteResult(
            tierUsed = VisionTier.LOCAL_VISION_MODEL,
            elements = localResult.elements,
            confidence = 0.60f,
            providerDescription = "Local UI Fallback Engine",
            latencyMs = System.currentTimeMillis() - startTime
        )
    }
}
