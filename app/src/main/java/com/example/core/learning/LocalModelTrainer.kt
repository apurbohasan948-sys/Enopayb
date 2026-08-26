package com.example.core.learning

import com.example.data.local.dao.JarvisDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrainingReadinessReport(
    val curatedExamplesCount: Int,
    val estimatedTokens: Long,
    val recommendedMethod: String,
    val targetModelArchitecture: String,
    val isReadyForLoRA: Boolean,
    val isReadyForDistillation: Boolean,
    val trainingStatusMessage: String
)

/**
 * LocalModelTrainer.
 * Truthful, concrete architecture for local model fine-tuning and distillation preparation.
 * Calculates dataset quality metrics and LoRA export parameters without claiming fake on-device training.
 */
class LocalModelTrainer(
    private val dao: JarvisDao
) {

    /**
     * Inspects curated training dataset and generates honest readiness telemetry.
     */
    suspend fun evaluateReadiness(): TrainingReadinessReport = withContext(Dispatchers.IO) {
        val examples = dao.getCuratedTrainingExamples()
        val count = examples.size
        val totalChars = examples.sumOf { it.inputInstruction.length + it.contextSummary.length + it.successfulPlanJson.length }
        val estimatedTokens = totalChars / 4L

        val isReadyForLoRA = count >= 50
        val isReadyForDistillation = count >= 200

        val status = when {
            count == 0 -> "Dataset empty. Collect verified task executions to prepare for model distillation."
            count < 50 -> "Dataset growing ($count/50 examples for initial LoRA adapter). Real-time experience learning active."
            count < 200 -> "LoRA adapter threshold reached ($count examples). Ready for off-device QLoRA fine-tuning."
            else -> "Comprehensive dataset ready ($count examples). Suitable for full edge distillation into Qwen2.5/SmolLM2."
        }

        TrainingReadinessReport(
            curatedExamplesCount = count,
            estimatedTokens = estimatedTokens,
            recommendedMethod = if (count < 100) "4-Bit QLoRA (Rank 16, Alpha 32)" else "Knowledge Distillation (GGUF Q4_K_M)",
            targetModelArchitecture = "Qwen2.5-1.5B-Instruct / SmolLM2-1.7B",
            isReadyForLoRA = isReadyForLoRA,
            isReadyForDistillation = isReadyForDistillation,
            trainingStatusMessage = status
        )
    }
}
