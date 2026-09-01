package com.example.core.agent.nlu

import com.example.core.model.ToolIntent

enum class IntentCategory {
    APP_CONTROL,
    MEDIA_CONTROL,
    COMMUNICATION,
    WEB_SEARCH,
    DEVICE_SETTINGS,
    SCREEN_INTERACTION,
    FOLLOW_UP_ACTION,
    CONFIRMATION_RESPONSE,
    STOP_INTERRUPT,
    GENERAL_CONVERSATION,
    KNOWLEDGE_QUERY,
    UNKNOWN
}

data class SemanticUserIntent(
    val category: IntentCategory,
    val canonicalAction: String,
    val confidence: Float,
    val entities: ExtractedEntities,
    val isMultiStep: Boolean = false,
    val directToolIntent: ToolIntent? = null,
    val isFollowUp: Boolean = false,
    val requiresScreenObservation: Boolean = true,
    val naturalResponseRecommendation: String? = null,
    val rawUtterance: String = ""
)

/**
 * NaturalLanguageUnderstandingEngine.
 * Semantic Intent Mapping & Parsing for English, Bengali (বাংলা), and Banglish.
 * Eliminates brittle single-phrase matches and extracts structured parameters cleanly.
 */
object NaturalLanguageUnderstandingEngine {

    fun parse(input: String, currentApp: String? = null, isAwaitingFollowUp: Boolean = false): SemanticUserIntent {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()
        val entities = EntityExtractor.extract(trimmed, currentApp)

        // 1. Emergency Stop / Barge-in
        if (isEmergencyStop(lower)) {
            return SemanticUserIntent(
                category = IntentCategory.STOP_INTERRUPT,
                canonicalAction = "STOP_EXECUTION",
                confidence = 1.0f,
                entities = entities,
                directToolIntent = ToolIntent("cancel_task", emptyMap(), "LOW"),
                naturalResponseRecommendation = "Execution stopped.",
                rawUtterance = trimmed
            )
        }

        // 2. Affirmative / Negative Confirmations
        if (isAffirmative(lower)) {
            return SemanticUserIntent(
                category = IntentCategory.CONFIRMATION_RESPONSE,
                canonicalAction = "CONFIRM_YES",
                confidence = 0.98f,
                entities = entities,
                naturalResponseRecommendation = "Confirmed.",
                rawUtterance = trimmed
            )
        }
        if (isNegative(lower)) {
            return SemanticUserIntent(
                category = IntentCategory.CONFIRMATION_RESPONSE,
                canonicalAction = "CONFIRM_NO",
                confidence = 0.98f,
                entities = entities,
                naturalResponseRecommendation = "Cancelled.",
                rawUtterance = trimmed
            )
        }

        // 3. Follow-up Anaphoric References ("that one", "oita", "prothom ta", "scroll down and tap it")
        if (isAwaitingFollowUp || isAnaphoricFollowUp(lower)) {
            val action = inferFollowUpAction(lower, entities)
            return SemanticUserIntent(
                category = IntentCategory.FOLLOW_UP_ACTION,
                canonicalAction = action,
                confidence = 0.90f,
                entities = entities,
                isFollowUp = true,
                requiresScreenObservation = true,
                rawUtterance = trimmed
            )
        }

        // 4. Navigation Actions (Back, Home, Lock, Screenshot)
        if (isBackCommand(lower)) {
            return SemanticUserIntent(
                category = IntentCategory.SCREEN_INTERACTION,
                canonicalAction = "NAVIGATE_BACK",
                confidence = 0.99f,
                entities = entities,
                directToolIntent = ToolIntent("press_back", emptyMap(), "LOW"),
                naturalResponseRecommendation = "Going back.",
                rawUtterance = trimmed
            )
        }
        if (isHomeCommand(lower)) {
            return SemanticUserIntent(
                category = IntentCategory.SCREEN_INTERACTION,
                canonicalAction = "NAVIGATE_HOME",
                confidence = 0.99f,
                entities = entities,
                directToolIntent = ToolIntent("press_home", emptyMap(), "LOW"),
                naturalResponseRecommendation = "Returning to Home.",
                rawUtterance = trimmed
            )
        }
        if (isLockCommand(lower)) {
            return SemanticUserIntent(
                category = IntentCategory.DEVICE_SETTINGS,
                canonicalAction = "LOCK_SCREEN",
                confidence = 0.99f,
                entities = entities,
                directToolIntent = ToolIntent("lock_screen", emptyMap(), "LOW"),
                naturalResponseRecommendation = "Locking screen.",
                rawUtterance = trimmed
            )
        }
        if (isScreenshotCommand(lower)) {
            return SemanticUserIntent(
                category = IntentCategory.SCREEN_INTERACTION,
                canonicalAction = "TAKE_SCREENSHOT",
                confidence = 0.98f,
                entities = entities,
                directToolIntent = ToolIntent("take_screenshot", emptyMap(), "LOW"),
                naturalResponseRecommendation = "Capturing screen.",
                rawUtterance = trimmed
            )
        }

        // 5. Flashlight / Hardware Toggles
        if (entities.settingName == "Flashlight" || lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশলাইট") || lower.contains("টর্চ")) {
            val state = entities.toggleState ?: (!lower.contains("off") && !lower.contains("বন্ধ") && !lower.contains("নিভাও") && !lower.contains("bondho"))
            return SemanticUserIntent(
                category = IntentCategory.DEVICE_SETTINGS,
                canonicalAction = "TOGGLE_FLASHLIGHT",
                confidence = 0.98f,
                entities = entities,
                directToolIntent = ToolIntent("toggle_flashlight", mapOf("state" to if (state) "on" else "off", "enable" to state.toString()), "LOW"),
                naturalResponseRecommendation = if (state) "Flashlight turned on." else "Flashlight turned off.",
                rawUtterance = trimmed
            )
        }

        // 6. Device Status / Battery
        if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("চার্জ কত") || lower.contains("device status")) {
            return SemanticUserIntent(
                category = IntentCategory.DEVICE_SETTINGS,
                canonicalAction = "CHECK_BATTERY",
                confidence = 0.98f,
                entities = entities,
                directToolIntent = ToolIntent("check_battery", emptyMap(), "LOW"),
                naturalResponseRecommendation = "Checking battery status.",
                rawUtterance = trimmed
            )
        }

