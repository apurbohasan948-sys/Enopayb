package com.example.core.learning

import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity

data class LearnedSkillCandidate(
    val title: String,
    val summary: String,
    val structuredFact: String,
    val sourceQuery: String,
    val validationScore: Float,
    val isValidated: Boolean
)

object TeacherStudentPipeline {

    /**
     * Inspects a Gemini Teacher output and derives structured local knowledge if safety checks pass.
     */
    fun processTeacherResponse(
        userQuery: String,
        teacherOutput: String
    ): LearnedSkillCandidate? {
        // Quality & Safety Filter
        if (teacherOutput.length < 15 || teacherOutput.contains("Error") || teacherOutput.contains("unavailable")) {
            return null
        }

        // Check for PII / sensitive markers (never learn passwords, credentials)
        val lower = teacherOutput.lowercase()
        if (lower.contains("password") || lower.contains("pin") || lower.contains("token") || lower.contains("credit card")) {
            return null
        }

        val firstSentence = teacherOutput.substringBefore(".").trim()
        val summary = if (firstSentence.length in 10..120) firstSentence else teacherOutput.take(100)

        return LearnedSkillCandidate(
            title = "Teacher Knowledge: ${userQuery.take(30)}...",
            summary = summary,
            structuredFact = teacherOutput,
            sourceQuery = userQuery,
            validationScore = 0.92f,
            isValidated = true
        )
    }

    fun convertToKnowledgeChunk(candidate: LearnedSkillCandidate): KnowledgeChunkEntity {
        return KnowledgeChunkEntity(
            title = candidate.title,
            sourceDocument = "teacher_learning_pipeline.md",
            content = candidate.structuredFact,
            tags = "teacher, learned, offline-knowledge"
        )
    }

    fun convertToMemoryEntity(candidate: LearnedSkillCandidate): MemoryEntity {
        return MemoryEntity(
            category = MemoryCategory.KNOWLEDGE,
            key = candidate.title,
            value = candidate.summary,
            confidence = candidate.validationScore,
            source = "Gemini Teacher Supervisor"
        )
    }
}
