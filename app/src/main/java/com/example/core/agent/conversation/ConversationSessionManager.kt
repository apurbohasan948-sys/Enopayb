package com.example.core.agent.conversation

import com.example.core.agent.nlu.SemanticUserIntent
import com.example.core.model.ToolIntent
import com.example.core.vision.SemanticScreenModel
import com.example.core.vision.SemanticUIElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ConversationSessionManager.
 * Manages continuous multi-turn dialogue, context preservation, follow-up windows,
 * and tracks the evolving ConversationState.
 */
class ConversationSessionManager(
    val followUpResolver: FollowUpResolver = FollowUpResolver()
) {
    private val _conversationState = MutableStateFlow(ConversationState())
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    fun updateScreenState(appPackage: String, appLabel: String, screenSummary: String, visibleItems: List<String>) {
        _conversationState.value = _conversationState.value.copy(
            currentAppPackage = appPackage,
            currentAppLabel = appLabel,
            currentScreenSummary = screenSummary,
            visibleItemsOnScreen = visibleItems,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun setActiveTask(goal: String?) {
        _conversationState.value = _conversationState.value.copy(
            activeTaskGoal = goal,
            isTaskInProgress = goal != null,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun setAwaitingFollowUp(awaiting: Boolean) {
        _conversationState.value = _conversationState.value.copy(
            awaitingFollowUp = awaiting,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun setPendingConfirmation(intent: ToolIntent?) {
        _conversationState.value = _conversationState.value.copy(
            pendingConfirmationIntent = intent,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun recordTurn(
        userInput: String,
        intent: SemanticUserIntent,
        toolIntent: ToolIntent?,
        actionSuccess: Boolean,
        isVerified: Boolean,
        agentResponse: String,
        targetApp: String? = null,
        referencedElement: SemanticUIElement? = null
    ) {
        val current = _conversationState.value
        val turn = ConversationTurn(
            turnId = current.turnCount + 1,
            userInput = userInput,
            normalizedGoal = intent.canonicalAction,
            detectedIntent = intent.category.name,
            extractedEntities = intent.entities.rawMap,
            targetApp = targetApp ?: current.currentAppPackage,
            toolIntent = toolIntent,
            actionSuccess = actionSuccess,
            isVerified = isVerified,
            agentResponse = agentResponse,
            referencedElement = referencedElement
        )

        _conversationState.value = current.withNewTurn(turn)
    }

    fun resolveFollowUp(
        intent: SemanticUserIntent,
        screen: SemanticScreenModel?
    ): ResolvedFollowUpResult {
        return followUpResolver.resolve(intent, _conversationState.value, screen)
    }

    fun resetSession() {
        _conversationState.value = ConversationState()
    }
}
