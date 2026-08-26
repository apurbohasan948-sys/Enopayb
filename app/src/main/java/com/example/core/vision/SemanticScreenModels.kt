package com.example.core.vision

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

/**
 * SemanticUIElement.
 * Represents any UI control detected via Accessibility, Icon Recognizer, OCR, or Multimodal Vision.
 */
data class SemanticUIElement(
    val id: String,
    val role: String,
    val label: String? = null,
    val description: String? = null,
    val iconMeaning: String? = null,
    val bounds: Rect = Rect(),
    val clickable: Boolean = true,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val confidence: Float = 1.0f,
    val source: String = "ACCESSIBILITY", // "ACCESSIBILITY", "ICON_RECOGNIZER", "OCR", "LOCAL_VISION", "GEMINI_VISION", "HYBRID"
    val isDialogElement: Boolean = false,
    val isSensitive: Boolean = false,
    val originalViewId: String? = null,
    val className: String = "android.view.View"
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("label", label ?: JSONObject.NULL)
        put("description", description ?: JSONObject.NULL)
        put("iconMeaning", iconMeaning ?: JSONObject.NULL)
        put("clickable", clickable)
        put("editable", editable)
        put("scrollable", scrollable)
        put("enabled", enabled)
        put("visible", visible)
        put("confidence", confidence)
        put("source", source)
        put("isDialogElement", isDialogElement)
        put("isSensitive", isSensitive)
        put("viewId", originalViewId ?: JSONObject.NULL)
        put("className", className)
        put("bounds", JSONObject().apply {
            put("left", bounds.left)
            put("top", bounds.top)
            put("right", bounds.right)
            put("bottom", bounds.bottom)
            put("centerX", bounds.centerX())
            put("centerY", bounds.centerY())
        })
    }

    val displaySummary: String
        get() {
            val name = label ?: description ?: iconMeaning ?: role
            return "[$role: \"$name\" | conf: ${(confidence * 100).toInt()}% | $source]"
        }
}

/**
 * Screen Type / Archetype for contextual understanding.
 */
enum class ScreenType {
    APP_HOME,
    SEARCH_SCREEN,
    DETAIL_VIEW,
    DIALOG_PERMISSION,
    DIALOG_CONFIRMATION,
    DIALOG_SYSTEM,
    LOGIN_SCREEN,
    SETTINGS_PAGE,
    GRAPHICAL_CANVAS,
    UNKNOWN
}

/**
 * SemanticScreenModel.
 * Comprehensive model of the active screen combining text, accessibility, UI structure, icons, and visual semantics.
 */
data class SemanticScreenModel(
    val packageName: String,
    val screenTitle: String,
    val screenType: ScreenType = ScreenType.UNKNOWN,
    val elements: List<SemanticUIElement> = emptyList(),
    val isDialogActive: Boolean = false,
    val dialogType: String? = null, // "PERMISSION", "CONFIRMATION", "LOGIN", "SYSTEM_ALERT", "ERROR"
    val primaryActions: List<String> = emptyList(),
    val screenConfidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val screenshotBase64: String? = null,
    val hasGraphicalUIOnly: Boolean = false
) {
    fun findElementById(elementId: String): SemanticUIElement? {
        return elements.firstOrNull { it.id == elementId }
    }

    fun findElementsByRole(role: String): List<SemanticUIElement> {
        val normalized = SemanticTarget.normalizeIntent(role)
        return elements.filter { it.role == normalized || it.role == role }
    }

    fun findBestTargetForGoal(goal: String): SemanticUIElement? {
        val normalized = SemanticTarget.normalizeIntent(goal)
        val goalLower = goal.lowercase().trim()

        // 1. Direct role match
        val byRole = elements.firstOrNull { it.role == normalized && it.confidence >= 0.7f }
        if (byRole != null) return byRole

        // 2. Icon meaning match
        val byIcon = elements.firstOrNull { elem ->
            elem.iconMeaning != null && (goalLower.contains(elem.iconMeaning.lowercase()) || elem.iconMeaning.lowercase().contains(goalLower))
        }
        if (byIcon != null) return byIcon

        // 3. Label or description match
        val byText = elements.firstOrNull { elem ->
            (elem.label != null && elem.label.contains(goalLower, ignoreCase = true)) ||
                    (elem.description != null && elem.description.contains(goalLower, ignoreCase = true))
        }
        if (byText != null) return byText

        return elements.maxByOrNull { it.confidence }
    }

    fun toJson(): String {
        val root = JSONObject().apply {
            put("packageName", packageName)
            put("screenTitle", screenTitle)
            put("screenType", screenType.name)
            put("isDialogActive", isDialogActive)
            put("dialogType", dialogType ?: JSONObject.NULL)
            put("screenConfidence", screenConfidence)
            put("timestamp", timestamp)
            put("hasGraphicalUIOnly", hasGraphicalUIOnly)
            val actionsArr = JSONArray()
            primaryActions.forEach { actionsArr.put(it) }
            put("primaryActions", actionsArr)
            val elementsArr = JSONArray()
            elements.forEach { elementsArr.put(it.toJsonObject()) }
            put("elements", elementsArr)
        }
        return root.toString(2)
    }

    fun toSummary(): String {
        val count = elements.size
        val topRoles = elements.take(6).map { it.role }
        return "App: $packageName | Screen: $screenTitle ($screenType) | Elements: $count | Dialog: $isDialogActive | Roles: ${topRoles.joinToString(", ")}"
    }
}
