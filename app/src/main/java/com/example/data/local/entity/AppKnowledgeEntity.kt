package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_knowledge")
data class AppKnowledgeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appName: String,
    val packageName: String,
    val version: String = "1.0",
    val knownScreensJson: String = "[]",
    val semanticTargetsJson: String = "[]",
    val commonActionsJson: String = "[]",
    val successfulSkillsJson: String = "[]",
    val failedStrategiesJson: String = "[]",
    val recoveryStrategiesJson: String = "[]",
    val lastVerified: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
