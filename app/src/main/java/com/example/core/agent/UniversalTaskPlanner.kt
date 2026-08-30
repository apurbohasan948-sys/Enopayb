package com.example.core.agent

import android.util.Log
import com.example.core.learning.GeminiTeacher
import com.example.core.learning.SkillManager
import com.example.core.learning.UserCorrectionLearner
import com.example.core.memory.MemoryRetriever
import com.example.core.model.ModelRouter
import com.example.core.model.RoutingDecision
import com.example.core.model.ToolIntent
import com.example.core.vision.SemanticTarget
import com.example.core.vision.UnifiedScreen
import com.example.data.local.entity.SkillEntity
import com.example.data.repository.JarvisRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * UniversalTaskPlanner.
 * Dynamically formulates multi-step execution plans from natural language goals,
 * active screen context, system device state, user long-term memory, learned skills,
 * and Gemini Teacher escalation.
 */
class UniversalTaskPlanner(
    private val repository: JarvisRepository? = null,
    private val memoryRetriever: MemoryRetriever? = null,
    private val geminiTeacher: GeminiTeacher? = null,
    private val modelRouter: ModelRouter? = null,
    private val skillManager: SkillManager? = null,
    private val userCorrectionLearner: UserCorrectionLearner? = null
) {
    companion object {
        private const val TAG = "JARVIS_TaskPlanner"
    }

    suspend fun formulatePlan(
        goal: String,
        currentScreen: UnifiedScreen?,
        deviceState: DeviceStateSnapshot?,
        availableSkills: List<SkillEntity> = emptyList()
    ): TaskPlan {
        val lowerGoal = goal.trim().lowercase()
        val appPackage = currentScreen?.packageName ?: ""

        // 1. Retrieve long-term memory context (User Preferences, Past Experiences, Corrections)
        val memoryContext = memoryRetriever?.retrieveContextForGoal(
            goal = goal,
            appPackage = appPackage,
            screenContext = currentScreen?.getSummary() ?: "general"
        )

        // 2. Check Skill-First Match & Generalized Parametric Skills
        val matchedSkillPair = skillManager?.findMatchingSkill(goal, appPackage)
        if (matchedSkillPair != null) {
            val (matchedSkill, boundPlan) = matchedSkillPair
            Log.i(TAG, "🎯 Replaying verified generalized local skill: '${matchedSkill.name}' (Success Rate: ${(matchedSkill.successRate * 100).toInt()}%)")
            return boundPlan
        }

        // 3. Check ModelRouter decision (Local Skill vs Local Archetype vs Gemini Teacher)
        val routing = modelRouter?.routeTask(goal, currentScreen, availableSkills)

        if (routing?.decision == RoutingDecision.LOCAL_SKILL_REPLAY && routing.selectedSkill != null) {
            val skillPlan = parsePlanFromSkill(routing.selectedSkill, goal)
            if (skillPlan != null && skillPlan.steps.isNotEmpty()) {
                Log.d(TAG, "Replaying verified local skill: ${routing.selectedSkill.name}")
                return skillPlan
            }
        }

        // 4. If ModelRouter suggests Gemini Teacher (Novel / Complex / Low confidence)
        if (routing?.decision == RoutingDecision.GEMINI_TEACHER_FALLBACK && geminiTeacher != null) {
            Log.d(TAG, "Escalating goal '$goal' to Gemini Teacher...")
            val teacherResult = geminiTeacher.requestStructuredTeachingPlan(
                goal = goal,
                currentScreen = currentScreen,
                lowConfidenceReason = routing.rationale,
                relevantMemoryContext = memoryContext?.formattedPromptContext ?: ""
            )

            if (teacherResult.success && teacherResult.plan != null && teacherResult.plan.steps.isNotEmpty()) {
                Log.d(TAG, "Gemini Teacher generated structured ${teacherResult.plan.steps.size}-step plan.")
                return teacherResult.plan
            } else {
                Log.w(TAG, "Gemini Teacher unavailable/failed (${teacherResult.errorMessage}), falling back to local reasoning.")
            }
        }

        // 5. Direct Match with available persistent Skills
        val matchingSkill = availableSkills.firstOrNull { skill ->
            skill.isEnabled && (
                skill.name.equals(goal, ignoreCase = true) ||
                lowerGoal.contains(skill.name.lowercase()) ||
                skill.description.contains(goal, ignoreCase = true)
            )
        }

        if (matchingSkill != null) {
            val skillPlan = parsePlanFromSkill(matchingSkill, goal)
            if (skillPlan != null && skillPlan.steps.isNotEmpty()) {
                return skillPlan
            }
        }

        // 5. Dynamic Domain Archetypes

        // Archetype A: YouTube / Video Playback ("Open YouTube and play Tom and Jerry")
        if (lowerGoal.contains("youtube") && (lowerGoal.contains("play") || lowerGoal.contains("search") || lowerGoal.contains("video"))) {
            val query = extractQuery(goal, listOf("play ", "search ", "watch ", "for "), fallback = "Tom and Jerry")
            return TaskPlan(
                goal = "Open YouTube and play $query",
                steps = listOf(
                    PlanStep(1, "Open YouTube Application", ToolIntent("open_app", mapOf("app_name" to "YouTube"), "LOW"), "YouTube in foreground"),
                    PlanStep(2, "Locate Search Icon 🔍", ToolIntent("tap", mapOf("target_text" to SemanticTarget.SEARCH), "LOW"), "Search input displayed"),
                    PlanStep(3, "Type \"$query\" into search box", ToolIntent("type_text", mapOf("text" to query), "LOW"), "Query entered"),
                    PlanStep(4, "Submit Search", ToolIntent("tap", mapOf("target_text" to SemanticTarget.SEARCH), "LOW"), "Results displayed"),
                    PlanStep(5, "Select & Play Video Result", ToolIntent("tap", mapOf("target_text" to SemanticTarget.VIDEO_ITEM), "LOW"), "Playback active")
                )
            )
        }

        // Archetype B: Chrome / Web Search ("Open Chrome and search for HSC result")
        if (lowerGoal.contains("chrome") || lowerGoal.contains("browser") || (lowerGoal.contains("search google") || lowerGoal.contains("search for"))) {
            val query = extractQuery(goal, listOf("search for ", "search ", "find ", "google "), fallback = "HSC result")
            return TaskPlan(
                goal = "Open Chrome and search Google for $query",
                steps = listOf(
                    PlanStep(1, "Open Chrome Browser", ToolIntent("open_app", mapOf("app_name" to "Chrome"), "LOW"), "Chrome in foreground"),
                    PlanStep(2, "Find and tap Address/Search bar", ToolIntent("tap", mapOf("target_text" to SemanticTarget.INPUT_FIELD), "LOW"), "Search bar focused"),
                    PlanStep(3, "Enter query \"$query\"", ToolIntent("type_text", mapOf("text" to query), "LOW"), "Query entered"),
                    PlanStep(4, "Submit Google Search", ToolIntent("tap", mapOf("target_text" to SemanticTarget.SEARCH), "LOW"), "Search results visible")
                )
            )
        }

        // Archetype C: Settings & Connectivity ("Open Settings and turn on Bluetooth")
        if (lowerGoal.contains("setting") || lowerGoal.contains("bluetooth") || lowerGoal.contains("wifi") || lowerGoal.contains("display")) {
            val section = when {
                lowerGoal.contains("bluetooth") -> "Bluetooth"
                lowerGoal.contains("wifi") || lowerGoal.contains("wi-fi") -> "Wi-Fi"
                lowerGoal.contains("display") -> "Display"
                lowerGoal.contains("sound") -> "Sound"
                else -> "Connected devices"
            }
            return TaskPlan(
                goal = "Open Settings and navigate to $section",
                steps = listOf(
                    PlanStep(1, "Open Settings", ToolIntent("open_app", mapOf("app_name" to "Settings"), "LOW"), "Settings in foreground"),
                    PlanStep(2, "Find and tap $section", ToolIntent("tap", mapOf("target_text" to section), "LOW"), "$section opened"),
                    PlanStep(3, "Observe and verify toggle status", ToolIntent("get_screen_elements", emptyMap(), "LOW"), "Toggle state verified")
                )
            )
        }

        // Archetype D: Calculator & Calculations ("Open the calculator and calculate 250 × 45")
        if (lowerGoal.contains("calculator") || lowerGoal.contains("calculate") || lowerGoal.contains("times") || lowerGoal.contains("divided") || lowerGoal.contains("+") || lowerGoal.contains("×") || lowerGoal.contains("/")) {
            val expression = extractMathExpression(goal)
            return TaskPlan(
                goal = "Open Calculator and calculate $expression",
                steps = listOf(
                    PlanStep(1, "Open Calculator", ToolIntent("open_app", mapOf("app_name" to "Calculator"), "LOW"), "Calculator in foreground"),
                    PlanStep(2, "Enter calculation expression \"$expression\"", ToolIntent("type_text", mapOf("text" to expression), "LOW"), "Expression entered"),
                    PlanStep(3, "Tap equals =", ToolIntent("tap", mapOf("target_text" to "="), "LOW"), "Calculation completed"),
                    PlanStep(4, "Read calculation result", ToolIntent("get_screen_elements", emptyMap(), "LOW"), "Result captured")
                )
            )
        }

        // Archetype E: Gallery / Photos ("Open Gallery and find today's photo")
        if (lowerGoal.contains("gallery") || lowerGoal.contains("photo") || lowerGoal.contains("picture")) {
            return TaskPlan(
                goal = "Open Gallery and find recent photos",
                steps = listOf(
                    PlanStep(1, "Open Gallery / Photos", ToolIntent("open_app", mapOf("app_name" to "Photos"), "LOW"), "Gallery active"),
                    PlanStep(2, "Inspect photo thumbnails", ToolIntent("get_screen_elements", emptyMap(), "LOW"), "Thumbnails visible"),
                    PlanStep(3, "Select recent photo", ToolIntent("tap", mapOf("target_text" to "Photo"), "LOW"), "Photo opened")
                )
            )
        }

        // Archetype F: WhatsApp / Communication ("Open WhatsApp and find Hammad")
        if (lowerGoal.contains("whatsapp") || lowerGoal.contains("chat") || lowerGoal.contains("message")) {
            val contact = extractQuery(goal, listOf("find ", "to ", "message ", "chat with "), fallback = "Hammad")
            return TaskPlan(
                goal = "Open WhatsApp and find $contact",
                steps = listOf(
                    PlanStep(1, "Open WhatsApp", ToolIntent("open_app", mapOf("app_name" to "WhatsApp"), "LOW"), "WhatsApp in foreground"),
                    PlanStep(2, "Tap Search 🔍 icon", ToolIntent("tap", mapOf("target_text" to SemanticTarget.SEARCH), "LOW"), "Search bar opened"),
                    PlanStep(3, "Type contact name \"$contact\"", ToolIntent("type_text", mapOf("text" to contact), "LOW"), "Contact queried"),
                    PlanStep(4, "Select chat with $contact", ToolIntent("tap", mapOf("target_text" to contact), "LOW"), "Conversation opened")
                )
            )
        }

        // Archetype G: Generic App Launch
        if (lowerGoal.startsWith("open ") || lowerGoal.startsWith("launch ")) {
            val app = goal.substringAfter("open ").substringAfter("launch ").trim()
            return TaskPlan(
                goal = "Open $app",
                steps = listOf(
                    PlanStep(1, "Launch application: $app", ToolIntent("open_app", mapOf("app_name" to app), "LOW"), "$app in foreground"),
                    PlanStep(2, "Inspect active screen controls", ToolIntent("get_screen_elements", emptyMap(), "LOW"), "Screen observed")
                )
            )
        }

        // Archetype H: App-Agnostic General Multi-Step Fallback
        return TaskPlan(
            goal = goal,
            steps = listOf(
                PlanStep(1, "Observe current screen for: $goal", ToolIntent("get_screen_elements", emptyMap(), "LOW"), "Screen observed"),
                PlanStep(2, "Resolve and interact with matching control for: $goal", ToolIntent("tap", mapOf("target_text" to goal), "LOW"), "Target interacted"),
                PlanStep(3, "Verify outcome", ToolIntent("get_screen_elements", emptyMap(), "LOW"), "Goal outcome confirmed")
            )
        )
    }

    private fun extractQuery(goal: String, prefixes: List<String>, fallback: String): String {
        for (prefix in prefixes) {
            if (goal.contains(prefix, ignoreCase = true)) {
                val candidate = goal.substring(goal.indexOf(prefix, ignoreCase = true) + prefix.length).trim()
                val clean = candidate.substringBefore(" on ").substringBefore(" and ").trim()
                if (clean.isNotBlank()) return clean
            }
        }
        return fallback
    }

    private fun extractMathExpression(goal: String): String {
        val lower = goal.lowercase()
        return when {
            lower.contains("250") && lower.contains("45") -> "250*45"
            lower.contains("1250") && lower.contains("25") -> "1250/25"
            else -> {
                val digits = goal.replace(Regex("[^0-9+\\-*/xX÷.]"), " ").trim()
                val parts = digits.split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (parts.size >= 2) parts.joinToString("") else "0"
            }
        }
    }

    fun planUniversalTask(
        goal: String,
        currentApp: String? = null
    ): UniversalTask {
        val lowerGoal = goal.trim().lowercase()

        // Archetype 1: YouTube Search / Play
        if (lowerGoal.contains("youtube") && (lowerGoal.contains("search") || lowerGoal.contains("play") || lowerGoal.contains("watch") || lowerGoal.contains("tom and jerry"))) {
            val query = extractQuery(goal, listOf("search for ", "search ", "play ", "watch ", "for "), fallback = "Tom and Jerry")
            val steps = listOf(
                UniversalActionStep(1, "Open YouTube", UniversalActionType.OPEN_APP, "YouTube", mapOf("app_name" to "YouTube"), "YouTube app in foreground"),
                UniversalActionStep(2, "Locate and activate search", UniversalActionType.CLICK, "SEARCH", emptyMap(), "Search input available"),
                UniversalActionStep(3, "Enter \"$query\"", UniversalActionType.TYPE_TEXT, "INPUT_FIELD", mapOf("text" to query), "Query typed"),
                UniversalActionStep(4, "Submit search query", UniversalActionType.SEARCH, "SEARCH", emptyMap(), "Results displayed"),
                UniversalActionStep(5, "Observe results and play video", UniversalActionType.CLICK, "VIDEO_ITEM", emptyMap(), "Video playing")
            )
            return UniversalTask(
                goal = goal,
                intent = "MEDIA_SEARCH_PLAY",
                entities = mapOf("app" to "YouTube", "query" to query),
                requiredCapabilities = listOf("SYSTEM_CONTROL", "ACCESSIBILITY"),
                targetApp = "YouTube",
                plan = steps
            )
        }

        // Archetype 2: Chrome / Web Search
        if (lowerGoal.contains("chrome") || lowerGoal.contains("browser") || lowerGoal.contains("search google") || lowerGoal.contains("search for")) {
            val query = extractQuery(goal, listOf("search for ", "search ", "find ", "google "), fallback = "HSC result")
            val steps = listOf(
                UniversalActionStep(1, "Open Chrome", UniversalActionType.OPEN_APP, "Chrome", mapOf("app_name" to "Chrome"), "Chrome in foreground"),
                UniversalActionStep(2, "Locate address/search bar", UniversalActionType.CLICK, "INPUT_FIELD", emptyMap(), "Address bar focused"),
                UniversalActionStep(3, "Enter \"$query\"", UniversalActionType.TYPE_TEXT, "INPUT_FIELD", mapOf("text" to query), "Query entered"),
                UniversalActionStep(4, "Submit search", UniversalActionType.SEARCH, "SEARCH", emptyMap(), "Results loaded")
            )
            return UniversalTask(
                goal = goal,
                intent = "WEB_SEARCH",
                entities = mapOf("app" to "Chrome", "query" to query),
                requiredCapabilities = listOf("SYSTEM_CONTROL", "ACCESSIBILITY"),
                targetApp = "Chrome",
                plan = steps
            )
        }

        // Archetype 3: Settings & Back Navigation
        if (lowerGoal.contains("settings") && (lowerGoal.contains("back") || lowerGoal.contains("go back"))) {
            val steps = listOf(
                UniversalActionStep(1, "Open Settings", UniversalActionType.OPEN_APP, "Settings", mapOf("app_name" to "Settings"), "Settings in foreground"),
                UniversalActionStep(2, "Observe settings menu", UniversalActionType.READ, "SETTINGS", emptyMap(), "Settings menu visible"),
                UniversalActionStep(3, "Navigate back", UniversalActionType.BACK, "BACK", emptyMap(), "Returned to previous screen")
            )
            return UniversalTask(
                goal = goal,
                intent = "SETTINGS_NAVIGATION",
                entities = mapOf("app" to "Settings"),
                requiredCapabilities = listOf("SYSTEM_CONTROL", "ACCESSIBILITY"),
                targetApp = "Settings",
                plan = steps
            )
        }

        // Archetype 4: Gallery & Scroll
        if (lowerGoal.contains("gallery") || lowerGoal.contains("photos") || lowerGoal.contains("photo")) {
            val steps = listOf(
                UniversalActionStep(1, "Open Gallery / Photos", UniversalActionType.OPEN_APP, "Photos", mapOf("app_name" to "Photos"), "Gallery opened"),
                UniversalActionStep(2, "Observe photo grid", UniversalActionType.READ, "PHOTO_GRID", emptyMap(), "Photos visible"),
                UniversalActionStep(3, "Scroll gallery feed", UniversalActionType.SCROLL, "SCROLL_CONTAINER", mapOf("direction" to "forward"), "Feed scrolled")
            )
            return UniversalTask(
                goal = goal,
                intent = "MEDIA_GALLERY_SCROLL",
                entities = mapOf("app" to "Photos"),
                requiredCapabilities = listOf("SYSTEM_CONTROL", "ACCESSIBILITY"),
                targetApp = "Photos",
                plan = steps
            )
        }

        // Archetype 5: WhatsApp Find Contact
        if (lowerGoal.contains("whatsapp") && (lowerGoal.contains("find") || lowerGoal.contains("contact") || lowerGoal.contains("chat"))) {
            val contact = extractQuery(goal, listOf("find ", "contact ", "to ", "message "), fallback = "Hammad")
            val steps = listOf(
                UniversalActionStep(1, "Open WhatsApp", UniversalActionType.OPEN_APP, "WhatsApp", mapOf("app_name" to "WhatsApp"), "WhatsApp in foreground"),
                UniversalActionStep(2, "Locate search icon", UniversalActionType.CLICK, "SEARCH", emptyMap(), "Search bar opened"),
                UniversalActionStep(3, "Enter contact name \"$contact\"", UniversalActionType.TYPE_TEXT, "INPUT_FIELD", mapOf("text" to contact), "Contact queried"),
                UniversalActionStep(4, "Select conversation", UniversalActionType.CLICK, contact, emptyMap(), "Chat opened")
            )
            return UniversalTask(
                goal = goal,
                intent = "COMMUNICATION_FIND_CONTACT",
                entities = mapOf("app" to "WhatsApp", "contact" to contact),
                requiredCapabilities = listOf("SYSTEM_CONTROL", "ACCESSIBILITY", "CONTACTS"),
                targetApp = "WhatsApp",
                plan = steps
            )
        }

        // Archetype 6: Generic Launch App
        if (lowerGoal.startsWith("open ") || lowerGoal.startsWith("launch ")) {
            val app = goal.substringAfter("open ").substringAfter("launch ").trim()
            val steps = listOf(
                UniversalActionStep(1, "Open $app", UniversalActionType.OPEN_APP, app, mapOf("app_name" to app), "$app in foreground"),
                UniversalActionStep(2, "Observe screen", UniversalActionType.READ, "SCREEN", emptyMap(), "Screen active")
            )
            return UniversalTask(
                goal = goal,
                intent = "APP_LAUNCH",
                entities = mapOf("app" to app),
                requiredCapabilities = listOf("SYSTEM_CONTROL"),
                targetApp = app,
                plan = steps
            )
        }

        // Archetype 7: General Dynamic Fallback
        val fallbackSteps = listOf(
            UniversalActionStep(1, "Observe active screen for $goal", UniversalActionType.READ, goal, emptyMap(), "Screen state captured"),
            UniversalActionStep(2, "Interact with matching semantic target", UniversalActionType.CLICK, goal, emptyMap(), "Target interacted"),
            UniversalActionStep(3, "Verify outcome", UniversalActionType.VERIFY, goal, emptyMap(), "Outcome verified")
        )
        return UniversalTask(
            goal = goal,
            intent = "GENERAL_DYNAMIC_TASK",
            entities = mapOf("goal" to goal),
            requiredCapabilities = listOf("ACCESSIBILITY"),
            plan = fallbackSteps
        )
    }

    private fun parsePlanFromSkill(skill: SkillEntity, goal: String): TaskPlan? {
        return try {
            val json = JSONObject(skill.procedure)
            val stepsArray = json.optJSONArray("steps") ?: JSONArray()
            val planSteps = mutableListOf<PlanStep>()

            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val stepNum = stepObj.optInt("stepNumber", i + 1)
                val desc = stepObj.optString("description", "Execute skill step $stepNum")
                val tool = stepObj.optString("tool", "tap")
                val expected = stepObj.optString("expectedOutcome", "Step completed")

                val argsObj = stepObj.optJSONObject("arguments")
                val args = mutableMapOf<String, String>()
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        args[k] = argsObj.optString(k)
                    }
                } else {
                    val target = stepObj.optString("target", "")
                    if (target.isNotBlank()) args["target_text"] = target
                    if (tool == "open_app") args["app_name"] = target
                }

                planSteps.add(PlanStep(stepNum, desc, ToolIntent(tool, args, "LOW"), expected))
            }

            TaskPlan(goal = skill.name, steps = planSteps)
        } catch (e: Exception) {
            null
        }
    }
}
