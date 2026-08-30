package com.example.core.agent

import java.util.UUID

/**
 * UniversalTask Models for Phase 11 Task Execution Engine.
 */
enum class UniversalTaskStatus {
    PENDING,
    PLANNING,
    RUNNING,
    PAUSED,
    CONFIRMATION_REQUIRED,
    VERIFYING,
    RECOVERING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class TaskRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class UniversalActionType {
    OPEN_APP,
    CLICK,
    TYPE_TEXT,
    CLEAR_TEXT,
    SCROLL,
    SWIPE,
    BACK,
    HOME,
    LONG_PRESS,
    SELECT,
    SUBMIT,
    OPEN_MENU,
    SEARCH,
    PLAY,
    PAUSE,
    CLOSE,
    REFRESH,
    WAIT,
    READ,
    VERIFY
}

data class UniversalActionStep(
    val stepId: Int,
    val description: String,
    val actionType: UniversalActionType,
    val semanticTarget: String,
    val arguments: Map<String, String> = emptyMap(),
    val expectedOutcome: String = "Step executed successfully",
    val riskLevel: TaskRiskLevel = TaskRiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val maxRetries: Int = 3
)

data class UniversalTask(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val intent: String,
    val entities: Map<String, String> = emptyMap(),
    val requiredCapabilities: List<String> = emptyList(),
    val targetApp: String? = null,
    val plan: List<UniversalActionStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val status: UniversalTaskStatus = UniversalTaskStatus.PENDING,
    val risk: TaskRiskLevel = TaskRiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val result: String? = null,
    val failureReason: String? = null,
    val retryCount: Int = 0
)

data class TaskContext(
    val goal: String,
    val currentApp: String,
    val currentActivity: String = "",
    val currentScreenSummary: String = "",
    val completedSteps: List<UniversalActionStep> = emptyList(),
    val remainingSteps: List<UniversalActionStep> = emptyList(),
    val lastAction: UniversalActionType? = null,
    val lastTarget: String? = null,
    val lastResult: String? = null,
    val knownTargets: Map<String, String> = emptyMap(),
    val actionHistory: List<String> = emptyList()
)

sealed class WaitForCondition {
    data class ScreenChange(val timeoutMs: Long = 2000L) : WaitForCondition()
    data class ElementAppears(val semanticTarget: String, val timeoutMs: Long = 3000L) : WaitForCondition()
    data class KeyboardAppears(val timeoutMs: Long = 2000L) : WaitForCondition()
    data class TextAppears(val text: String, val timeoutMs: Long = 3000L) : WaitForCondition()
    data class AppOpens(val packageName: String, val timeoutMs: Long = 4000L) : WaitForCondition()
    data class LoadingFinishes(val timeoutMs: Long = 5000L) : WaitForCondition()
}

enum class TargetConfidenceLevel {
    HIGH,    // 0.90 - 1.00
    MEDIUM,  // 0.75 - 0.89
    LOW,     // 0.50 - 0.74
    UNKNOWN  // < 0.50
}

data class TargetResolutionResult(
    val found: Boolean,
    val semanticRole: String,
    val confidence: Float,
    val confidenceLevel: TargetConfidenceLevel = when {
        confidence >= 0.90f -> TargetConfidenceLevel.HIGH
        confidence >= 0.75f -> TargetConfidenceLevel.MEDIUM
        confidence >= 0.50f -> TargetConfidenceLevel.LOW
        else -> TargetConfidenceLevel.UNKNOWN
    },
    val source: String, // "ACCESSIBILITY", "RESOURCE_ID", "CONTENT_DESC", "TEXT", "OCR", "ICON_SEMANTICS", "LOCAL_VISION", "GEMINI_VISION"
    val bounds: android.graphics.Rect = android.graphics.Rect(),
    val centerCoordinates: Pair<Float, Float> = Pair(bounds.exactCenterX(), bounds.exactCenterY()),
    val isEditable: Boolean = false,
    val isClickable: Boolean = true,
    val originalText: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val failureReason: String? = null
)
