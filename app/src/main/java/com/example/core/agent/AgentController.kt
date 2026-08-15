package com.example.core.agent

import android.content.Context
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.model.ToolIntent
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolRouter
import com.example.core.vision.ScreenUnderstandingEngine
import com.example.core.vision.SemanticTarget
import com.example.core.vision.UnifiedScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class AgentState {
    IDLE,
    LISTENING,
    UNDERSTANDING,
    PLANNING,
    OBSERVING,
    ACTING,
    WAITING,
    VERIFYING,
    LEARNING,
    COMPLETED,
    FAILED,
    WAITING_FOR_USER,
    CONFIRMATION_REQUIRED,
    CANCELLED
}

data class PlanStep(
    val stepNumber: Int,
    val description: String,
    val toolIntent: ToolIntent,
    val expectedOutcome: String
)

data class TaskPlan(
    val goal: String,
    val steps: List<PlanStep>
)

data class StepExecutionRecord(
    val step: PlanStep,
    val result: ToolExecutionResult,
    val beforeScreenSummary: String,
    val afterScreenSummary: String,
    val isVerified: Boolean
)

data class TaskExecutionSummary(
    val goal: String,
    val success: Boolean,
    val completedSteps: Int,
    val totalSteps: Int,
    val stepRecords: List<StepExecutionRecord>,
    val finalOutput: String
)

