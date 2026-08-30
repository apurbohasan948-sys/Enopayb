package com.example.core.knowledge

import com.example.core.health.NetworkStatus
import com.example.core.model.CloudUsagePolicy
import com.example.core.rag.LocalKnowledgeRetriever
import com.example.core.research.ResearchPolicy
import com.example.core.security.SecurityPolicyEngine
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeItemEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class TeachingLoopDecision {
    data class LocalSufficient(
        val skill: SkillEntity?,
        val knowledge: List<KnowledgeItemEntity>,
        val strategy: String
    ) : TeachingLoopDecision()

    data class CloudResearchNeeded(
        val reason: String,
        val suggestedSearchQuery: String
    ) : TeachingLoopDecision()

    data class OfflineBlocked(
        val reason: String
    ) : TeachingLoopDecision()
}

class KnowledgeTeachingLoop(
    private val dao: JarvisDao,
    private val retriever: LocalKnowledgeRetriever,
    private val ingestionEngine: KnowledgeIngestionEngine,
    private val researchPolicy: ResearchPolicy,
    private val cloudPolicy: CloudUsagePolicy,
    private val securityEngine: SecurityPolicyEngine
) {
    suspend fun evaluateTaskKnowledge(
        userGoal: String,
        currentAppPackage: String? = null
    ): TeachingLoopDecision = withContext(Dispatchers.IO) {
        // 1. Search local verified skills
        val allSkills = dao.getAllSkillsSync().filter { it.isEnabled }
        val matchingSkill = allSkills.find { skill ->
            val matchGoal = userGoal.lowercase().contains(skill.name.lowercase().replace("_", " "))
            val matchDesc = skill.description.lowercase().contains(userGoal.lowercase().take(20))
            matchGoal || matchDesc
        }

        // 2. Search local verified knowledge items
        val localKnowledge = dao.searchKnowledgeItems(userGoal.take(30))
            .filter { !it.isStale && !it.isUncertain && it.confidence >= 0.70f }

        if (matchingSkill != null && matchingSkill.confidence >= 0.80f) {
            return@withContext TeachingLoopDecision.LocalSufficient(
                skill = matchingSkill,
                knowledge = localKnowledge,
                strategy = "Exact verified skill found: ${matchingSkill.name}"
            )
        }

        if (localKnowledge.isNotEmpty() && localKnowledge.maxOf { it.confidence } >= 0.85f) {
            return@withContext TeachingLoopDecision.LocalSufficient(
                skill = matchingSkill,
                knowledge = localKnowledge,
                strategy = "Sufficient verified local knowledge available (${localKnowledge.size} items)"
            )
        }

        // 3. Insufficient local knowledge -> Check if research is permitted
        val canUseCloud = cloudPolicy.canMakeCloudCall()
        val (canResearch, researchReason) = researchPolicy.canPerformResearch(
            isUserTriggered = false,
            networkStatus = NetworkStatus.ONLINE,
            queriesUsedToday = cloudPolicy.getStats().webResearchRequests
        )

        if (!canUseCloud || !canResearch) {
            return@withContext TeachingLoopDecision.OfflineBlocked(
                reason = "Local brain insufficient, but research blocked: $researchReason (Cloud calls left: ${cloudPolicy.getStats().cloudCallsRemaining})"
            )
        }

        TeachingLoopDecision.CloudResearchNeeded(
            reason = "Local knowledge confidence too low; initiating research loop.",
            suggestedSearchQuery = userGoal
        )
    }

    suspend fun recordTeachingSuccess(
        knowledgeId: Long?,
        skillName: String?
    ) = withContext(Dispatchers.IO) {
        if (knowledgeId != null) {
            dao.incrementKnowledgeUsage(knowledgeId)
        }
        if (!skillName.isNullOrBlank()) {
            dao.recordSkillSuccess(skillName)
        }
    }
}
