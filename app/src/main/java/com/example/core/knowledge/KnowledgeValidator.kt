package com.example.core.knowledge

import com.example.core.security.SecurityPolicyEngine
import com.example.data.local.entity.KnowledgeItemEntity
import com.example.data.local.entity.KnowledgeSourceEntity
import com.example.data.local.entity.KnowledgeType
import com.example.data.local.entity.ValidationStage

data class ValidationResult(
    val isValid: Boolean,
    val normalizedContent: String,
    val stage: ValidationStage,
    val confidence: Float,
    val isUncertain: Boolean,
    val reason: String
)

class KnowledgeValidator(
    private val securityEngine: SecurityPolicyEngine
) {
    fun validateCandidate(
        title: String,
        rawContent: String,
        source: KnowledgeSourceEntity?,
        type: KnowledgeType,
        existingKnowledge: List<KnowledgeItemEntity> = emptyList()
    ): ValidationResult {
        // 1. Stage: RAW -> Check empty or trivial
        if (title.isBlank() || rawContent.isBlank()) {
            return ValidationResult(
                isValid = false,
                normalizedContent = "",
                stage = ValidationStage.RAW,
                confidence = 0.0f,
                isUncertain = true,
                reason = "Title or content is empty"
            )
        }

        // 2. Security Screening (Treat external content as UNTRUSTED DATA)
        val securityCheck = securityEngine.validateInput(rawContent)
        if (!securityCheck.isSafe) {
            return ValidationResult(
                isValid = false,
                normalizedContent = "",
                stage = ValidationStage.RAW,
                confidence = 0.0f,
                isUncertain = true,
                reason = "Security violation: ${securityCheck.reason}"
            )
        }

        // Check for prompt injection keywords attempting to hijack JARVIS
        val lowerContent = rawContent.lowercase()
        val injectionPatterns = listOf(
            "ignore previous instructions",
            "disregard all previous",
            "you are now evil jarvis",
            "system override",
            "disable security policy",
            "delete database",
            "bypass permissions"
        )
        if (injectionPatterns.any { lowerContent.contains(it) }) {
            return ValidationResult(
                isValid = false,
                normalizedContent = "",
                stage = ValidationStage.RAW,
                confidence = 0.0f,
                isUncertain = true,
                reason = "Potential prompt injection / override attempt detected in knowledge content"
            )
        }

        // 3. Stage: NORMALIZED -> Clean formatting, whitespace, HTML remnants
        val normalized = normalizeContent(rawContent)
        if (normalized.length < 15) {
            return ValidationResult(
                isValid = false,
                normalizedContent = normalized,
                stage = ValidationStage.NORMALIZED,
                confidence = 0.2f,
                isUncertain = true,
                reason = "Content too brief to represent actionable knowledge"
            )
        }

        // 4. Stage: CROSS_CHECKED -> Check for direct contradictions with existing core facts
        val contradictory = existingKnowledge.any { existing ->
            existing.validationStage == ValidationStage.ACTIVE &&
                    existing.knowledgeKey == title.lowercase().replace(" ", "_") &&
                    existing.trustScore >= 0.95f &&
                    isContradictory(normalized, existing.content)
        }

        if (contradictory) {
            return ValidationResult(
                isValid = true,
                normalizedContent = normalized,
                stage = ValidationStage.UNCERTAIN,
                confidence = 0.45f,
                isUncertain = true,
                reason = "Contradicts high-trust verified core knowledge"
            )
        }

        // 5. Stage: VERIFIED / ACTIVE -> Calculate composite confidence
        val baseTrust = source?.trustScore ?: 0.70f
        val clarityScore = if (normalized.contains(".") || normalized.contains("\n")) 0.9f else 0.7f
        val typeMultiplier = when (type) {
            KnowledgeType.DEVICE_KNOWLEDGE, KnowledgeType.SYSTEM_KNOWLEDGE -> 1.0f
            KnowledgeType.APP_BEHAVIOR, KnowledgeType.PROCEDURE -> 0.95f
            KnowledgeType.FACT, KnowledgeType.SKILL_GUIDANCE -> 0.90f
            KnowledgeType.RECOVERY_PATTERN, KnowledgeType.USER_PREFERENCE -> 0.85f
            KnowledgeType.TECHNICAL_KNOWLEDGE -> 0.88f
        }

        val calculatedConfidence = (baseTrust * 0.6f + clarityScore * 0.4f) * typeMultiplier

        val finalStage = if (calculatedConfidence >= 0.75f) {
            ValidationStage.ACTIVE
        } else if (calculatedConfidence >= 0.60f) {
            ValidationStage.VERIFIED
        } else {
            ValidationStage.UNCERTAIN
        }

        val isUncertain = finalStage == ValidationStage.UNCERTAIN

        return ValidationResult(
            isValid = true,
            normalizedContent = normalized,
            stage = finalStage,
            confidence = calculatedConfidence.coerceIn(0.1f, 1.0f),
            isUncertain = isUncertain,
            reason = "Validated through pipeline to stage $finalStage (confidence: ${"%.2f".format(calculatedConfidence)})"
        )
    }

    private fun normalizeContent(raw: String): String {
        return raw
            .replace(Regex("<[^>]*>"), "") // Strip HTML
            .replace(Regex("[ \\t]+"), " ") // Normalize spaces
            .replace(Regex("\\n{3,}"), "\n\n") // Max 2 consecutive newlines
            .trim()
    }

    private fun isContradictory(newContent: String, existingContent: String): Boolean {
        val newLower = newContent.lowercase()
        val existLower = existingContent.lowercase()
        // Simple negation check on overlapping keywords
        if (existLower.contains("not possible") && (newLower.contains("always possible") || newLower.contains("supported directly without"))) return true
        if (existLower.contains("forbidden") && newLower.contains("allowed without restriction")) return true
        return false
    }
}
