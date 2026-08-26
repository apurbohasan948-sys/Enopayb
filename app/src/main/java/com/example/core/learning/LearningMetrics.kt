package com.example.core.learning

data class LearningMetrics(
    val totalMemories: Int = 0,
    val totalExperiences: Int = 0,
    val successfulExperiences: Int = 0,
    val failedExperiences: Int = 0,
    val totalSkills: Int = 0,
    val learnedSkillsCount: Int = 0,
    val userCorrectionsCount: Int = 0,
    val geminiTeachingSessions: Int = 0,
    val trainingExamplesCount: Int = 0,
    val localExecutionCount: Int = 0,
    val geminiAssistedCount: Int = 0,
    val localAutonomyPercentage: Int = 100
) {
    val trainingReadinessScore: Int
        get() {
            val score = (trainingExamplesCount * 2 + successfulExperiences + totalMemories / 2).coerceIn(0, 100)
            return score
        }

    val isReadyForTraining: Boolean
        get() = trainingExamplesCount >= 10 || trainingReadinessScore >= 40
}
