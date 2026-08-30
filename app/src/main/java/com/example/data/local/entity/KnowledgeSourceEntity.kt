package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class KnowledgeSourceType {
    OFFICIAL_DOCUMENTATION,
    TRUSTED_WEBSITE,
    USER_PROVIDED,
    GEMINI_TEACHING,
    EXPERIENCE,
    VERIFIED_SKILL
}

enum class SourceStatus {
    ACTIVE,
    FLAGGED,
    DEPRECATED
}

@Entity(tableName = "knowledge_sources")
data class KnowledgeSourceEntity(
    @PrimaryKey
    val sourceId: String,
    val sourceType: KnowledgeSourceType,
    val sourceUrl: String?,
    val title: String,
    val retrievedAt: Long = System.currentTimeMillis(),
    val contentHash: String,
    val trustScore: Float = 0.8f,
    val lastVerified: Long = System.currentTimeMillis(),
    val status: SourceStatus = SourceStatus.ACTIVE
)
