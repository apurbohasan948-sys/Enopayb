package com.example.core.vision

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

/**
 * Standard semantic target roles for multimodal UI understanding.
 */
object SemanticTarget {
    const val SEARCH = "SEARCH"
    const val PLAY = "PLAY"
    const val PAUSE = "PAUSE"
    const val MORE_OPTIONS = "MORE_OPTIONS"
    const val BACK = "BACK"
    const val HOME = "HOME"
    const val SETTINGS = "SETTINGS"
    const val SHARE = "SHARE"
    const val DOWNLOAD = "DOWNLOAD"
    const val ADD = "ADD"
    const val CLOSE = "CLOSE"
    const val INPUT_FIELD = "INPUT_FIELD"
    const val VIDEO_ITEM = "VIDEO_ITEM"
    const val CONTACT_ITEM = "CONTACT_ITEM"
    const val SEND_BUTTON = "SEND_BUTTON"
    const val REFRESH = "REFRESH"
    const val NOTIFICATION = "NOTIFICATION"
    const val UNKNOWN = "UNKNOWN"

    fun normalizeIntent(rawQuery: String): String {
        val lower = rawQuery.trim().lowercase()
        return when {
            lower.contains("search") || lower.contains("find") || lower.contains("খোঁজ") || lower.contains("ম্যাগনিফায়ার") || lower.contains("magnifying") -> SEARCH
            lower.contains("play") || lower.contains("চালাও") || lower.contains("বাজাও") || lower.contains("play video") -> PLAY
            lower.contains("pause") || lower.contains("থামাও") -> PAUSE
            lower.contains("more") || lower.contains("menu") || lower.contains("options") || lower.contains("তিন ডট") || lower.contains("⋮") -> MORE_OPTIONS
            lower.contains("back") || lower.contains("পিছনে") || lower.contains("←") -> BACK
            lower.contains("home") || lower.contains("হোম") || lower.contains("🏠") -> HOME
            lower.contains("setting") || lower.contains("সেটিংস") || lower.contains("gear") || lower.contains("⚙") -> SETTINGS
            lower.contains("share") || lower.contains("শেয়ার") || lower.contains("↗") -> SHARE
            lower.contains("download") || lower.contains("ডাউনলোড") || lower.contains("save") || lower.contains("⬇") -> DOWNLOAD
            lower.contains("add") || lower.contains("plus") || lower.contains("নতুন") || lower.contains("＋") -> ADD
            lower.contains("send") || lower.contains("পাঠাও") || lower.contains("message") -> SEND_BUTTON
            lower.contains("input") || lower.contains("type") || lower.contains("search bar") -> INPUT_FIELD
            lower.contains("video") || lower.contains("result") || lower.contains("thumbnail") -> VIDEO_ITEM
            else -> rawQuery.uppercase()
        }
    }
}

/**
 * Visual element detected by visual analysis, OCR, or heuristic vision engine.
 */
data class VisualElement(
    val semanticRole: String,
    val visualDescription: String,
    val bounds: Rect,
    val confidence: Float,
    val source: String = "VISION" // "VISION", "OCR", "LOCAL_HEURISTIC", "GEMINI_VISION", "EXPERIENCE_DB"
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("semanticRole", semanticRole)
        put("visualDescription", visualDescription)
        put("confidence", confidence)
        put("source", source)
        put("bounds", JSONObject().apply {
            put("left", bounds.left)
            put("top", bounds.top)
            put("right", bounds.right)
            put("bottom", bounds.bottom)
        })
    }
}

/**
 * Unified representation of a screen element combining accessibility semantics and visual understanding.
 */
data class ScreenElement(
    val semanticRole: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String = "android.view.View",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val bounds: Rect = Rect(),
    val confidence: Float = 1.0f,
    val source: String = "ACCESSIBILITY", // "ACCESSIBILITY", "VISION", "HYBRID", "OCR"
    val visualDescription: String? = null
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("semanticRole", semanticRole)
        put("text", text ?: JSONObject.NULL)
        put("contentDescription", contentDescription ?: JSONObject.NULL)
        put("viewId", viewId ?: JSONObject.NULL)
        put("className", className)
        put("clickable", isClickable)
        put("editable", isEditable)
        put("scrollable", isScrollable)
        put("confidence", confidence)
        put("source", source)
        if (visualDescription != null) {
            put("visualDescription", visualDescription)
        }
        put("bounds", JSONObject().apply {
            put("left", bounds.left)
            put("top", bounds.top)
            put("right", bounds.right)
            put("bottom", bounds.bottom)
        })
    }
}

/**
 * Unified screen observation model containing both the accessibility tree and visual analysis.
 */
data class UnifiedScreen(
    val packageName: String,
    val totalNodes: Int,
    val elements: List<ScreenElement>,
    val visualElements: List<VisualElement> = emptyList(),
    val screenshotBase64: String? = null,
    val hasVisualAnalysis: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val root = JSONObject().apply {
            put("package", packageName)
            put("timestamp", timestamp)
            put("totalNodes", totalNodes)
            put("hasVisualAnalysis", hasVisualAnalysis)
            val elementsArr = JSONArray()
            elements.forEach { elementsArr.put(it.toJsonObject()) }
            put("elements", elementsArr)
            val visualArr = JSONArray()
            visualElements.forEach { visualArr.put(it.toJsonObject()) }
            put("visualElements", visualArr)
        }
        return root.toString(2)
    }

    fun getSummary(): String {
        val primaryRoles = elements.take(8).map {
            val label = it.text?.ifEmpty { null } ?: it.contentDescription?.ifEmpty { null } ?: it.semanticRole
            "[$label (${it.source})]"
        }
        return "App: $packageName | Elements: ${elements.size} | Visual: ${visualElements.size} | Top: ${primaryRoles.joinToString(", ")}"
    }
}
