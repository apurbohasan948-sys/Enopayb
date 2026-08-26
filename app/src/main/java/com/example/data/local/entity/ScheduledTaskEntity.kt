package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ScheduleTriggerType {
    ONE_TIME,
    RECURRING,
    DELAYED,
    SCHEDULED_RESEARCH,
    MEMORY_MAINTENANCE
}

enum class AutonomousTaskPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    MAINTENANCE
}

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val instruction: String,
    val triggerType: ScheduleTriggerType = ScheduleTriggerType.ONE_TIME,
    val cronOrInterval: String = "", // e.g., "DAILY", "WEEKLY", "HOURLY", "INTERVAL_3600"
    val scheduledTimeMillis: Long = System.currentTimeMillis(),
    val lastExecutedMillis: Long = 0L,
    val isEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val riskLevel: SkillRiskLevel = SkillRiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val payloadJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)
