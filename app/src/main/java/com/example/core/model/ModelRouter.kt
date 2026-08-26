package com.example.core.model

import com.example.core.learning.SkillManager
import com.example.core.memory.MemoryRetriever
import com.example.core.vision.UnifiedScreen
import com.example.data.local.entity.SkillEntity
import com.example.data.local.preference.JarvisPreferences

enum class RoutingDecision {
    NO_AI_DETERMINISTIC,
    LOCAL_SKILL_REPLAY,
    LOCAL_ARCHETYPE_PLAN,
    LOCAL_REASONER,
    LOCAL_VISION_PERCEPTION,
    GEMINI_TEACHER_FALLBACK,
    GEMINI_VISION_FALLBACK,
    OFFLINE_LOCAL_FALLBACK,
    REQUIRES_INTERNET_NOTICE
}

data class TaskComplexityProfile(
    val complexityScore: Float, // 0.0 (Simple) to 1.0 (High)
    val requiresVision: Boolean,
    val requiresInternet: Boolean,
    val isPrivacySensitive: Boolean,
    val latencyRequirement: String // LOW, NORMAL
)

data class PlanRoutingResult(
    val decision: RoutingDecision,
    val selectedSkill: SkillEntity? = null,
    val estimatedLocalConfidence: Float,
    val rationale: String,
    val complexityProfile: TaskComplexityProfile = TaskComplexityProfile(
        complexityScore = 0.2f,
        requiresVision = false,
        requiresInternet = false,
        isPrivacySensitive = false,
        latencyRequirement = "LOW"
    )
)

/**
 * ModelRouter.
 * Phase 9 Multi-Tier Intelligent Model & Execution Routing Engine:
 * 1. NO-AI Deterministic Commands (Flashlight, Back, Home, Volume, App Launch)
 * 2. SKILL-FIRST (Verified reusable local skills)
 * 3. LOCAL ARCHETYPES (Standard device workflows)
 * 4. LOCAL SLM MODEL (On-device reasoning)
 * 5. LOCAL VISION (Accessibility + OCR + Icon heuristics)
 * 6. GEMINI TEACHER (Only if permitted, online, and task is novel/complex)
 * 7. SAFE OFFLINE FALLBACK
 */
