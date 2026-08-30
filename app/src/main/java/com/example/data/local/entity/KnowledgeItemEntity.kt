package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class KnowledgeType {
    FACT,
    PROCEDURE,
    APP_BEHAVIOR,
    TECHNICAL_KNOWLEDGE,
    DEVICE_KNOWLEDGE,
    SKILL_GUIDANCE,
    RECOVERY_PATTERN,
    USER_PREFERENCE,
    SYSTEM_KNOWLEDGE
}

enum class ValidationStage {
    RAW,
    NORMALIZED,
    CROSS_CHECKED,
    VERIFIED,
    ACTIVE,
    UNCERTAIN
}

@Entity(tableName = "knowledge_items")
data class KnowledgeItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val knowledgeKey: String,
    val title: String,
    val content: String,
    val summary: String,
    val knowledgeType: KnowledgeType = KnowledgeType.FACT,
    val validationStage: ValidationStage = ValidationStage.ACTIVE,
    val confidence: Float = 0.9f,
    val trustScore: Float = 0.85f,
    val sourceCount: Int = 1,
    val usageCount: Int = 0,
    val failureCount: Int = 0,
    val sourceId: String? = null,
    val sourceUrl: String? = null,
    val contentHash: String = "",
    val tags: String = "",
    val appPackage: String? = null,
    val appVersion: String? = null,
    val osVersion: String? = null,
    val expiryPolicy: String = "NEVER",
    val lastVerified: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
    val isUncertain: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
