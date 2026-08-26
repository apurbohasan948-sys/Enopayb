package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gemini_teacher_sessions")
data class GeminiTeacherSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userGoal: String,
    val lowConfidenceReason: String,
    val teacherModel: String,
    val structuredPlanJson: String,
    val wasExecuted: Boolean = false,
    val executionSuccessful: Boolean = false,
    val skillExtracted: Boolean = false,
    val generatedSkillName: String? = null,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
