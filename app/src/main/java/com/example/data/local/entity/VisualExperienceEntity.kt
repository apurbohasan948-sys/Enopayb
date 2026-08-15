package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visual_experiences")
data class VisualExperienceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appPackage: String,
    val screenContext: String,
    val semanticRole: String,
    val visualDescription: String,
    val actionTaken: String,
    val result: String,
    val confidence: Float,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val source: String = "VISION",
    val timestamp: Long = System.currentTimeMillis()
)
