package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class HealthSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

@Entity(tableName = "health_events")
data class HealthEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val component: String, // e.g. "GEMINI_MODEL", "LOCAL_SLM", "DATABASE", "ACCESSIBILITY", "WORKER", "NETWORK"
    val severity: HealthSeverity = HealthSeverity.INFO,
    val description: String,
    val recoveryAttempted: Boolean = false,
    val recoverySuccessful: Boolean = false,
    val recoveryActionTaken: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
