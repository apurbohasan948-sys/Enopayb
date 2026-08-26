package com.example.core.model

/**
 * IntentRouter: Deterministic No-LLM Command Routing Engine.
 * Intercepts simple system commands, device toggles, app launches, and accessibility actions.
 * Dispatches directly to tool execution without invoking any LLM, minimizing latency,
 * conserving battery, and eliminating cloud API dependencies.
 */
object IntentRouter {

    data class DeterministicMatch(
        val isMatched: Boolean,
        val toolIntent: ToolIntent? = null,
        val directOutput: String? = null,
        val confidence: Float = 1.0f,
        val rationale: String = "Deterministic No-LLM rule match"
    )

    /**
     * Matches raw input against deterministic Android command patterns.
     * Returns DeterministicMatch with isMatched = true if no LLM is required.
     */
    fun matchCommand(input: String): DeterministicMatch {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Navigation Actions
        if (lower == "back" || lower == "go back" || lower == "press back" || lower == "পিছনে যাও" || lower == "পেছনে") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("press_back", emptyMap(), "LOW", "No-LLM Back"),
                directOutput = "Navigating back.",
                rationale = "System back action executed directly."
            )
        }

        if (lower == "home" || lower == "go home" || lower == "press home" || lower == "হোমে যাও" || lower == "হোম") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("press_home", emptyMap(), "LOW", "No-LLM Home"),
                directOutput = "Returning to Home screen.",
                rationale = "System home launcher executed directly."
            )
        }

        if (lower == "lock" || lower == "lock screen" || lower == "screen off" || lower == "ফোন লক করো" || lower == "স্ক্রিন বন্ধ করো") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("lock_screen", emptyMap(), "LOW", "No-LLM Lock"),
                directOutput = "Locking device screen.",
                rationale = "System screen lock action executed directly."
            )
        }

        // 2. Flashlight / Torch
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশলাইট") || lower.contains("টর্চ")) {
            val isOff = lower.contains("off") || lower.contains("বন্ধ") || lower.contains("নিভাও")
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("toggle_flashlight", mapOf("state" to if (isOff) "off" else "on"), "LOW", "No-LLM Flashlight"),
                directOutput = if (isOff) "Flashlight turned off." else "Flashlight turned on.",
                rationale = "Camera2 hardware torch toggle executed directly."
            )
        }

        // 3. Media Volume & Sound
        if (lower == "volume up" || lower == "sound up" || lower == "ভলিউম বাড়াও" || lower == "আওয়াজ বাড়াও") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("volume_up", emptyMap(), "LOW", "No-LLM Volume Up"),
                directOutput = "Media volume increased.",
                rationale = "AudioManager volume step up executed directly."
            )
        }

        if (lower == "volume down" || lower == "sound down" || lower == "ভলিউম কমাও" || lower == "আওয়াজ কমাও") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("volume_down", emptyMap(), "LOW", "No-LLM Volume Down"),
                directOutput = "Media volume decreased.",
                rationale = "AudioManager volume step down executed directly."
            )
        }

        if (lower == "mute" || lower == "silent" || lower == "শব্দ বন্ধ করো" || lower == "সাইলেন্ট করো") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("mute", emptyMap(), "LOW", "No-LLM Mute"),
                directOutput = "Device muted.",
                rationale = "AudioManager mute executed directly."
            )
        }

        // 4. Screenshot / Screen Capture
        if (lower == "take screenshot" || lower == "screenshot" || lower == "স্ক্রিনশট নাও" || lower == "স্ক্রিনশট") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("take_screenshot", emptyMap(), "LOW", "No-LLM Screenshot"),
                directOutput = "Capturing screen snapshot.",
                rationale = "System screenshot trigger executed directly."
            )
        }

        // 5. Battery and Status
        if (lower == "battery" || lower == "battery level" || lower == "battery percentage" || lower == "ব্যাটারি কত" || lower == "ব্যাটারি চার্জ") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("check_battery", emptyMap(), "LOW", "No-LLM Battery"),
                directOutput = "Checking battery level.",
                rationale = "BatteryManager query executed directly."
            )
        }

        // 6. Direct App Launch (e.g. "open YouTube", "launch Chrome", "settings", "calculator")
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("খোল ") || lower.startsWith("ওপেন করো ")) {
            val appTarget = trimmed
                .substringAfter("open ", "")
                .substringAfter("launch ", "")
                .substringAfter("খোল ", "")
                .substringAfter("ওপেন করো ", "")
                .trim()

            if (appTarget.isNotBlank() && !appTarget.contains(" and ") && !appTarget.contains(" then ")) {
                return DeterministicMatch(
                    isMatched = true,
                    toolIntent = ToolIntent("open_app", mapOf("app_name" to appTarget), "LOW", "No-LLM App Launch: $appTarget"),
                    directOutput = "Opening $appTarget.",
                    rationale = "PackageManager package launch intent executed directly."
                )
            }
        }

        // Specific single-word app shortcuts
        if (lower == "youtube") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("open_app", mapOf("app_name" to "YouTube"), "LOW", "No-LLM Open YouTube"),
                directOutput = "Opening YouTube.",
                rationale = "Direct app shortcut."
            )
        }
        if (lower == "settings" || lower == "setting" || lower == "সেটিংস") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("open_app", mapOf("app_name" to "Settings"), "LOW", "No-LLM Open Settings"),
                directOutput = "Opening Settings.",
                rationale = "Direct system settings shortcut."
            )
        }
        if (lower == "calculator" || lower == "ক্যালকুলেটর") {
            return DeterministicMatch(
                isMatched = true,
                toolIntent = ToolIntent("open_app", mapOf("app_name" to "Calculator"), "LOW", "No-LLM Open Calculator"),
                directOutput = "Opening Calculator.",
                rationale = "Direct calculator shortcut."
            )
        }

        // No deterministic match found -> proceed to skill/local model/vision/router
        return DeterministicMatch(isMatched = false)
    }
}
