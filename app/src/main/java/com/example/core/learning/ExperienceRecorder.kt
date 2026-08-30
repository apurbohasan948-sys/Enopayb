package com.example.core.learning

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.core.agent.StepExecutionRecord
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.ExperienceSource
import com.example.data.local.preference.JarvisPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TaskExperienceResult(
    val experienceId: Long,
    val evaluation: ExperienceEvaluation,
    val generatedSkillId: String? = null,
    val isPromotedToSkill: Boolean = false
)

/**
 * ExperienceRecorder.
 * Centralized, privacy-hardened recorder for task execution history.
 * Sanitizes sensitive data, records execution telemetry, evaluates reliability,
 * and triggers skill candidate generation.
 */
class ExperienceRecorder(
    private val dao: JarvisDao,
    private val preferences: JarvisPreferences,
    private val evaluator: ExperienceEvaluator = ExperienceEvaluator(),
    private val candidateGenerator: SkillCandidateGenerator = SkillCandidateGenerator(dao),
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "JARVIS_ExpRecorder"
    }

    /**
     * Records a completed or failed task run, scores it, and synthesizes skills if verified.
     */
    suspend fun recordTaskRun(
        goal: String,
        appPackage: String,
        initialScreenSummary: String,
        stepRecords: List<StepExecutionRecord>,
        isSuccess: Boolean,
        failedStrategy: String? = null,
        recoveryStrategy: String? = null,
        hadRecovery: Boolean = false,
        recoverySuccess: Boolean = false,
        hasUserCorrection: Boolean = false,
        durationMs: Long = 0L,
        modelUsed: String = "LOCAL_PLANNER",
        source: ExperienceSource = ExperienceSource.LOCAL_PLANNER
    ): TaskExperienceResult = withContext(Dispatchers.IO) {
        try {
            // 1. Privacy Sanitization
            val sanitizedGoal = sanitize(goal)
            val sanitizedScreen = sanitize(initialScreenSummary)
            val sanitizedFail = failedStrategy?.let { sanitize(it) }
            val sanitizedRecovery = recoveryStrategy?.let { sanitize(it) }

            // 2. Query App Version safely
            val appVersion = getAppVersionCode(appPackage)

            // 3. Serialize Actions & Targets
            val actionsArray = JSONArray()
            var totalTargetConfidence = 0f
            var stepConfidenceCount = 0

            stepRecords.forEach { record ->
                val sObj = JSONObject()
                sObj.put("stepNumber", record.step.stepNumber)
                sObj.put("description", sanitize(record.step.description))
                sObj.put("tool", record.step.toolIntent.toolName)

                val safeArgs = JSONObject()
                record.step.toolIntent.arguments.forEach { (k, v) ->
                    if (!isSensitiveKey(k)) {
                        safeArgs.put(k, sanitize(v))
                    }
                }
                sObj.put("arguments", safeArgs)
                sObj.put("verified", record.isVerified)
                sObj.put("success", record.result.success)
                sObj.put("output", sanitize(record.result.output))

                actionsArray.put(sObj)
                totalTargetConfidence += if (record.result.success) 0.95f else 0.40f
                stepConfidenceCount++
            }

            val avgConfidence = if (stepConfidenceCount > 0) totalTargetConfidence / stepConfidenceCount else if (isSuccess) 0.95f else 0.40f

            // 4. Objectively Evaluate Experience
            val evaluation = evaluator.evaluateExperience(
                isSuccess = isSuccess,
                stepRecords = stepRecords,
                hasUserCorrection = hasUserCorrection,
                hadRecovery = hadRecovery,
                recoverySuccess = recoverySuccess,
                averageTargetConfidence = avgConfidence,
                durationMs = durationMs
            )

            val verificationSummary = JSONObject().apply {
                put("score", evaluation.score)
                put("grade", evaluation.grade.name)
                put("modelUsed", modelUsed)
                put("appVersion", appVersion)
                put("verifiedSteps", stepRecords.count { it.isVerified })
                put("totalSteps", stepRecords.size)
                put("reasons", JSONArray(evaluation.reasons))
            }.toString()

            // 5. Store Experience into Room DB (if enabled in settings)
            var expId = -1L
            if (preferences.isStoreExperiencesEnabled) {
                val experience = ExperienceEntity(
                    goal = sanitizedGoal,
                    appPackage = appPackage.ifBlank { "system" },
                    initialScreenSummary = sanitizedScreen,
                    actionsTakenJson = actionsArray.toString(),
                    verificationSummary = verificationSummary,
                    isSuccess = isSuccess,
                    failedStrategy = sanitizedFail,
                    recoveryStrategy = sanitizedRecovery,
                    durationMs = durationMs,
                    confidence = evaluation.score / 100f,
                    source = source,
                    timestamp = System.currentTimeMillis()
                )
                expId = dao.insertExperience(experience)
                Log.d(TAG, "Task Experience recorded #$expId (Score: ${evaluation.score}/100, Grade: ${evaluation.grade})")
            }

            // 6. Generate Reusable Skill Candidate if eligible
            var generatedSkillName: String? = null
            var isPromoted = false

            if (preferences.isLearningEnabled && preferences.isAutoSkillCreationEnabled && evaluation.isEligibleForSkillCandidate) {
                val skillEntity = candidateGenerator.generateCandidateFromExperience(
                    goal = sanitizedGoal,
                    appPackage = appPackage,
                    appVersion = appVersion,
                    stepRecords = stepRecords,
                    qualityScore = evaluation.score / 100f
                )
                if (skillEntity != null) {
                    generatedSkillName = skillEntity.name
                    isPromoted = true
                    Log.i(TAG, "🌟 Automatically promoted experience to Skill Candidate: \"${skillEntity.name}\"")
                }
            }

            TaskExperienceResult(
                experienceId = expId,
                evaluation = evaluation,
                generatedSkillId = generatedSkillName,
                isPromotedToSkill = isPromoted
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record task run", e)
            TaskExperienceResult(
                experienceId = -1L,
                evaluation = ExperienceEvaluation(0, EvaluationGrade.UNRELIABLE, false, emptyMap(), listOf("Recording failed: ${e.message}"))
            )
        }
    }

    private fun getAppVersionCode(packageName: String): Long {
        if (context == null || packageName.isBlank()) return 0L
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0).versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower.contains("password") || lower.contains("pin") || lower.contains("secret") ||
               lower.contains("token") || lower.contains("bearer") || lower.contains("auth")
    }

    private fun sanitize(text: String): String {
        if (!preferences.isPrivacyFilteringEnabled) return text
        return text
            .replace(Regex("(?i)(password|passwd|pwd)(\\s*[:=]?\\s+)(\\S+)"), "password [REDACTED]")
            .replace(Regex("(?i)(pin|secret)(\\s*[:=]?\\s+)(\\S+)"), "$1 [REDACTED]")
            .replace(Regex("(?i)(token|bearer|api[_-]?key)(\\s*[:=]?\\s+)(\\S+)"), "$1 [REDACTED]")
            .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9_.-]+"), "bearer [REDACTED]")
            .replace(Regex("(?i)bearer_[a-zA-Z0-9_.-]+"), "[REDACTED]")
            .replace(Regex("(?i)pin\\s*[:=]?\\s*\\d+"), "pin [REDACTED]")
            .replace(Regex("(?i)my_pin_\\d+"), "[REDACTED]")
            .replace(Regex("(?i)pin_\\d+"), "[REDACTED]")
    }
}
