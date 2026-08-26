package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryCategory {
    USER_PREFERENCE,
    PERSONAL_CONTEXT,
    TASK_HISTORY,
    EXPERIENCE,
    SKILL,
    KNOWLEDGE,
    APP_PATTERN,
    SCREEN_PATTERN,
    USER_CORRECTION,
    IMPORTANT_FACT,
    USER_PROFILE,
    CONVERSATION_SUMMARY,
    SECURITY_EVENT
}

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: MemoryCategory,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val source: String = "User Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val importance: Float = 0.5f,
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0L
)
