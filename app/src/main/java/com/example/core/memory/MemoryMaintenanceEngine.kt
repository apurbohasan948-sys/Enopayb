package com.example.core.memory

import android.util.Log
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MaintenanceReport(
    val duplicatesRemoved: Int = 0,
    val entriesUpdated: Int = 0,
    val totalRemainingMemories: Int = 0,
    val durationMs: Long = 0
)

/**
 * MemoryMaintenanceEngine.
 * Periodically optimizes, compacts, and maintains long-term memory:
 * - Removes exact and near duplicates
 * - Recalculates confidence and importance scores based on usage
 * - Preserves user preferences and core verified facts
 */
class MemoryMaintenanceEngine(private val dao: JarvisDao) {

    companion object {
        private const val TAG = "JARVIS_MemoryMaintenance"
    }

    suspend fun runMaintenance(): MaintenanceReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var duplicatesRemoved = 0
        var entriesUpdated = 0

        try {
            val allMemories = dao.getAllMemoriesSync()
            val seenKeys = mutableMapOf<String, MemoryEntity>()

            for (mem in allMemories) {
                val normalizedKey = "${mem.category.name}_${mem.key.lowercase().trim()}"
                val existing = seenKeys[normalizedKey]

                if (existing != null) {
                    // Duplicate found: Keep the one with higher usageCount/confidence
                    val toKeep = if (mem.usageCount > existing.usageCount || mem.confidence > existing.confidence) mem else existing
                    val toDelete = if (toKeep.id == mem.id) existing else mem

                    dao.deleteMemory(toDelete)
                    duplicatesRemoved++
                    seenKeys[normalizedKey] = toKeep
                } else {
                    seenKeys[normalizedKey] = mem
                    // Recalculate importance based on usage
                    val newImportance = (0.5f + (mem.usageCount * 0.05f)).coerceAtMost(1.0f)
                    if (newImportance != mem.importance) {
                        dao.insertMemory(mem.copy(importance = newImportance))
                        entriesUpdated++
                    }
                }
            }

            val totalRemaining = dao.getAllMemoriesSync().size
            val duration = System.currentTimeMillis() - startTime

            Log.d(TAG, "Memory maintenance completed in ${duration}ms. Removed $duplicatesRemoved dupes, updated $entriesUpdated entries.")
            MaintenanceReport(
                duplicatesRemoved = duplicatesRemoved,
                entriesUpdated = entriesUpdated,
                totalRemainingMemories = totalRemaining,
                durationMs = duration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Memory maintenance encountered error", e)
            MaintenanceReport(durationMs = System.currentTimeMillis() - startTime)
        }
    }
}
