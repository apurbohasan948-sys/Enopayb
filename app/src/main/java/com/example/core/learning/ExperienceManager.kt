package com.example.core.learning

import android.util.Log
import com.example.core.agent.PlanStep
import com.example.core.agent.StepExecutionRecord
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.ExperienceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ExperienceManager.
 * Captures, verifies, and indexes verified task experiences.
 * Differentiates between successful executions and failed strategies.
 * Identifies repeated successful patterns for Skill synthesis.
 */
class ExperienceManager(
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_ExperienceMgr"
    }

    val allExperiences: Flow<List<ExperienceEntity>> = dao.getAllExperiences()
    val successfulExperiences: Flow<List<ExperienceEntity>> = dao.getSuccessfulExperiences()
    val failedExperiences: Flow<List<ExperienceEntity>> = dao.getFailedExperiences()

    /**
     * Records a completed task execution as an experience.
     */
    suspend fun recordTaskExperience(
        goal: String,
        appPackage: String,
        initialScreenSummary: String,
        stepRecords: List<StepExecutionRecord>,
        isSuccess: Boolean,
        failedStrategy: String? = null,
        recoveryStrategy: String? = null,
        durationMs: Long = 0L,
        source: ExperienceSource = ExperienceSource.LOCAL_PLANNER
    ): Long = withContext(Dispatchers.IO) {
        try {
            val actionsArray = JSONArray()
            stepRecords.forEach { record ->
                val stepObj = JSONObject()
                stepObj.put("stepNumber", record.step.stepNumber)
                stepObj.put("description", sanitize(record.step.description))
                stepObj.put("tool", record.step.toolIntent.toolName)
                
                // Sanitize arguments to eliminate sensitive fields
                val safeArgs = JSONObject()
                record.step.toolIntent.arguments.forEach { (k, v) ->
                    if (!k.contains("password", ignoreCase = true) && !k.contains("pin", ignoreCase = true)) {
                        safeArgs.put(k, sanitize(v))
                    }
                }
                stepObj.put("arguments", safeArgs)
                stepObj.put("verified", record.isVerified)
                stepObj.put("success", record.result.success)
                actionsArray.put(stepObj)
            }

            val verificationSummary = if (isSuccess) {
                "All ${stepRecords.size} steps executed and state transition verified."
            } else {
                "Task halted: $failedStrategy"
            }

            val experience = ExperienceEntity(
                goal = sanitize(goal),
                appPackage = appPackage.ifBlank { "unknown" },
                initialScreenSummary = sanitize(initialScreenSummary),
                actionsTakenJson = actionsArray.toString(),
                verificationSummary = verificationSummary,
                isSuccess = isSuccess,
                failedStrategy = failedStrategy?.let { sanitize(it) },
                recoveryStrategy = recoveryStrategy?.let { sanitize(it) },
                durationMs = durationMs,
                confidence = if (isSuccess) 0.95f else 0.40f,
                source = source,
                timestamp = System.currentTimeMillis()
            )

            val id = dao.insertExperience(experience)
            Log.d(TAG, "Task Experience recorded (ID: $id, Success: $isSuccess, Goal: '$goal')")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record experience", e)
            -1L
        }
    }

    /**
     * Checks if this goal has repeated verified successes eligible for automated skill generation.
     */
    suspend fun getSuccessfulExperiencesForPackage(pkg: String): List<ExperienceEntity> = withContext(Dispatchers.IO) {
        dao.getSuccessfulExperiencesForPackage(pkg)
    }

    suspend fun deleteExperience(experience: ExperienceEntity) = withContext(Dispatchers.IO) {
        dao.deleteExperience(experience)
    }

    suspend fun clearAllExperiences() = withContext(Dispatchers.IO) {
        dao.clearAllExperiences()
    }

    private fun sanitize(text: String): String {
        return text.replace(Regex("(?i)password\\s*[:=]\\s*\\S+"), "password=[FILTERED]")
            .replace(Regex("(?i)pin\\s*[:=]\\s*\\d+"), "pin=[FILTERED]")
            .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9_.-]+"), "bearer=[FILTERED]")
    }
}
