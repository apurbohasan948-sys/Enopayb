package com.example.core.rag

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LocalKnowledgeRetriever.
 * High-performance on-device RAG retrieval pipeline:
 * Query -> Keyword Index -> Semantic Ranking (TF-Cosine) -> Top-K Filtered Chunks.
 */
class LocalKnowledgeRetriever(private val dao: JarvisDao) {

    suspend fun retrieveRelevantKnowledge(
        query: String,
        topK: Int = 3,
        minScoreThreshold: Float = 0.18f
    ): List<KnowledgeChunkEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // 1. Initial fast keyword retrieval from Room DB
        val candidateChunks = try {
            val dbChunks = dao.searchKnowledge(trimmed)
            if (dbChunks.isNotEmpty()) {
                dbChunks
            } else {
                dao.getAllKnowledgeChunksSync()
            }
        } catch (e: Exception) {
            emptyList()
        }

        if (candidateChunks.isEmpty()) return@withContext emptyList()

        // 2. Semantic Ranking and scoring
        val rankedPairs = RagEngine.findRelevantChunks(
            query = trimmed,
            allChunks = candidateChunks,
            topK = topK,
            minScoreThreshold = minScoreThreshold
        )

        rankedPairs.map { it.first }
    }
}
