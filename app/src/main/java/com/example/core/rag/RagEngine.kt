package com.example.core.rag

import com.example.data.local.entity.KnowledgeChunkEntity
import kotlin.math.sqrt

object RagEngine {

    /**
     * Chunks raw text or document content into digestible snippets for local RAG.
     */
    fun chunkDocument(
        title: String,
        sourceDoc: String,
        rawText: String,
        tags: String = "general",
        chunkSizeWords: Int = 80
    ): List<KnowledgeChunkEntity> {
        val words = rawText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val chunks = mutableListOf<KnowledgeChunkEntity>()

        var index = 0
        var chunkIndex = 1
        while (index < words.size) {
            val end = (index + chunkSizeWords).coerceAtMost(words.size)
            val chunkWords = words.subList(index, end)
            val chunkContent = chunkWords.joinToString(" ")
            
            chunks.add(
                KnowledgeChunkEntity(
                    title = "$title [Part $chunkIndex]",
                    sourceDocument = sourceDoc,
                    content = chunkContent,
                    tags = tags,
                    embeddingPreview = generateSimulatedVector(chunkContent)
                )
            )
            chunkIndex++
            index += (chunkSizeWords - 15).coerceAtLeast(1) // Overlap 15 words
        }

        return chunks
    }

    /**
     * Ranks stored knowledge chunks based on keyword frequency and Cosine TF similarity.
     */
    fun findRelevantChunks(
        query: String,
        allChunks: List<KnowledgeChunkEntity>,
        topK: Int = 3,
        minScoreThreshold: Float = 0.15f
    ): List<Pair<KnowledgeChunkEntity, Float>> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scored = allChunks.map { chunk ->
            val chunkTokens = tokenize("${chunk.title} ${chunk.content} ${chunk.tags}")
            val score = computeCosineSimilarity(queryTokens, chunkTokens)
            chunk to score
        }

        return scored
            .filter { it.second >= minScoreThreshold }
            .sortedByDescending { it.second }
            .take(topK)
    }

    private fun tokenize(text: String): Map<String, Int> {
        val words = text.lowercase()
            .replace("[^a-zA-Z0-9\\u0980-\\u09FF]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }

        val freq = mutableMapOf<String, Int>()
        for (w in words) {
            freq[w] = (freq[w] ?: 0) + 1
        }
        return freq
    }

    private fun computeCosineSimilarity(v1: Map<String, Int>, v2: Map<String, Int>): Float {
        var dot = 0.0
        for ((k, count1) in v1) {
            val count2 = v2[k] ?: 0
            dot += (count1 * count2)
        }
        val norm1 = sqrt(v1.values.sumOf { (it * it).toDouble() })
        val norm2 = sqrt(v2.values.sumOf { (it * it).toDouble() })
        if (norm1 == 0.0 || norm2 == 0.0) return 0.0f
        return (dot / (norm1 * norm2)).toFloat()
    }

    private fun generateSimulatedVector(text: String): String {
        val hash = text.hashCode()
        val v1 = String.format("%.2f", (hash % 100) / 100.0)
        val v2 = String.format("%.2f", ((hash shr 3) % 100) / 100.0)
        val v3 = String.format("%.2f", ((hash shr 7) % 100) / 100.0)
        return "[$v1, $v2, $v3, ... 384-dim]"
    }
}
