package com.example.core.agent

import com.example.core.vision.UnifiedScreen
import com.example.core.tools.ToolExecutionResult

data class TransitionEvaluation(
    val transitionOccurred: Boolean,
    val transitionType: String,
    val beforeContext: String,
    val afterContext: String,
    val reason: String,
    val confidence: Float
)

/**
 * ScreenTransitionVerifier.
 * Validates whether an action actually triggered an expected screen transition.
 * Prevents assuming action success without empirical verification of UI state change.
 */
class ScreenTransitionVerifier {

    fun verifyTransition(
        expectedOutcome: String,
        beforeScreen: UnifiedScreen?,
        afterScreen: UnifiedScreen?,
        actionResult: ToolExecutionResult
    ): TransitionEvaluation {
        if (beforeScreen == null || afterScreen == null) {
            return TransitionEvaluation(
                transitionOccurred = actionResult.success,
                transitionType = "UNOBSERVED_FALLBACK",
                beforeContext = "Unknown",
                afterContext = "Unknown",
                reason = "One of the observation states was null; falling back to tool execution status",
                confidence = if (actionResult.success) 0.6f else 0.2f
            )
        }

        val pkgBefore = beforeScreen.packageName.lowercase()
        val pkgAfter = afterScreen.packageName.lowercase()
        val nodesBefore = beforeScreen.totalNodes
        val nodesAfter = afterScreen.totalNodes

        // 1. Package-Level Transition (e.g. Launching an App or switching tasks)
        if (pkgBefore != pkgAfter) {
            return TransitionEvaluation(
                transitionOccurred = true,
                transitionType = "APP_SWITCH",
                beforeContext = pkgBefore,
                afterContext = pkgAfter,
                reason = "Active foreground application switched from $pkgBefore to $pkgAfter",
                confidence = 0.98f
            )
        }

        // 2. Significant Node Count Difference (Screen loaded new view / dialog / list)
        val nodeDiff = Math.abs(nodesAfter - nodesBefore)
        if (nodeDiff > 4) {
            return TransitionEvaluation(
                transitionOccurred = true,
                transitionType = "LAYOUT_RESTRUCTURING",
                beforeContext = "$pkgBefore ($nodesBefore nodes)",
                afterContext = "$pkgAfter ($nodesAfter nodes)",
                reason = "Accessibility tree shifted significantly (Δ $nodeDiff nodes)",
                confidence = 0.92f
            )
        }

        // 3. Content Text Differential Verification
        val beforeTexts = beforeScreen.elements.mapNotNull { it.text ?: it.contentDescription }.toSet()
        val afterTexts = afterScreen.elements.mapNotNull { it.text ?: it.contentDescription }.toSet()
        val newTexts = afterTexts - beforeTexts

        if (newTexts.isNotEmpty()) {
            return TransitionEvaluation(
                transitionOccurred = true,
                transitionType = "CONTENT_UPDATE",
                beforeContext = "$pkgBefore",
                afterContext = "$pkgAfter (New: ${newTexts.take(2).joinToString()})",
                reason = "New content appeared: ${newTexts.take(3).joinToString(", ")}",
                confidence = 0.88f
            )
        }

        // 4. Fallback check on Tool result
        if (actionResult.success && actionResult.verified) {
            return TransitionEvaluation(
                transitionOccurred = true,
                transitionType = "TOOL_VERIFIED",
                beforeContext = pkgBefore,
                afterContext = pkgAfter,
                reason = "Action reported confirmed execution: ${actionResult.evidence}",
                confidence = 0.80f
            )
        }

        return TransitionEvaluation(
            transitionOccurred = false,
            transitionType = "NO_TRANSITION_DETECTED",
            beforeContext = pkgBefore,
            afterContext = pkgAfter,
            reason = "Screen showed no meaningful changes after action dispatch.",
            confidence = 0.85f
        )
    }
}
