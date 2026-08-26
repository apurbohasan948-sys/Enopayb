package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExperienceSource {
    LOCAL_PLANNER,
    GEMINI_TEACHER,
    USER_INTERACTIVE
}

@Entity(tableName = "experiences")
data class ExperienceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goal: String,
    val appPackage: String,
    val initialScreenSummary: String,
    val actionsTakenJson: String, // JSON array of steps executed
    val verificationSummary: String,
    val isSuccess: Boolean,
    val failedStrategy: String? = null,
    val recoveryStrategy: String? = null,
    val durationMs: Long = 0L,
    val confidence: Float = 1.0f,
    val source: ExperienceSource = ExperienceSource.LOCAL_PLANNER,
    val timestamp: Long = System.currentTimeMillis()
)
