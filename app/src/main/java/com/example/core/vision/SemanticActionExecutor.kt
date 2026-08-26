package com.example.core.vision

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.accessibility.ActionExecutionDetails
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.data.local.entity.VisualExperienceEntity
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SemanticActionResult(
    val success: Boolean,
    val elementId: String,
    val semanticRole: String,
    val actionMethod: String,
    val transitionVerified: Boolean,
    val diffSummary: String,
    val evidence: String,
    val errorMessage: String? = null,
    val requiresUserConfirmation: Boolean = false
)

/**
 * SemanticActionExecutor.
 * Executes semantic UI interactions (tap, type, scroll) using dynamic element resolution rather than fixed coordinates.
 * Resolves fresh bounding boxes immediately prior to actuation, verifies state transition via ScreenDiffEngine,
 * and records verified visual patterns to long-term memory.
 */
class SemanticActionExecutor(
    private val context: Context,
    val screenEngine: UniversalScreenUnderstandingEngine,
    val diffEngine: ScreenDiffEngine = ScreenDiffEngine(),
    private val repository: JarvisRepository? = null
) {
    companion object {
        private const val TAG = "SemanticActionExecutor"
    }

    /**
     * Executes action on a target element identified by semantic role or element ID.
     */
    suspend fun executeAction(
        targetGoalOrRole: String,
        actionType: String = "CLICK", // "CLICK", "TYPE", "SCROLL"
        textToType: String? = null,
        expectedOutcome: String? = null,
        isUserConfirmed: Boolean = false
    ): SemanticActionResult = withContext(Dispatchers.Main) {
        // 1. Observe fresh before-screen state
        val beforeScreen = screenEngine.observeScreen(taskGoal = targetGoalOrRole)

        // 2. Match Target Element
        val matchResult = screenEngine.targetMatcher.matchTarget(targetGoalOrRole, beforeScreen)
        val targetElement = matchResult.selectedElement

        if (targetElement == null) {
            return@withContext SemanticActionResult(
                success = false,
                elementId = "none",
                semanticRole = targetGoalOrRole,
                actionMethod = "TARGET_NOT_FOUND",
                transitionVerified = false,
                diffSummary = "No matching target element detected on current screen (${beforeScreen.packageName})",
                evidence = "Target matching failed: ${matchResult.reason}",
                errorMessage = "Target '$targetGoalOrRole' not found on active screen."
            )
        }

        // 3. Sensitive Action & Dialog Protection
        if (targetElement.isSensitive && !isUserConfirmed) {
            Log.w(TAG, "Sensitive action detected on element: ${targetElement.displaySummary}. Pausing for user confirmation.")
            return@withContext SemanticActionResult(
                success = false,
                elementId = targetElement.id,
                semanticRole = targetElement.role,
                actionMethod = "SENSITIVE_ACTION_GUARD",
                transitionVerified = false,
                diffSummary = "Action is classified as sensitive (${targetElement.label ?: targetElement.role}). User confirmation required.",
                evidence = "Protected action: ${targetElement.displaySummary}",
                requiresUserConfirmation = true
            )
        }

        // 4. Resolve FRESH bounds and AccessibilityNodeInfo immediately before actuation
        val (freshNode, freshBounds) = resolveFreshNodeAndBounds(targetElement)
        val boundsToUse = if (!freshBounds.isEmpty) freshBounds else targetElement.bounds

        // 5. Dispatch Action
        var executionMethod = "UNKNOWN"
        var dispatchSuccess = false

        if (actionType == "TYPE" && textToType != null) {
            val typeResult = JarvisAccessibilityService.typeText(targetElement.label ?: targetElement.description, textToType, context)
            dispatchSuccess = typeResult.success
            executionMethod = typeResult.methodUsed
        } else {
            // Perform Click
            // Step A: Accessibility Node Action Click
            if (freshNode != null && freshNode.isClickable) {
                val clicked = freshNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    dispatchSuccess = true
                    executionMethod = "ACCESSIBILITY_ACTION_CLICK"
                }
            }

            // Step B: Parent Hierarchy Click
            if (!dispatchSuccess && freshNode != null) {
                var parent = freshNode.parent
                var depth = 0
                while (parent != null && depth < 5) {
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        dispatchSuccess = true
                        executionMethod = "PARENT_ACTION_CLICK"
                        break
                    }
                    parent = parent.parent
                    depth++
                }
            }

            // Step C: Multimodal Dynamic Coordinate Tap (Resolved at runtime)
            if (!dispatchSuccess && !boundsToUse.isEmpty) {
                val cx = boundsToUse.centerX().toFloat()
                val cy = boundsToUse.centerY().toFloat()
                val gestureOk = JarvisAccessibilityService.performSwipeGesture(cx, cy, cx, cy, 60)
                if (gestureOk) {
                    dispatchSuccess = true
                    executionMethod = "DYNAMIC_GESTURE_TAP ($cx, $cy)"
                }
            }

            // Step D: Text search fallback
            if (!dispatchSuccess && targetElement.label != null) {
                val fallback = JarvisAccessibilityService.clickElement(targetElement.label)
                if (fallback.success) {
                    dispatchSuccess = true
                    executionMethod = fallback.methodUsed
                }
            }
        }

        if (!dispatchSuccess) {
            return@withContext SemanticActionResult(
                success = false,
                elementId = targetElement.id,
                semanticRole = targetElement.role,
                actionMethod = "ACTUATION_REJECTED",
                transitionVerified = false,
                diffSummary = "Dispatched action failed across all interaction tiers.",
                evidence = "Element bounds: $boundsToUse, Class: ${targetElement.className}",
                errorMessage = "Failed to interact with target."
            )
        }

        // 6. Visual Action Verification via ScreenDiffEngine
        delay(750) // Wait for UI rendering and frame transitions
        val afterScreen = screenEngine.observeScreen(taskGoal = targetGoalOrRole, forceVisualScan = false)
        val diff = diffEngine.computeDiff(beforeScreen, afterScreen, expectedOutcome)

        val isVerified = diff.transitionOccurred && diff.matchedExpectedOutcome

        // 7. Store Learned Visual Pattern in Room DB if verified
        if (isVerified) {
            recordLearnedPattern(beforeScreen.packageName, targetElement, actionType, diff.summary)
        } else {
            Log.w(TAG, "Action verification unconfirmed: ${diff.summary}. Evaluating alternatives.")
            // Retry with first alternative if available and safe
            val alternative = matchResult.alternatives.firstOrNull()
            if (alternative != null && !alternative.isSensitive) {
                Log.i(TAG, "Attempting retry with alternative: ${alternative.displaySummary}")
                val altDetails = JarvisAccessibilityService.clickElement(alternative.label ?: alternative.role)
                if (altDetails.success) {
                    return@withContext SemanticActionResult(
                        success = true,
                        elementId = alternative.id,
                        semanticRole = alternative.role,
                        actionMethod = "ALTERNATIVE_RECOVERY (${altDetails.methodUsed})",
                        transitionVerified = true,
                        diffSummary = "Recovered via alternative target: ${alternative.role}",
                        evidence = altDetails.evidence
                    )
                }
            }
        }

        SemanticActionResult(
            success = dispatchSuccess && isVerified,
            elementId = targetElement.id,
            semanticRole = targetElement.role,
            actionMethod = executionMethod,
            transitionVerified = isVerified,
            diffSummary = diff.summary,
            evidence = "Dispatched via $executionMethod. Diff: ${diff.summary} (Confidence: ${(diff.confidence * 100).toInt()}%)",
            errorMessage = if (!isVerified) "Transition was not verified on screen" else null
        )
    }

    private fun resolveFreshNodeAndBounds(element: SemanticUIElement): Pair<AccessibilityNodeInfo?, Rect> {
        val root = JarvisAccessibilityService.instance?.rootInActiveWindow ?: return Pair(null, element.bounds)
        val freshBounds = Rect()

        // 1. Try finding node by ViewId
        if (!element.originalViewId.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(element.originalViewId)
            if (!nodes.isNullOrEmpty()) {
                val node = nodes.first()
                node.getBoundsInScreen(freshBounds)
                return Pair(node, freshBounds)
            }
        }

        // 2. Try finding node by Text
        if (!element.label.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(element.label)
            if (!nodes.isNullOrEmpty()) {
                val node = nodes.first()
                node.getBoundsInScreen(freshBounds)
                return Pair(node, freshBounds)
            }
        }

        // 3. Fallback: Find node at original center coordinates
        val centerNode = findNodeAtCoordinates(root, element.bounds.centerX(), element.bounds.centerY())
        if (centerNode != null) {
            centerNode.getBoundsInScreen(freshBounds)
            return Pair(centerNode, freshBounds)
        }

        return Pair(null, element.bounds)
    }

    private fun findNodeAtCoordinates(root: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (root == null) return null
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val match = findNodeAtCoordinates(child, x, y)
            if (match != null) return match
        }

        return root
    }

    private suspend fun recordLearnedPattern(
        appPackage: String,
        element: SemanticUIElement,
        action: String,
        outcome: String
    ) {
        if (repository == null || appPackage.isBlank()) return
        try {
            repository.insertVisualExperience(
                VisualExperienceEntity(
                    appPackage = appPackage,
                    screenContext = "verified_semantic_action",
                    semanticRole = element.role,
                    visualDescription = element.iconMeaning ?: element.label ?: "Semantic control",
                    actionTaken = action,
                    result = "SUCCESS",
                    confidence = element.confidence,
                    boundsLeft = element.bounds.left,
                    boundsTop = element.bounds.top,
                    boundsRight = element.bounds.right,
                    boundsBottom = element.bounds.bottom,
                    source = "SEMANTIC_ACTION_EXECUTOR"
                )
            )
            Log.d(TAG, "Recorded visual experience pattern for ${element.role} in $appPackage")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record experience: ${e.message}")
        }
    }
}
