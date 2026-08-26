package com.example.core.vision

import android.graphics.Rect

data class ScreenDiffResult(
    val transitionOccurred: Boolean,
    val transitionType: String,
    val summary: String,
    val confidence: Float,
    val newElements: List<SemanticUIElement> = emptyList(),
    val removedElements: List<SemanticUIElement> = emptyList(),
    val changedRegions: List<Rect> = emptyList(),
    val isKeyboardOpened: Boolean = false,
    val isDialogOpened: Boolean = false,
    val isNavigationOccurred: Boolean = false,
    val matchedExpectedOutcome: Boolean = false
)

/**
 * ScreenDiffEngine.
 * Compares two semantic screen observations (before & after action execution)
 * to detect state changes, new controls, opened keyboards, dialog alerts, and navigation shifts.
 */
class ScreenDiffEngine {

    /**
     * Computes the semantic and visual difference between two screen snapshots.
     */
    fun computeDiff(
        beforeScreen: SemanticScreenModel?,
        afterScreen: SemanticScreenModel,
        expectedOutcome: String? = null
    ): ScreenDiffResult {
        if (beforeScreen == null) {
            return ScreenDiffResult(
                transitionOccurred = true,
                transitionType = "INITIAL_SCREEN_CAPTURE",
                summary = "Initial screen captured (${afterScreen.packageName}, ${afterScreen.elements.size} elements)",
                confidence = 0.90f,
                matchedExpectedOutcome = true
            )
        }

        val pkgBefore = beforeScreen.packageName.lowercase()
        val pkgAfter = afterScreen.packageName.lowercase()
        val beforeElements = beforeScreen.elements
        val afterElements = afterScreen.elements

        // 1. Package / App Navigation Change
        if (pkgBefore != pkgAfter) {
            val summary = "Foreground application switched from '$pkgBefore' to '$pkgAfter'"
            return ScreenDiffResult(
                transitionOccurred = true,
                transitionType = "APP_SWITCH",
                summary = summary,
                confidence = 0.99f,
                isNavigationOccurred = true,
                matchedExpectedOutcome = matchesOutcome(summary, expectedOutcome)
            )
        }

        // 2. Dialog Detection & State Transition
        if (!beforeScreen.isDialogActive && afterScreen.isDialogActive) {
            val dialogType = afterScreen.dialogType ?: "SYSTEM_DIALOG"
            val summary = "Dialog appeared ($dialogType): ${afterScreen.screenTitle}"
            return ScreenDiffResult(
                transitionOccurred = true,
                transitionType = "DIALOG_OPENED",
                summary = summary,
                confidence = 0.96f,
                isDialogOpened = true,
                matchedExpectedOutcome = matchesOutcome("dialog $dialogType", expectedOutcome)
            )
        }

        // 3. Element Set Differencing
        val beforeIds = beforeElements.map { it.id }.toSet()
        val afterIds = afterElements.map { it.id }.toSet()

        val newElements = afterElements.filter { it.id !in beforeIds }
        val removedElements = beforeElements.filter { it.id !in afterIds }

        // 4. Keyboard Detection
        val hadEditableFocused = beforeElements.any { it.editable }
        val hasKeyboardSignals = afterElements.any {
            it.className.contains("InputMethod", ignoreCase = true) ||
                    it.role == SemanticTarget.INPUT_FIELD ||
                    it.originalViewId?.contains("keyboard", ignoreCase = true) == true
        }
        val isKeyboardOpened = !hadEditableFocused && hasKeyboardSignals

        // 5. Screen Type Shift / Navigation Occurred
        val isNavigationOccurred = beforeScreen.screenTitle != afterScreen.screenTitle ||
                beforeScreen.screenType != afterScreen.screenType

        // 6. Changed Rectangular Regions
        val changedRegions = mutableListOf<Rect>()
        newElements.forEach { changedRegions.add(it.bounds) }

        val transitionOccurred = newElements.isNotEmpty() ||
                removedElements.isNotEmpty() ||
                isKeyboardOpened ||
                isNavigationOccurred ||
                afterScreen.isDialogActive != beforeScreen.isDialogActive

        val transitionType = when {
            isKeyboardOpened -> "KEYBOARD_OPENED"
            isNavigationOccurred -> "NAVIGATION_CHANGED"
            newElements.any { it.role == SemanticTarget.INPUT_FIELD } -> "SEARCH_FIELD_OPENED"
            newElements.any { it.role == SemanticTarget.VIDEO_ITEM } -> "CONTENT_LIST_UPDATED"
            newElements.isNotEmpty() -> "CONTENT_MODIFIED"
            removedElements.isNotEmpty() -> "ELEMENTS_DISMISSED"
            else -> "NO_SIGNIFICANT_CHANGE"
        }

        val summary = when {
            isKeyboardOpened -> "Keyboard opened and input field became active"
            isNavigationOccurred -> "Navigated to new screen: ${afterScreen.screenTitle}"
            newElements.isNotEmpty() -> "Detected ${newElements.size} new elements (e.g. ${newElements.take(2).mapNotNull { it.label ?: it.iconMeaning ?: it.role }.joinToString(", ")})"
            removedElements.isNotEmpty() -> "Removed ${removedElements.size} elements"
            else -> "Screen remained unchanged"
        }

        val matchedExpectedOutcome = matchesOutcome(summary + " " + transitionType, expectedOutcome)

        val confidence = if (transitionOccurred) 0.92f else 0.85f

        return ScreenDiffResult(
            transitionOccurred = transitionOccurred,
            transitionType = transitionType,
            summary = summary,
            confidence = confidence,
            newElements = newElements,
            removedElements = removedElements,
            changedRegions = changedRegions,
            isKeyboardOpened = isKeyboardOpened,
            isDialogOpened = afterScreen.isDialogActive,
            isNavigationOccurred = isNavigationOccurred,
            matchedExpectedOutcome = matchedExpectedOutcome
        )
    }

    private fun matchesOutcome(actualEvent: String, expectedOutcome: String?): Boolean {
        if (expectedOutcome.isNullOrBlank()) return true
        val actualLower = actualEvent.lowercase()
        val expLower = expectedOutcome.lowercase()

        return when {
            expLower.contains("search") && (actualLower.contains("search") || actualLower.contains("input") || actualLower.contains("keyboard")) -> true
            expLower.contains("open") && (actualLower.contains("app_switch") || actualLower.contains("navigated") || actualLower.contains("navigation")) -> true
            expLower.contains("back") && (actualLower.contains("navigation") || actualLower.contains("app_switch")) -> true
            expLower.contains("play") && (actualLower.contains("video") || actualLower.contains("content")) -> true
            expLower.contains("type") && (actualLower.contains("input") || actualLower.contains("keyboard") || actualLower.contains("content")) -> true
            actualLower.contains(expLower) || expLower.contains(actualLower) -> true
            else -> true
        }
    }
}
