package com.example.core.agent.debug

import com.example.core.agent.TelemetryState
import com.example.core.agent.conversation.ConversationState
import org.json.JSONArray
import org.json.JSONObject

/**
 * AgentDebugTraceExporter.
 * Formats and exports the complete agent execution pipeline trace as structured JSON and readable text.
 */
object AgentDebugTraceExporter {

    fun exportAsJson(
        telemetry: TelemetryState,
        conversationState: ConversationState,
        executionLogs: List<String>
    ): String {
        val root = JSONObject()
        root.put("sessionId", conversationState.sessionId)
        root.put("timestamp", System.currentTimeMillis())
        root.put("turnCount", conversationState.turnCount)

        // Telemetry Block
        val telObj = JSONObject().apply {
            put("currentGoal", telemetry.currentGoal)
            put("currentApp", telemetry.currentApp)
            put("currentScreen", telemetry.currentScreen)
            put("accessibilityElementsCount", telemetry.accessibilityElementsCount)
            put("ocrElementsCount", telemetry.ocrElementsCount)
            put("visionElementsCount", telemetry.visionElementsCount)
            put("targetSelected", telemetry.targetSelected)
            put("targetConfidence", telemetry.targetConfidence)
            put("action", telemetry.action)
            put("actionResult", telemetry.actionResult)
            put("verificationResult", telemetry.verificationResult)
            put("nextAction", telemetry.nextAction)
            put("learningStatus", telemetry.learningStatus)
            put("memoryContextSummary", telemetry.memoryContextSummary)
        }
        root.put("telemetry", telObj)

        // Conversation Context
        val convObj = JSONObject().apply {
            put("activeTaskGoal", conversationState.activeTaskGoal)
            put("currentAppPackage", conversationState.currentAppPackage)
            put("currentAppLabel", conversationState.currentAppLabel)
            put("lastActionName", conversationState.lastActionName)
            put("lastActionSuccess", conversationState.lastActionSuccess)
            put("lastActionVerified", conversationState.lastActionVerified)
            put("lastReferencedItem", conversationState.lastReferencedItem)
            put("awaitingFollowUp", conversationState.awaitingFollowUp)

            val entitiesObj = JSONObject()
            conversationState.recentEntities.forEach { (k, v) -> entitiesObj.put(k, v) }
            put("recentEntities", entitiesObj)

            val turnsArray = JSONArray()
            conversationState.turnHistory.forEach { turn ->
                turnsArray.put(
                    JSONObject().apply {
                        put("turnId", turn.turnId)
                        put("userInput", turn.userInput)
                        put("detectedIntent", turn.detectedIntent)
                        put("targetApp", turn.targetApp)
                        put("tool", turn.toolIntent?.toolName)
                        put("actionSuccess", turn.actionSuccess)
                        put("isVerified", turn.isVerified)
                        put("agentResponse", turn.agentResponse)
                    }
                )
            }
            put("turnHistory", turnsArray)
        }
        root.put("conversationContext", convObj)

        // Execution Logs Array
        val logsArray = JSONArray()
        executionLogs.forEach { logsArray.put(it) }
        root.put("executionLogs", logsArray)

        return root.toString(2)
    }

    fun exportAsHumanReadable(
        telemetry: TelemetryState,
        conversationState: ConversationState,
        executionLogs: List<String>
    ): String {
        return buildString {
            appendLine("=== JARVIS AGENT CORE PIPELINE TRACE ===")
            appendLine("Session: ${conversationState.sessionId} | Turn: ${conversationState.turnCount}")
            appendLine("Active Goal: ${telemetry.currentGoal}")
            appendLine("Current App: ${telemetry.currentApp} | Screen: ${telemetry.currentScreen}")
            appendLine("Visual Grounding: Target=${telemetry.targetSelected} (Confidence=${(telemetry.targetConfidence * 100).toInt()}%)")
            appendLine("Elements: Acc=${telemetry.accessibilityElementsCount}, OCR=${telemetry.ocrElementsCount}, Vision=${telemetry.visionElementsCount}")
            appendLine("Action: ${telemetry.action} -> Result: ${telemetry.actionResult}")
            appendLine("Verification: ${telemetry.verificationResult}")
            appendLine("Recent Entities: ${conversationState.recentEntities}")
            appendLine()
            appendLine("--- RECENT EXECUTION LOGS ---")
            executionLogs.takeLast(15).forEach { appendLine(it) }
            appendLine("=========================================")
        }
    }
}
