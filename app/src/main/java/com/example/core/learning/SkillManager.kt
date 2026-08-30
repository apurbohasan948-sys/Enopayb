package com.example.core.learning

import android.util.Log
import com.example.core.agent.PlanStep
import com.example.core.agent.TaskPlan
import com.example.core.model.ToolIntent
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import com.example.data.local.entity.SkillSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SkillManager.
 * Manages reusable, versioned, semantic skills for JARVIS.
 * Lifecycle: CANDIDATE -> VALIDATING -> VERIFIED -> ACTIVE.
 * Retires failing skills (DEPRECATED) and flags UI changes (STALE).
 */
class SkillManager(
    private val dao: JarvisDao,
    private val candidateGenerator: SkillCandidateGenerator = SkillCandidateGenerator(dao)
) {
    companion object {
        private const val TAG = "JARVIS_SkillManager"
        const val SUCCESS_RATE_THRESHOLD = 0.70f
        const val MAX_CONSECUTIVE_FAILURES = 3
    }

    val allSkills: Flow<List<SkillEntity>> = dao.getAllSkills()
    val enabledSkills: Flow<List<SkillEntity>> = dao.getEnabledSkills()
    val learnedSkills: Flow<List<SkillEntity>> = dao.getLearnedSkills()

    /**
     * Finds a matching reusable Skill and binds runtime variables into an executable TaskPlan.
     */
    suspend fun findMatchingSkill(goal: String, currentApp: String? = null): Pair<SkillEntity, TaskPlan>? = withContext(Dispatchers.IO) {
        try {
            val (intent, slots) = candidateGenerator.analyzeIntentAndSlots(goal, currentApp ?: "")
            val allSkillsList = dao.getAllSkillsSync().filter { it.isEnabled }

            for (skill in allSkillsList) {
                if (!skill.isEnabled) continue

                // Check Generalized Model
                val genModel = GeneralizedSkillModel.fromJson(skill.procedure)
                if (genModel != null) {
                    if (genModel.status == SkillLifecycleStatus.DEPRECATED || genModel.status == SkillLifecycleStatus.DISABLED) {
                        continue
                    }

                    val normIntent = intent.lowercase().replace("_", "")
                    val normArchetype = genModel.intentArchetype.lowercase().replace("_", "")
                    val normSkillName = skill.name.lowercase().replace("_", "")
                    val normGoal = goal.lowercase().replace("_", " ")

                    val matchesIntent = genModel.intentArchetype.equals(intent, ignoreCase = true) ||
                                          normArchetype.contains(normIntent) ||
                                          normIntent.contains(normArchetype) ||
                                          normSkillName.contains(normIntent) ||
                                          normGoal.contains(skill.name.replace("_", " ")) ||
                                          skill.name.replace("_", " ").split(" ").all { word -> word.length < 3 || normGoal.contains(word) }
                    val matchesApp = currentApp.isNullOrBlank() || genModel.targetAppPackage.isBlank() ||
                                     genModel.targetAppPackage.contains(currentApp, ignoreCase = true) ||
                                     currentApp.contains(genModel.targetAppPackage, ignoreCase = true)

                    if (matchesIntent && matchesApp) {
                        val boundPlan = candidateGenerator.bindSkillParameters(skill, goal, slots)
                        if (boundPlan != null && boundPlan.steps.isNotEmpty()) {
                            Log.d(TAG, "🎯 Matched Generalized Skill: ${skill.name} (Status: ${genModel.status}) with ${slots.size} slots")
                            return@withContext Pair(skill, boundPlan)
                        }
                    }
                } else {
                    // Direct name or keyword match for Builtin/Legacy skills
                    val lowerGoal = goal.lowercase().trim()
                    val lowerName = skill.name.lowercase().trim()
                    if (lowerName == lowerGoal || lowerGoal.contains(lowerName) || (lowerName.contains("youtube") && lowerGoal.contains("youtube"))) {
                        val plan = candidateGenerator.bindSkillParameters(skill, goal, slots) ?: convertSkillToPlan(skill, goal)
                        if (plan != null && plan.steps.isNotEmpty()) {
                            return@withContext Pair(skill, plan)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error in findMatchingSkill", e)
            null
        }
    }

    /**
     * Extracts and synthesizes a new reusable skill from a verified experience.
     */
    suspend fun synthesizeSkillFromExperience(experience: ExperienceEntity): SkillEntity? = withContext(Dispatchers.IO) {
        if (!experience.isSuccess) return@withContext null

        try {
            val actionsArray = JSONArray(experience.actionsTakenJson)
            if (actionsArray.length() == 0) return@withContext null

            val skillName = generateSemanticSkillName(experience.goal, experience.appPackage)
            val existing = dao.getSkillByName(skillName)

            val procedureObj = JSONObject().apply {
                put("goalArchetype", experience.goal)
                put("appPackage", experience.appPackage)
                put("steps", actionsArray)
                put("status", SkillLifecycleStatus.ACTIVE.name)
            }

            val skill = SkillEntity(
                id = existing?.id ?: 0L,
                name = skillName,
                description = "Automated skill synthesized for: ${experience.goal} in ${experience.appPackage}",
                requiredPermissions = "AccessibilityService",
                inputSchema = "{\"query\": \"string\"}",
                outputSchema = "{\"status\": \"string\", \"verified\": \"boolean\"}",
                riskLevel = SkillRiskLevel.LOW,
                procedure = procedureObj.toString(2),
                verificationMethod = "Multimodal State & Screen Transition Verification",
                version = if (existing != null) incrementVersion(existing.version) else "1.0.0",
                isEnabled = true,
                executionCount = (existing?.executionCount ?: 0) + 1,
                successCount = (existing?.successCount ?: 0) + 1,
                failureCount = existing?.failureCount ?: 0,
                successRate = 1.0f,
                confidence = 0.96f,
                lastExecutedAt = System.currentTimeMillis(),
                lastSuccessAt = System.currentTimeMillis(),
                isLearnedFromExperience = true,
                source = SkillSource.EXPERIENCE_EXTRACTED,
                previousVersionProcedure = existing?.procedure
            )

            dao.insertSkill(skill)
            Log.d(TAG, "Synthesized new Skill: $skillName (v${skill.version})")
            skill
        } catch (e: Exception) {
            Log.e(TAG, "Skill synthesis failed", e)
            null
        }
    }

    suspend fun synthesizeSkillFromExperience(
        goal: String,
        plan: TaskPlan,
        initialScreenContext: String = ""
    ): SkillEntity? = withContext(Dispatchers.IO) {
        val actionsArray = JSONArray()
        plan.steps.forEach { step ->
            val sObj = JSONObject().apply {
                put("stepNumber", step.stepNumber)
                put("description", step.description)
                put("tool", step.toolIntent.toolName)
                put("arguments", JSONObject(step.toolIntent.arguments))
                put("expectedOutcome", step.expectedOutcome)
            }
            actionsArray.put(sObj)
        }
        val exp = ExperienceEntity(
            goal = goal,
            appPackage = "com.android.system",
            initialScreenSummary = initialScreenContext,
            actionsTakenJson = actionsArray.toString(),
            verificationSummary = "Automated execution verified",
            isSuccess = true
        )
        synthesizeSkillFromExperience(exp)
    }

    /**
     * Synthesizes a skill directly from a structured Gemini Teacher output plan.
     */
    suspend fun synthesizeSkillFromTeacher(
        skillName: String,
        description: String,
        steps: List<PlanStep>,
        appPackage: String = ""
    ): SkillEntity = withContext(Dispatchers.IO) {
        val existing = dao.getSkillByName(skillName)
        val stepsArray = JSONArray()

        steps.forEach { step ->
            val sObj = JSONObject().apply {
                put("stepNumber", step.stepNumber)
                put("description", step.description)
                put("tool", step.toolIntent.toolName)
                put("arguments", JSONObject(step.toolIntent.arguments))
                put("expectedOutcome", step.expectedOutcome)
            }
            stepsArray.put(sObj)
        }

        val procedureObj = JSONObject().apply {
            put("steps", stepsArray)
            put("source", "Gemini Teacher Supervisor")
            put("appPackage", appPackage)
            put("status", SkillLifecycleStatus.ACTIVE.name)
        }

        val skill = SkillEntity(
            id = existing?.id ?: 0L,
            name = skillName,
            description = description,
            requiredPermissions = "AccessibilityService",
            inputSchema = "{\"params\": \"object\"}",
            outputSchema = "{\"status\": \"string\"}",
            riskLevel = SkillRiskLevel.LOW,
            procedure = procedureObj.toString(2),
            verificationMethod = "Screen Transition Verification",
            version = if (existing != null) incrementVersion(existing.version) else "1.0.0",
            isEnabled = true,
            executionCount = (existing?.executionCount ?: 0) + 1,
            successCount = (existing?.successCount ?: 0) + 1,
            failureCount = existing?.failureCount ?: 0,
            successRate = 1.0f,
            confidence = 0.98f,
            lastExecutedAt = System.currentTimeMillis(),
            lastSuccessAt = System.currentTimeMillis(),
            isLearnedFromExperience = true,
            source = SkillSource.TEACHER,
            previousVersionProcedure = existing?.procedure
        )

        dao.insertSkill(skill)
        skill
    }

    /**
     * Updates Skill execution stats and evaluates lifecycle transitions (CANDIDATE -> VALIDATING -> VERIFIED -> ACTIVE or DEPRECATED).
     */
    suspend fun recordExecution(skillName: String, success: Boolean) = withContext(Dispatchers.IO) {
        val skill = dao.getSkillByName(skillName) ?: return@withContext
        val newExecCount = skill.executionCount + 1
        val newSuccessCount = if (success) skill.successCount + 1 else skill.successCount
        val newFailureCount = if (!success) skill.failureCount + 1 else skill.failureCount
        val newRate = newSuccessCount.toFloat() / newExecCount.coerceAtLeast(1)

        val genModel = GeneralizedSkillModel.fromJson(skill.procedure)
        var newStatus = genModel?.status ?: SkillLifecycleStatus.ACTIVE
        val consecutiveFailures = if (success) 0 else (genModel?.consecutiveFailures ?: 0) + 1

        // Lifecycle Progression
        if (success) {
            newStatus = when (newStatus) {
                SkillLifecycleStatus.CANDIDATE -> SkillLifecycleStatus.VALIDATING
                SkillLifecycleStatus.VALIDATING -> if (newSuccessCount >= 3) SkillLifecycleStatus.VERIFIED else SkillLifecycleStatus.VALIDATING
                SkillLifecycleStatus.VERIFIED -> if (newSuccessCount >= 5) SkillLifecycleStatus.ACTIVE else SkillLifecycleStatus.VERIFIED
                SkillLifecycleStatus.STALE -> SkillLifecycleStatus.VERIFIED
                else -> newStatus
            }
        } else {
            // Deprecation check
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES || (newExecCount >= 3 && newRate < SUCCESS_RATE_THRESHOLD)) {
                newStatus = SkillLifecycleStatus.DEPRECATED
                Log.w(TAG, "⚠️ Skill '$skillName' demoted to DEPRECATED (Rate: ${(newRate * 100).toInt()}%, Consecutive fails: $consecutiveFailures)")
            }
        }

        val updatedProcedure = if (genModel != null) {
            genModel.copy(
                status = newStatus,
                successRate = newRate,
                usageCount = newExecCount,
                failureCount = newFailureCount,
                consecutiveFailures = consecutiveFailures,
                lastVerifiedAt = if (success) System.currentTimeMillis() else genModel.lastVerifiedAt
            ).toJson().toString(2)
        } else {
            try {
                JSONObject(skill.procedure).apply {
                    put("status", newStatus.name)
                    put("consecutiveFailures", consecutiveFailures)
                }.toString(2)
            } catch (e: Exception) {
                skill.procedure
            }
        }

        val updatedSkill = skill.copy(
            executionCount = newExecCount,
            successCount = newSuccessCount,
            failureCount = newFailureCount,
            successRate = newRate,
            lastExecutedAt = System.currentTimeMillis(),
            lastSuccessAt = if (success) System.currentTimeMillis() else skill.lastSuccessAt,
            procedure = updatedProcedure,
            isEnabled = newStatus != SkillLifecycleStatus.DEPRECATED && newStatus != SkillLifecycleStatus.DISABLED
        )

        dao.updateSkill(updatedSkill)
    }

    /**
     * Marks a skill as STALE when an app UI change or version upgrade is detected.
     */
    suspend fun markSkillStale(skillName: String, reason: String) = withContext(Dispatchers.IO) {
        val skill = dao.getSkillByName(skillName) ?: return@withContext
        val genModel = GeneralizedSkillModel.fromJson(skill.procedure)
        val updatedProcedure = if (genModel != null) {
            genModel.copy(status = SkillLifecycleStatus.STALE).toJson().toString(2)
        } else {
            skill.procedure
        }
        dao.updateSkill(skill.copy(procedure = updatedProcedure, confidence = 0.60f))
        Log.w(TAG, "Marked Skill '$skillName' as STALE: $reason")
    }

    fun convertSkillToPlan(skill: SkillEntity, runtimeGoal: String): TaskPlan? {
        return try {
            val root = JSONObject(skill.procedure)
            val stepsArray = root.optJSONArray("steps") ?: JSONArray()
            val steps = mutableListOf<PlanStep>()

            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val num = stepObj.optInt("stepNumber", i + 1)
                val desc = stepObj.optString("description", "Step $num")
                val tool = stepObj.optString("tool", "tap")
                val exp = stepObj.optString("expectedOutcome", "Success")

                val argsObj = stepObj.optJSONObject("arguments") ?: JSONObject()
                val argsMap = mutableMapOf<String, String>()
                val keys = argsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    argsMap[k] = argsObj.optString(k)
                }

                steps.add(PlanStep(num, desc, ToolIntent(tool, argsMap, skill.riskLevel.name), exp))
            }

            TaskPlan(goal = runtimeGoal.ifBlank { skill.name }, steps = steps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TaskPlan from Skill ${skill.name}", e)
            null
        }
    }

    suspend fun rollbackSkill(skill: SkillEntity): Boolean = withContext(Dispatchers.IO) {
        if (skill.previousVersionProcedure == null) return@withContext false
        val rolledBack = skill.copy(
            procedure = skill.previousVersionProcedure,
            version = "${skill.version}-reverted",
            previousVersionProcedure = null
        )
        dao.updateSkill(rolledBack)
        true
    }

    suspend fun toggleSkill(skill: SkillEntity) = withContext(Dispatchers.IO) {
        dao.updateSkill(skill.copy(isEnabled = !skill.isEnabled))
    }

    suspend fun deleteSkill(skill: SkillEntity) = withContext(Dispatchers.IO) {
        dao.deleteSkill(skill)
    }

    suspend fun clearLearnedSkills() = withContext(Dispatchers.IO) {
        dao.clearLearnedSkills()
    }

    private fun generateSemanticSkillName(goal: String, pkg: String): String {
        val clean = goal.lowercase()
            .replace(Regex("[^a-z0-9_ ]"), "")
            .trim()
            .replace("\\s+".toRegex(), "_")
        val appTag = pkg.substringAfterLast(".")
        return "skill_${appTag}_$clean".take(40)
    }

    private fun incrementVersion(ver: String): String {
        return try {
            val parts = ver.split(".")
            val major = parts.getOrNull(0)?.toInt() ?: 1
            val minor = parts.getOrNull(1)?.toInt() ?: 0
            val patch = (parts.getOrNull(2)?.toInt() ?: 0) + 1
            "$major.$minor.$patch"
        } catch (e: Exception) {
            "1.1.0"
        }
    }
}
