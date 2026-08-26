package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ResearchStatus {
    PLANNING,
    SEARCHING,
    EXTRACTING,
    ANALYZING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "web_research_records")
data class WebResearchRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val userGoal: String,
    val status: ResearchStatus = ResearchStatus.COMPLETED,
    val synthesizedSummary: String,
    val sourcesCount: Int = 0,
    val verifiedSourcesJson: String = "[]", // Array of SourceMetadata
    val keyFindingsJson: String = "[]",
    val confidence: Float = 0.85f,
    val durationMs: Long = 0L,
    val storedAsKnowledge: Boolean = false,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
