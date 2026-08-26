package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class KnowledgeStatus {
    ACTIVE,
    PENDING_APPROVAL,
    ARCHIVED,
    SUPERSEDED
}

@Entity(tableName = "knowledge_versions")
data class KnowledgeVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val knowledgeKey: String,
    val topic: String,
    val version: Int = 1,
    val content: String,
    val summary: String,
    val sourceUrl: String? = null,
    val sourceQualityScore: Float = 0.8f,
    val confidence: Float = 0.9f,
    val status: KnowledgeStatus = KnowledgeStatus.ACTIVE,
    val oldVersionContent: String? = null,
    val changeReason: String = "Initial Creation",
    val isAutoUpdated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
