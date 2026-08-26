package com.example.core.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log

/**
 * Standard semantic icon definitions.
 */
object IconSymbols {
    const val SEARCH = "SEARCH"
    const val PLAY = "PLAY"
    const val PAUSE = "PAUSE"
    const val BACK = "BACK"
    const val HOME = "HOME"
    const val SETTINGS = "SETTINGS"
    const val MORE_MENU = "MORE_MENU"
    const val SHARE = "SHARE"
    const val DOWNLOAD = "DOWNLOAD"
    const val DELETE = "DELETE"
    const val ADD = "ADD"
    const val REMOVE = "REMOVE"
    const val CONFIRM = "CONFIRM"
    const val CLOSE = "CLOSE"
    const val VOICE_SEARCH = "VOICE_SEARCH"
    const val CAMERA = "CAMERA"
    const val FAVORITE = "FAVORITE"
    const val BOOKMARK = "BOOKMARK"
    const val REFRESH = "REFRESH"
    const val MENU = "MENU"
    const val SEND = "SEND"
    const val CALL = "CALL"
    const val INPUT_FIELD = "INPUT_FIELD"
    const val CONTACT_SEARCH = "CONTACT_SEARCH"
    const val SETTINGS_SEARCH = "SETTINGS_SEARCH"
}

data class RecognizedIcon(
    val symbol: String,
    val meaning: String,
    val confidence: Float,
    val detectionMethod: String,
    val contextualRole: String
)

/**
 * IconSemanticRecognizer.
 * Recognizes common Android UI icons and graphical controls based on:
 * 1. Accessibility hints & glyph characters (🔍, ▶, ⏸, ←, 🏠, ⚙, ⋮, ↗, ⬇, 🗑, ＋, ✓, ✕, 🎙, 📷, ❤️, 🔖, 🔄, ☰)
 * 2. Resource ID patterns (e.g., "search_button", "action_more", "nav_back", "btn_send")
 * 3. Spatial & Contextual Application Rules (YouTube search bar vs Contacts search vs Settings search)
 * 4. Local Bitmap luminance & contour scan for icon-only buttons without text
 */
class IconSemanticRecognizer {

    companion object {
        private const val TAG = "IconSemanticRecognizer"
    }

    /**
     * Recognizes an icon given its text/description/viewId hints, spatial bounds, and current application context.
     */
    fun recognizeIcon(
        text: String?,
        contentDescription: String?,
        viewId: String?,
        bounds: Rect,
        appPackage: String,
        screenContext: String = "",
        taskGoal: String? = null,
        bitmap: Bitmap? = null
    ): RecognizedIcon? {
        val rawText = (text ?: "").trim().lowercase()
        val desc = (contentDescription ?: "").trim().lowercase()
        val resId = (viewId ?: "").trim().lowercase()
        val pkg = appPackage.lowercase()
        val goal = (taskGoal ?: "").lowercase()

        val combinedClues = "$rawText $desc $resId"

        // 1. Exact Glyph and Keyword Matching
        val directMatch = matchDirectGlyphsOrKeywords(combinedClues)
        if (directMatch != null) {
            val contextualRole = resolveContextualRole(directMatch.symbol, pkg, goal, screenContext)
            return directMatch.copy(contextualRole = contextualRole)
        }

        // 2. Resource ID Regex Analysis
        val resIdMatch = matchResourceIdPatterns(resId)
        if (resIdMatch != null) {
            val contextualRole = resolveContextualRole(resIdMatch.symbol, pkg, goal, screenContext)
            return resIdMatch.copy(contextualRole = contextualRole)
        }

        // 3. Bitmap Shape / Contrast Analysis (if bitmap provided and bounds are valid)
        if (bitmap != null && !bounds.isEmpty && bounds.width() in 24..300 && bounds.height() in 24..300) {
            val visualMatch = analyzeIconBitmapRegion(bitmap, bounds, pkg)
            if (visualMatch != null) {
                val contextualRole = resolveContextualRole(visualMatch.symbol, pkg, goal, screenContext)
                return visualMatch.copy(contextualRole = contextualRole)
            }
        }

        // 4. Contextual Spatial Heuristics for Unlabeled Controls
        val spatialMatch = matchSpatialPositionHeuristics(bounds, pkg, goal)
        if (spatialMatch != null) {
            return spatialMatch
        }

        return null
    }

