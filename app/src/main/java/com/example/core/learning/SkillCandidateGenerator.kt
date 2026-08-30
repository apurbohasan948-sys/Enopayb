package com.example.core.learning

import android.util.Log
import com.example.core.agent.PlanStep
import com.example.core.agent.StepExecutionRecord
import com.example.core.agent.TaskPlan
import com.example.core.model.ToolIntent
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import com.example.data.local.entity.SkillSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SkillCandidateGenerator(
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_CandidateGen"
    }

    /**
     * Extracts and generates a parameterized, reusable Skill candidate from a verified experience.
     */
    suspend fun generateCandidateFromExperience(
        goal: String,
        appPackage: String,
        appVersion: Long = 0L,
        stepRecords: List<StepExecutionRecord>,
        qualityScore: Float = 0.95f
    ): SkillEntity? = withContext(Dispatchers.IO) {
        if (stepRecords.isEmpty()) return@withContext null

        try {
            val lowerGoal = goal.lowercase().trim()
            val (intentArchetype, detectedSlots) = analyzeIntentAndSlots(goal, appPackage)

            // Parameter extraction & template variable substitution
            val generalizedSteps = mutableListOf<GeneralizedStepTemplate>()
            val parameters = mutableListOf<SkillParameter>()

            // Register detected slots as parameters
            detectedSlots.forEach { (slotName, slotValue) ->
                val type = when (slotName) {
                    "query" -> "string"
                    "contact_name" -> "contact"
                    "app_name" -> "app_name"
                    "text" -> "string"
                    else -> "string"
                }
                parameters.add(
                    SkillParameter(
                        name = slotName,
                        type = type,
                        required = true,
                        defaultValue = slotValue,
                        description = "Extracted slot for $slotName"
                    )
                )
            }

            // Generalize each step
            stepRecords.forEachIndexed { index, record ->
                val step = record.step
                var genDesc = step.description
                var genTarget = step.toolIntent.arguments["target_text"] ?: step.toolIntent.arguments["target"] ?: ""
                val genArgs = mutableMapOf<String, String>()

                step.toolIntent.arguments.forEach { (key, value) ->
                    var templatedVal = value
                    detectedSlots.forEach { (slotName, slotValue) ->
                        if (slotValue.isNotBlank() && templatedVal.contains(slotValue, ignoreCase = true)) {
                            templatedVal = templatedVal.replace(slotValue, "{{$slotName}}", ignoreCase = true)
                        }
                    }
                    genArgs[key] = templatedVal
                }

                detectedSlots.forEach { (slotName, slotValue) ->
                    if (slotValue.isNotBlank()) {
                        if (genDesc.contains(slotValue, ignoreCase = true)) {
                            genDesc = genDesc.replace(slotValue, "{{$slotName}}", ignoreCase = true)
                        }
                        if (genTarget.contains(slotValue, ignoreCase = true)) {
                            genTarget = genTarget.replace(slotValue, "{{$slotName}}", ignoreCase = true)
                        }
                    }
                }

                generalizedSteps.add(
                    GeneralizedStepTemplate(
                        stepNumber = index + 1,
                        description = genDesc,
                        tool = step.toolIntent.toolName,
                        targetTemplate = genTarget,
                        argumentsTemplate = genArgs,
                        expectedOutcome = step.expectedOutcome
                    )
                )
            }

            // Generate canonical skill identifier
            val skillName = generateCanonicalSkillName(intentArchetype, appPackage)
            val existingSkill = dao.getSkillByName(skillName)

            val generalizedModel = GeneralizedSkillModel(
                skillId = skillName,
                name = skillName,
                description = "Parameterized skill for $intentArchetype in ${appPackage.ifBlank { "system" }}",
                intentArchetype = intentArchetype,
                targetAppPackage = appPackage,
                minAppVersion = appVersion,
                parameters = parameters,
                steps = generalizedSteps,
                status = if (existingSkill != null && existingSkill.successCount >= 2) SkillLifecycleStatus.ACTIVE else SkillLifecycleStatus.CANDIDATE,
                version = if (existingSkill != null) incrementVersion(existingSkill.version) else "1.0.0",
                qualityScore = qualityScore,
                successRate = 1.0f,
                usageCount = (existingSkill?.executionCount ?: 0) + 1,
                failureCount = existingSkill?.failureCount ?: 0,
                consecutiveFailures = 0,
                lastVerifiedAt = System.currentTimeMillis(),
                source = "EXPERIENCE_EXTRACTED"
            )

            val procedureJson = generalizedModel.toJson().toString(2)
            val inputSchemaJson = JSONObject().apply {
                parameters.forEach { put(it.name, it.type) }
            }.toString()

            val riskLevel = when {
                intentArchetype.contains("CALL") || intentArchetype.contains("SMS") -> SkillRiskLevel.HIGH
                intentArchetype.contains("MESSAGE") -> SkillRiskLevel.MEDIUM
                else -> SkillRiskLevel.LOW
            }

            val skillEntity = SkillEntity(
                id = existingSkill?.id ?: 0L,
                name = skillName,
                description = generalizedModel.description,
                requiredPermissions = if (riskLevel == SkillRiskLevel.HIGH) "CALL_PHONE, READ_CONTACTS" else "AccessibilityService",
                inputSchema = inputSchemaJson,
                outputSchema = "{\"status\": \"string\", \"verified\": \"boolean\"}",
                riskLevel = riskLevel,
                procedure = procedureJson,
                verificationMethod = "Screen Transition & Target Verification",
                version = generalizedModel.version,
                isEnabled = true,
                executionCount = generalizedModel.usageCount,
                successCount = (existingSkill?.successCount ?: 0) + 1,
                failureCount = existingSkill?.failureCount ?: 0,
                successRate = 1.0f,
                confidence = qualityScore,
                lastExecutedAt = System.currentTimeMillis(),
                lastSuccessAt = System.currentTimeMillis(),
                isLearnedFromExperience = true,
                source = SkillSource.EXPERIENCE_EXTRACTED,
                previousVersionProcedure = existingSkill?.procedure
            )

            dao.insertSkill(skillEntity)
            Log.d(TAG, "Generated Skill Candidate: $skillName (Status: ${generalizedModel.status}, Params: ${parameters.map { it.name }})")
            skillEntity
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate skill candidate", e)
            null
        }
    }

    /**
     * Binds runtime user goal entities into a generalized skill procedure to produce an executable TaskPlan.
     */
    fun bindSkillParameters(
        skill: SkillEntity,
        runtimeGoal: String,
        extractedSlots: Map<String, String>
    ): TaskPlan? {
        return try {
            val genModel = GeneralizedSkillModel.fromJson(skill.procedure)
            if (genModel == null || genModel.steps.isEmpty()) {
                // Fallback to legacy parsing if not in generalized format
                return parseLegacySkillProcedure(skill, runtimeGoal)
            }

            val boundSteps = mutableListOf<PlanStep>()
            genModel.steps.forEach { stepTemplate ->
                var boundDesc = stepTemplate.description
                val boundArgs = mutableMapOf<String, String>()

                stepTemplate.argumentsTemplate.forEach { (k, v) ->
                    var argVal = v
                    extractedSlots.forEach { (slotKey, slotVal) ->
                        argVal = argVal.replace("{{$slotKey}}", slotVal)
                    }
                    boundArgs[k] = argVal
                }

                extractedSlots.forEach { (slotKey, slotVal) ->
                    boundDesc = boundDesc.replace("{{$slotKey}}", slotVal)
                }

                boundSteps.add(
                    PlanStep(
                        stepNumber = stepTemplate.stepNumber,
                        description = boundDesc,
                        toolIntent = ToolIntent(stepTemplate.tool, boundArgs, skill.riskLevel.name),
                        expectedOutcome = stepTemplate.expectedOutcome
                    )
                )
            }

            TaskPlan(goal = runtimeGoal, steps = boundSteps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind skill parameters for ${skill.name}", e)
            null
        }
    }

    private fun parseLegacySkillProcedure(skill: SkillEntity, goal: String): TaskPlan? {
        return try {
            val root = JSONObject(skill.procedure)
            val stepsArray = root.optJSONArray("steps") ?: JSONArray()
            val steps = mutableListOf<PlanStep>()
            for (i in 0 until stepsArray.length()) {
                val s = stepsArray.getJSONObject(i)
                val num = s.optInt("stepNumber", i + 1)
                val desc = s.optString("description", "Step $num")
                val tool = s.optString("tool", "tap")
                val exp = s.optString("expectedOutcome", "Success")
                val argsObj = s.optJSONObject("arguments")
                val argsMap = mutableMapOf<String, String>()
                if (argsObj != null) {
                    val kIter = argsObj.keys()
                    while (kIter.hasNext()) {
                        val k = kIter.next()
                        argsMap[k] = argsObj.optString(k)
                    }
                }
                steps.add(PlanStep(num, desc, ToolIntent(tool, argsMap, skill.riskLevel.name), exp))
            }
            TaskPlan(goal = goal, steps = steps)
        } catch (e: Exception) {
            null
        }
    }

    fun analyzeIntentAndSlots(goal: String, appPackage: String): Pair<String, Map<String, String>> {
        val lower = goal.lowercase().trim()
        val slots = mutableMapOf<String, String>()

        // 1. YouTube Playback / Search / Music
        if (lower.contains("youtube") || appPackage.contains("youtube") || lower.startsWith("play ") || lower.contains("watch ") || lower.contains("lofi")) {
            val query = extractQuery(goal, listOf("play ", "search for ", "search ", "watch ", "for "), "Tom and Jerry")
            slots["query"] = query
            slots["app_name"] = "YouTube"
            return Pair("YOUTUBE_SEARCH_PLAY", slots)
        }

        // 2. Web / Browser Search
        if (lower.contains("chrome") || lower.contains("browser") || lower.contains("search google") || lower.contains("search for")) {
            val query = extractQuery(goal, listOf("search for ", "search google for ", "find ", "google "), "HSC result")
            slots["query"] = query
            slots["app_name"] = "Chrome"
            return Pair("WEB_SEARCH", slots)
        }

        // 3. WhatsApp Messaging
        if (lower.contains("whatsapp") || lower.contains("message") || lower.contains("chat")) {
            val contact = extractQuery(goal, listOf("message to ", "to ", "find ", "chat with "), "Hammad")
            val text = if (lower.contains("saying")) goal.substringAfter("saying ").trim() else ""
            slots["contact_name"] = contact
            slots["app_name"] = "WhatsApp"
            if (text.isNotBlank()) slots["text"] = text
            return Pair("WHATSAPP_MESSAGING", slots)
        }

        // 4. Calculator Computation
        if (lower.contains("calculator") || lower.contains("calculate")) {
            val expr = goal.replace(Regex("[^0-9+\\-*/xX÷.]"), " ").trim()
            slots["expression"] = expr
            slots["app_name"] = "Calculator"
            return Pair("CALCULATOR_COMPUTATION", slots)
        }

        // 5. Generic App Launch
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val app = goal.substringAfter("open ").substringAfter("launch ").trim()
            slots["app_name"] = app
            return Pair("APP_LAUNCH", slots)
        }

        return Pair("GENERAL_TASK", mapOf("goal" to goal))
    }

    private fun extractQuery(goal: String, prefixes: List<String>, fallback: String): String {
        for (prefix in prefixes) {
            val idx = goal.indexOf(prefix, ignoreCase = true)
            if (idx != -1) {
                val candidate = goal.substring(idx + prefix.length).trim()
                val clean = candidate.substringBefore(" on ").substringBefore(" saying").trim()
                if (clean.isNotBlank()) return clean
            }
        }
        return fallback
    }

    private fun generateCanonicalSkillName(intentArchetype: String, appPackage: String): String {
        val appTag = if (appPackage.isNotBlank()) {
            appPackage.substringAfterLast(".").replace("[^a-zA-Z0-9_]".toRegex(), "_")
        } else {
            "system"
        }
        return "skill_${appTag.lowercase()}_${intentArchetype.lowercase()}"
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
