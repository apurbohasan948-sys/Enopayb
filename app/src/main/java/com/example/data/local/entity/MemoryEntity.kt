package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryCategory {
    USER_PREFERENCE,
    USER_PROFILE,
    CONVERSATION_SUMMARY,
    IMPORTANT_FACT,
    SKILL,
    KNOWLEDGE,
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
    val isEncrypted: Boolean = false
)