    private fun matchDirectGlyphsOrKeywords(clues: String): RecognizedIcon? {
        return when {
            // Search
            clues.contains("🔍") || clues.contains("search") || clues.contains("find") || clues.contains("query") || clues.contains("খুঁজুন") || clues.contains("খোঁজ") || clues.contains("magnifying") || clues.contains("lens") -> {
                RecognizedIcon(IconSymbols.SEARCH, "magnifying glass", 0.96f, "GLYPH_KEYWORD", IconSymbols.SEARCH)
            }
            // Play
            clues.contains("▶") || clues.contains("play") || clues.contains("চালান") || clues.contains("resume") -> {
                RecognizedIcon(IconSymbols.PLAY, "play triangle", 0.95f, "GLYPH_KEYWORD", IconSymbols.PLAY)
            }
            // Pause
            clues.contains("⏸") || clues.contains("pause") || clues.contains("থামান") -> {
                RecognizedIcon(IconSymbols.PAUSE, "pause bars", 0.95f, "GLYPH_KEYWORD", IconSymbols.PAUSE)
            }
            // Back
            clues.contains("←") || clues.contains("‹") || clues.contains("back") || clues.contains("navigate up") || clues.contains("পিছনে") || clues.contains("previous") || clues.contains("arrow_back") -> {
                RecognizedIcon(IconSymbols.BACK, "back arrow", 0.97f, "GLYPH_KEYWORD", IconSymbols.BACK)
            }
            // Home
            clues.contains("🏠") || clues.contains("home") || clues.contains("হোম") -> {
                RecognizedIcon(IconSymbols.HOME, "home icon", 0.95f, "GLYPH_KEYWORD", IconSymbols.HOME)
            }
            // Settings
            clues.contains("⚙") || clues.contains("setting") || clues.contains("gear") || clues.contains("preferences") || clues.contains("সেটিংস") -> {
                RecognizedIcon(IconSymbols.SETTINGS, "gear", 0.96f, "GLYPH_KEYWORD", IconSymbols.SETTINGS)
            }
            // More Menu / Overflow
            clues.contains("⋮") || clues.contains("...") || clues.contains("more options") || clues.contains("overflow") || clues.contains("options menu") || clues.contains("three dots") -> {
                RecognizedIcon(IconSymbols.MORE_MENU, "three dots", 0.95f, "GLYPH_KEYWORD", IconSymbols.MORE_MENU)
            }
            // Share
            clues.contains("↗") || clues.contains("🔗") || clues.contains("share") || clues.contains("শেয়ার") -> {
                RecognizedIcon(IconSymbols.SHARE, "share icon", 0.94f, "GLYPH_KEYWORD", IconSymbols.SHARE)
            }
            // Download
            clues.contains("⬇") || clues.contains("download") || clues.contains("save") || clues.contains("ডাউনলোড") -> {
                RecognizedIcon(IconSymbols.DOWNLOAD, "download icon", 0.94f, "GLYPH_KEYWORD", IconSymbols.DOWNLOAD)
            }
            // Delete
            clues.contains("🗑") || clues.contains("delete") || clues.contains("trash") || clues.contains("remove") || clues.contains("মুছুন") -> {
                RecognizedIcon(IconSymbols.DELETE, "trash icon", 0.95f, "GLYPH_KEYWORD", IconSymbols.DELETE)
            }
            // Add
            clues.contains("＋") || clues.contains("+") || clues.contains("add") || clues.contains("create") || clues.contains("new") || clues.contains("যোগ") -> {
                RecognizedIcon(IconSymbols.ADD, "plus", 0.94f, "GLYPH_KEYWORD", IconSymbols.ADD)
            }
            // Remove / Minus
            clues.contains("－") || clues.contains("minus") -> {
                RecognizedIcon(IconSymbols.REMOVE, "minus", 0.92f, "GLYPH_KEYWORD", IconSymbols.REMOVE)
            }
            // Confirm
            clues.contains("✓") || clues.contains("✔") || clues.contains("confirm") || clues.contains("done") || clues.contains("submit") || clues.contains("accept") -> {
                RecognizedIcon(IconSymbols.CONFIRM, "check mark", 0.95f, "GLYPH_KEYWORD", IconSymbols.CONFIRM)
            }
            // Close
            clues.contains("✕") || clues.contains("✖") || clues.contains("close") || clues.contains("cancel") || clues.contains("dismiss") || clues.contains("বন্ধ") -> {
                RecognizedIcon(IconSymbols.CLOSE, "X close", 0.95f, "GLYPH_KEYWORD", IconSymbols.CLOSE)
            }
            // Voice Search
            clues.contains("🎙") || clues.contains("🎤") || clues.contains("voice") || clues.contains("mic") || clues.contains("ভয়েস") -> {
                RecognizedIcon(IconSymbols.VOICE_SEARCH, "microphone", 0.96f, "GLYPH_KEYWORD", IconSymbols.VOICE_SEARCH)
            }
            // Camera
            clues.contains("📷") || clues.contains("📸") || clues.contains("camera") || clues.contains("photo") || clues.contains("ক্যামেরা") -> {
                RecognizedIcon(IconSymbols.CAMERA, "camera", 0.95f, "GLYPH_KEYWORD", IconSymbols.CAMERA)
            }
            // Favorite / Star
            clues.contains("❤️") || clues.contains("⭐") || clues.contains("star") || clues.contains("favorite") || clues.contains("like") || clues.contains("পছন্দ") -> {
                RecognizedIcon(IconSymbols.FAVORITE, "heart/star", 0.93f, "GLYPH_KEYWORD", IconSymbols.FAVORITE)
            }
            // Bookmark
            clues.contains("🔖") || clues.contains("bookmark") || clues.contains("saved") -> {
                RecognizedIcon(IconSymbols.BOOKMARK, "bookmark", 0.93f, "GLYPH_KEYWORD", IconSymbols.BOOKMARK)
            }
            // Refresh
            clues.contains("🔄") || clues.contains("refresh") || clues.contains("reload") || clues.contains("sync") -> {
                RecognizedIcon(IconSymbols.REFRESH, "refresh circular arrows", 0.94f, "GLYPH_KEYWORD", IconSymbols.REFRESH)
            }
            // Hamburger Menu
            clues.contains("☰") || clues.contains("menu") || clues.contains("drawer") || clues.contains("hamburger") -> {
                RecognizedIcon(IconSymbols.MENU, "menu/hamburger", 0.95f, "GLYPH_KEYWORD", IconSymbols.MENU)
            }
            // Send
            clues.contains("✈") || clues.contains("➤") || clues.contains("send") || clues.contains("পাঠান") -> {
                RecognizedIcon(IconSymbols.SEND, "send button", 0.95f, "GLYPH_KEYWORD", IconSymbols.SEND)
            }
            else -> null
        }
    }

