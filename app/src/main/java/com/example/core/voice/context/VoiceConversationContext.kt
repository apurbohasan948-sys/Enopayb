package com.example.core.voice.context

import com.example.core.model.ToolIntent

data class VoiceConversationContext(
    val previousCommand: String? = null,
    val currentApp: String? = null,
    val currentTask: String? = null,
    val pendingIntent: ToolIntent? = null,
    val recentResult: String? = null,
    val lastInteractionTimestamp: Long = 0L,
    val isAwaitingConfirmation: Boolean = false,
    val turnCount: Int = 0
) {
    companion object {
        const val CONTEXT_EXPIRATION_MS = 60_000L // 60s conversational memory window

        fun isContextActive(context: VoiceConversationContext): Boolean {
            return (System.currentTimeMillis() - context.lastInteractionTimestamp) < CONTEXT_EXPIRATION_MS
        }

        fun enrichFollowUpQuery(query: String, context: VoiceConversationContext): String {
            val lower = query.lowercase().trim()
            if (!isContextActive(context)) return query

            // Handle multi-turn continuity: "Search for Tom and Jerry" when YouTube is open
            if ((lower.startsWith("search ") || lower.startsWith("find ") || lower.startsWith("play ")) &&
                (context.currentApp?.contains("youtube", ignoreCase = true) == true ||
                 context.previousCommand?.contains("youtube", ignoreCase = true) == true)) {
                if (!lower.contains("youtube")) {
                    return "$query on YouTube"
                }
            }

            // Handle multi-turn continuity for contacts / messaging
            if ((lower.startsWith("send ") || lower.startsWith("tell him ") || lower.startsWith("saying ")) &&
                context.previousCommand?.contains("whatsapp", ignoreCase = true) == true) {
                return "$query (continuing WhatsApp conversation)"
            }

            return query
        }

        fun isAffirmative(text: String): Boolean {
            val lower = text.lowercase().trim()
            val affirmatives = listOf(
                "yes", "yeah", "yup", "sure", "proceed", "confirm", "send", "send it", "call", "call now",
                "do it", "affirmative", "ok", "okay", "হ্যাঁ", "হাঁ", "করো", "পাঠাও", "কল দাও", "ঠিক আছে"
            )
            return affirmatives.any { lower == it || lower.startsWith(it) }
        }

        fun isNegative(text: String): Boolean {
            val lower = text.lowercase().trim()
            val negatives = listOf(
                "no", "nope", "cancel", "stop", "don't", "dont send", "abort", "reject", "never mind",
                "না", "করো না", "পাঠাবে না", "বাতিল", "বন্ধ করো"
            )
            return negatives.any { lower == it || lower.startsWith(it) }
        }

        fun isStopOrCancelCommand(text: String): Boolean {
            val lower = text.lowercase().trim()
            val stopWords = listOf("stop", "cancel", "shut up", "quiet", "halt", "never mind", "থেমে যাও", "চুপ করো", "বন্ধ করো")
            return stopWords.any { lower == it || lower.startsWith(it) }
        }
    }
}
