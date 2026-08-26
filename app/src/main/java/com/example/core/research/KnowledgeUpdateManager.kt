package com.example.core.research

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.KnowledgeStatus
import com.example.data.local.entity.KnowledgeVersionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

data class KnowledgeUpdateProposal(
    val knowledgeKey: String,
    val topic: String,
    val content: String,
    val summary: String,
    val sourceUrl: String? = null,
    val sourceQualityScore: Float = 0.85f,
    val confidence: Float = 0.90f,
    val changeReason: String = "Web Research Synthesis",
    val autoApproveIfConfident: Boolean = true
)

class KnowledgeUpdateManager(
    private val dao: JarvisDao
) {
    val allVersions: Flow<List<KnowledgeVersionEntity>> = dao.getAllKnowledgeVersions()

    /**
     * Proposes or applies a new version for a knowledge item.
     */
    suspend fun proposeUpdate(proposal: KnowledgeUpdateProposal): KnowledgeVersionEntity = withContext(Dispatchers.IO) {
        val existingActive = dao.getLatestActiveKnowledge(proposal.knowledgeKey)
        val nextVersion = (existingActive?.version ?: 0) + 1

        val isAutoApproved = proposal.autoApproveIfConfident && proposal.confidence >= 0.85f && proposal.sourceQualityScore >= 0.75f
        val initialStatus = if (isAutoApproved) KnowledgeStatus.ACTIVE else KnowledgeStatus.PENDING_APPROVAL

        val versionEntity = KnowledgeVersionEntity(
            knowledgeKey = proposal.knowledgeKey,
            topic = proposal.topic,
            version = nextVersion,
            content = proposal.content,
            summary = proposal.summary,
            sourceUrl = proposal.sourceUrl,
            sourceQualityScore = proposal.sourceQualityScore,
            confidence = proposal.confidence,
            status = initialStatus,
            oldVersionContent = existingActive?.content,
            changeReason = proposal.changeReason,
            isAutoUpdated = isAutoApproved,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val id = dao.insertKnowledgeVersion(versionEntity)
        val created = versionEntity.copy(id = id)

        if (isAutoApproved) {
            // Also synchronize with KnowledgeChunkEntity for quick retrieval
            dao.insertKnowledgeChunk(
                KnowledgeChunkEntity(
                    title = proposal.topic,
                    sourceDocument = proposal.sourceUrl ?: "WEB_RESEARCH",
                    content = proposal.content,
                    tags = "${proposal.knowledgeKey},RESEARCH_KNOWLEDGE,v$nextVersion",
                    timestamp = System.currentTimeMillis()
                )
            )

            // Mark previous active version as SUPERSEDED if exists
            if (existingActive != null) {
                dao.updateKnowledgeVersion(existingActive.copy(status = KnowledgeStatus.SUPERSEDED, updatedAt = System.currentTimeMillis()))
            }
        }

        created
    }

    /**
     * Approves a pending knowledge update proposal.
     */
    suspend fun approvePendingUpdate(versionId: Long): Boolean = withContext(Dispatchers.IO) {
        val versions = dao.getAllKnowledgeVersions().firstOrNull() ?: return@withContext false
        val target = versions.find { it.id == versionId } ?: return@withContext false

        // Mark any currently active version for this key as SUPERSEDED
        val existingActive = dao.getLatestActiveKnowledge(target.knowledgeKey)
        if (existingActive != null && existingActive.id != target.id) {
            dao.updateKnowledgeVersion(existingActive.copy(status = KnowledgeStatus.SUPERSEDED, updatedAt = System.currentTimeMillis()))
        }

        dao.updateKnowledgeVersion(target.copy(status = KnowledgeStatus.ACTIVE, updatedAt = System.currentTimeMillis()))

        dao.insertKnowledgeChunk(
            KnowledgeChunkEntity(
                title = target.topic,
                sourceDocument = target.sourceUrl ?: "WEB_RESEARCH",
                content = target.content,
                tags = "${target.knowledgeKey},APPROVED_KNOWLEDGE,v${target.version}",
                timestamp = System.currentTimeMillis()
            )
        )
        true
    }

    /**
     * Rolls back a knowledge key to a previous version.
     */
    suspend fun rollbackToVersion(knowledgeKey: String, targetVersion: Int): Boolean = withContext(Dispatchers.IO) {
        val versions = dao.getAllKnowledgeVersions().firstOrNull()?.filter { it.knowledgeKey == knowledgeKey } ?: return@withContext false
        val target = versions.find { it.version == targetVersion } ?: return@withContext false

        val currentActive = versions.find { it.status == KnowledgeStatus.ACTIVE }
        if (currentActive != null) {
            dao.updateKnowledgeVersion(currentActive.copy(status = KnowledgeStatus.ARCHIVED, updatedAt = System.currentTimeMillis()))
        }

        dao.updateKnowledgeVersion(target.copy(status = KnowledgeStatus.ACTIVE, updatedAt = System.currentTimeMillis()))
        true
    }
}
