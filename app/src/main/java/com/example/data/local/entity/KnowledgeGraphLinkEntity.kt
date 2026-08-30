package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_graph_links")
data class KnowledgeGraphLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromType: String,
    val fromId: String,
    val relation: String,
    val toType: String,
    val toId: String,
    val weight: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)
