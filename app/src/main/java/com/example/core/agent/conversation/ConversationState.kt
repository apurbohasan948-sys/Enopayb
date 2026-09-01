package com.example.core.agent.conversation

import com.example.core.model.ToolIntent
import com.example.core.vision.SemanticUIElement

/**
 * Represents a single turn in a multi-turn conversation.
 */
data class ConversationTurn(
    val turnId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val userInput: String,
    val normalizedGoal: String,
    val detectedIntent: String,
    val extractedEntities: Map<String, String>,
    val targetApp: String? = null,
    val toolIntent: ToolIntent? = null,
    val actionSuccess: Boolean = false,
    val isVerified: Boolean = false,
    val agentResponse: String = "",
    val referencedElement: SemanticUIElement? = null
)

/**
 * Global persistent container that maintains complete context across multi-turn interactions.
 */
data class ConversationState(
    val sessionId: String = "session_${System.currentTimeMillis()}",
    val turnCount: Int = 0,
    val currentAppPackage: String = "com.example",
    val currentAppLabel: String = "JARVIS",
    val currentScreenSummary: String = "Standby",
    val activeTaskGoal: String? = null,
    val isTaskInProgress: Boolean = false,
    val lastActionName: String? = null,
    val lastActionSuccess: Boolean = true,
    val lastActionVerified: Boolean = true,
    val lastReferencedItem: String? = null,
    val recentEntities: Map<String, String> = emptyMap(),
    val visibleItemsOnScreen: List<String> = emptyList(),
    val awaitingFollowUp: Boolean = false,
    val pendingConfirmationIntent: ToolIntent? = null,
    val turnHistory: List<ConversationTurn> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun getLastTurn(): ConversationTurn? = turnHistory.lastOrNull()

    fun withNewTurn(turn: ConversationTurn): ConversationState {
        val updatedEntities = recentEntities.toMutableMap()
        updatedEntities.putAll(turn.extractedEntities)
        if (turn.targetApp != null) {
            updatedEntities["current_app"] = turn.targetApp
        }

        return copy(
            turnCount = turnCount + 1,
            currentAppPackage = turn.targetApp ?: currentAppPackage,
            lastActionName = turn.toolIntent?.toolName ?: lastActionName,
            lastActionSuccess = turn.actionSuccess,
            lastActionVerified = turn.isVerified,
            lastReferencedItem = turn.extractedEntities["target_text"] ?: turn.extractedEntities["query"] ?: lastReferencedItem,
            recentEntities = updatedEntities,
            turnHistory = (turnHistory + turn).takeLast(20),
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun getSummary(): String {
        return "App: $currentAppLabel ($currentAppPackage) | Screen: $currentScreenSummary | Active Goal: ${activeTaskGoal ?: "(None)"} | Last Action: ${lastActionName ?: "(None)"} [Verified: $lastActionVerified] | Turns: $turnCount"
    }
}