class AgentController(
    private val context: Context,
    private val toolRouter: ToolRouter,
    val screenEngine: ScreenUnderstandingEngine? = null
) {
    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _currentTaskDescription = MutableStateFlow("")
    val currentTaskDescription: StateFlow<String> = _currentTaskDescription.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _totalStepsCount = MutableStateFlow(0)
    val totalStepsCount: StateFlow<Int> = _totalStepsCount.asStateFlow()

    private val _liveStatusMessage = MutableStateFlow("JARVIS Standby")
    val liveStatusMessage: StateFlow<String> = _liveStatusMessage.asStateFlow()

    private var isTaskCancelled = false

    fun cancelActiveTask() {
        isTaskCancelled = true
        _agentState.value = AgentState.CANCELLED
        _liveStatusMessage.value = "Task cancelled by user."
    }

    /**
     * Executes a planned multi-step sequence with multimodal screen observation and verification before & after each step.
     */
    suspend fun executeTaskPlan(
        plan: TaskPlan,
        onStepUpdate: (String) -> Unit = {}
    ): TaskExecutionSummary = withContext(Dispatchers.Main) {
        isTaskCancelled = false
        _agentState.value = AgentState.PLANNING
        _currentTaskDescription.value = plan.goal
        _totalStepsCount.value = plan.steps.size
        _currentStepIndex.value = 0

        val stepRecords = mutableListOf<StepExecutionRecord>()
        var allSuccess = true
        var finalMessage = ""

        for ((index, step) in plan.steps.withIndex()) {
            if (isTaskCancelled) {
                _agentState.value = AgentState.CANCELLED
                return@withContext TaskExecutionSummary(
                    goal = plan.goal,
                    success = false,
                    completedSteps = index,
                    totalSteps = plan.steps.size,
                    stepRecords = stepRecords,
                    finalOutput = "Task execution was cancelled."
                )
            }

            _currentStepIndex.value = index + 1
            _agentState.value = AgentState.OBSERVING
            val status = "Step ${index + 1}/${plan.steps.size}: ${step.description}"
            _liveStatusMessage.value = status
            onStepUpdate(status)

            // 1. Observe Screen Before Action (Multimodal Screen Observation)
            val semanticGoal = extractGoalFromStep(step)
            val beforeScreen = screenEngine?.observeScreen(semanticGoal = semanticGoal)
            val beforeSummary = beforeScreen?.getSummary() ?: JarvisAccessibilityService.observeScreen()?.getSummary() ?: "Screen observing..."

            // 2. Act
            _agentState.value = AgentState.ACTING
            val result = toolRouter.executeTool(step.toolIntent)

            // 3. Wait for UI animation & transition
            _agentState.value = AgentState.WAITING
            delay(850)

            // 4. Observe Screen After Action
            _agentState.value = AgentState.OBSERVING
            val afterScreen = screenEngine?.observeScreen(semanticGoal = semanticGoal)
            val afterSummary = afterScreen?.getSummary() ?: JarvisAccessibilityService.observeScreen()?.getSummary() ?: "Action observed"

            // 5. Verify
            _agentState.value = AgentState.VERIFYING
            val verified = verifyStepOutcome(step, beforeScreen, afterScreen, result)

            val record = StepExecutionRecord(
                step = step,
                result = result,
                beforeScreenSummary = beforeSummary,
                afterScreenSummary = afterSummary,
                isVerified = verified
            )
            stepRecords.add(record)

            if (!result.success && !verified) {
                // If a step fails, attempt one intelligent multimodal recovery
                val recoveryResult = attemptStepRecovery(step, semanticGoal)
                if (!recoveryResult) {
                    allSuccess = false
                    finalMessage = "Step failed at: ${step.description} (${result.errorMessage ?: result.output})"
                    break
                }
            }
        }

        if (allSuccess && stepRecords.isNotEmpty()) {
            _agentState.value = AgentState.COMPLETED
            finalMessage = "Task successfully completed: ${plan.goal}"
            _liveStatusMessage.value = finalMessage
        } else if (!allSuccess) {
            _agentState.value = AgentState.FAILED
            _liveStatusMessage.value = finalMessage
        }

        TaskExecutionSummary(
            goal = plan.goal,
            success = allSuccess,
            completedSteps = stepRecords.count { it.result.success || it.isVerified },
            totalSteps = plan.steps.size,
            stepRecords = stepRecords,
            finalOutput = finalMessage
        )
    }

    private fun extractGoalFromStep(step: PlanStep): String {
        val tool = step.toolIntent.toolName.lowercase()
        val target = step.toolIntent.arguments["target_text"] ?: step.toolIntent.arguments["query"] ?: step.toolIntent.arguments["target"] ?: ""
        return when {
            tool == "open_app" -> step.toolIntent.arguments["app_name"] ?: "App"
            target.isNotBlank() -> target
            tool.contains("search") -> "SEARCH"
            tool.contains("play") -> "PLAY"
            else -> step.description
        }
    }

    private fun verifyStepOutcome(
        step: PlanStep,
        before: UnifiedScreen?,
        after: UnifiedScreen?,
        result: ToolExecutionResult
    ): Boolean {
        if (!result.success && !result.verified) return false
        if (before == null || after == null) return result.success

        return when (step.toolIntent.toolName.lowercase()) {
            "open_app" -> {
                val appName = step.toolIntent.arguments["app_name"].orEmpty().lowercase()
                after.packageName.lowercase().contains(appName) ||
                        after.elements.any { it.text?.contains(appName, ignoreCase = true) == true } ||
                        after.packageName != before.packageName
            }
            "tap", "find_and_tap", "click", "click_element" -> {
                after.timestamp != before.timestamp || result.success
            }
            "type_text", "type" -> {
                val typed = step.toolIntent.arguments["text"].orEmpty()
                after.elements.any { it.text?.contains(typed, ignoreCase = true) == true } || result.success
            }
            "press_back", "press_home" -> {
                after.packageName != before.packageName || after.totalNodes != before.totalNodes
            }
            "scroll" -> {
                after.elements.map { it.text } != before.elements.map { it.text } || result.success
            }
            else -> result.success
        }
    }

    private suspend fun attemptStepRecovery(step: PlanStep, semanticGoal: String): Boolean {
        delay(500)
        // If it was a tap action, attempt multimodal intent tap via ScreenUnderstandingEngine
        if (screenEngine != null && (step.toolIntent.toolName == "tap" || step.toolIntent.toolName == "click")) {
            val tapDetails = screenEngine.tapElementByIntent(semanticGoal)
            if (tapDetails.success) return true
        }
        val retryResult = toolRouter.executeTool(step.toolIntent)
        return retryResult.success
    }

    companion object {
        /**
         * Decomposes complex user goals into an ordered multimodal task plan.
         */
        fun planTaskForQuery(query: String): TaskPlan? {
            val lower = query.lowercase().trim()

            // 1. "Play Tom and Jerry" / "YouTube-এ Tom and Jerry চালাও"
            if (lower.contains("tom and jerry") || (lower.contains("youtube") && (lower.contains("play") || lower.contains("চালাও") || lower.contains("চালু")))) {
                val searchQuery = if (lower.contains("tom and jerry")) "Tom and Jerry" else query.substringAfter("play ").substringAfter("চালাও ")
                return TaskPlan(
                    goal = "Play $searchQuery on YouTube",
                    steps = listOf(
                        PlanStep(
                            stepNumber = 1,
                            description = "Open YouTube application",
                            toolIntent = ToolIntent("open_app", mapOf("app_name" to "YouTube"), "LOW", "Launch YouTube"),
                            expectedOutcome = "YouTube foreground"
                        ),
                        PlanStep(
                            stepNumber = 2,
                            description = "Locate and click Search 🔍 button",
                            toolIntent = ToolIntent("tap", mapOf("target_text" to "SEARCH"), "LOW", "Tap Search icon"),
                            expectedOutcome = "Search bar opened"
                        ),
                        PlanStep(
                            stepNumber = 3,
                            description = "Type \"$searchQuery\" in search field",
                            toolIntent = ToolIntent("type_text", mapOf("text" to searchQuery, "target" to "Search"), "LOW", "Input query"),
                            expectedOutcome = "Search text populated"
                        ),
                        PlanStep(
                            stepNumber = 4,
                            description = "Tap search suggestion or submit",
                            toolIntent = ToolIntent("tap", mapOf("target_text" to searchQuery), "LOW", "Execute search"),
                            expectedOutcome = "Search results displayed"
                        ),
                        PlanStep(
                            stepNumber = 5,
                            description = "Play first relevant video result",
                            toolIntent = ToolIntent("tap", mapOf("target_text" to "VIDEO_ITEM"), "LOW", "Start video playback"),
                            expectedOutcome = "Video player active"
                        )
                    )
                )
            }

            // 2. Generic Search and Play Video
            if (lower.startsWith("play ") || lower.contains(" search and play ")) {
                val target = query.substringAfter("play ").trim()
                return TaskPlan(
                    goal = "Play $target",
                    steps = listOf(
                        PlanStep(1, "Open YouTube", ToolIntent("open_app", mapOf("app_name" to "YouTube"), "LOW"), "YouTube open"),
                        PlanStep(2, "Click Search 🔍", ToolIntent("tap", mapOf("target_text" to "SEARCH"), "LOW"), "Search bar open"),
                        PlanStep(3, "Type $target", ToolIntent("type_text", mapOf("text" to target), "LOW"), "Text entered"),
                        PlanStep(4, "Select video result", ToolIntent("tap", mapOf("target_text" to "VIDEO_ITEM"), "LOW"), "Playback started")
                    )
                )
            }

            return null
        }
    }
}
