package com.example.core.model

import com.example.data.local.entity.KnowledgeChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FallbackProvider.
 * Safe, deterministic on-device fallback engine when other providers are unreachable
 * or when operating under extreme resource constraints.
 * Guarantees zero crashing and produces safe Android action intents.
 */
class FallbackProvider : ModelProvider {

    override suspend fun generateResponse(
        prompt: String,
        contextChunks: List<KnowledgeChunkEntity>,
        language: String
    ): ModelResponse = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val raw = prompt.trim()
        val lower = raw.lowercase()

        // 1. Direct deterministic mapping for standard device commands
        val (toolIntent, responseText) = when {
            lower.contains("youtube") && (lower.contains("open") || lower.contains("launch")) -> {
                ToolIntent("open_app", mapOf("app_name" to "YouTube"), "LOW", "Fallback open YouTube") to
                        "Opening YouTube via local fallback engine."
            }
            lower.contains("chrome") || lower.contains("browser") -> {
                ToolIntent("open_app", mapOf("app_name" to "Chrome"), "LOW", "Fallback open Chrome") to
                        "Opening browser via local fallback engine."
            }
            lower.contains("setting") -> {
                ToolIntent("open_app", mapOf("app_name" to "Settings"), "LOW", "Fallback open Settings") to
                        "Opening System Settings via local fallback engine."
            }
            lower.contains("flashlight") || lower.contains("torch") -> {
                val turnOff = lower.contains("off")
                ToolIntent("toggle_flashlight", mapOf("state" to if (turnOff) "off" else "on"), "LOW", "Fallback flashlight") to
                        if (turnOff) "Turning flashlight off." else "Turning flashlight on."
            }
            lower.contains("volume up") -> {
                ToolIntent("volume_up", emptyMap(), "LOW", "Fallback volume") to "Increasing media volume."
            }
            lower.contains("volume down") -> {
                ToolIntent("volume_down", emptyMap(), "LOW", "Fallback volume") to "Decreasing media volume."
            }
            lower.contains("lock") || lower.contains("screen off") -> {
                ToolIntent("lock_screen", emptyMap(), "LOW", "Fallback lock") to "Locking screen."
            }
            lower == "back" || lower.contains("go back") -> {
                ToolIntent("press_back", emptyMap(), "LOW", "Fallback back") to "Navigating back."
            }
            lower == "home" || lower.contains("go home") -> {
                ToolIntent("press_home", emptyMap(), "LOW", "Fallback home") to "Returning to Home."
            }
            lower.contains("battery") -> {
                ToolIntent("check_battery", emptyMap(), "LOW", "Fallback battery") to "Checking battery level and power state."
            }
            lower.contains("screenshot") || lower.contains("capture") -> {
                ToolIntent("take_screenshot", emptyMap(), "LOW", "Fallback screenshot") to "Capturing screenshot."
            }
            else -> {
                null to "I processed your request using local fallback rules. Goal: '$raw'."
            }
        }

        val latency = System.currentTimeMillis() - startTime
        ModelResponse(
            text = responseText,
            latencyMs = latency,
            providerType = "LOCAL_FALLBACK",
            confidence = if (toolIntent != null) 0.85f else 0.60f,
            toolIntent = toolIntent,
            usedContextChunks = contextChunks
        )
    }
}
