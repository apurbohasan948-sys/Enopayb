package com.example.core.learning

import com.example.core.agent.StepExecutionRecord
import com.example.data.local.entity.ExperienceEntity

enum class EvaluationGrade {
    HIGHLY_RELIABLE, // 80 - 100
    RELIABLE,        // 60 - 79
    UNCERTAIN,       // 40 - 59
    UNRELIABLE       // 0 - 39
}

data class ExperienceEvaluation(
    val score: Int,                    // 0 - 100
    val grade: EvaluationGrade,
    val isEligibleForSkillCandidate: Boolean,
    val breakdown: Map<String, Int>,
    val reasons: List<String>
)

/**
 * ExperienceEvaluator.
 * Objectively scores task execution records based on:
 * - Execution success & completion
 * - Screen transition verification
 * - Retries and recovery steps count
 * - Target confidence
 * - User correction penalties
 * - Consistency and loop-free progress
 */
class ExperienceEvaluator {

    fun evaluateExperience(
        isSuccess: Boolean,
        stepRecords: List<StepExecutionRecord>,
        hasUserCorrection: Boolean = false,
        hadRecovery: Boolean = false,
        recoverySuccess: Boolean = false,
        consecutiveActionLoops: Int = 0,
        averageTargetConfidence: Float = 0.95f,
        durationMs: Long = 0L
    ): ExperienceEvaluation {
        val breakdown = mutableMapOf<String, Int>()
        val reasons = mutableListOf<String>()

        var score = 0

        // 1. Overall Task Success (Max 40 points)
        if (isSuccess && stepRecords.isNotEmpty()) {
            val allStepsSucceeded = stepRecords.all { it.result.success }
            if (allStepsSucceeded) {
                score += 40
                breakdown["Execution Success"] = 40
                reasons.add("All execution steps succeeded without failure (+40)")
            } else {
                score += 25
                breakdown["Partial Execution Success"] = 25
                reasons.add("Task completed with intermediate step retries (+25)")
            }
        } else {
            breakdown["Execution Failure"] = 0
            reasons.add("Task execution failed (0)")
        }

        // 2. Transition Verification (Max 30 points)
        val verifiedSteps = stepRecords.count { it.isVerified }
        val totalSteps = stepRecords.size.coerceAtLeast(1)
        val verifiedRatio = verifiedSteps.toFloat() / totalSteps.toFloat()
        val verificationPoints = (verifiedRatio * 30).toInt()
        score += verificationPoints
        breakdown["Transition Verification"] = verificationPoints
        reasons.add("Verified $verifiedSteps/$totalSteps state transitions (+$verificationPoints)")

        // 3. Target Resolution Confidence (Max 20 points)
        val confidencePoints = (averageTargetConfidence * 20).toInt().coerceIn(0, 20)
        score += confidencePoints
        breakdown["Target Confidence"] = confidencePoints
        reasons.add("Average target confidence ${(averageTargetConfidence * 100).toInt()}% (+$confidencePoints)")

        // 4. Recovery & Retries Impact (+10 for successful recovery, -15 if unrecovered)
        if (hadRecovery) {
            if (recoverySuccess) {
                score += 5
                breakdown["Successful Recovery"] = 5
                reasons.add("Engaged multimodal recovery and successfully recovered (+5)")
            } else {
                score -= 15
                breakdown["Unsuccessful Recovery"] = -15
                reasons.add("Recovery attempted but failed (-15)")
            }
        } else {
            score += 10
            breakdown["Zero Recovery Clean Run"] = 10
            reasons.add("Clean single-shot execution with zero recovery needed (+10)")
        }

        // 5. User Correction Penalty (-40 points)
        if (hasUserCorrection) {
            score -= 40
            breakdown["User Negative Correction"] = -40
            reasons.add("User manually corrected an incorrect action (-40)")
        }

        // 6. Action Loop Penalty
        if (consecutiveActionLoops > 0) {
            val loopPenalty = (consecutiveActionLoops * 15).coerceAtMost(30)
            score -= loopPenalty
            breakdown["Action Loops"] = -loopPenalty
            reasons.add("Action loop detected (-$loopPenalty)")
        }

        // Final score normalization
        val finalScore = score.coerceIn(0, 100)

        val grade = when {
            finalScore >= 80 -> EvaluationGrade.HIGHLY_RELIABLE
            finalScore >= 60 -> EvaluationGrade.RELIABLE
            finalScore >= 40 -> EvaluationGrade.UNCERTAIN
            else -> EvaluationGrade.UNRELIABLE
        }

        // Must score >= 75 and be verified success to be promoted to skill candidate
        val eligibleForCandidate = isSuccess && finalScore >= 75 && !hasUserCorrection && verifiedRatio >= 0.8f

        return ExperienceEvaluation(
            score = finalScore,
            grade = grade,
            isEligibleForSkillCandidate = eligibleForCandidate,
            breakdown = breakdown,
            reasons = reasons
        )
    }

    fun evaluateStoredExperience(experience: ExperienceEntity): ExperienceEvaluation {
        return evaluateExperience(
            isSuccess = experience.isSuccess,
            stepRecords = emptyList(),
            hadRecovery = experience.recoveryStrategy != null,
            recoverySuccess = experience.isSuccess && experience.recoveryStrategy != null,
            averageTargetConfidence = experience.confidence
        )
    }
}
