package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AutonomousTaskStatus {
    QUEUED,
    RUNNING,
    WAITING,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED,
    BLOCKED
}

enum class AutonomousTaskType {
    USER_PROMPTED,
    SCHEDULED,
    WEB_RESEARCH,
    MEMORY_MAINTENANCE,
    SKILL_REPAIR,
    PROACTIVE_ASSISTANCE
}

@Entity(tableName = "autonomous_tasks")
data class AutonomousTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goal: String,
    val taskType: AutonomousTaskType = AutonomousTaskType.USER_PROMPTED,
    val priority: AutonomousTaskPriority = AutonomousTaskPriority.MEDIUM,
    val status: AutonomousTaskStatus = AutonomousTaskStatus.QUEUED,
    val riskLevel: SkillRiskLevel = SkillRiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val plannedActionsJson: String = "[]",
    val executionLogsJson: String = "[]",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val failureReason: String? = null,
    val blockingReason: String? = null,
    val targetAppPackage: String? = null,
    val durationMs: Long = 0L,
    val resultSummary: String? = null,
    val verificationProof: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = 0L,
    val completedAt: Long = 0L
)
