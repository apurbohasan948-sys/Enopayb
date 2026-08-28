package com.example.core.autonomy

import android.content.Context
import com.example.core.agent.JarvisAgentCore
import com.example.core.capability.CapabilityManager
import com.example.core.health.JarvisHealthMonitor
import com.example.core.health.NetworkStateMonitor
import com.example.core.health.ResourceManager
import com.example.core.model.GeminiModelProvider
import com.example.core.model.LocalSLMModelProvider
import com.example.core.research.KnowledgeUpdateManager
import com.example.core.research.WebResearchManager
import com.example.core.research.WebSearchEngine
import com.example.core.scheduler.JarvisProactiveAssistant
import com.example.core.scheduler.JarvisScheduler
import com.example.core.scheduler.KnowledgeMaintenanceWorker
import com.example.core.security.SecurityPolicyEngine
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AutonomousTaskEntity
import com.example.data.local.entity.AutonomousTaskPriority
import com.example.data.local.entity.AutonomousTaskType
import com.example.data.local.entity.ScheduleTriggerType
import com.example.data.local.entity.ScheduledTaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutonomousAgentManager(
    private val context: Context,
    val dao: JarvisDao,
    val capabilityManager: CapabilityManager,
    val securityPolicyEngine: SecurityPolicyEngine,
    val geminiProvider: GeminiModelProvider? = null,
    val localSLMProvider: LocalSLMModelProvider? = null,
    private val agentCoreProvider: () -> JarvisAgentCore?,
    val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    // 1. Policy & Autonomy Mode State
    private val _autonomyMode = MutableStateFlow(AutonomyMode.AUTONOMOUS)
    val autonomyMode: StateFlow<AutonomyMode> = _autonomyMode.asStateFlow()

    private val _policyConfig = MutableStateFlow(AutonomyPolicyConfig())
    val policyConfig: StateFlow<AutonomyPolicyConfig> = _policyConfig.asStateFlow()

    // 2. Resource & Health Monitoring
    val networkMonitor = NetworkStateMonitor(context)
    val resourceManager = ResourceManager(context)
    val healthMonitor = JarvisHealthMonitor(
        context = context,
        dao = dao,
        geminiProvider = geminiProvider,
        localSLMProvider = localSLMProvider,
        networkMonitor = networkMonitor,
        resourceManager = resourceManager
    )

    // 3. Proactive Assistant & Research Engine
    val proactiveAssistant = JarvisProactiveAssistant(context, dao)
    val searchEngine = WebSearchEngine(geminiProvider)
    val knowledgeUpdateManager = KnowledgeUpdateManager(dao)
    val webResearchManager = WebResearchManager(dao, searchEngine, knowledgeUpdateManager, geminiProvider)

    // 4. Task Queue & Engine
    val taskQueue = TaskQueue(dao)
    val maintenanceWorker = KnowledgeMaintenanceWorker(dao)

    val taskEngine = AutonomousTaskEngine(
        context = context,
        dao = dao,
        taskQueue = taskQueue,
        capabilityManager = capabilityManager,
        securityPolicyEngine = securityPolicyEngine,
        healthMonitor = healthMonitor,
        resourceManager = resourceManager,
        proactiveAssistant = proactiveAssistant,
        agentCoreProvider = agentCoreProvider,
        webResearchManagerProvider = { webResearchManager },
        maintenanceWorker = maintenanceWorker
    )

    // 5. Scheduler
    val scheduler = JarvisScheduler(
        dao = dao,
        onExecuteScheduledTask = { scheduledTask ->
            handleScheduledTaskTrigger(scheduledTask)
        },
        scope = coroutineScope
    )

    init {
        // Register Master Stop callback
        MasterStopManager.registerCancelCallback {
            coroutineScope.launch {
                taskQueue.cancelAllTasks("Emergency Master Stop Triggered")
            }
        }
    }

    fun setAutonomyMode(mode: AutonomyMode) {
        _autonomyMode.value = mode
    }

    fun updatePolicyConfig(config: AutonomyPolicyConfig) {
        _policyConfig.value = config
    }

    fun triggerEmergencyStop(reason: String = "User Triggered Stop") {
        MasterStopManager.triggerEmergencyStop(reason)
    }

    fun resetEmergencyStop() {
        MasterStopManager.resetEmergencyStop()
    }

    /**
     * Submits a new goal to the autonomous task engine and starts execution asynchronously.
     */
    fun submitGoal(
        goal: String,
        taskType: AutonomousTaskType = AutonomousTaskType.USER_PROMPTED,
        priority: AutonomousTaskPriority = AutonomousTaskPriority.MEDIUM,
        targetAppPackage: String? = null
    ): Long {
        val taskIdHolder = LongArray(1)
        coroutineScope.launch(Dispatchers.IO) {
            val taskId = taskQueue.enqueueTask(
                goal = goal,
                taskType = taskType,
                priority = priority,
                targetAppPackage = targetAppPackage
            )
            taskIdHolder[0] = taskId

            val task = taskQueue.getTaskById(taskId)
            if (task != null) {
                taskEngine.executeTask(
                    task = task,
                    autonomyMode = _autonomyMode.value,
                    policyConfig = _policyConfig.value,
                    coroutineScope = coroutineScope
                )
            }
        }
        return taskIdHolder[0]
    }

    /**
     * Triggers web research on a specific topic and returns the queued task ID.
     */
    fun triggerResearch(query: String): Long {
        return submitGoal(
            goal = query,
            taskType = AutonomousTaskType.WEB_RESEARCH,
            priority = AutonomousTaskPriority.MEDIUM
        )
    }

    /**
     * Triggers immediate memory and database maintenance.
     */
    fun triggerMaintenance(): Long {
        return submitGoal(
            goal = "Database and Memory Maintenance",
            taskType = AutonomousTaskType.MEMORY_MAINTENANCE,
            priority = AutonomousTaskPriority.MAINTENANCE
        )
    }

    private suspend fun handleScheduledTaskTrigger(scheduledTask: ScheduledTaskEntity) {
        val taskType = when (scheduledTask.triggerType) {
            ScheduleTriggerType.SCHEDULED_RESEARCH -> AutonomousTaskType.WEB_RESEARCH
            ScheduleTriggerType.MEMORY_MAINTENANCE -> AutonomousTaskType.MEMORY_MAINTENANCE
            else -> AutonomousTaskType.SCHEDULED
        }

        val taskId = taskQueue.enqueueTask(
            goal = scheduledTask.instruction,
            taskType = taskType,
            priority = AutonomousTaskPriority.HIGH,
            riskLevel = scheduledTask.riskLevel,
            requiresConfirmation = scheduledTask.requiresConfirmation
        )

        val task = taskQueue.getTaskById(taskId)
        if (task != null) {
            taskEngine.executeTask(
                task = task,
                autonomyMode = _autonomyMode.value,
                policyConfig = _policyConfig.value,
                coroutineScope = coroutineScope
            )
        }
    }
}
