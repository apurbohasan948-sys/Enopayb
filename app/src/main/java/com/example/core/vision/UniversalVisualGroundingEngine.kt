package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

data class GroundedVisualTarget(
    val element: SemanticUIElement?,
    val role: String,
    val confidence: Float,
    val groundingSource: String, // ACCESSIBILITY, ICON_GLYPH, OCR, SPATIAL_HEURISTIC, GEMINI_VISION
    val rationale: String,
    val boundingBox: Rect = Rect()
)

/**
 * UniversalVisualGroundingEngine.
 * Connects User Intent → Screen Observation → Target Resolution with Icon & OCR Grounding.
 * Ensures unlabeled controls (e.g. Magnifying glass 🔍, Play button ▶, Back arrow ←, Settings ⚙)
 * are accurately located and targeted.
 */
class UniversalVisualGroundingEngine(
    private val context: Context,
    val screenEngine: UniversalScreenUnderstandingEngine,
    val iconRecognizer: IconSemanticRecognizer = IconSemanticRecognizer(),
    val targetMatcher: VisualTargetMatcher = VisualTargetMatcher()
) {
    companion object {
        private const val TAG = "VisualGrounding"
    }

    /**
     * Grounds a semantic goal/role onto the active screen.
     */
    suspend fun groundTarget(
        semanticGoal: String,
        screen: SemanticScreenModel,
        screenshot: Bitmap? = null
    ): GroundedVisualTarget {
        val lowerGoal = semanticGoal.lowercase().trim()
        val normalizedRole = SemanticTarget.normalizeIntent(semanticGoal)

        // 1. First pass: Match via VisualTargetMatcher against semantic screen elements
        val matchResult = targetMatcher.matchTarget(semanticGoal, screen)
        if (matchResult.isConfident && matchResult.selectedElement != null) {
            val elem = matchResult.selectedElement
            return GroundedVisualTarget(
                element = elem,
                role = elem.role,
                confidence = matchResult.confidence,
                groundingSource = elem.source,
                rationale = matchResult.reason,
                boundingBox = elem.bounds
            )
        }

        // 2. Second pass: Direct Icon recognition over all screen elements
        for (elem in screen.elements) {
            val recognized = iconRecognizer.recognizeIcon(
                text = elem.label,
                contentDescription = elem.description,
                viewId = elem.originalViewId,
                bounds = elem.bounds,
                appPackage = screen.packageName,
                screenContext = "",
                taskGoal = semanticGoal,
                bitmap = screenshot
            )

            if (recognized != null) {
                val matchesGoal = recognized.symbol.equals(normalizedRole, ignoreCase = true) ||
                        recognized.contextualRole.equals(normalizedRole, ignoreCase = true) ||
                        lowerGoal.contains(recognized.meaning.lowercase())

                if (matchesGoal) {
                    val updatedElem = elem.copy(
                        role = recognized.contextualRole,
                        iconMeaning = recognized.meaning,
                        confidence = recognized.confidence,
                        source = recognized.detectionMethod
                    )
                    return GroundedVisualTarget(
                        element = updatedElem,
                        role = recognized.contextualRole,
                        confidence = recognized.confidence,
                        groundingSource = recognized.detectionMethod,
                        rationale = "Grounding via Icon recognition: ${recognized.symbol} (${recognized.meaning})",
                        boundingBox = elem.bounds
                    )
                }
            }
        }

        // 3. Third pass: Text & OCR exact / partial match
        val ocrOrTextMatch = screen.elements.firstOrNull { elem ->
            val label = (elem.label ?: "").lowercase()
            val desc = (elem.description ?: "").lowercase()
            label.contains(lowerGoal) || desc.contains(lowerGoal) || (lowerGoal.isNotBlank() && (label.startsWith(lowerGoal) || desc.startsWith(lowerGoal)))
        }

        if (ocrOrTextMatch != null) {
            return GroundedVisualTarget(
                element = ocrOrTextMatch,
                role = ocrOrTextMatch.role,
                confidence = 0.85f,
                groundingSource = ocrOrTextMatch.source,
                rationale = "Textual OCR/Label ground match for '$semanticGoal'.",
                boundingBox = ocrOrTextMatch.bounds
            )
        }

        // 4. Low-confidence fallback
        return GroundedVisualTarget(
            element = matchResult.selectedElement,
            role = normalizedRole,
            confidence = matchResult.confidence.coerceAtMost(0.40f),
            groundingSource = "UNRESOLVED_FALLBACK",
            rationale = "Could not confidently ground '$semanticGoal' on ${screen.packageName}.",
            boundingBox = matchResult.selectedElement?.bounds ?: Rect()
        )
    }
}
