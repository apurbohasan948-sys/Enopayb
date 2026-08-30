package com.example.core.knowledge

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeSourceEntity
import com.example.data.local.entity.KnowledgeSourceType
import com.example.data.local.entity.SourceStatus
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class KnowledgeSourceManager(
    private val dao: JarvisDao
) {
    val allSources: Flow<List<KnowledgeSourceEntity>> = dao.getAllKnowledgeSources()

    fun calculateContentHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.trim().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun getDefaultTrustScore(sourceType: KnowledgeSourceType, sourceUrl: String? = null): Float {
        return when (sourceType) {
            KnowledgeSourceType.OFFICIAL_DOCUMENTATION -> 0.98f
            KnowledgeSourceType.VERIFIED_SKILL -> 0.95f
            KnowledgeSourceType.USER_PROVIDED -> 0.90f
            KnowledgeSourceType.EXPERIENCE -> 0.85f
            KnowledgeSourceType.TRUSTED_WEBSITE -> {
                if (sourceUrl?.contains("developer.android.com") == true ||
                    sourceUrl?.contains("github.com") == true ||
                    sourceUrl?.contains("kotlinlang.org") == true
                ) {
                    0.90f
                } else {
                    0.80f
                }
            }
            KnowledgeSourceType.GEMINI_TEACHING -> 0.75f // Gemini is not automatically true
        }
    }

    suspend fun registerOrGetSource(
        sourceType: KnowledgeSourceType,
        title: String,
        sourceUrl: String? = null,
        sampleContent: String = ""
    ): KnowledgeSourceEntity {
        val hash = calculateContentHash(sampleContent.ifBlank { title })
        val sourceId = "src_${sourceType.name.lowercase()}_${hash.take(8)}"

        val existing = dao.getKnowledgeSourceById(sourceId)
        if (existing != null) {
            return existing
        }

        val trustScore = getDefaultTrustScore(sourceType, sourceUrl)
        val newSource = KnowledgeSourceEntity(
            sourceId = sourceId,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            title = title,
            retrievedAt = System.currentTimeMillis(),
            contentHash = hash,
            trustScore = trustScore,
            lastVerified = System.currentTimeMillis(),
            status = SourceStatus.ACTIVE
        )
        dao.insertKnowledgeSource(newSource)
        return newSource
    }

    suspend fun updateSourceTrust(sourceId: String, newTrustScore: Float) {
        val source = dao.getKnowledgeSourceById(sourceId) ?: return
        val updated = source.copy(
            trustScore = newTrustScore.coerceIn(0.1f, 1.0f),
            lastVerified = System.currentTimeMillis()
        )
        dao.updateKnowledgeSource(updated)
    }

    suspend fun flagSource(sourceId: String) {
        val source = dao.getKnowledgeSourceById(sourceId) ?: return
        val updated = source.copy(
            status = SourceStatus.FLAGGED,
            trustScore = (source.trustScore * 0.5f).coerceAtLeast(0.1f)
        )
        dao.updateKnowledgeSource(updated)
    }
}
