package com.example.core.autonomy

import com.example.data.local.entity.SkillRiskLevel

data class PolicyDecision(
    val isAllowed: Boolean,
    val requiresConfirmation: Boolean,
    val riskLevel: SkillRiskLevel,
    val reason: String,
    val matchedRule: String? = null
)

data class AutonomyPolicyConfig(
    val allowedTasks: Set<String> = setOf(
        "check weather",
        "research a topic",
        "update knowledge",
        "organize JARVIS memory",
        "summarize saved information",
        "check scheduled tasks",
        "search web",
        "read screen",
        "read notifications",
        "battery status",
        "system maintenance"
    ),
    val blockedTasks: Set<String> = setOf(
        "delete system files",
        "format disk",
        "bypass security",
        "wipe memory",
        "leak passwords",
        "financial transfer",
        "unauthorized call"
    ),
    val allowedApps: Set<String> = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.docs",
        "com.android.chrome",
        "com.google.android.calendar",
        "com.google.android.gm",
        "com.android.settings",
        "com.android.calculator2",
        "com.google.android.deskclock"
    ),
    val blockedApps: Set<String> = setOf(
        "com.android.vending.billing",
        "com.google.android.apps.walletnfcrel",
        "com.paypal.android.p2pmobile"
    ),
    val allowedWebsites: Set<String> = setOf(
        "wikipedia.org",
        "github.com",
        "android.com",
        "google.com",
        "stackoverflow.com",
        "developer.android.com",
        "gov",
        "edu"
    ),
    val blockedWebsites: Set<String> = setOf(
        "darkweb",
        "phishing",
        "torrent"
    ),
    val maxTaskDurationSec: Long = 180L,
    val maxActionsPerTask: Int = 12,
    val maxRetries: Int = 3,
    val maxNetworkKbytesPerTask: Long = 10_000L,
    val minBatteryLevelForAutonomy: Int = 15,
    val autoUpdateKnowledgeOnHighConfidence: Boolean = true,
    val highConfidenceThreshold: Float = 0.85f
)

object AutonomyPolicy {

    private val SENSITIVE_KEYWORDS = listOf(
        "send message", "send sms", "send whatsapp",
        "make call", "call phone", "dial number",
        "install application", "install app", "download apk",
        "uninstall application", "uninstall app", "delete app",
        "change security settings", "modify pin", "change password",
        "pay", "transfer money", "credit card", "bank", "buy", "purchase"
    )

    /**
     * Evaluates whether a proposed autonomous goal or task action is permitted,
     * requires explicit user confirmation, or is strictly blocked.
     */
    fun evaluateTask(
        goal: String,
        targetAppPackage: String? = null,
        targetWebsite: String? = null,
        mode: AutonomyMode = AutonomyMode.AUTONOMOUS,
        config: AutonomyPolicyConfig = AutonomyPolicyConfig()
    ): PolicyDecision {
        val lowerGoal = goal.lowercase().trim()

        // 1. Check explicit blocked tasks
        for (blocked in config.blockedTasks) {
            if (lowerGoal.contains(blocked.lowercase())) {
                return PolicyDecision(
                    isAllowed = false,
                    requiresConfirmation = false,
                    riskLevel = SkillRiskLevel.CRITICAL,
                    reason = "Task is on the Autonomy Policy BLOCKED list: '$blocked'",
                    matchedRule = "BLOCKED_TASK:$blocked"
                )
            }
        }

        // 2. Check blocked app packages
        if (!targetAppPackage.isNullOrBlank()) {
            for (blockedApp in config.blockedApps) {
                if (targetAppPackage.contains(blockedApp, ignoreCase = true)) {
                    return PolicyDecision(
                        isAllowed = false,
                        requiresConfirmation = false,
                        riskLevel = SkillRiskLevel.CRITICAL,
                        reason = "Target app package is blocked from autonomous interaction: '$targetAppPackage'",
                        matchedRule = "BLOCKED_APP:$blockedApp"
                    )
                }
            }
        }

        // 3. Check blocked websites
        if (!targetWebsite.isNullOrBlank()) {
            for (blockedSite in config.blockedWebsites) {
                if (targetWebsite.contains(blockedSite, ignoreCase = true)) {
                    return PolicyDecision(
                        isAllowed = false,
                        requiresConfirmation = false,
                        riskLevel = SkillRiskLevel.HIGH,
                        reason = "Target domain is blocked by web research policy: '$targetWebsite'",
                        matchedRule = "BLOCKED_SITE:$blockedSite"
                    )
                }
            }
        }

        // 4. Check sensitive operations requiring explicit user confirmation
        for (sensitive in SENSITIVE_KEYWORDS) {
            if (lowerGoal.contains(sensitive)) {
                return PolicyDecision(
                    isAllowed = true,
                    requiresConfirmation = true,
                    riskLevel = SkillRiskLevel.HIGH,
                    reason = "Action contains sensitive operation '$sensitive' and requires explicit user confirmation.",
                    matchedRule = "SENSITIVE_CONFIRMATION_REQUIRED"
                )
            }
        }

        // 5. Mode specific constraints
        return when (mode) {
            AutonomyMode.MANUAL -> {
                PolicyDecision(
                    isAllowed = true,
                    requiresConfirmation = true,
                    riskLevel = SkillRiskLevel.LOW,
                    reason = "System in MANUAL mode. All tasks require explicit step execution.",
                    matchedRule = "MODE_MANUAL"
                )
            }
            AutonomyMode.ASSISTED -> {
                val isHighRisk = lowerGoal.contains("call") || lowerGoal.contains("message") || lowerGoal.contains("settings")
                PolicyDecision(
                    isAllowed = true,
                    requiresConfirmation = isHighRisk,
                    riskLevel = if (isHighRisk) SkillRiskLevel.HIGH else SkillRiskLevel.MEDIUM,
                    reason = if (isHighRisk) "ASSISTED mode requires confirmation for moderate-risk workflows." else "Allowed in ASSISTED mode.",
                    matchedRule = "MODE_ASSISTED"
                )
            }
            AutonomyMode.AUTONOMOUS -> {
                // In AUTONOMOUS mode, approved tasks run freely, unlisted ones run with standard LOW/MEDIUM risk
                val isExplicitlyAllowed = config.allowedTasks.any { lowerGoal.contains(it.lowercase()) }
                PolicyDecision(
                    isAllowed = true,
                    requiresConfirmation = false,
                    riskLevel = if (isExplicitlyAllowed) SkillRiskLevel.LOW else SkillRiskLevel.MEDIUM,
                    reason = if (isExplicitlyAllowed) "Explicitly approved autonomous task." else "Standard low-risk autonomous task execution.",
                    matchedRule = if (isExplicitlyAllowed) "ALLOWLIST_APPROVED" else "DEFAULT_AUTONOMOUS"
                )
            }
        }
    }
}
