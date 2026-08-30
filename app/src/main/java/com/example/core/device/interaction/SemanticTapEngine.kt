package com.example.core.device.interaction

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.example.core.accessibility.ActionExecutionDetails
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.accessibility.ObservedNode
import com.example.core.accessibility.ObservedScreen
import kotlinx.coroutines.delay

data class SemanticTapResult(
    val success: Boolean,
    val target: String,
    val methodUsed: String,
    val matchedNode: ObservedNode? = null,
    val evidence: String,
    val verifiedStateChange: Boolean = false,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class SemanticTapEngine(private val context: Context) {
    private val TAG = "JARVIS_SemanticTap"

    suspend fun executeTap(target: String): SemanticTapResult {
        val trimmedTarget = target.trim()
        if (trimmedTarget.isEmpty()) {
            return SemanticTapResult(
                success = false,
                target = target,
                methodUsed = "NONE",
                evidence = "Target query cannot be empty.",
                error = "EMPTY_TARGET"
            )
        }

        if (!JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return SemanticTapResult(
                success = false,
                target = target,
                methodUsed = "NONE",
                evidence = "Accessibility Service is not enabled. Go to Settings > Accessibility to enable JARVIS Accessibility Controller.",
                error = "ACCESSIBILITY_DISABLED"
            )
        }

        val initialScreen = JarvisAccessibilityService.observeScreen()

        // Handle system navigation targets directly
        val upperTarget = trimmedTarget.uppercase()
        if (upperTarget == "BACK" || upperTarget == "GO_BACK" || upperTarget == "PRESS_BACK") {
            val backOk = JarvisAccessibilityService.goBack()
            delay(150)
            return SemanticTapResult(
                success = backOk,
                target = target,
                methodUsed = "GLOBAL_ACTION_BACK",
                evidence = if (backOk) "Dispatched system GLOBAL_ACTION_BACK." else "Failed to dispatch back action.",
                verifiedStateChange = backOk
            )
        }
        if (upperTarget == "HOME" || upperTarget == "GO_HOME" || upperTarget == "PRESS_HOME") {
            val homeOk = JarvisAccessibilityService.pressHome()
            delay(150)
            return SemanticTapResult(
                success = homeOk,
                target = target,
                methodUsed = "GLOBAL_ACTION_HOME",
                evidence = if (homeOk) "Dispatched system GLOBAL_ACTION_HOME." else "Failed to dispatch home action.",
                verifiedStateChange = homeOk
            )
        }

        // Execute click via JarvisAccessibilityService
        val executionDetails = JarvisAccessibilityService.clickElement(trimmedTarget)

        if (!executionDetails.success) {
            return SemanticTapResult(
                success = false,
                target = target,
                methodUsed = executionDetails.methodUsed,
                matchedNode = executionDetails.matchedNode,
                evidence = executionDetails.evidence,
                error = executionDetails.error ?: "CLICK_EXECUTION_FAILED"
            )
        }

        // Verification phase: wait briefly and observe screen diff
        delay(200)
        val postScreen = JarvisAccessibilityService.observeScreen()
        val stateChanged = hasScreenChanged(initialScreen, postScreen)

        return SemanticTapResult(
            success = true,
            target = target,
            methodUsed = executionDetails.methodUsed,
            matchedNode = executionDetails.matchedNode,
            evidence = "${executionDetails.evidence} (Post-action screen change verified: $stateChanged)",
            verifiedStateChange = stateChanged
        )
    }

    private fun hasScreenChanged(before: ObservedScreen?, after: ObservedScreen?): Boolean {
        if (before == null || after == null) return false
        if (before.packageName != after.packageName) return true
        if (before.elements.size != after.elements.size) return true
        val beforeTexts = before.elements.map { it.text.ifEmpty { it.contentDescription } }.toSet()
        val afterTexts = after.elements.map { it.text.ifEmpty { it.contentDescription } }.toSet()
        return beforeTexts != afterTexts
    }
}
