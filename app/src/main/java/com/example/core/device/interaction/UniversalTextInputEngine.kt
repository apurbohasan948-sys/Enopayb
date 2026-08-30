package com.example.core.device.interaction

import android.content.Context
import android.util.Log
import com.example.core.accessibility.ActionExecutionDetails
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.accessibility.ObservedNode
import kotlinx.coroutines.delay

data class TextInputResult(
    val success: Boolean,
    val targetField: String,
    val typedText: String,
    val methodUsed: String,
    val matchedNode: ObservedNode? = null,
    val evidence: String,
    val verifiedText: Boolean = false,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class UniversalTextInputEngine(private val context: Context) {
    private val TAG = "JARVIS_TextInputEngine"

    suspend fun executeType(text: String, targetQuery: String? = null): TextInputResult {
        if (text.isEmpty()) {
            return TextInputResult(
                success = false,
                targetField = targetQuery ?: "ACTIVE_INPUT",
                typedText = text,
                methodUsed = "NONE",
                evidence = "Input text cannot be empty.",
                error = "EMPTY_TEXT"
            )
        }

        if (!JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return TextInputResult(
                success = false,
                targetField = targetQuery ?: "ACTIVE_INPUT",
                typedText = text,
                methodUsed = "NONE",
                evidence = "Accessibility Service is disabled. Please enable JARVIS Accessibility Controller to type into on-screen fields.",
                error = "ACCESSIBILITY_DISABLED"
            )
        }

        val exec = JarvisAccessibilityService.typeText(targetQuery, text, context)

        if (!exec.success) {
            return TextInputResult(
                success = false,
                targetField = targetQuery ?: "ACTIVE_INPUT",
                typedText = text,
                methodUsed = exec.methodUsed,
                matchedNode = exec.matchedNode,
                evidence = exec.evidence,
                error = exec.error ?: "TYPE_FAILED"
            )
        }

        // Post-typing verification: inspect recent nodes
        delay(150)
        val postScreen = JarvisAccessibilityService.observeScreen()
        val textVerified = postScreen?.elements?.any { it.text.contains(text) || it.contentDescription.contains(text) } ?: false

        return TextInputResult(
            success = true,
            targetField = targetQuery ?: "ACTIVE_INPUT",
            typedText = text,
            methodUsed = exec.methodUsed,
            matchedNode = exec.matchedNode,
            evidence = "${exec.evidence} (Text verified on screen: $textVerified)",
            verifiedText = textVerified
        )
    }
}
