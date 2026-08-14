package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_chunks")
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceDocument: String,
    val content: String,
    val tags: String,
    val embeddingPreview: String = "[0.12, -0.45, 0.78, 0.33, -0.09...]",
    val timestamp: Long = System.currentTimeMillis()
)
