package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_corrections")
data class UserCorrectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userGoal: String,
    val previousAssumption: String,
    val userCorrection: String,
    val correctedAction: String,
    val actualTarget: String,
    val appPackage: String,
    val screenContext: String,
    val confidence: Float = 1.0f,
    val appliedCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
