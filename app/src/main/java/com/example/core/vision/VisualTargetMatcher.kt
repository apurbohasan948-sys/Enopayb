package com.example.core.vision

import android.graphics.Rect
import android.util.Log

data class MatchedTargetResult(
    val selectedElement: SemanticUIElement?,
    val targetRole: String,
    val confidence: Float,
    val reason: String,
    val alternatives: List<SemanticUIElement> = emptyList(),
    val isConfident: Boolean = true,
    val recommendedAction: String = "CLICK"
)

/**
 * VisualTargetMatcher.
 * Matches user goals to the best candidate SemanticUIElement on the current screen.
 * Answers "What is this element?" using visual semantics, icons, accessibility, and context.
 */
class VisualTargetMatcher {

    companion object {
        private const val TAG = "VisualTargetMatcher"
        private const val CONFIDENCE_THRESHOLD = 0.65f
    }

    fun matchTarget(
        goal: String,
        screen: SemanticScreenModel,
        previousAction: String? = null
    ): MatchedTargetResult {
        val normalizedRole = SemanticTarget.normalizeIntent(goal)
        val goalLower = goal.trim().lowercase()
        val appPkg = screen.packageName.lowercase()

        val candidates = mutableListOf<ScoredTarget>()

        screen.elements.forEach { elem ->
            val score = scoreCandidate(elem, goalLower, normalizedRole, appPkg)
            if (score > 0.15f) {
                candidates.add(ScoredTarget(elem, score))
            }
        }

        candidates.sortByDescending { it.score }

        val best = candidates.firstOrNull()
        if (best != null && best.score >= CONFIDENCE_THRESHOLD) {
            val element = best.element
            val alternatives = candidates.drop(1).take(3).map { it.element }
            val recommendedAction = when {
                element.editable || element.role == SemanticTarget.INPUT_FIELD -> "TYPE"
                element.scrollable -> "SCROLL"
                else -> "CLICK"
            }

            val labelSummary = element.label ?: element.description ?: element.iconMeaning ?: element.role
            val reason = "Matched ${element.role} ('$labelSummary') via ${element.source} with confidence ${(best.score * 100).toInt()}%"

            return MatchedTargetResult(
                selectedElement = element,
                targetRole = element.role,
                confidence = best.score,
                reason = reason,
                alternatives = alternatives,
                isConfident = true,
                recommendedAction = recommendedAction
            )
        }

        // Fallback contextual layout matching for common apps
        val heuristic = matchContextualHeuristic(goalLower, normalizedRole, appPkg, screen)
        if (heuristic != null) {
            return heuristic
        }

        return MatchedTargetResult(
            selectedElement = best?.element,
            targetRole = normalizedRole,
            confidence = best?.score ?: 0.30f,
            reason = "No high-confidence target found for '$goal'. Best match: ${best?.element?.role ?: "None"} (${((best?.score ?: 0f) * 100).toInt()}%)",
            alternatives = candidates.take(3).map { it.element },
            isConfident = false,
            recommendedAction = "OBSERVE_AGAIN"
        )
    }

