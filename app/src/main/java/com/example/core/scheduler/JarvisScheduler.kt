package com.example.core.scheduler

import com.example.core.autonomy.MasterStopManager
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AutonomousTaskPriority
import com.example.data.local.entity.AutonomousTaskType
import com.example.data.local.entity.ScheduleTriggerType
import com.example.data.local.entity.ScheduledTaskEntity
import com.example.data.local.entity.SkillRiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarvisScheduler(
    private val dao: JarvisDao,
    private val onExecuteScheduledTask: (suspend (ScheduledTaskEntity) -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val allScheduledTasks: Flow<List<ScheduledTaskEntity>> = dao.getAllScheduledTasks()
    val activeScheduledTasks: Flow<List<ScheduledTaskEntity>> = dao.getActiveScheduledTasks()

    private var schedulerJob: Job? = null

    init {
        startSchedulerLoop()
    }

    fun startSchedulerLoop() {
        schedulerJob?.cancel()
        schedulerJob = scope.launch {
            while (isActive) {
                try {
                    checkAndExecuteDueTasks()
                } catch (e: Exception) {}
                delay(30_000L) // Check every 30s
            }
        }
    }

    fun stopSchedulerLoop() {
        schedulerJob?.cancel()
    }

    suspend fun scheduleTask(
        title: String,
        instruction: String,
        triggerType: ScheduleTriggerType = ScheduleTriggerType.ONE_TIME,
        scheduledTimeMillis: Long = System.currentTimeMillis() + 60_000L,
        cronOrInterval: String = "",
        riskLevel: SkillRiskLevel = SkillRiskLevel.LOW,
        requiresConfirmation: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val task = ScheduledTaskEntity(
            title = title,
            instruction = instruction,
            triggerType = triggerType,
            cronOrInterval = cronOrInterval,
            scheduledTimeMillis = scheduledTimeMillis,
            isEnabled = true,
            isCompleted = false,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            createdAt = System.currentTimeMillis()
        )
        dao.insertScheduledTask(task)
    }

    suspend fun checkAndExecuteDueTasks() = withContext(Dispatchers.IO) {
        if (MasterStopManager.isEmergencyStopActive.value) return@withContext

        val now = System.currentTimeMillis()
        val activeTasks = dao.getActiveScheduledTasks().firstOrNull() ?: return@withContext

        for (task in activeTasks) {
            if (task.scheduledTimeMillis <= now && task.isEnabled && !task.isCompleted) {
                // Execute task
                try {
                    onExecuteScheduledTask?.invoke(task)
                } catch (e: Exception) {}

                // Update schedule record
                val nextScheduleTime = when (task.triggerType) {
                    ScheduleTriggerType.ONE_TIME, ScheduleTriggerType.DELAYED -> null
                    ScheduleTriggerType.RECURRING, ScheduleTriggerType.SCHEDULED_RESEARCH, ScheduleTriggerType.MEMORY_MAINTENANCE -> {
                        when (task.cronOrInterval.uppercase()) {
                            "HOURLY" -> now + (60 * 60 * 1000L)
                            "DAILY" -> now + (24 * 60 * 60 * 1000L)
                            "WEEKLY" -> now + (7 * 24 * 60 * 60 * 1000L)
                            else -> now + (12 * 60 * 60 * 1000L)
                        }
                    }
                }

                if (nextScheduleTime != null) {
                    dao.updateScheduledTask(
                        task.copy(
                            lastExecutedMillis = now,
                            scheduledTimeMillis = nextScheduleTime,
                            retryCount = 0
                        )
                    )
                } else {
                    dao.updateScheduledTask(
                        task.copy(
                            lastExecutedMillis = now,
                            isCompleted = true
                        )
                    )
                }
            }
        }
    }

    suspend fun toggleTaskEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        val task = dao.getScheduledTaskById(id) ?: return@withContext
        dao.updateScheduledTask(task.copy(isEnabled = enabled))
    }

    suspend fun deleteScheduledTask(task: ScheduledTaskEntity) = withContext(Dispatchers.IO) {
        dao.deleteScheduledTask(task)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAllScheduledTasks()
    }
}
