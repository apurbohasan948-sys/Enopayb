package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SkillRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class SkillSource {
    BUILTIN,
    TEACHER,
    EXPERIENCE_EXTRACTED,
    USER_CUSTOM
}

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val requiredPermissions: String,
    val inputSchema: String,
    val outputSchema: String,
    val riskLevel: SkillRiskLevel = SkillRiskLevel.LOW,
    val procedure: String,
    val verificationMethod: String,
    val version: String = "1.0.0",
    val isEnabled: Boolean = true,
    val executionCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val successRate: Float = 1.0f,
    val confidence: Float = 0.95f,
    val lastExecutedAt: Long = 0L,
    val lastSuccessAt: Long = 0L,
    val isLearnedFromExperience: Boolean = false,
    val source: SkillSource = SkillSource.BUILTIN,
    val previousVersionProcedure: String? = null
) {
    val isBuiltIn: Boolean
        get() = source == SkillSource.BUILTIN && !isLearnedFromExperience
}
