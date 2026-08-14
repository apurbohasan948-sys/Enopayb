package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // USER, JARVIS, SYSTEM, TOOL
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val providerType: String = "LOCAL", // LOCAL, GEMINI, HYBRID, SYSTEM
    val toolCallInfo: String? = null,
    val latencyMs: Long = 0L,
    val language: String = "EN" // EN, BN, BANGLISH
)
