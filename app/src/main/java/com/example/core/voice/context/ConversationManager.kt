package com.example.core.voice.context

import com.example.core.model.ToolIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 12 ConversationManager.
 * Manages conversational context, turn history, and intelligent multi-turn continuity
 * (e.g. understanding "Search Tom and Jerry" when YouTube was previously opened).
 * Strictly stores structured metadata only — never raw microphone recordings.
 */
class ConversationManager {

    data class ConversationState(
        val lastApplication: String? = null,
        val currentTask: String? = null,
        val recentUserIntent: String? = null,
        val lastVerifiedResult: String? = null,
        val pendingIntent: ToolIntent? = null,
        val pendingClarification: String? = null,
        val lastInteractionTimestamp: Long = 0L,
        val turnCount: Int = 0,
        val isAwaitingFollowUp: Boolean = false
    )

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    companion object {
        const val CONTEXT_EXPIRATION_MS = 60_000L // 60s conversational memory window
        const val FOLLOW_UP_WINDOW_MS = 6_000L // 6s active follow-up listening window
    }

    fun isContextActive(): Boolean {
        val lastTime = _state.value.lastInteractionTimestamp
        return (System.currentTimeMillis() - lastTime) < CONTEXT_EXPIRATION_MS
    }

    fun recordTurn(
        utterance: String,
        targetApp: String? = null,
        taskName: String? = null,
        resultSummary: String? = null
    ) {
        val current = _state.value
        _state.value = current.copy(
            lastApplication = targetApp ?: current.lastApplication,
            currentTask = taskName ?: current.currentTask,
            recentUserIntent = utterance,
            lastVerifiedResult = resultSummary ?: current.lastVerifiedResult,
            lastInteractionTimestamp = System.currentTimeMillis(),
            turnCount = current.turnCount + 1,
            isAwaitingFollowUp = false
        )
    }

    fun setPendingConfirmation(intent: ToolIntent?, clarificationPrompt: String? = null) {
        _state.value = _state.value.copy(
            pendingIntent = intent,
            pendingClarification = clarificationPrompt,
            lastInteractionTimestamp = System.currentTimeMillis()
        )
    }

    fun clearPendingConfirmation() {
        _state.value = _state.value.copy(
            pendingIntent = null,
            pendingClarification = null
        )
    }

    fun setAwaitingFollowUp(awaiting: Boolean) {
        _state.value = _state.value.copy(
            isAwaitingFollowUp = awaiting,
            lastInteractionTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Contextual query enrichment: Resolves pronoun / contextual follow-ups.
     * E.g.: "Search Tom and Jerry" when YouTube is active becomes "Search Tom and Jerry on YouTube".
     */
    fun enrichFollowUpUtterance(rawUtterance: String): String {
        val trimmed = rawUtterance.trim()
        val lower = trimmed.lowercase()

        if (!isContextActive()) return trimmed

        val current = _state.value
        val activeApp = current.lastApplication

        // If user says "Search <query>" or "Play <query>" and active app is YouTube
        if ((lower.startsWith("search ") || lower.startsWith("play ") || lower.startsWith("watch ") ||
                    lower.startsWith("search koro ") || lower.startsWith("সার্চ করো ")) &&
            (activeApp?.contains("youtube", ignoreCase = true) == true ||
                    current.recentUserIntent?.contains("youtube", ignoreCase = true) == true)
        ) {
            if (!lower.contains("youtube") && !lower.contains("ইউটিউব")) {
                return "$trimmed on YouTube"
            }
        }

        // If user says "Search for <query>" and active app is Chrome
        if ((lower.startsWith("search ") || lower.startsWith("find ") || lower.startsWith("খুঁজো ")) &&
            (activeApp?.contains("chrome", ignoreCase = true) == true ||
                    current.recentUserIntent?.contains("chrome", ignoreCase = true) == true)
        ) {
            if (!lower.contains("chrome") && !lower.contains("google")) {
                return "$trimmed on Chrome"
            }
        }

        return trimmed
    }

    fun resetContext() {
        _state.value = ConversationState()
    }
}