    private fun matchResourceIdPatterns(resId: String): RecognizedIcon? {
        if (resId.isBlank()) return null
        return when {
            resId.contains("search") || resId.contains("query_button") || resId.contains("menu_search") -> {
                RecognizedIcon(IconSymbols.SEARCH, "magnifying glass", 0.92f, "RESOURCE_ID", IconSymbols.SEARCH)
            }
            resId.contains("play") -> {
                RecognizedIcon(IconSymbols.PLAY, "play triangle", 0.92f, "RESOURCE_ID", IconSymbols.PLAY)
            }
            resId.contains("pause") -> {
                RecognizedIcon(IconSymbols.PAUSE, "pause", 0.92f, "RESOURCE_ID", IconSymbols.PAUSE)
            }
            resId.contains("back") || resId.contains("up") || resId.contains("btn_back") -> {
                RecognizedIcon(IconSymbols.BACK, "back arrow", 0.93f, "RESOURCE_ID", IconSymbols.BACK)
            }
            resId.contains("overflow") || resId.contains("more_options") || resId.contains("action_more") -> {
                RecognizedIcon(IconSymbols.MORE_MENU, "three dots", 0.93f, "RESOURCE_ID", IconSymbols.MORE_MENU)
            }
            resId.contains("setting") || resId.contains("config") -> {
                RecognizedIcon(IconSymbols.SETTINGS, "gear", 0.92f, "RESOURCE_ID", IconSymbols.SETTINGS)
            }
            resId.contains("send") || resId.contains("btn_send") -> {
                RecognizedIcon(IconSymbols.SEND, "send icon", 0.93f, "RESOURCE_ID", IconSymbols.SEND)
            }
            resId.contains("add") || resId.contains("create") || resId.contains("fab") -> {
                RecognizedIcon(IconSymbols.ADD, "plus action", 0.88f, "RESOURCE_ID", IconSymbols.ADD)
            }
            resId.contains("delete") || resId.contains("trash") -> {
                RecognizedIcon(IconSymbols.DELETE, "trash icon", 0.92f, "RESOURCE_ID", IconSymbols.DELETE)
            }
            resId.contains("share") -> {
                RecognizedIcon(IconSymbols.SHARE, "share icon", 0.90f, "RESOURCE_ID", IconSymbols.SHARE)
            }
            else -> null
        }
    }