class ModelRouter(
    private val memoryRetriever: MemoryRetriever? = null,
    private val skillManager: SkillManager? = null,
    private val preferences: JarvisPreferences? = null,
    private val cloudUsagePolicy: CloudUsagePolicy? = null,
    private val offlineManager: OfflineManager? = null
) {

    /**
     * Determines the optimal execution routing for a given user task goal.
     */
    suspend fun routeTask(
        goal: String,
        currentScreen: UnifiedScreen? = null,
        availableSkills: List<SkillEntity> = emptyList(),
        forceLocalOnly: Boolean = false
    ): PlanRoutingResult {
        val lowerGoal = goal.lowercase().trim()

        // 1. Task Complexity & Telemetry Assessment
        val complexityProfile = assessTaskComplexity(lowerGoal, currentScreen)

        // 2. Deterministic No-LLM Direct Routing (Fastest, zero tokens)
        val deterministicMatch = IntentRouter.matchCommand(goal)
        if (deterministicMatch.isMatched) {
            return PlanRoutingResult(
                decision = RoutingDecision.NO_AI_DETERMINISTIC,
                selectedSkill = null,
                estimatedLocalConfidence = 1.0f,
                rationale = "Direct Android API/Intent route: ${deterministicMatch.rationale}",
                complexityProfile = complexityProfile
            )
        }

        // 3. Skill-First Match (Search verified local skills in SkillManager)
        val matchingSkill = availableSkills.firstOrNull { skill ->
            skill.isEnabled && (
                skill.name.equals(goal, ignoreCase = true) ||
                lowerGoal.contains(skill.name.lowercase()) ||
                skill.description.contains(goal, ignoreCase = true)
            )
        }

        if (matchingSkill != null && matchingSkill.confidence >= 0.80f && matchingSkill.successRate >= 0.65f) {
            return PlanRoutingResult(
                decision = RoutingDecision.LOCAL_SKILL_REPLAY,
                selectedSkill = matchingSkill,
                estimatedLocalConfidence = matchingSkill.confidence,
                rationale = "High-confidence local skill match: '${matchingSkill.name}' (Success: ${(matchingSkill.successRate * 100).toInt()}%)",
                complexityProfile = complexityProfile
            )
        }

        // 4. Standard Device Archetypes (YouTube, Settings, WhatsApp, Chrome, Camera, Phone)
        val isStandardArchetype = lowerGoal.contains("youtube") ||
                lowerGoal.contains("chrome") ||
                lowerGoal.contains("setting") ||
                lowerGoal.contains("bluetooth") ||
                lowerGoal.contains("wifi") ||
                lowerGoal.contains("calculator") ||
                lowerGoal.contains("calculate") ||
                lowerGoal.contains("whatsapp") ||
                lowerGoal.contains("gallery") ||
                lowerGoal.contains("flashlight") ||
                lowerGoal.contains("torch") ||
                lowerGoal.contains("battery") ||
                lowerGoal.startsWith("open ") ||
                lowerGoal.startsWith("call ") ||
                lowerGoal.startsWith("sms ") ||
                lowerGoal == "back" || lowerGoal == "home"

        if (isStandardArchetype) {
            return PlanRoutingResult(
                decision = RoutingDecision.LOCAL_ARCHETYPE_PLAN,
                selectedSkill = null,
                estimatedLocalConfidence = 0.92f,
                rationale = "Matched native Android device archetype locally without cloud latency.",
                complexityProfile = complexityProfile
            )
        }

        // 5. Offline Intelligence & Internet Requirement Check
        val isOnline = offlineManager?.isOnline() ?: true
        if (!isOnline) {
            if (complexityProfile.requiresInternet) {
                return PlanRoutingResult(
                    decision = RoutingDecision.REQUIRES_INTERNET_NOTICE,
                    selectedSkill = null,
                    estimatedLocalConfidence = 0.0f,
                    rationale = "Task requires live internet access, but device is currently offline.",
                    complexityProfile = complexityProfile
                )
            } else {
                return PlanRoutingResult(
                    decision = RoutingDecision.OFFLINE_LOCAL_FALLBACK,
                    selectedSkill = null,
                    estimatedLocalConfidence = 0.75f,
                    rationale = "Offline Mode: Executing locally via on-device SLM and device APIs.",
                    complexityProfile = complexityProfile
                )
            }
        }

        if (forceLocalOnly) {
            return PlanRoutingResult(
                decision = RoutingDecision.LOCAL_REASONER,
                selectedSkill = null,
                estimatedLocalConfidence = 0.80f,
                rationale = "Local-Only execution forced by active user configuration.",
                complexityProfile = complexityProfile
            )
        }

        // 6. Cloud Policy and Gemini Escalation Evaluation
        val (isCloudPermitted, cloudReason) = cloudUsagePolicy?.isCloudRequestPermitted(
            isVision = complexityProfile.requiresVision,
            isBackground = false
        ) ?: Pair(true, "")

        val isKeyConfigured = preferences?.geminiApiKey?.isNotBlank() == true &&
                preferences.geminiApiKey != "MY_GEMINI_API_KEY"

        if (isCloudPermitted && isKeyConfigured && preferences?.isGeminiTeacherEnabled == true) {
            // Escalate complex tasks, deep research, or unfamiliar visual UIs
            if (complexityProfile.complexityScore >= 0.65f) {
                return PlanRoutingResult(
                    decision = if (complexityProfile.requiresVision) RoutingDecision.GEMINI_VISION_FALLBACK else RoutingDecision.GEMINI_TEACHER_FALLBACK,
                    selectedSkill = null,
                    estimatedLocalConfidence = 0.50f,
                    rationale = "Complex multi-step task (Score ${complexityProfile.complexityScore}). Escalating to Gemini Teacher.",
                    complexityProfile = complexityProfile
                )
            }
        }

        // 7. Default to On-Device SLM Local Reasoner
        return PlanRoutingResult(
            decision = RoutingDecision.LOCAL_REASONER,
            selectedSkill = null,
            estimatedLocalConfidence = 0.85f,
            rationale = "Local Brain SLM: Routine conversational or device automation task.",
            complexityProfile = complexityProfile
        )
    }

    private fun assessTaskComplexity(lowerGoal: String, screen: UnifiedScreen?): TaskComplexityProfile {
        val requiresInternet = lowerGoal.contains("search web") ||
                lowerGoal.contains("google search") ||
                lowerGoal.contains("latest news") ||
                lowerGoal.contains("current weather") ||
                lowerGoal.contains("stock price") ||
                lowerGoal.contains("research online")

        val requiresVision = lowerGoal.contains("what is on screen") ||
                lowerGoal.contains("describe screen") ||
                lowerGoal.contains("read screen") ||
                lowerGoal.contains("look at") ||
                (screen != null && screen.elements.isEmpty())

        val isPrivacySensitive = lowerGoal.contains("password") ||
                lowerGoal.contains("pin") ||
                lowerGoal.contains("bank") ||
                lowerGoal.contains("payment") ||
                lowerGoal.contains("credit card")

        var score = 0.2f
        if (lowerGoal.contains("explain") || lowerGoal.contains("analyze") || lowerGoal.contains("summarize")) score += 0.3f
        if (lowerGoal.contains("and then") || lowerGoal.contains("after that") || lowerGoal.contains("step")) score += 0.3f
        if (requiresInternet) score += 0.2f
        if (requiresVision) score += 0.2f

        return TaskComplexityProfile(
            complexityScore = score.coerceIn(0.0f, 1.0f),
            requiresVision = requiresVision,
            requiresInternet = requiresInternet,
            isPrivacySensitive = isPrivacySensitive,
            latencyRequirement = if (score < 0.4f) "LOW" else "NORMAL"
        )
    }
}
