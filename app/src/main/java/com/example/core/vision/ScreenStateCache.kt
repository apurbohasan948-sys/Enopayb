package com.example.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

data class CachedScreenState(
    val packageKey: String,
    val windowHash: Int,
    val nodeCount: Int,
    val elements: List<ScreenElement>,
    val visualElements: List<VisualElement>,
    val timestamp: Long
)

/**
 * ScreenStateCache.
 * Caches recent screen perception results.
 * If the screen layout, active package, and accessibility structure have not changed,
 * avoids redundant OCR and vision inference passes.
 */
class ScreenStateCache {

    companion object {
        private const val TAG = "JARVIS_ScreenCache"
        private const val CACHE_EXPIRY_MS = 2500L // 2.5 seconds max validity for static screens
    }

    private var cachedState: CachedScreenState? = null

    /**
     * Checks if current screen state matches cached state.
     */
    fun getValidCache(
        currentPackage: String,
        nodeCount: Int,
        sampleBoundsSummary: Int
    ): CachedScreenState? {
        val cache = cachedState ?: return null
        val now = System.currentTimeMillis()

        if (now - cache.timestamp > CACHE_EXPIRY_MS) {
            return null
        }

        if (cache.packageKey == currentPackage &&
            cache.nodeCount == nodeCount &&
            cache.windowHash == sampleBoundsSummary
        ) {
            Log.d(TAG, "ScreenStateCache HIT for $currentPackage (saved OCR/Vision pass)")
            return cache
        }

        return null
    }

    fun updateCache(
        packageName: String,
        nodeCount: Int,
        sampleBoundsSummary: Int,
        elements: List<ScreenElement>,
        visualElements: List<VisualElement>
    ) {
        cachedState = CachedScreenState(
            packageKey = packageName,
            windowHash = sampleBoundsSummary,
            nodeCount = nodeCount,
            elements = elements,
            visualElements = visualElements,
            timestamp = System.currentTimeMillis()
        )
    }

    fun invalidate(reason: String = "UI change detected") {
        if (cachedState != null) {
            Log.d(TAG, "Invalidating ScreenStateCache: $reason")
            cachedState = null
        }
    }
}
