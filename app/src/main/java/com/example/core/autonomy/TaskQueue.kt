package com.example.core.autonomy

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AutonomousTaskEntity
import com.example.data.local.entity.AutonomousTaskPriority
import com.example.data.local.entity.AutonomousTaskStatus
import com.example.data.local.entity.AutonomousTaskType
import com.example.data.local.entity.SkillRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TaskQueue(
    private val dao: JarvisDao
) {
    val allTasks: Flow<List<AutonomousTaskEntity>> = dao.getAllAutonomousTasks()

    private val _activeRunningTask = MutableStateFlow<AutonomousTaskEntity?>(null)
    val activeRunningTask: StateFlow<AutonomousTaskEntity?> = _activeRunningTask.asStateFlow()

    suspend fun enqueueTask(
        goal: String,
        taskType: AutonomousTaskType = AutonomousTaskType.USER_PROMPTED,
        priority: AutonomousTaskPriority = AutonomousTaskPriority.MEDIUM,
        riskLevel: SkillRiskLevel = SkillRiskLevel.LOW,
        requiresConfirmation: Boolean = false,
        targetAppPackage: String? = null,
        maxRetries: Int = 3
    ): Long = withContext(Dispatchers.IO) {
        val task = AutonomousTaskEntity(
            goal = goal,
            taskType = taskType,
            priority = priority,
            status = AutonomousTaskStatus.QUEUED,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            targetAppPackage = targetAppPackage,
            maxRetries = maxRetries,
            createdAt = System.currentTimeMillis()
        )
        dao.insertAutonomousTask(task)
    }

    suspend fun getTaskById(taskId: Long): AutonomousTaskEntity? = withContext(Dispatchers.IO) {
        dao.getAutonomousTaskById(taskId)
    }

    suspend fun updateTaskStatus(
        taskId: Long,
        status: AutonomousTaskStatus,
        failureReason: String? = null,
        blockingReason: String? = null,
        resultSummary: String? = null,
        verificationProof: String? = null,
        durationMs: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val task = dao.getAutonomousTaskById(taskId) ?: return@withContext
        val updated = task.copy(
            status = status,
            failureReason = failureReason ?: task.failureReason,
            blockingReason = blockingReason ?: task.blockingReason,
            resultSummary = resultSummary ?: task.resultSummary,
            verificationProof = verificationProof ?: task.verificationProof,
            durationMs = if (durationMs > 0) durationMs else task.durationMs,
            startedAt = if (status == AutonomousTaskStatus.RUNNING && task.startedAt == 0L) System.currentTimeMillis() else task.startedAt,
            completedAt = if (status in listOf(AutonomousTaskStatus.COMPLETED, AutonomousTaskStatus.FAILED, AutonomousTaskStatus.CANCELLED, AutonomousTaskStatus.BLOCKED)) System.currentTimeMillis() else task.completedAt
        )
        dao.updateAutonomousTask(updated)
        if (status == AutonomousTaskStatus.RUNNING) {
            _activeRunningTask.value = updated
        } else if (_activeRunningTask.value?.id == taskId) {
            _activeRunningTask.value = null
        }
    }

    suspend fun updatePlannedActions(taskId: Long, actionsJson: String) = withContext(Dispatchers.IO) {
        val task = dao.getAutonomousTaskById(taskId) ?: return@withContext
        dao.updateAutonomousTask(task.copy(plannedActionsJson = actionsJson))
    }

    suspend fun appendExecutionLog(taskId: Long, logEntry: String) = withContext(Dispatchers.IO) {
        val task = dao.getAutonomousTaskById(taskId) ?: return@withContext
        val existing = task.executionLogsJson
        val updatedLogs = if (existing == "[]" || existing.isBlank()) {
            "[\"${logEntry.replace("\"", "\\\"")}\"]"
        } else {
            existing.removeSuffix("]") + ",\"${logEntry.replace("\"", "\\\"")}\"]"
        }
        dao.updateAutonomousTask(task.copy(executionLogsJson = updatedLogs))
    }

    suspend fun cancelTask(taskId: Long, reason: String = "User Cancelled") = withContext(Dispatchers.IO) {
        TaskLockManager.releaseAllForTask(taskId)
        updateTaskStatus(taskId, AutonomousTaskStatus.CANCELLED, failureReason = reason)
    }

    suspend fun cancelAllTasks(reason: String = "Emergency Stop") = withContext(Dispatchers.IO) {
        TaskLockManager.emergencyReleaseAll()
        _activeRunningTask.value = null
    }

    suspend fun deleteTask(task: AutonomousTaskEntity) = withContext(Dispatchers.IO) {
        dao.deleteAutonomousTask(task)
    }

    suspend fun clearAllTasks() = withContext(Dispatchers.IO) {
        dao.clearAllAutonomousTasks()
        _activeRunningTask.value = null
    }
}