        // 7. Volume Control
        if (lower.contains("volume") || lower.contains("ভলিউম") || lower.contains("আওয়াজ") || lower.contains("sound")) {
            val isUp = lower.contains("up") || lower.contains("বাড়াও") || lower.contains("baraw") || lower.contains("increase")
            val isDown = lower.contains("down") || lower.contains("কমাও") || lower.contains("komao") || lower.contains("decrease")
            val isMute = lower.contains("mute") || lower.contains("silent") || lower.contains("সাইলেন্ট") || lower.contains("বন্ধ")
            val toolName = when {
                isMute -> "mute"
                isUp -> "volume_up"
                isDown -> "volume_down"
                else -> "volume_up"
            }
            return SemanticUserIntent(
                category = IntentCategory.DEVICE_SETTINGS,
                canonicalAction = "ADJUST_VOLUME",
                confidence = 0.96f,
                entities = entities,
                directToolIntent = ToolIntent(toolName, emptyMap(), "LOW"),
                naturalResponseRecommendation = "Adjusted volume.",
                rawUtterance = trimmed
            )
        }

        // 8. Communication (Call, SMS, WhatsApp)
        if (entities.contactName != null || entities.phoneNumber != null || lower.contains("call") || lower.contains("কল") || lower.contains("sms") || lower.contains("whatsapp")) {
            val isWhatsApp = lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ")
            val isSms = lower.contains("sms") || (lower.contains("message") && !isWhatsApp) || lower.contains("মেসেজ")
            val isCall = (lower.contains("call") || lower.contains("কল") || lower.contains("ফোন")) && !isSms && !isWhatsApp

            if (isCall) {
                return SemanticUserIntent(
                    category = IntentCategory.COMMUNICATION,
                    canonicalAction = "MAKE_PHONE_CALL",
                    confidence = 0.95f,
                    entities = entities,
                    directToolIntent = ToolIntent("make_phone_call", mapOf("contact_name" to (entities.contactName ?: entities.phoneNumber ?: ""), "number" to (entities.phoneNumber ?: "")), "HIGH"),
                    rawUtterance = trimmed
                )
            }
            if (isWhatsApp && (entities.messageBody != null || lower.contains("send") || lower.contains("পাঠাও") || lower.contains("বলো"))) {
                return SemanticUserIntent(
                    category = IntentCategory.COMMUNICATION,
                    canonicalAction = "SEND_WHATSAPP_MESSAGE",
                    confidence = 0.95f,
                    entities = entities,
                    directToolIntent = ToolIntent("send_whatsapp_message", mapOf("contact_name" to (entities.contactName ?: "Contact"), "message" to (entities.messageBody ?: "")), "HIGH"),
                    rawUtterance = trimmed
                )
            }
            if (isSms && (entities.messageBody != null || lower.contains("send") || lower.contains("পাঠাও"))) {
                return SemanticUserIntent(
                    category = IntentCategory.COMMUNICATION,
                    canonicalAction = "SEND_SMS",
                    confidence = 0.95f,
                    entities = entities,
                    directToolIntent = ToolIntent("send_sms", mapOf("recipient" to (entities.contactName ?: entities.phoneNumber ?: "Contact"), "number" to (entities.phoneNumber ?: ""), "message" to (entities.messageBody ?: "")), "HIGH"),
                    rawUtterance = trimmed
                )
            }
        }

