package com.example.core.knowledge

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.KnowledgeItemEntity
import com.example.data.local.entity.KnowledgeSourceEntity
import com.example.data.local.entity.KnowledgeSourceType
import com.example.data.local.entity.KnowledgeType
import com.example.data.local.entity.ValidationStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IngestionCandidate(
    val title: String,
    val content: String,
    val summary: String? = null,
    val sourceType: KnowledgeSourceType = KnowledgeSourceType.USER_PROVIDED,
    val sourceUrl: String? = null,
    val tags: String = "",
    val appPackage: String? = null,
    val appVersion: String? = null,
    val osVersion: String? = "Android 15",
    val explicitType: KnowledgeType? = null
)

data class IngestionResult(
    val success: Boolean,
    val item: KnowledgeItemEntity?,
    val isDuplicate: Boolean,
    val stage: ValidationStage,
    val confidence: Float,
    val message: String
)

class KnowledgeIngestionEngine(
    private val dao: JarvisDao,
    private val sourceManager: KnowledgeSourceManager,
    private val validator: KnowledgeValidator
) {
    suspend fun ingest(candidate: IngestionCandidate): IngestionResult = withContext(Dispatchers.IO) {
        val classifiedType = candidate.explicitType ?: classifyKnowledge(candidate.title, candidate.content, candidate.appPackage)
        val source = sourceManager.registerOrGetSource(
            sourceType = candidate.sourceType,
            title = candidate.title,
            sourceUrl = candidate.sourceUrl,
            sampleContent = candidate.content
        )

        val existingItems = dao.getAllKnowledgeItemsSync()
        val validation = validator.validateCandidate(
            title = candidate.title,
            rawContent = candidate.content,
            source = source,
            type = classifiedType,
            existingKnowledge = existingItems
        )

        if (!validation.isValid) {
            return@withContext IngestionResult(
                success = false,
                item = null,
                isDuplicate = false,
                stage = validation.stage,
                confidence = validation.confidence,
                message = "Validation rejected: ${validation.reason}"
            )
        }

        val contentHash = sourceManager.calculateContentHash(validation.normalizedContent)
        val knowledgeKey = candidate.title.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").take(48)

        // Duplicate / Existing Check
        val existingByHash = dao.getKnowledgeItemByHash(contentHash)
        val existingByKey = dao.getKnowledgeItemByKey(knowledgeKey)
        val existing = existingByHash ?: existingByKey

        if (existing != null) {
            // Reinforce or update
            val updated = existing.copy(
                sourceCount = existing.sourceCount + 1,
                confidence = (existing.confidence * 0.7f + validation.confidence * 0.3f).coerceIn(0.1f, 1.0f),
                trustScore = (existing.trustScore * 0.7f + source.trustScore * 0.3f).coerceIn(0.1f, 1.0f),
                lastVerified = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isStale = false,
                summary = candidate.summary ?: existing.summary
            )
            dao.updateKnowledgeItem(updated)

            return@withContext IngestionResult(
                success = true,
                item = updated,
                isDuplicate = true,
                stage = updated.validationStage,
                confidence = updated.confidence,
                message = "Knowledge reinforced from source (${source.title})"
            )
        }

        val newItem = KnowledgeItemEntity(
            knowledgeKey = knowledgeKey,
            title = candidate.title.trim(),
            content = validation.normalizedContent,
            summary = candidate.summary ?: validation.normalizedContent.take(120),
            knowledgeType = classifiedType,
            validationStage = validation.stage,
            confidence = validation.confidence,
            trustScore = source.trustScore,
            sourceCount = 1,
            sourceId = source.sourceId,
            sourceUrl = candidate.sourceUrl,
            contentHash = contentHash,
            tags = candidate.tags.ifBlank { classifiedType.name.lowercase() },
            appPackage = candidate.appPackage,
            appVersion = candidate.appVersion,
            osVersion = candidate.osVersion,
            lastVerified = System.currentTimeMillis(),
            isStale = false,
            isUncertain = validation.isUncertain
        )

        val id = dao.insertKnowledgeItem(newItem)
        val savedItem = newItem.copy(id = id)

        // Synchronize into KnowledgeChunk for local RAG vector engine
        dao.insertKnowledgeChunk(
            KnowledgeChunkEntity(
                title = savedItem.title,
                sourceDocument = savedItem.sourceUrl ?: "knowledge_item_${savedItem.id}.md",
                content = savedItem.content,
                tags = savedItem.tags
            )
        )

        return@withContext IngestionResult(
            success = true,
            item = savedItem,
            isDuplicate = false,
            stage = savedItem.validationStage,
            confidence = savedItem.confidence,
            message = "New knowledge ingested successfully"
        )
    }

    fun classifyKnowledge(title: String, content: String, appPackage: String?): KnowledgeType {
        val combined = "$title $content ${appPackage ?: ""}".lowercase()
        return when {
            combined.contains("hardware") || combined.contains("snapdragon") || combined.contains("cpu") || combined.contains("ram") || combined.contains("redmi") -> KnowledgeType.DEVICE_KNOWLEDGE
            combined.contains("permission") || combined.contains("security") || combined.contains("foreground service") || combined.contains("android 15") -> KnowledgeType.SYSTEM_KNOWLEDGE
            combined.contains("how to") || combined.contains("procedure") || combined.contains("steps to") || combined.contains("navigation") -> KnowledgeType.PROCEDURE
            combined.contains("whatsapp") || combined.contains("youtube") || combined.contains("settings") || appPackage != null -> KnowledgeType.APP_BEHAVIOR
            combined.contains("recovery") || combined.contains("failed strategy") || combined.contains("workaround") -> KnowledgeType.RECOVERY_PATTERN
            combined.contains("preference") || combined.contains("theme") || combined.contains("favorite") -> KnowledgeType.USER_PREFERENCE
            combined.contains("skill") || combined.contains("tool call") -> KnowledgeType.SKILL_GUIDANCE
            combined.contains("api") || combined.contains("sdk") || combined.contains("protocol") -> KnowledgeType.TECHNICAL_KNOWLEDGE
            else -> KnowledgeType.FACT
        }
    }
}
