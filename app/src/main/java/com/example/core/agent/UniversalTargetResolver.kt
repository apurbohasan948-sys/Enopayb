package com.example.core.agent

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.vision.ScreenElement
import com.example.core.vision.ScreenUnderstandingEngine
import com.example.core.vision.UnifiedScreen
import com.example.core.vision.VisualElement

/**
 * UniversalTargetResolver.
 * Resolves semantic targets dynamically across 8 hierarchical strategies:
 * 1. Accessibility semantics (Role, clickability, editable status)
 * 2. Resource ID matching
 * 3. Content Description matching
 * 4. Visible Text exact & fuzzy matching
 * 5. OCR on-screen spatial text recognition
 * 6. Icon Semantic Classification (magnifying glass -> SEARCH, gear -> SETTINGS, etc.)
 * 7. Local Vision bounding box spatial heuristics
 * 8. Multimodal Vision Fallback
 *
 * Never assumes visible text exists.
 */
class UniversalTargetResolver(
    private val context: Context,
    private val screenEngine: ScreenUnderstandingEngine
) {
    companion object {
        private const val TAG = "JARVIS_TargetResolver"
    }

    /**
     * Resolves target for a semantic role/goal against a live screen state.
     */
    suspend fun resolveTarget(
        semanticGoal: String,
        screen: UnifiedScreen,
        requireEditable: Boolean = false
    ): TargetResolutionResult {
        val targetQuery = semanticGoal.trim().lowercase()

        // 1. Accessibility Semantics & Resource ID & Content Description & Text
        val accessibilityCandidate = findAccessibilityCandidate(targetQuery, screen, requireEditable)
        if (accessibilityCandidate != null) {
            return accessibilityCandidate
        }

        // 2. Icon Semantics Classification (e.g. SEARCH icon, PLAY button, BACK, SETTINGS)
        val iconCandidate = findIconSemanticCandidate(targetQuery, screen)
        if (iconCandidate != null) {
            return iconCandidate
        }

        // 3. On-Screen OCR Spatial Matching
        val ocrCandidate = findOcrCandidate(targetQuery, screen)
        if (ocrCandidate != null) {
            return ocrCandidate
        }

        // 4. Local Vision & Multimodal Target Resolution via ScreenEngine
        val engineResolved = screenEngine.resolveTargetForGoal(semanticGoal, screen)
        if (engineResolved.isConfident && engineResolved.element != null) {
            val el = engineResolved.element
            return TargetResolutionResult(
                found = true,
                semanticRole = engineResolved.targetRole,
                confidence = engineResolved.confidence,
                source = "LOCAL_VISION (${engineResolved.matchSource})",
                bounds = el.bounds,
                isEditable = el.isEditable,
                isClickable = el.isClickable,
                originalText = el.text,
                contentDescription = el.contentDescription,
                viewId = el.viewId
            )
        }

        Log.w(TAG, "Failed to resolve target for semantic goal: '$semanticGoal' on ${screen.packageName}")
        return TargetResolutionResult(
            found = false,
            semanticRole = semanticGoal,
            confidence = 0f,
            source = "NONE",
            failureReason = "SEARCH_TARGET_NOT_FOUND on active package ${screen.packageName}"
        )
    }

    private fun findAccessibilityCandidate(
        query: String,
        screen: UnifiedScreen,
        requireEditable: Boolean
    ): TargetResolutionResult? {
        val elements = screen.elements

        // Filter elements
        for (el in elements) {
            if (requireEditable && !el.isEditable && !el.className.contains("EditText", ignoreCase = true)) {
                continue
            }

            val text = el.text?.lowercase() ?: ""
            val desc = el.contentDescription?.lowercase() ?: ""
            val viewId = el.viewId?.lowercase() ?: ""
            val role = el.semanticRole.lowercase()

            // A. Exact Semantic Role Match
            if (role == query || role.contains(query)) {
                return TargetResolutionResult(
                    found = true,
                    semanticRole = el.semanticRole,
                    confidence = 0.98f,
                    source = "ACCESSIBILITY_ROLE",
                    bounds = el.bounds,
                    isEditable = el.isEditable,
                    isClickable = el.isClickable,
                    originalText = el.text,
                    contentDescription = el.contentDescription,
                    viewId = el.viewId
                )
            }

            // B. Content Description Match (e.g. YouTube Search Button "Search", "Search YouTube", "Voice Search")
            if (desc.isNotBlank()) {
                val score = when {
                    desc == query -> 0.96f
                    desc.contains(query) -> 0.92f
                    query.contains(desc) && desc.length > 2 -> 0.85f
                    query == "search" && (desc.contains("search") || desc.contains("অনুসন্ধান")) -> 0.95f
                    query == "video_item" && desc.contains("views") -> 0.88f
                    else -> 0f
                }
                if (score > 0.80f) {
                    return TargetResolutionResult(
                        found = true,
                        semanticRole = el.semanticRole,
                        confidence = score,
                        source = "CONTENT_DESC",
                        bounds = el.bounds,
                        isEditable = el.isEditable,
                        isClickable = el.isClickable,
                        originalText = el.text,
                        contentDescription = el.contentDescription,
                        viewId = el.viewId
                    )
                }
            }

            // C. Resource ID Match (e.g. "search_edit_text", "search_button", "menu_search")
            if (viewId.isNotBlank()) {
                val score = when {
                    viewId.contains(query) -> 0.90f
                    query == "search" && (viewId.contains("search") || viewId.contains("query")) -> 0.92f
                    query == "input_field" && (viewId.contains("input") || viewId.contains("edit") || viewId.contains("search")) -> 0.90f
                    else -> 0f
                }
                if (score > 0.80f) {
                    return TargetResolutionResult(
                        found = true,
                        semanticRole = el.semanticRole,
                        confidence = score,
                        source = "RESOURCE_ID",
                        bounds = el.bounds,
                        isEditable = el.isEditable,
                        isClickable = el.isClickable,
                        originalText = el.text,
                        contentDescription = el.contentDescription,
                        viewId = el.viewId
                    )
                }
            }

            // D. Visible Text Match
            if (text.isNotBlank()) {
                val score = when {
                    text == query -> 0.95f
                    text.contains(query) -> 0.88f
                    query.contains(text) && text.length > 3 -> 0.82f
                    else -> 0f
                }
                if (score > 0.80f) {
                    return TargetResolutionResult(
                        found = true,
                        semanticRole = el.semanticRole,
                        confidence = score,
                        source = "VISIBLE_TEXT",
                        bounds = el.bounds,
                        isEditable = el.isEditable,
                        isClickable = el.isClickable,
                        originalText = el.text,
                        contentDescription = el.contentDescription,
                        viewId = el.viewId
                    )
                }
            }
        }

        return null
    }

    private fun findIconSemanticCandidate(
        query: String,
        screen: UnifiedScreen
    ): TargetResolutionResult? {
        val visualIcons = screen.visualElements.filter { it.semanticRole.isNotBlank() }

        for (icon in visualIcons) {
            val iconRole = icon.semanticRole.lowercase()
            val score = when {
                iconRole == query -> 0.92f
                query == "search" && (iconRole.contains("search") || iconRole.contains("magnifying")) -> 0.94f
                query == "settings" && (iconRole.contains("settings") || iconRole.contains("gear")) -> 0.93f
                query == "play" && (iconRole.contains("play") || iconRole.contains("triangle")) -> 0.90f
                query == "back" && (iconRole.contains("back") || iconRole.contains("arrow")) -> 0.91f
                else -> 0f
            }

            if (score > 0.80f) {
                return TargetResolutionResult(
                    found = true,
                    semanticRole = icon.semanticRole,
                    confidence = score,
                    source = "ICON_SEMANTICS",
                    bounds = icon.bounds,
                    isEditable = false,
                    isClickable = true,
                    originalText = icon.visualDescription
                )
            }
        }

        return null
    }

    private fun findOcrCandidate(
        query: String,
        screen: UnifiedScreen
    ): TargetResolutionResult? {
        val ocrElements = screen.visualElements.filter { it.source == "OCR" }

        for (el in ocrElements) {
            val text = el.visualDescription.lowercase()
            if (text.isNotBlank()) {
                val score = when {
                    text == query -> 0.88f
                    text.contains(query) -> 0.82f
                    query.contains(text) && text.length > 3 -> 0.78f
                    else -> 0f
                }
                if (score > 0.75f) {
                    return TargetResolutionResult(
                        found = true,
                        semanticRole = "OCR_TEXT",
                        confidence = score,
                        source = "OCR",
                        bounds = el.bounds,
                        isEditable = false,
                        isClickable = true,
                        originalText = el.visualDescription
                    )
                }
            }
        }

        return null
    }
}