        // 9. Screen Reading ("Read screen", "What is visible", "স্ক্রিন পড়")
        if (lower.contains("read screen") || lower.contains("read my screen") || lower.contains("স্ক্রিন পড়") || lower.contains("what is on screen") || lower.contains("screen dekho")) {
            return SemanticUserIntent(
                category = IntentCategory.SCREEN_INTERACTION,
                canonicalAction = "READ_SCREEN",
                confidence = 0.98f,
                entities = entities,
                directToolIntent = ToolIntent("read_screen", emptyMap(), "LOW"),
                naturalResponseRecommendation = "Observing active screen.",
                rawUtterance = trimmed
            )
        }

        // 10. Scrolling
        if (lower.contains("scroll") || lower.contains("স্ক্রোল") || lower.contains("নিচে নামাও") || lower.contains("উপরে ওঠাও")) {
            val direction = entities.direction ?: if (lower.contains("up") || lower.contains("উপরে")) "UP" else "DOWN"
            return SemanticUserIntent(
                category = IntentCategory.SCREEN_INTERACTION,
                canonicalAction = "SCROLL_SCREEN",
                confidence = 0.98f,
                entities = entities,
                directToolIntent = ToolIntent("scroll", mapOf("direction" to direction), "LOW"),
                naturalResponseRecommendation = "Scrolling $direction.",
                rawUtterance = trimmed
            )
        }

        // 11. Multi-Step Goal Archetypes (e.g. "Open YouTube and play Tom and Jerry", "Open Chrome and search HSC result")
        if (entities.appName != null && (entities.searchQuery != null || entities.mediaTitle != null || lower.contains(" and ") || lower.contains(" then ") || lower.contains(" আর "))) {
            return SemanticUserIntent(
                category = if (entities.appName.equals("youtube", ignoreCase = true)) IntentCategory.MEDIA_CONTROL else IntentCategory.WEB_SEARCH,
                canonicalAction = "EXECUTE_APP_MULTISTEP_TASK",
                confidence = 0.95f,
                entities = entities,
                isMultiStep = true,
                requiresScreenObservation = true,
                rawUtterance = trimmed
            )
        }

        // 12. App Launch
        if (entities.appName != null) {
            return SemanticUserIntent(
                category = IntentCategory.APP_CONTROL,
                canonicalAction = "OPEN_APP",
                confidence = 0.96f,
                entities = entities,
                directToolIntent = ToolIntent("open_app", mapOf("app_name" to entities.appName), "LOW"),
                naturalResponseRecommendation = "Opening ${entities.appName}.",
                rawUtterance = trimmed
            )
        }

