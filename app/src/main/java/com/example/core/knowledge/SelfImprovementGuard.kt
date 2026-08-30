package com.example.core.knowledge

import com.example.core.security.SecurityPolicyEngine
import com.example.data.local.entity.SecurityEventEntity

enum class ImprovementDomain {
    KNOWLEDGE_BASE,
    SKILL_PROCEDURES,
    RETRIEVAL_WEIGHTS,
    TASK_STRATEGY,
    RECOVERY_PATTERNS,
    SECURITY_POLICY,
    SYSTEM_PERMISSIONS,
    USER_CONFIRMATION_RULES,
    CORE_APPLICATION_IDENTITY
}

data class GuardEvaluation(
    val isPermitted: Boolean,
    val domain: ImprovementDomain,
    val reason: String
)

class SelfImprovementGuard(
    private val securityEngine: SecurityPolicyEngine
) {
    fun evaluateProposedModification(
        targetDomain: ImprovementDomain,
        description: String
    ): GuardEvaluation {
        return when (targetDomain) {
            ImprovementDomain.KNOWLEDGE_BASE,
            ImprovementDomain.SKILL_PROCEDURES,
            ImprovementDomain.RETRIEVAL_WEIGHTS,
            ImprovementDomain.TASK_STRATEGY,
            ImprovementDomain.RECOVERY_PATTERNS -> {
                // Check if description tries to disguise a forbidden policy change
                val lower = description.lowercase()
                if (lower.contains("bypass permission") ||
                    lower.contains("disable confirmation") ||
                    lower.contains("ignore security") ||
                    lower.contains("override root safety")
                ) {
                    GuardEvaluation(
                        isPermitted = false,
                        domain = targetDomain,
                        reason = "Autonomous modification attempted to evade security guardrails."
                    )
                } else {
                    GuardEvaluation(
                        isPermitted = true,
                        domain = targetDomain,
                        reason = "Modification permitted under autonomous self-improvement boundaries."
                    )
                }
            }

            ImprovementDomain.SECURITY_POLICY,
            ImprovementDomain.SYSTEM_PERMISSIONS,
            ImprovementDomain.USER_CONFIRMATION_RULES,
            ImprovementDomain.CORE_APPLICATION_IDENTITY -> {
                GuardEvaluation(
                    isPermitted = false,
                    domain = targetDomain,
                    reason = "CRITICAL BOUNDARY: Self-improvement is strictly forbidden from modifying security policies, system permissions, user confirmation requirements, or core application identity."
                )
            }
        }
    }
}
