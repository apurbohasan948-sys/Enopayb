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
 * Extracts procedural workflows from verified experiences and Gemini teacher sessions.
 * Manages success rates, confidence scores, and version rollback.
 */
class SkillManager(
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_SkillManager"
    }

    val allSkills: Flow<List<SkillEntity>> = dao.getAllSkills()
    val enabledSkills: Flow<List<SkillEntity>> = dao.getEnabledSkills()
    val learnedSkills: Flow<List<SkillEntity>> = dao.getLearnedSkills()

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
                executionCount = existing?.executionCount ?: 1,
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
     * Converts a stored Skill into an executable TaskPlan.
     */
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

                steps.add(PlanStep(num, desc, ToolIntent(tool, argsMap, "LOW"), exp))
            }

            TaskPlan(goal = runtimeGoal.ifBlank { skill.name }, steps = steps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TaskPlan from Skill ${skill.name}", e)
            null
        }
    }

    suspend fun recordExecution(skillName: String, success: Boolean) = withContext(Dispatchers.IO) {
        if (success) {
            dao.recordSkillSuccess(skillName)
        } else {
            dao.recordSkillFailure(skillName)
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
        return "skill_${pkg.substringAfterLast(".")}_$clean".take(40)
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
