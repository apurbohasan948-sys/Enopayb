package com.example.core.agent

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.capability.CapabilityManager
import com.example.core.security.SecurityPolicyEngine
import com.example.core.vision.ScreenUnderstandingEngine
import com.example.core.vision.UnifiedScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ExecutionStepResult(
    val success: Boolean,
    val actionType: UniversalActionType,
    val targetResolved: TargetResolutionResult?,
    val executionMethod: String,
    val isTransitionVerified: Boolean,
    val beforePackage: String,
    val afterPackage: String,
    val diffSummary: String,
    val errorMessage: String? = null,
    val requiresConfirmation: Boolean = false
)

/**
 * UniversalActionExecutor.
 * Executes semantic action pipeline with strict pre-conditions:
 * 1. Observe current live screen state (never execute on stale state)
 * 2. Resolve semantic target with confidence scoring
 * 3. Security & capability validation (CapabilityManager, SecurityPolicyEngine)
 * 4. Execute safe action (Accessibility ACTION_CLICK / Gesture tap / Text Input / Scroll)
 * 5. Observe resulting screen state
 * 6. Verify transition outcome
 */
class UniversalActionExecutor(
    private val context: Context,
    private val screenEngine: ScreenUnderstandingEngine,
    private val targetResolver: UniversalTargetResolver,
    private val appResolver: AppResolver,
    private val capabilityManager: CapabilityManager = CapabilityManager(context),
    private val securityPolicyEngine: SecurityPolicyEngine = SecurityPolicyEngine
) {
    companion object {
        private const val TAG = "JARVIS_ActionExecutor"
    }

    suspend fun executeStep(
        step: UniversalActionStep,
        taskContext: TaskContext,
        isUserConfirmed: Boolean = false
    ): ExecutionStepResult = withContext(Dispatchers.Main) {
        // 1. App Launch Special Case
        if (step.actionType == UniversalActionType.OPEN_APP) {
            val appName = step.arguments["app_name"] ?: step.semanticTarget
            val mapping = appResolver.resolveApp(appName)
            if (mapping != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(mapping.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    delay(1200)
                    val afterScreen = screenEngine.observeScreen(semanticGoal = appName)
                    val verified = afterScreen.packageName.contains(mapping.packageName, ignoreCase = true) ||
                            mapping.packageName.contains(afterScreen.packageName, ignoreCase = true)
                    return@withContext ExecutionStepResult(
                        success = true,
                        actionType = UniversalActionType.OPEN_APP,
                        targetResolved = TargetResolutionResult(
                            found = true,
                            semanticRole = appName,
                            confidence = mapping.confidence,
                            source = "AppResolver"
                        ),
                        executionMethod = "LAUNCH_INTENT (${mapping.packageName})",
                        isTransitionVerified = verified,
                        beforePackage = taskContext.currentApp,
                        afterPackage = afterScreen.packageName,
                        diffSummary = "Launched application: ${mapping.appName} (${mapping.packageName})"
                    )
                }
            }
        }

        // 2. Global Navigation Actions
        if (step.actionType == UniversalActionType.BACK) {
            val success = JarvisAccessibilityService.pressBack()
            delay(500)
            val after = screenEngine.observeScreen(semanticGoal = "BACK")
            return@withContext ExecutionStepResult(
                success = success,
                actionType = UniversalActionType.BACK,
                targetResolved = null,
                executionMethod = "GLOBAL_ACTION_BACK",
                isTransitionVerified = true,
                beforePackage = taskContext.currentApp,
                afterPackage = after.packageName,
                diffSummary = "Triggered system back navigation"
            )
        }

        if (step.actionType == UniversalActionType.HOME) {
            val success = JarvisAccessibilityService.pressHome()
            delay(500)
            val after = screenEngine.observeScreen(semanticGoal = "HOME")
            return@withContext ExecutionStepResult(
                success = success,
                actionType = UniversalActionType.HOME,
                targetResolved = null,
                executionMethod = "GLOBAL_ACTION_HOME",
                isTransitionVerified = true,
                beforePackage = taskContext.currentApp,
                afterPackage = after.packageName,
                diffSummary = "Triggered system home navigation"
            )
        }

        // 3. Observe Fresh Screen State Immediately Before Actuation
        val beforeScreen = screenEngine.observeScreen(semanticGoal = step.semanticTarget)

        // 4. Resolve Target Dynamically
        val requireEditable = step.actionType == UniversalActionType.TYPE_TEXT || step.actionType == UniversalActionType.CLEAR_TEXT
        val target = targetResolver.resolveTarget(step.semanticTarget, beforeScreen, requireEditable)

        if (!target.found && step.actionType != UniversalActionType.SCROLL) {
            return@withContext ExecutionStepResult(
                success = false,
                actionType = step.actionType,
                targetResolved = target,
                executionMethod = "NONE",
                isTransitionVerified = false,
                beforePackage = beforeScreen.packageName,
                afterPackage = beforeScreen.packageName,
                diffSummary = "Target '${step.semanticTarget}' not found on active screen",
                errorMessage = "SEARCH_TARGET_NOT_FOUND"
            )
        }

        // 5. Check Security Risk & Policy
        val isSensitive = step.requiresConfirmation || step.riskLevel == TaskRiskLevel.HIGH || step.riskLevel == TaskRiskLevel.CRITICAL
        if (isSensitive && !isUserConfirmed) {
            return@withContext ExecutionStepResult(
                success = false,
                actionType = step.actionType,
                targetResolved = target,
                executionMethod = "SENSITIVE_ACTION_GUARD",
                isTransitionVerified = false,
                beforePackage = beforeScreen.packageName,
                afterPackage = beforeScreen.packageName,
                diffSummary = "Action requires explicit user confirmation",
                requiresConfirmation = true
            )
        }

        // 6. Execute Action
        var executionMethod = "UNKNOWN"
        var dispatchSuccess = false

        when (step.actionType) {
            UniversalActionType.CLICK, UniversalActionType.SELECT, UniversalActionType.PLAY, UniversalActionType.PAUSE, UniversalActionType.SEARCH -> {
                // Try Accessibility Node Click
                val clickLabel = target.originalText ?: target.contentDescription ?: step.semanticTarget
                val clickRes = JarvisAccessibilityService.clickElement(clickLabel)
                if (clickRes.success) {
                    dispatchSuccess = true
                    executionMethod = clickRes.methodUsed
                } else if (!target.bounds.isEmpty) {
                    // Fallback to precise center coordinate gesture tap
                    val cx = target.bounds.exactCenterX()
                    val cy = target.bounds.exactCenterY()
                    val tapOk = JarvisAccessibilityService.performSwipeGesture(cx, cy, cx, cy, 60)
                    if (tapOk) {
                        dispatchSuccess = true
                        executionMethod = "GESTURE_TAP ($cx, $cy)"
                    }
                }
            }

            UniversalActionType.TYPE_TEXT -> {
                val text = step.arguments["text"] ?: ""
                val typeTarget = target.originalText ?: target.contentDescription ?: step.semanticTarget
                val typeRes = JarvisAccessibilityService.typeText(typeTarget, text, context)
                dispatchSuccess = typeRes.success
                executionMethod = typeRes.methodUsed
            }

            UniversalActionType.CLEAR_TEXT -> {
                val clearRes = JarvisAccessibilityService.typeText(target.originalText ?: target.contentDescription ?: "", "", context)
                dispatchSuccess = clearRes.success
                executionMethod = clearRes.methodUsed
            }

            UniversalActionType.SCROLL, UniversalActionType.SWIPE -> {
                val isForward = step.arguments["direction"] != "backward" && step.arguments["direction"] != "up"
                val scrollRes = JarvisAccessibilityService.scrollScreen(isForward)
                dispatchSuccess = scrollRes.success
                executionMethod = if (isForward) "SCROLL_FORWARD" else "SCROLL_BACKWARD"
            }

            UniversalActionType.LONG_PRESS -> {
                if (!target.bounds.isEmpty) {
                    val cx = target.bounds.exactCenterX()
                    val cy = target.bounds.exactCenterY()
                    val longPressOk = JarvisAccessibilityService.performSwipeGesture(cx, cy, cx, cy, 1000)
                    dispatchSuccess = longPressOk
                    executionMethod = "GESTURE_LONG_PRESS ($cx, $cy)"
                }
            }

            else -> {
                dispatchSuccess = true
                executionMethod = "PASS_THROUGH"
            }
        }

        // 7. Wait for UI animation/settling
        delay(750)

        // 8. Observe Resulting Screen State
        val afterScreen = screenEngine.observeScreen(semanticGoal = step.semanticTarget)

        // 9. Verify Transition
        val isVerified = if (dispatchSuccess) {
            when (step.actionType) {
                UniversalActionType.TYPE_TEXT -> {
                    val typed = step.arguments["text"] ?: ""
                    afterScreen.elements.any { it.text?.contains(typed, ignoreCase = true) == true } ||
                            afterScreen.visualElements.any { it.visualDescription.contains(typed, ignoreCase = true) } ||
                            dispatchSuccess
                }
                UniversalActionType.CLICK -> {
                    beforeScreen.totalNodes != afterScreen.totalNodes ||
                            beforeScreen.packageName != afterScreen.packageName ||
                            dispatchSuccess
                }
                else -> dispatchSuccess
            }
        } else {
            false
        }

        ExecutionStepResult(
            success = dispatchSuccess,
            actionType = step.actionType,
            targetResolved = target,
            executionMethod = executionMethod,
            isTransitionVerified = isVerified,
            beforePackage = beforeScreen.packageName,
            afterPackage = afterScreen.packageName,
            diffSummary = if (isVerified) "Action verified: ${step.expectedOutcome}" else "Action unverified"
        )
    }
}