        // 13. Screen Tap / Click
        if (entities.targetText != null) {
            return SemanticUserIntent(
                category = IntentCategory.SCREEN_INTERACTION,
                canonicalAction = "TAP_ELEMENT",
                confidence = 0.90f,
                entities = entities,
                directToolIntent = ToolIntent("tap", mapOf("target_text" to entities.targetText), "LOW"),
                naturalResponseRecommendation = "Tapping '${entities.targetText}'.",
                rawUtterance = trimmed
            )
        }

        // 14. Default to General Goal / Knowledge Query
        val isQuestion = lower.contains("what") || lower.contains("how") || lower.contains("why") || lower.contains("who") || lower.contains("কী") || lower.contains("কেমন") || lower.contains("কেন") || lower.endsWith("?")
        return SemanticUserIntent(
            category = if (isQuestion) IntentCategory.KNOWLEDGE_QUERY else IntentCategory.GENERAL_CONVERSATION,
            canonicalAction = if (isQuestion) "ANSWER_QUESTION" else "PROCESS_GOAL",
            confidence = 0.75f,
            entities = entities,
            requiresScreenObservation = true,
            rawUtterance = trimmed
        )
    }

    private fun isEmergencyStop(lower: String): Boolean {
        return lower == "stop" || lower == "cancel" || lower == "halt" || lower == "বন্ধ করো" || lower == "থামো" || lower == "থামাও" || lower == "bondho koro" || lower == "stop it"
    }

    private fun isAffirmative(lower: String): Boolean {
        return lower == "yes" || lower == "yeah" || lower == "yep" || lower == "sure" || lower == "ok" || lower == "okay" || lower == "হ্যাঁ" || lower == "হ্যা" || lower == "ঠিক আছে" || lower == "হাঁ" || lower == "ha" || lower == "thik ache"
    }

    private fun isNegative(lower: String): Boolean {
        return lower == "no" || lower == "nope" || lower == "cancel" || lower == "না" || lower == "বাতিল" || lower == "na" || lower == "cancel koro"
    }

    private fun isAnaphoricFollowUp(lower: String): Boolean {
        val pronouns = listOf(
            "that one", "this one", "the other one", "that", "this", "there", "it",
            "oita", "aita", "oi ta", "ai ta", "ager ta", "okhane", "seta", "tarpor", "eita", "oi pasher ta",
            "ওটা", "এটা", "ওইটা", "আগেরটা", "ওখানে", "সেটা", "তারপর", "এইটা", "ওই পাশেরটা", "অন্যটা"
        )
        return pronouns.any { lower.contains(it) } || lower.startsWith("then ") || lower.startsWith("now ") || lower.startsWith("তারপর ") || lower.startsWith("এখন ")
    }

    private fun inferFollowUpAction(lower: String, entities: ExtractedEntities): String {
        return when {
            lower.contains("play") || lower.contains("চালাও") || lower.contains("chalao") -> "PLAY_ITEM"
            lower.contains("open") || lower.contains("খোল") || lower.contains("open koro") -> "OPEN_ITEM"
            lower.contains("tap") || lower.contains("click") || lower.contains("ক্লিক") || lower.contains("চাপ") -> "TAP_ITEM"
            lower.contains("scroll") || lower.contains("স্ক্রোল") -> "SCROLL_SCREEN"
            else -> "INTERACT_REFERENCED_ITEM"
        }
    }

    private fun isBackCommand(lower: String): Boolean = lower in listOf("back", "go back", "press back", "পিছনে যাও", "পেছনে যাও", "back e jao", "pichone jao")
    private fun isHomeCommand(lower: String): Boolean = lower in listOf("home", "go home", "press home", "হোমে যাও", "হোম", "home e jao")
    private fun isLockCommand(lower: String): Boolean = lower in listOf("lock", "lock screen", "screen off", "ফোন লক করো", "স্ক্রিন বন্ধ করো", "phone lock koro")
    private fun isScreenshotCommand(lower: String): Boolean = lower in listOf("screenshot", "take screenshot", "স্ক্রিনশট নাও", "স্ক্রিনশট", "screenshot nao")
}