    private fun scoreCandidate(
        elem: SemanticUIElement,
        goalLower: String,
        normalizedRole: String,
        appPkg: String
    ): Float {
        var score = 0f

        // 1. Role Match
        if (elem.role == normalizedRole && elem.role != SemanticTarget.UNKNOWN) {
            score += 0.50f
        } else if (normalizedRole == SemanticTarget.PLAY && elem.role == SemanticTarget.VIDEO_ITEM) {
            score += 0.50f
        }
        if (goalLower.contains("video") && elem.role == SemanticTarget.VIDEO_ITEM) {
            score += 0.40f
        }

        // 2. Icon Meaning Match (e.g. "magnifying glass" -> search)
        val icon = elem.iconMeaning?.lowercase().orEmpty()
        if (icon.isNotBlank()) {
            if (goalLower.contains(icon) || icon.contains(goalLower)) {
                score += 0.45f
            }
            if (icon.contains("magnifying") && normalizedRole == SemanticTarget.SEARCH) score += 0.40f
            if (icon.contains("play") && normalizedRole == SemanticTarget.PLAY) score += 0.40f
            if (icon.contains("back") && normalizedRole == SemanticTarget.BACK) score += 0.40f
            if (icon.contains("gear") && normalizedRole == SemanticTarget.SETTINGS) score += 0.40f
            if (icon.contains("dots") && normalizedRole == SemanticTarget.MORE) score += 0.40f
        }

        // 3. Text / Label Match
        val text = elem.label?.lowercase().orEmpty()
        if (text.isNotBlank()) {
            if (goalLower.contains(text) || text.contains(goalLower)) {
                score += 0.40f
            }
        }

        // 4. Content Description Match
        val desc = elem.description?.lowercase().orEmpty()
        if (desc.isNotBlank()) {
            if (goalLower.contains(desc) || desc.contains(goalLower)) {
                score += 0.35f
            }
            if (desc.contains("search") && normalizedRole == SemanticTarget.SEARCH) score += 0.30f
            if (desc.contains("play") && normalizedRole == SemanticTarget.PLAY) score += 0.30f
        }

        // 5. Interactivity Bonus
        if (elem.clickable) score += 0.10f
        if (elem.editable && (normalizedRole == SemanticTarget.INPUT_FIELD || goalLower.contains("type") || goalLower.contains("search"))) {
            score += 0.25f
        }

        // 6. Application Contextual Bias
        if (appPkg.contains("youtube")) {
            if (normalizedRole == SemanticTarget.SEARCH && (elem.bounds.top < 300 && elem.bounds.left > 600)) {
                score += 0.20f
            }
            if (normalizedRole == SemanticTarget.VIDEO_ITEM && elem.bounds.top > 300) {
                score += 0.20f
            }
        } else if (appPkg.contains("chrome")) {
            if (normalizedRole == SemanticTarget.SEARCH || normalizedRole == SemanticTarget.INPUT_FIELD) {
                if (elem.bounds.top < 350) score += 0.25f
            }
        } else if (appPkg.contains("whatsapp")) {
            if (normalizedRole == SemanticTarget.SEARCH && elem.bounds.top < 300) {
                score += 0.20f
            }
        }

        // Scale by element detection confidence
        score *= elem.confidence

        return minOf(1.0f, score)
    }

    private fun matchContextualHeuristic(
        goalLower: String,
        normalizedRole: String,
        appPkg: String,
        screen: SemanticScreenModel
    ): MatchedTargetResult? {
        if (appPkg.contains("youtube") && normalizedRole == SemanticTarget.SEARCH) {
            val candidate = screen.elements.firstOrNull { it.bounds.top < 300 && it.bounds.right > 650 && it.clickable }
            if (candidate != null) {
                return MatchedTargetResult(
                    selectedElement = candidate,
                    targetRole = SemanticTarget.SEARCH,
                    confidence = 0.88f,
                    reason = "Identified YouTube top-bar search control by spatial layout and clickable bounds.",
                    isConfident = true,
                    recommendedAction = "CLICK"
                )
            }
        }

        if ((appPkg.contains("chrome") || appPkg.contains("browser")) && (normalizedRole == SemanticTarget.SEARCH || normalizedRole == SemanticTarget.INPUT_FIELD)) {
            val urlBar = screen.elements.firstOrNull { (it.editable || it.clickable) && it.bounds.top < 380 }
            if (urlBar != null) {
                return MatchedTargetResult(
                    selectedElement = urlBar,
                    targetRole = SemanticTarget.INPUT_FIELD,
                    confidence = 0.90f,
                    reason = "Identified browser address/search omnibox at top of screen.",
                    isConfident = true,
                    recommendedAction = "TYPE"
                )
            }
        }

        if (normalizedRole == SemanticTarget.BACK) {
            val backCandidate = screen.elements.firstOrNull { it.bounds.top < 300 && it.bounds.left < 300 && it.clickable }
            if (backCandidate != null) {
                return MatchedTargetResult(
                    selectedElement = backCandidate,
                    targetRole = SemanticTarget.BACK,
                    confidence = 0.89f,
                    reason = "Identified top-left navigation back button.",
                    isConfident = true,
                    recommendedAction = "CLICK"
                )
            }
        }

        return null
    }

    private data class ScoredTarget(
        val element: SemanticUIElement,
        val score: Float
    )
}
