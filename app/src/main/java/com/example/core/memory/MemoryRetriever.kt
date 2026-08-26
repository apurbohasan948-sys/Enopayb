package com.example.core.memory

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.UserCorrectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RetrievedMemoryContext(
    val relevantMemories: List<MemoryEntity>,
    val relevantSkills: List<SkillEntity>,
    val pastExperiences: List<ExperienceEntity>,
    val activeCorrections: List<UserCorrectionEntity>,
    val formattedPromptContext: String
)

/**
 * MemoryRetriever.
 * Scans long-term memory, skills, experiences, and corrections relevant to a user task.
 * Feeds structured semantic context to planners while updating usage metrics.
 */
class MemoryRetriever(
    private val dao: JarvisDao
) {

    suspend fun retrieveContextForGoal(
        goal: String,
        appPackage: String? = null,
        screenContext: String? = null
    ): RetrievedMemoryContext = withContext(Dispatchers.IO) {
        val lowerGoal = goal.lowercase().trim()
        val tokens = lowerGoal.split("\\s+".toRegex()).filter { it.length > 2 }

        // 1. Retrieve matching memories
        val memoryMatches = mutableSetOf<MemoryEntity>()
        for (token in tokens) {
            val matches = dao.searchMemories(token)
            memoryMatches.addAll(matches)
        }
        val topMemories = if (memoryMatches.isEmpty()) {
            dao.getTopMemories(5)
        } else {
            memoryMatches.sortedByDescending { it.importance * it.confidence }.take(8)
        }

        // Increment usage count for retrieved memories
        for (mem in topMemories) {
            dao.incrementMemoryUsage(mem.id)
        }

        // 2. Retrieve relevant skills
        val skillMatches = mutableSetOf<SkillEntity>()
        for (token in tokens) {
            val matches = dao.searchSkills(token)
            skillMatches.addAll(matches.filter { it.isEnabled })
        }
        val relevantSkills = skillMatches.sortedByDescending { it.confidence * it.successRate }.take(5)

        // 3. Retrieve past experiences
        val pastExperiences = dao.searchExperiences(goal, limit = 5)

        // 4. Retrieve user corrections for active app/screen
        val activeCorrections = if (!appPackage.isNullOrBlank() || !screenContext.isNullOrBlank()) {
            dao.getCorrectionsForContext(appPackage ?: "", screenContext ?: "")
        } else {
            emptyList()
        }

        // 5. Format prompt context
        val contextBuilder = StringBuilder()
        if (topMemories.isNotEmpty()) {
            contextBuilder.appendLine("[USER MEMORIES & PREFERENCES]")
            topMemories.forEach { m ->
                contextBuilder.appendLine("• ${m.key}: ${m.value} (Category: ${m.category.name})")
            }
        }

        if (activeCorrections.isNotEmpty()) {
            contextBuilder.appendLine("\n[ACTIVE USER CORRECTIONS]")
            activeCorrections.forEach { c ->
                contextBuilder.appendLine("• On '${c.appPackage}': User corrected '${c.previousAssumption}' -> '${c.actualTarget}' (${c.userCorrection})")
            }
        }

        if (relevantSkills.isNotEmpty()) {
            contextBuilder.appendLine("\n[AVAILABLE LEARNED SKILLS]")
            relevantSkills.forEach { s ->
                contextBuilder.appendLine("• ${s.name} (v${s.version}, Success Rate: ${(s.successRate * 100).toInt()}%): ${s.description}")
            }
        }

        if (pastExperiences.isNotEmpty()) {
            contextBuilder.appendLine("\n[PAST TASK EXPERIENCES]")
            pastExperiences.forEach { exp ->
                val status = if (exp.isSuccess) "SUCCESS" else "FAILED"
                contextBuilder.appendLine("• Goal: \"${exp.goal}\" [$status] in ${exp.appPackage}")
                if (!exp.isSuccess && !exp.failedStrategy.isNullOrBlank()) {
                    contextBuilder.appendLine("  Failed approach: ${exp.failedStrategy}")
                }
                if (!exp.recoveryStrategy.isNullOrBlank()) {
                    contextBuilder.appendLine("  Successful recovery: ${exp.recoveryStrategy}")
                }
            }
        }

        RetrievedMemoryContext(
            relevantMemories = topMemories,
            relevantSkills = relevantSkills,
            pastExperiences = pastExperiences,
            activeCorrections = activeCorrections,
            formattedPromptContext = contextBuilder.toString().trim()
        )
    }
}
