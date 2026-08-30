package com.example.core.rag

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AppKnowledgeEntity
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.KnowledgeItemEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-Tier Retrieval Priority for Phase 14:
 * 1. Verified Skill
 * 2. Local Knowledge Item
 * 3. Past Experience (Success)
 * 4. Recovery Strategy (Failure lessons)
 * 5. Local RAG Chunks (Cosine similarity)
 * 6. Cloud Model fallback
 */
data class BrainRetrievalResult(
    val verifiedSkill: SkillEntity? = null,
    val verifiedKnowledge: List<KnowledgeItemEntity> = emptyList(),
    val matchingExperience: ExperienceEntity? = null,
    val appRecoveryStrategy: String? = null,
    val relevantChunks: List<KnowledgeChunkEntity> = emptyList(),
    val confidenceScore: Float = 0.0f,
    val retrievalSource: String = "LOCAL_BRAIN"
)

class LocalKnowledgeRetriever(
    private val dao: JarvisDao
) {
    suspend fun retrieveForTask(
        goal: String,
        currentAppPackage: String? = null,
        minConfidence: Float = 0.60f
    ): BrainRetrievalResult = withContext(Dispatchers.IO) {
        val cleanGoal = goal.trim().lowercase()

        // 1. Verified Local Skill Check
        val allSkills = dao.getAllSkillsSync().filter { it.isEnabled }
        val matchingSkill = allSkills.find { skill ->
            cleanGoal.contains(skill.name.lowercase().replace("_", " ")) ||
            skill.description.lowercase().contains(cleanGoal.take(20))
        }

        // 2. Verified Local Knowledge Item Check
        val matchingKnowledge = dao.searchKnowledgeItems(cleanGoal.take(30))
            .filter { !it.isStale && it.confidence >= minConfidence }

        // 3. Past Experience Check (Successful runs)
        val pastExperiences = dao.getAllExperiencesSync()
        val successfulExp = pastExperiences.find { exp ->
            exp.isSuccess && (
                exp.goal.lowercase().contains(cleanGoal.take(20)) ||
                (currentAppPackage != null && exp.appPackage == currentAppPackage)
            )
        }

        // 4. Recovery Strategy from App Knowledge
        val appKnowledge = currentAppPackage?.let { dao.getAppKnowledgeByPackage(it) }
        val recoveryStrategy = appKnowledge?.recoveryStrategiesJson?.takeIf { it.isNotBlank() && it != "[]" }

        // 5. Local RAG Vector Chunks
        val allChunks = dao.getAllKnowledgeChunksSync()
        val vectorMatches = if (cleanGoal.isNotBlank()) {
            RagEngine.findRelevantChunks(cleanGoal, allChunks, topK = 3, minScoreThreshold = 0.20f)
                .map { it.first }
        } else emptyList()

        // Calculate Overall Retrieval Confidence
        val confidence = when {
            matchingSkill != null -> matchingSkill.confidence
            matchingKnowledge.isNotEmpty() -> matchingKnowledge.maxOf { it.confidence }
            successfulExp != null -> successfulExp.confidence
            vectorMatches.isNotEmpty() -> 0.70f
            else -> 0.0f
        }

        BrainRetrievalResult(
            verifiedSkill = matchingSkill,
            verifiedKnowledge = matchingKnowledge,
            matchingExperience = successfulExp,
            appRecoveryStrategy = recoveryStrategy,
            relevantChunks = vectorMatches,
            confidenceScore = confidence,
            retrievalSource = when {
                matchingSkill != null -> "VERIFIED_SKILL"
                matchingKnowledge.isNotEmpty() -> "VERIFIED_KNOWLEDGE"
                successfulExp != null -> "EXPERIENCE_REPLAY"
                vectorMatches.isNotEmpty() -> "RAG_VECTOR_CHUNKS"
                else -> "INSUFFICIENT_LOCAL"
            }
        )
    }

    suspend fun queryChunks(query: String, topK: Int = 3): List<Pair<KnowledgeChunkEntity, Float>> = withContext(Dispatchers.IO) {
        val allChunks = dao.getAllKnowledgeChunksSync()
        RagEngine.findRelevantChunks(query, allChunks, topK = topK)
    }
}
