package com.example.core.scheduler

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.HealthSeverity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

data class MaintenanceSummary(
    val deduplicatedMemoriesCount: Int = 0,
    val validatedSkillsCount: Int = 0,
    val cleanedFailedTasksCount: Int = 0,
    val indexedKnowledgeChunksCount: Int = 0,
    val durationMs: Long = 0L
)

class KnowledgeMaintenanceWorker(
    private val dao: JarvisDao
) {
    /**
     * Executes approved background memory & knowledge maintenance.
     */
    suspend fun runMaintenance(): MaintenanceSummary = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var dedupCount = 0
        var validatedSkills = 0
        var cleanedTasks = 0

        // 1. Deduplicate Memories (detect near-duplicate key/values)
        try {
            val allMemories = dao.getTopMemories(500)
            val seenKeys = mutableMapOf<String, MemoryEntity>()
            for (mem in allMemories) {
                val normalizedKey = "${mem.key.lowercase().trim()}_${mem.category.name}"
                val existing = seenKeys[normalizedKey]
                if (existing != null) {
                    // Keep the one with higher importance or more recent update
                    if (mem.importance > existing.importance || mem.updatedAt > existing.updatedAt) {
                        dao.deleteMemory(existing)
                        seenKeys[normalizedKey] = mem
                    } else {
                        dao.deleteMemory(mem)
                    }
                    dedupCount++
                } else {
                    seenKeys[normalizedKey] = mem
                }
            }
        } catch (e: Exception) {}

        // 2. Validate Skills
        try {
            val skills = dao.getAllSkills().firstOrNull() ?: emptyList()
            for (skill in skills) {
                if (skill.successRate < 0.3f && skill.failureCount > 5) {
                    // Downgrade confidence for unreliable skills
                    dao.updateSkill(skill.copy(confidence = 0.4f))
                }
                validatedSkills++
            }
        } catch (e: Exception) {}

        // 3. Clean temporary stale tasks
        try {
            val tasks = dao.getAllAutonomousTasks().firstOrNull() ?: emptyList()
            val oldCutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) // 7 days
            for (task in tasks) {
                if (task.completedAt in 1 until oldCutoff) {
                    dao.deleteAutonomousTask(task)
                    cleanedTasks++
                }
            }
        } catch (e: Exception) {}

        val duration = System.currentTimeMillis() - startTime
        MaintenanceSummary(
            deduplicatedMemoriesCount = dedupCount,
            validatedSkillsCount = validatedSkills,
            cleanedFailedTasksCount = cleanedTasks,
            indexedKnowledgeChunksCount = 0,
            durationMs = duration
        )
    }
}
