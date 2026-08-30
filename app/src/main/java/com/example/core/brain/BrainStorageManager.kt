package com.example.core.brain

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ValidationStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BrainStorageStats(
    val knowledgeItemCount: Int,
    val knowledgeChunkCount: Int,
    val verifiedKnowledgeCount: Int,
    val uncertainKnowledgeCount: Int,
    val staleKnowledgeCount: Int,
    val memoryCount: Int,
    val activeSkillCount: Int,
    val experienceCount: Int,
    val researchRecordCount: Int,
    val snapshotCount: Int,
    val estimatedSizeBytes: Long
)

class BrainStorageManager(
    private val dao: JarvisDao
) {
    suspend fun getStorageStats(): BrainStorageStats = withContext(Dispatchers.IO) {
        val knowledgeItems = dao.getAllKnowledgeItemsSync()
        val knowledgeChunks = dao.getAllKnowledgeChunksSync()
        val memories = dao.getAllMemoriesSync()
        val skills = dao.getAllSkillsSync()
        val experiences = dao.getAllExperiencesSync()

        val verified = knowledgeItems.count { it.validationStage == ValidationStage.ACTIVE || it.validationStage == ValidationStage.VERIFIED }
        val uncertain = knowledgeItems.count { it.isUncertain || it.validationStage == ValidationStage.UNCERTAIN }
        val stale = knowledgeItems.count { it.isStale }

        // Rough calculation of storage footprint
        val estimatedSize = (knowledgeItems.size * 512L) +
                (knowledgeChunks.size * 256L) +
                (memories.size * 128L) +
                (skills.size * 512L) +
                (experiences.size * 1024L) + 65536L

        BrainStorageStats(
            knowledgeItemCount = knowledgeItems.size,
            knowledgeChunkCount = knowledgeChunks.size,
            verifiedKnowledgeCount = verified,
            uncertainKnowledgeCount = uncertain,
            staleKnowledgeCount = stale,
            memoryCount = memories.size,
            activeSkillCount = skills.count { it.isEnabled },
            experienceCount = experiences.size,
            researchRecordCount = 0,
            snapshotCount = 0,
            estimatedSizeBytes = estimatedSize
        )
    }

    suspend fun cleanupDuplicates(): Int = withContext(Dispatchers.IO) {
        val allItems = dao.getAllKnowledgeItemsSync()
        val seenHashes = mutableSetOf<String>()
        var removed = 0

        for (item in allItems) {
            if (item.contentHash.isNotBlank() && seenHashes.contains(item.contentHash)) {
                dao.deleteKnowledgeItem(item)
                removed++
            } else if (item.contentHash.isNotBlank()) {
                seenHashes.add(item.contentHash)
            }
        }
        removed
    }

    suspend fun checkAndMarkStaleKnowledge(staleThresholdDays: Int = 60): Int = withContext(Dispatchers.IO) {
        val allItems = dao.getAllKnowledgeItemsSync()
        val cutoff = System.currentTimeMillis() - (staleThresholdDays.toLong() * 86_400_000L)
        var staleCount = 0

        for (item in allItems) {
            if (!item.isStale && item.lastVerified < cutoff && item.failureCount >= 1) {
                dao.updateKnowledgeItem(item.copy(isStale = true, validationStage = ValidationStage.UNCERTAIN))
                staleCount++
            }
        }
        staleCount
    }

    suspend fun compactBrain(): BrainStorageStats = withContext(Dispatchers.IO) {
        cleanupDuplicates()
        checkAndMarkStaleKnowledge()
        getStorageStats()
    }
}
