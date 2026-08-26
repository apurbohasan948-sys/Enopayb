package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.core.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ScreenCaptureManager.
 * Manages on-demand screen capture for visual inspection.
 * Prevents unnecessary continuous screen captures; only triggers capture when
 * accessibility tree data is insufficient or when visual multimodal analysis is requested.
 */
class ScreenCaptureManager(private val context: Context) {

    private var cachedBitmap: Bitmap? = null
    private var lastCaptureTimestamp: Long = 0
    private val cacheTtlMs: Long = 600 // Cache for 600ms to avoid burst capturing

    /**
     * Determines whether taking a screenshot is necessary.
     */
    fun shouldCaptureScreenshot(
        totalAccessibilityNodes: Int,
        clickableCount: Int,
        currentPackage: String,
        semanticGoal: String? = null
    ): Boolean {
        // 1. If accessibility tree is empty or very sparse (e.g., Unity/Flutter/Custom Canvas/Games/WebViews)
        if (totalAccessibilityNodes < 5 || clickableCount == 0) {
            return true
        }

        // 2. Specific media/video/streaming apps where icons (like search 🔍, play ▶) are often rendered without text
        val pkgLower = currentPackage.lowercase()
        if (pkgLower.contains("youtube") ||
            pkgLower.contains("instagram") ||
            pkgLower.contains("tiktok") ||
            pkgLower.contains("twitter") ||
            pkgLower.contains("camera") ||
            pkgLower.contains("netflix") ||
            pkgLower.contains("spotify")
        ) {
            return true
        }

        // 3. Goal specifically implies visual icons or image items (e.g. video thumbnails, qr codes, charts)
        if (semanticGoal != null) {
            val goalLower = semanticGoal.lowercase()
            if (goalLower.contains("icon") ||
                goalLower.contains("image") ||
                goalLower.contains("thumbnail") ||
                goalLower.contains("video") ||
                goalLower.contains("button without text") ||
                goalLower.contains("logo")
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Captures the screen bitmap if permitted, or returns the cached/placeholder bitmap.
     */
    suspend fun captureScreen(force: Boolean = false): Bitmap? = withContext(Dispatchers.Main) {
        val now = System.currentTimeMillis()
        if (!force && cachedBitmap != null && (now - lastCaptureTimestamp) < cacheTtlMs) {
            return@withContext cachedBitmap
        }

        return@withContext try {
            val bitmap = JarvisAccessibilityService.takeScreenshotBitmap()
            if (bitmap != null) {
                cachedBitmap = bitmap
                lastCaptureTimestamp = now
                Log.d("ScreenCaptureManager", "Screen captured successfully: ${bitmap.width}x${bitmap.height}")
            }
            bitmap
        } catch (e: Exception) {
            Log.e("ScreenCaptureManager", "Error capturing screen: ${e.message}", e)
            JarvisAccessibilityService.createPlaceholderScreenshotBitmap()
        }
    }

    /**
     * Invalidates screenshot cache.
     */
    fun invalidateCache() {
        cachedBitmap = null
        lastCaptureTimestamp = 0
    }
}
