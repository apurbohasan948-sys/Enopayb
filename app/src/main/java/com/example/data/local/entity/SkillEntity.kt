package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SkillRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
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
    val lastExecutedAt: Long = 0L
)
