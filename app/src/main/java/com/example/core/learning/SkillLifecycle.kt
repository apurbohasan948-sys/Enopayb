package com.example.core.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * SkillLifecycleStatus defines the progression of a skill from candidate to active execution.
 */
enum class SkillLifecycleStatus {
    CANDIDATE,    // Newly extracted from a verified experience or Gemini teacher, pending trial
    VALIDATING,   // Under trial execution across variations
    VERIFIED,     // Passed trial execution verification
    ACTIVE,       // Fully promoted for instant local fast-path execution
    STALE,        // App UI structure or version changed; requires re-verification
    DEPRECATED,   // Success rate fell below threshold (<70%) or repeated failures
    DISABLED      // Manually disabled by user or policy
}

/**
 * ParameterDefinition defines dynamic variable slots in a generalized skill.
 */
data class SkillParameter(
    val name: String,
    val type: String = "string", // "string", "number", "boolean", "app_name", "contact"
    val required: Boolean = true,
    val defaultValue: String? = null,
    val description: String = ""
)

/**
 * GeneralizedSkillModel encapsulates a parameterized, versioned, reusable skill.
 */
data class GeneralizedSkillModel(
    val skillId: String,
    val name: String,
    val description: String,
    val intentArchetype: String,
    val targetAppPackage: String,
    val minAppVersion: Long = 0L,
    val parameters: List<SkillParameter> = emptyList(),
    val steps: List<GeneralizedStepTemplate> = emptyList(),
    val status: SkillLifecycleStatus = SkillLifecycleStatus.CANDIDATE,
    val version: String = "1.0.0",
    val qualityScore: Float = 0.95f,
    val successRate: Float = 1.0f,
    val usageCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastVerifiedAt: Long = System.currentTimeMillis(),
    val source: String = "EXPERIENCE_EXTRACTED"
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("skillId", skillId)
        root.put("name", name)
        root.put("description", description)
        root.put("intentArchetype", intentArchetype)
        root.put("targetAppPackage", targetAppPackage)
        root.put("minAppVersion", minAppVersion)
        root.put("status", status.name)
        root.put("version", version)
        root.put("qualityScore", qualityScore.toDouble())
        root.put("successRate", successRate.toDouble())
        root.put("usageCount", usageCount)
        root.put("failureCount", failureCount)
        root.put("consecutiveFailures", consecutiveFailures)
        root.put("lastVerifiedAt", lastVerifiedAt)
        root.put("source", source)

        val paramsArray = JSONArray()
        parameters.forEach { param ->
            val pObj = JSONObject()
            pObj.put("name", param.name)
            pObj.put("type", param.type)
            pObj.put("required", param.required)
            pObj.put("defaultValue", param.defaultValue ?: "")
            pObj.put("description", param.description)
            paramsArray.put(pObj)
        }
        root.put("parameters", paramsArray)

        val stepsArray = JSONArray()
        steps.forEach { step ->
            val sObj = JSONObject()
            sObj.put("stepNumber", step.stepNumber)
            sObj.put("description", step.description)
            sObj.put("tool", step.tool)
            sObj.put("target", step.targetTemplate)
            sObj.put("expectedOutcome", step.expectedOutcome)

            val argsObj = JSONObject()
            step.argumentsTemplate.forEach { (k, v) -> argsObj.put(k, v) }
            sObj.put("arguments", argsObj)

            stepsArray.put(sObj)
        }
        root.put("steps", stepsArray)

        return root
    }

    companion object {
        fun fromJson(jsonStr: String): GeneralizedSkillModel? {
            return try {
                val root = JSONObject(jsonStr)
                val paramsList = mutableListOf<SkillParameter>()
                val paramsArray = root.optJSONArray("parameters")
                if (paramsArray != null) {
                    for (i in 0 until paramsArray.length()) {
                        val p = paramsArray.getJSONObject(i)
                        paramsList.add(
                            SkillParameter(
                                name = p.optString("name"),
                                type = p.optString("type", "string"),
                                required = p.optBoolean("required", true),
                                defaultValue = p.optString("defaultValue").ifBlank { null },
                                description = p.optString("description")
                            )
                        )
                    }
                }

                val stepsList = mutableListOf<GeneralizedStepTemplate>()
                val stepsArray = root.optJSONArray("steps")
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        val s = stepsArray.getJSONObject(i)
                        val argsMap = mutableMapOf<String, String>()
                        val argsObj = s.optJSONObject("arguments")
                        if (argsObj != null) {
                            val keys = argsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                argsMap[k] = argsObj.optString(k)
                            }
                        }
                        stepsList.add(
                            GeneralizedStepTemplate(
                                stepNumber = s.optInt("stepNumber", i + 1),
                                description = s.optString("description"),
                                tool = s.optString("tool", "tap"),
                                targetTemplate = s.optString("target"),
                                argumentsTemplate = argsMap,
                                expectedOutcome = s.optString("expectedOutcome")
                            )
                        )
                    }
                }

                val statusStr = root.optString("status", SkillLifecycleStatus.ACTIVE.name)
                val status = try {
                    SkillLifecycleStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    SkillLifecycleStatus.ACTIVE
                }

                GeneralizedSkillModel(
                    skillId = root.optString("skillId", "skill_unknown"),
                    name = root.optString("name", "Unknown Skill"),
                    description = root.optString("description", ""),
                    intentArchetype = root.optString("intentArchetype", "GENERAL"),
                    targetAppPackage = root.optString("targetAppPackage", ""),
                    minAppVersion = root.optLong("minAppVersion", 0L),
                    parameters = paramsList,
                    steps = stepsList,
                    status = status,
                    version = root.optString("version", "1.0.0"),
                    qualityScore = root.optDouble("qualityScore", 0.95).toFloat(),
                    successRate = root.optDouble("successRate", 1.0).toFloat(),
                    usageCount = root.optInt("usageCount", 0),
                    failureCount = root.optInt("failureCount", 0),
                    consecutiveFailures = root.optInt("consecutiveFailures", 0),
                    lastVerifiedAt = root.optLong("lastVerifiedAt", System.currentTimeMillis()),
                    source = root.optString("source", "EXPERIENCE_EXTRACTED")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class GeneralizedStepTemplate(
    val stepNumber: Int,
    val description: String,
    val tool: String,
    val targetTemplate: String,
    val argumentsTemplate: Map<String, String>,
    val expectedOutcome: String
)
