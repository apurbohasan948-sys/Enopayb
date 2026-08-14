package com.example.core.security

import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillRiskLevel

data class SecurityScanResult(
    val isSafe: Boolean,
    val riskScore: Int, // 0 to 100
    val riskLevel: SkillRiskLevel,
    val flaggedPatterns: List<String>,
    val sanitizedPrompt: String,
    val reason: String
)

object SecurityPolicyEngine {

    // Defensive patterns for prompt injection, jailbreaking, and memory poisoning
    private val PROMPT_INJECTION_PATTERNS = listOf(
        Regex("(?i)ignore (all )?(previous|prior) (instructions|prompts|rules)"),
        Regex("(?i)system prompt (leak|reveal|show|dump)"),
        Regex("(?i)you are now (unrestricted|jailbroken|dan|evil|root)"),
        Regex("(?i)bypass (security|permission|policy|auth)"),
        Regex("(?i)delete (all )?(system|database|root|files)"),
        Regex("(?i)reveal (api[ _]?key|password|token|secret)"),
        Regex("(?i)base64[ _]?decode.*exec"),
        Regex("(?i)sudo rm -rf"),
        Regex("(?i)chmod 777")
    )

    private val SUSPICIOUS_COMMANDS = listOf(
        "uninstall",
        "factory reset",
        "wipe data",
        "format disk",
        "kill background process",
        "inject touch"
    )

    /**
     * Evaluates incoming text against prompt injection and malicious instructions.
     */
    fun scanPrompt(rawPrompt: String): SecurityScanResult {
        val flagged = mutableListOf<String>()
        var calculatedScore = 5 // Baseline low

        for (pattern in PROMPT_INJECTION_PATTERNS) {
            if (pattern.containsMatchIn(rawPrompt)) {
                flagged.add("PROMPT_INJECTION: ${pattern.pattern}")
                calculatedScore += 45
            }
        }

        val lower = rawPrompt.lowercase()
        for (cmd in SUSPICIOUS_COMMANDS) {
            if (lower.contains(cmd)) {
                flagged.add("SUSPICIOUS_SYSTEM_KEYWORD: $cmd")
                calculatedScore += 30
            }
        }

        val riskScore = calculatedScore.coerceIn(0, 100)
        val riskLevel = when {
            riskScore >= 80 -> SkillRiskLevel.CRITICAL
            riskScore >= 50 -> SkillRiskLevel.HIGH
            riskScore >= 20 -> SkillRiskLevel.MEDIUM
            else -> SkillRiskLevel.LOW
        }

        val isSafe = riskScore < 50

        // Defensive sanitization: strip zero-width characters or dangerous control escapes
        val sanitized = rawPrompt.replace("[\u200B-\u200D\uFEFF]".toRegex(), "")

        val reason = if (!isSafe) {
            "Threat detected: Prompt injection or high-risk system command blocked by defensive shield."
        } else {
            "Prompt sanitized and validated by JARVIS Defensive Policy."
        }

        return SecurityScanResult(
            isSafe = isSafe,
            riskScore = riskScore,
            riskLevel = riskLevel,
            flaggedPatterns = flagged,
            sanitizedPrompt = sanitized,
            reason = reason
        )
    }

    /**
     * Evaluates risk level of an executed tool.
     */
    fun evaluateToolRisk(toolName: String, args: Map<String, String>): SkillRiskLevel {
        return when (toolName) {
            "open_app", "toggle_flashlight", "query_battery_status", "search_knowledge_rag", "security_audit_check" -> SkillRiskLevel.LOW
            "make_call", "send_message", "web_search", "clipboard_copy" -> SkillRiskLevel.MEDIUM
            "delete_memory", "clear_database", "install_app" -> SkillRiskLevel.HIGH
            "modify_security_policy", "credential_operation", "system_wipe" -> SkillRiskLevel.CRITICAL
            else -> SkillRiskLevel.MEDIUM
        }
    }

    /**
     * Determines whether user explicit confirmation is required before proceeding.
     */
    fun requiresUserConfirmation(riskLevel: SkillRiskLevel): Boolean {
        return riskLevel == SkillRiskLevel.HIGH || riskLevel == SkillRiskLevel.CRITICAL
    }
}
