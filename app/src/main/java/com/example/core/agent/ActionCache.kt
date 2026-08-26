package com.example.core.agent

import com.example.core.model.ToolIntent
import java.util.concurrent.ConcurrentHashMap

data class CachedActionEntry(
    val goalKey: String,
    val toolIntent: ToolIntent,
    val expectedAppPackage: String?,
    val confidence: Float,
    val successCount: Int,
    val lastUsedAt: Long
)

/**
 * ActionCache.
 * Caches safe, deterministic action executions to avoid repeated deep model reasoning
 * for frequent identical operations while ensuring precondition validation.
 */
class ActionCache {

    private val cache = ConcurrentHashMap<String, CachedActionEntry>()

    fun getAction(goal: String, currentPackage: String?): ToolIntent? {
        val normalized = goal.lowercase().trim()
        val entry = cache[normalized] ?: return null

        // Validate package match if target was package-specific
        if (entry.expectedAppPackage != null && currentPackage != null) {
            if (!currentPackage.contains(entry.expectedAppPackage, ignoreCase = true)) {
                return null
            }
        }

        if (entry.confidence >= 0.85f && entry.successCount >= 1) {
            cache[normalized] = entry.copy(
                successCount = entry.successCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
            return entry.toolIntent
        }
        return null
    }

    fun putAction(goal: String, toolIntent: ToolIntent, appPackage: String?, confidence: Float = 0.95f) {
        val normalized = goal.lowercase().trim()
        val existing = cache[normalized]
        cache[normalized] = CachedActionEntry(
            goalKey = normalized,
            toolIntent = toolIntent,
            expectedAppPackage = appPackage,
            confidence = confidence,
            successCount = (existing?.successCount ?: 0) + 1,
            lastUsedAt = System.currentTimeMillis()
        )
    }

    fun invalidate(goal: String) {
        cache.remove(goal.lowercase().trim())
    }

    fun clear() {
        cache.clear()
    }
}