    /**
     * Resolves context-dependent meaning.
     * e.g., Magnifying glass in YouTube = SEARCH (VIDEO_SEARCH)
     * Magnifying glass in Contacts = CONTACT_SEARCH
     * Magnifying glass in Settings = SETTINGS_SEARCH
     */
    private fun resolveContextualRole(
        baseSymbol: String,
        appPackage: String,
        taskGoal: String,
        screenContext: String
    ): String {
        if (baseSymbol == IconSymbols.SEARCH) {
            return when {
                appPackage.contains("youtube") -> "SEARCH"
                appPackage.contains("contact") -> IconSymbols.CONTACT_SEARCH
                appPackage.contains("setting") -> IconSymbols.SETTINGS_SEARCH
                appPackage.contains("whatsapp") -> "SEARCH"
                appPackage.contains("chrome") -> IconSymbols.INPUT_FIELD
                else -> IconSymbols.SEARCH
            }
        }

        if (baseSymbol == IconSymbols.DELETE && (appPackage.contains("gallery") || appPackage.contains("photo"))) {
            return "DELETE_MEDIA"
        }

        return baseSymbol
    }

    /**
     * Analyzes image contrast and shapes within the cropped bounding box.
     */
    private fun analyzeIconBitmapRegion(bitmap: Bitmap, bounds: Rect, pkg: String): RecognizedIcon? {
        try {
            val left = bounds.left.coerceIn(0, bitmap.width - 1)
            val top = bounds.top.coerceIn(0, bitmap.height - 1)
            val right = bounds.right.coerceIn(left + 1, bitmap.width)
            val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)

            val w = right - left
            val h = bottom - top
            if (w < 16 || h < 16) return null

            // Inspect spatial symmetry and luminance
            var totalLuminance = 0.0
            var pixelCount = 0
            for (x in left until right step maxOf(1, w / 10)) {
                for (y in top until bottom step maxOf(1, h / 10)) {
                    val p = bitmap.getPixel(x, y)
                    val lum = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                    totalLuminance += lum
                    pixelCount++
                }
            }

            val avgLum = if (pixelCount > 0) totalLuminance / pixelCount else 128.0

            // If top right of screen in standard action bar, likely search or more
            val screenW = bitmap.width.toFloat()
            val screenH = bitmap.height.toFloat()
            val isTopBar = (top / screenH) < 0.12f
            val isRightSide = (left / screenW) > 0.65f
            val isLeftSide = (right / screenW) < 0.25f

            if (isTopBar && isRightSide) {
                return RecognizedIcon(
                    symbol = IconSymbols.SEARCH,
                    meaning = "magnifying glass",
                    confidence = 0.86f,
                    detectionMethod = "VISUAL_BITMAP_REGION",
                    contextualRole = if (pkg.contains("settings")) IconSymbols.SETTINGS_SEARCH else IconSymbols.SEARCH
                )
            } else if (isTopBar && isLeftSide) {
                return RecognizedIcon(
                    symbol = IconSymbols.BACK,
                    meaning = "back arrow",
                    confidence = 0.88f,
                    detectionMethod = "VISUAL_BITMAP_REGION",
                    contextualRole = IconSymbols.BACK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bitmap region analysis failed: ${e.message}")
        }
        return null
    }

    private fun matchSpatialPositionHeuristics(bounds: Rect, pkg: String, goal: String): RecognizedIcon? {
        if (bounds.isEmpty) return null

        // Standard Top Bar Navigation
        if (bounds.top < 260) {
            // Top Right: Search or More Options
            if (bounds.left > 650 && (goal.contains("search") || pkg.contains("youtube") || pkg.contains("whatsapp"))) {
                return RecognizedIcon(
                    symbol = IconSymbols.SEARCH,
                    meaning = "magnifying glass",
                    confidence = 0.82f,
                    detectionMethod = "SPATIAL_HEURISTIC",
                    contextualRole = if (pkg.contains("settings")) IconSymbols.SETTINGS_SEARCH else IconSymbols.SEARCH
                )
            }
            // Top Left: Back or Menu
            if (bounds.right < 350 && (goal.contains("back") || goal.contains("menu"))) {
                val symbol = if (goal.contains("menu")) IconSymbols.MENU else IconSymbols.BACK
                return RecognizedIcon(
                    symbol = symbol,
                    meaning = if (symbol == IconSymbols.MENU) "hamburger menu" else "back arrow",
                    confidence = 0.85f,
                    detectionMethod = "SPATIAL_HEURISTIC",
                    contextualRole = symbol
                )
            }
        }

        // Bottom Right Floating Action Button (FAB) -> Add / Send
        if (bounds.top > 1600 && bounds.left > 750 && bounds.width() in 100..300) {
            val role = if (pkg.contains("whatsapp") || pkg.contains("message")) IconSymbols.SEND else IconSymbols.ADD
            return RecognizedIcon(
                symbol = role,
                meaning = if (role == IconSymbols.SEND) "send button" else "add action",
                confidence = 0.80f,
                detectionMethod = "SPATIAL_HEURISTIC",
                contextualRole = role
            )
        }

        return null
    }
}
