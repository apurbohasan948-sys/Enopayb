package com.example.core.agent.conversation

import com.example.core.agent.nlu.ExtractedEntities
import com.example.core.agent.nlu.SemanticUserIntent
import com.example.core.model.ToolIntent
import com.example.core.vision.SemanticScreenModel
import com.example.core.vision.SemanticTarget
import com.example.core.vision.SemanticUIElement
import com.example.core.vision.UnifiedScreen

data class ResolvedFollowUpResult(
    val isResolved: Boolean,
    val resolvedGoal: String,
    val resolvedToolIntent: ToolIntent?,
    val targetElement: SemanticUIElement? = null,
    val confidence: Float,
    val clarifyingQuestion: String? = null,
    val explanation: String
)

/**
 * FollowUpResolver.
 * Resolves context-dependent and anaphoric follow-up queries across English, Bengali (বাংলা), and Banglish.
 * Maps references like "oita", "aita", "that one", "the first video", "okhane click koro" to active screen elements and conversation history.
 */
class FollowUpResolver {

    /**
     * Resolves follow-up intent against conversation state and active screen.
     */
    fun resolve(
        intent: SemanticUserIntent,
        conversationState: ConversationState,
        screen: SemanticScreenModel?
    ): ResolvedFollowUpResult {
        val utterance = intent.rawUtterance.lowercase().trim()
        val entities = intent.entities

        // 1. Ordinal Target Resolution (e.g. "play the first one", "click 2nd result", "prothom ta chalao")
        if (entities.ordinalIndex != null && screen != null) {
            val listItems = screen.elements.filter { elem ->
                elem.role == SemanticTarget.VIDEO_ITEM ||
                elem.role == SemanticTarget.CONTACT_ITEM ||
                elem.clickable ||
                !elem.label.isNullOrBlank()
            }

            val targetIndex = if (entities.ordinalIndex == -1) {
                listItems.lastIndex
            } else {
                entities.ordinalIndex
            }

            if (targetIndex in listItems.indices) {
                val target = listItems[targetIndex]
                val label = target.label ?: target.description ?: "Item ${targetIndex + 1}"
                val actionName = if (intent.canonicalAction == "PLAY_ITEM" || target.role == SemanticTarget.VIDEO_ITEM) "tap" else "tap"
                return ResolvedFollowUpResult(
                    isResolved = true,
                    resolvedGoal = "Interact with #$targetIndex item: $label",
                    resolvedToolIntent = ToolIntent(actionName, mapOf("target_text" to label), "LOW"),
                    targetElement = target,
                    confidence = 0.94f,
                    explanation = "Resolved ordinal reference '${entities.ordinalIndex + 1}' to on-screen element '$label'."
                )
            }
        }

        // 2. Anaphoric pronoun reference to the last referenced item / search query ("that one", "oita", "aita", "ager ta")
        val isThatOne = utterance.contains("that one") || utterance.contains("this one") ||
                utterance.contains("oita") || utterance.contains("aita") || utterance.contains("oi ta") ||
                utterance.contains("ai ta") || utterance.contains("ওটা") || utterance.contains("এটা") ||
                utterance.contains("ওইটা") || utterance.contains("এইটা")

        if (isThatOne) {
            // Check if there is an unambiguous single prominent item or previous turn item
            val lastItem = conversationState.lastReferencedItem
            val visibleElements = screen?.elements?.filter { it.clickable && !it.label.isNullOrBlank() } ?: emptyList()

            if (visibleElements.size == 1) {
                val elem = visibleElements.first()
                val label = elem.label ?: elem.description ?: "Item"
                return ResolvedFollowUpResult(
                    isResolved = true,
                    resolvedGoal = "Tap referenced item: $label",
                    resolvedToolIntent = ToolIntent("tap", mapOf("target_text" to label), "LOW"),
                    targetElement = elem,
                    confidence = 0.92f,
                    explanation = "Resolved 'that one' to the single interactive item on screen: '$label'."
                )
            } else if (lastItem != null) {
                return ResolvedFollowUpResult(
                    isResolved = true,
                    resolvedGoal = "Action on previous item: $lastItem",
                    resolvedToolIntent = ToolIntent("tap", mapOf("target_text" to lastItem), "LOW"),
                    confidence = 0.88f,
                    explanation = "Resolved 'that one' to previous turn target '$lastItem'."
                )
            } else if (visibleElements.isNotEmpty()) {
                // Ambiguous choice — Ask clarifying question
                val candidatesList = visibleElements.take(3).joinToString(", ") { it.label ?: it.description ?: "item" }
                return ResolvedFollowUpResult(
                    isResolved = false,
                    resolvedGoal = utterance,
                    resolvedToolIntent = null,
                    confidence = 0.45f,
                    clarifyingQuestion = "Which one would you like me to select? Visible options: $candidatesList",
                    explanation = "Multiple candidates found on screen for 'that one'."
                )
            }
        }

        // 3. Sequential Follow-up ("then search X", "now type Y", "tarpor X search koro")
        if (entities.searchQuery != null || entities.mediaTitle != null) {
            val query = entities.searchQuery ?: entities.mediaTitle ?: ""
            val toolIntent = if (intent.canonicalAction == "PLAY_ITEM") {
                ToolIntent("tap", mapOf("target_text" to query), "LOW")
            } else {
                ToolIntent("type_text", mapOf("text" to query), "LOW")
            }
            return ResolvedFollowUpResult(
                isResolved = true,
                resolvedGoal = "Sequential action: $query",
                resolvedToolIntent = toolIntent,
                confidence = 0.90f,
                explanation = "Resolved sequential command with query '$query' in current app context (${conversationState.currentAppLabel})."
            )
        }

        // 4. Directional follow-up ("scroll down then click that")
        if (entities.direction != null) {
            return ResolvedFollowUpResult(
                isResolved = true,
                resolvedGoal = "Scroll screen ${entities.direction}",
                resolvedToolIntent = ToolIntent("scroll", mapOf("direction" to entities.direction), "LOW"),
                confidence = 0.95f,
                explanation = "Resolved directional action '${entities.direction}'."
            )
        }

        return ResolvedFollowUpResult(
            isResolved = false,
            resolvedGoal = utterance,
            resolvedToolIntent = null,
            confidence = 0.30f,
            clarifyingQuestion = "Could you please specify which item or action you mean?",
            explanation = "Unable to resolve follow-up reference without ambiguity."
        )
    }
}
