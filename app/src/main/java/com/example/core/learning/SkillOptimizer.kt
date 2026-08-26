package com.example.core.learning

import android.util.Log
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SkillOptimizationReport(
    val totalSkillsAnalyzed: Int = 0,
    val prioritizedSkillsCount: Int = 0,
    val dormantSkillsCount: Int = 0,
    val durationMs: Long = 0
)

/**
 * SkillOptimizer.
 * Analyzes skill telemetry (usage counts, success rates, latency).
 * Boosts execution priority for reliable skills and marks stale/underperforming skills
 * as DORMANT without deleting them destructively.
 */
class SkillOptimizer(private val dao: JarvisDao) {

    companion object {
        private const val TAG = "JARVIS_SkillOptimizer"
    }

    suspend fun optimizeSkills(): SkillOptimizationReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var prioritized = 0
        var dormant = 0

        try {
            val allSkills = dao.getAllSkillsSync()

            for (skill in allSkills) {
                // If high success and tested multiple times -> ensure high confidence
                if (skill.successCount >= 3 && skill.successRate >= 0.80f) {
                    if (skill.confidence < 0.95f) {
                        dao.updateSkill(skill.copy(confidence = 0.95f, isEnabled = true))
                        prioritized++
                    }
                } else if (skill.failureCount >= 3 && skill.successRate < 0.40f && skill.isLearnedFromExperience) {
                    // Underperforming learned skill -> mark disabled/dormant
                    if (skill.isEnabled) {
                        dao.updateSkill(skill.copy(isEnabled = false, confidence = 0.40f))
                        dormant++
                    }
                }
            }

            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Skill optimization complete: $prioritized prioritized, $dormant marked dormant.")
            SkillOptimizationReport(
                totalSkillsAnalyzed = allSkills.size,
                prioritizedSkillsCount = prioritized,
                dormantSkillsCount = dormant,
                durationMs = duration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during skill optimization", e)
            SkillOptimizationReport(durationMs = System.currentTimeMillis() - startTime)
        }
    }
}
