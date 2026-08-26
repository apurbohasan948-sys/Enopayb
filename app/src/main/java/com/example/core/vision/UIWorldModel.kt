package com.example.core.vision

import android.graphics.Rect

/**
 * UIWorldModel.
 * Structured semantic representation of Android screen layout and UI elements.
 * Unifies Accessibility, OCR, and Multimodal Vision perception into a canonical state.
 */
data class UIElementNode(
    val id: String,
    val role: String,
    val text: String?,
    val description: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isEnabled: Boolean = true,
    val isVisible: Boolean = true,
    val confidence: Float = 1.0f,
    val source: String = "ACCESSIBILITY",
    val visualDescription: String? = null
)

data class UIWorldModelSnapshot(
    val packageName: String,
    val screenArchetype: String,
    val totalElements: Int,
    val interactiveElements: List<UIElementNode>,
    val timestamp: Long = System.currentTimeMillis(),
    val isKeyguardLocked: Boolean = false,
    val primaryContextText: String = ""
) {
    fun findTarget(role: String): UIElementNode? {
        val normalized = SemanticTarget.normalizeIntent(role)
        return interactiveElements.firstOrNull { it.role == normalized }
            ?: interactiveElements.firstOrNull { 
                it.text?.contains(role, ignoreCase = true) == true || 
                it.description?.contains(role, ignoreCase = true) == true 
            }
    }

    companion object {
        fun fromSemanticScreenModel(model: SemanticScreenModel): UIWorldModelSnapshot {
            val nodes = model.elements.map { elem ->
                UIElementNode(
                    id = elem.id,
                    role = elem.role,
                    text = elem.label,
                    description = elem.description ?: elem.iconMeaning,
                    resourceId = elem.originalViewId,
                    className = elem.className,
                    bounds = elem.bounds,
                    isClickable = elem.clickable,
                    isEditable = elem.editable,
                    isScrollable = elem.scrollable,
                    isEnabled = elem.enabled,
                    isVisible = elem.visible,
                    confidence = elem.confidence,
                    source = elem.source,
                    visualDescription = elem.iconMeaning ?: elem.description
                )
            }
            return UIWorldModelSnapshot(
                packageName = model.packageName,
                screenArchetype = model.screenType.name,
                totalElements = model.elements.size,
                interactiveElements = nodes,
                timestamp = model.timestamp,
                primaryContextText = model.screenTitle
            )
        }
    }
}

