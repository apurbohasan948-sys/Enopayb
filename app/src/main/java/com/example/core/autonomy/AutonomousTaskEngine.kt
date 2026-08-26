package com.example.core.autonomy

import android.content.Context
import com.example.core.agent.JarvisAgentCore
import com.example.core.agent.TaskExecutionSummary
import com.example.core.capability.CapabilityManager
import com.example.core.capability.CapabilityStatus
import com.example.core.health.JarvisHealthMonitor
import com.example.core.health.ResourceManager
import com.example.core.research.WebResearchManager
import com.example.core.scheduler.JarvisProactiveAssistant
import com.example.core.scheduler.KnowledgeMaintenanceWorker
import com.example.core.security.SecurityPolicyEngine
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AutonomousTaskEntity
import com.example.data.local.entity.AutonomousTaskStatus
import com.example.data.local.entity.AutonomousTaskType
import com.example.data.local.entity.HealthSeverity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillRiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutonomousTaskEngine(
    private val context: Context,
    private val dao: JarvisDao,
    private val taskQueue: TaskQueue,
    private val capabilityManager: CapabilityManager,
    private val securityPolicyEngine: SecurityPolicyEngine,
    private val healthMonitor: JarvisHealthMonitor,
    private val resourceManager: ResourceManager,
    private val proactiveAssistant: JarvisProactiveAssistant,
    private val agentCoreProvider: () -> JarvisAgentCore?,
    private val webResearchManagerProvider: () -> WebResearchManager?,
    private val maintenanceWorker: KnowledgeMaintenanceWorker = KnowledgeMaintenanceWorker(dao)
) {

    /**
     * Executes the full autonomous execution lifecycle for a queued task.
     * TASK -> CHECK POLICY -> CHECK CAPABILITIES -> PLAN -> EXECUTE -> OBSERVE -> VERIFY -> LEARN -> COMPLETE
     */
    suspend fun executeTask(
        task: AutonomousTaskEntity,
        autonomyMode: AutonomyMode = AutonomyMode.AUTONOMOUS,
        policyConfig: AutonomyPolicyConfig = AutonomyPolicyConfig(),
        coroutineScope: CoroutineScope
    ): AutonomousTaskStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Check Master Emergency Stop
        if (MasterStopManager.isEmergencyStopActive.value) {
            taskQueue.updateTaskStatus(
                taskId = task.id,
                status = AutonomousTaskStatus.CANCELLED,
                failureReason = "Master Emergency Stop Active: ${MasterStopManager.lastStopReason.value}"
            )
            return@withContext AutonomousTaskStatus.CANCELLED
        }

        // 2. CHECK POLICY & SECURITY
        taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.RUNNING)
        taskQueue.appendExecutionLog(task.id, "Evaluating task against Security and Autonomy Policies...")

        // Security check for prompt injections or malicious phrases
        val securityCheck = securityPolicyEngine.validateInput(task.goal)
        if (!securityCheck.isSafe) {
            val blockedReason = "Security Engine Blocked Task: ${securityCheck.reason}"
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    eventType = "AUTONOMOUS_TASK_SECURITY_VIOLATION",
                    riskScore = securityCheck.riskScore,
                    source = "AutonomousTaskEngine",
                    description = blockedReason,
                    actionTaken = "BLOCKED",
                    timestamp = System.currentTimeMillis()
                )
            )
            taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.BLOCKED, blockingReason = blockedReason)
            return@withContext AutonomousTaskStatus.BLOCKED
        }

        // Autonomy Policy allowlist/blocklist/sensitive checks
        val policyDecision = AutonomyPolicy.evaluateTask(
            goal = task.goal,
            targetAppPackage = task.targetAppPackage,
            mode = autonomyMode,
            config = policyConfig
        )

        if (!policyDecision.isAllowed) {
            val blockedReason = "Autonomy Policy Rejected Task: ${policyDecision.reason}"
            taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.BLOCKED, blockingReason = blockedReason)
            taskQueue.appendExecutionLog(task.id, "❌ $blockedReason")
            return@withContext AutonomousTaskStatus.BLOCKED
        }

        if (policyDecision.requiresConfirmation && autonomyMode != AutonomyMode.AUTONOMOUS) {
            val waitingReason = "Requires User Confirmation: ${policyDecision.reason}"
            taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.WAITING_FOR_USER, blockingReason = waitingReason)
            taskQueue.appendExecutionLog(task.id, "⚠️ $waitingReason")
            proactiveAssistant.notifyUser(
                category = "CONFIRMATION_REQUIRED",
                title = "Approval Required",
                message = "Task requires approval: ${task.goal}"
            )
            return@withContext AutonomousTaskStatus.WAITING_FOR_USER
        }

        // 3. RESOURCE & BATTERY LIMITS
        if (resourceManager.shouldThrottleHeavyTasks() && task.taskType == AutonomousTaskType.WEB_RESEARCH) {
            val batteryReason = "Paused heavy autonomous task due to low battery or Battery Saver mode."
            taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.WAITING, blockingReason = batteryReason)
            taskQueue.appendExecutionLog(task.id, "🔋 $batteryReason")
            return@withContext AutonomousTaskStatus.WAITING
        }

        // 4. CONCURRENCY SAFETY & MUTEX LOCKS
        val lockResource = task.targetAppPackage ?: if (task.taskType == AutonomousTaskType.WEB_RESEARCH) "WEB_RESEARCH" else "GENERAL_AGENT"
        val lockAcquired = TaskLockManager.tryAcquire(lockResource, task.id)
        if (!lockAcquired) {
            val lockWaitReason = "Resource '$lockResource' currently locked by another task."
            taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.WAITING, blockingReason = lockWaitReason)
            taskQueue.appendExecutionLog(task.id, "🔒 $lockWaitReason")
            return@withContext AutonomousTaskStatus.WAITING
        }

        try {
            // 5. CHECK CAPABILITIES
            taskQueue.appendExecutionLog(task.id, "Verifying Android system capabilities...")
            val allCaps = capabilityManager.getAllCapabilities()

            if (task.taskType == AutonomousTaskType.USER_PROMPTED || task.taskType == AutonomousTaskType.SCHEDULED) {
                val accessibilityCap = allCaps.find { it.id == "ACCESSIBILITY" }
                if (accessibilityCap?.status != CapabilityStatus.GRANTED && task.targetAppPackage != null) {
                    val capError = "Accessibility Service is not enabled. Cannot perform UI automation."
                    taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.FAILED, failureReason = capError)
                    taskQueue.appendExecutionLog(task.id, "❌ $capError")
                    healthMonitor.recordIssue("ACCESSIBILITY", HealthSeverity.WARNING, capError)
                    return@withContext AutonomousTaskStatus.FAILED
                }
            }

            // 6. ROUTE & EXECUTE TASK BY TYPE
            when (task.taskType) {
                AutonomousTaskType.WEB_RESEARCH -> {
                    taskQueue.appendExecutionLog(task.id, "Initiating web research agent for: '${task.goal}'")
                    val researchManager = webResearchManagerProvider()
                    if (researchManager == null) {
                        taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.FAILED, failureReason = "WebResearchManager unavailable")
                        return@withContext AutonomousTaskStatus.FAILED
                    }

                    val researchResult = researchManager.conductResearch(
                        query = task.goal,
                        userGoal = task.goal,
                        storeInKnowledgeBase = true,
                        autoApproveKnowledge = policyConfig.autoUpdateKnowledgeOnHighConfidence
                    )

                    val duration = System.currentTimeMillis() - startTime
                    val finalStatus = if (researchResult.confidence > 0.4f) AutonomousTaskStatus.COMPLETED else AutonomousTaskStatus.FAILED

                    taskQueue.updateTaskStatus(
                        taskId = task.id,
                        status = finalStatus,
                        resultSummary = researchResult.summary,
                        verificationProof = "Verified ${researchResult.sources.size} web sources (Confidence: ${(researchResult.confidence * 100).toInt()}%)",
                        durationMs = duration
                    )
                    taskQueue.appendExecutionLog(task.id, "✅ Research completed. Verified ${researchResult.sources.size} sources.")

                    proactiveAssistant.notifyUser(
                        category = "RESEARCH_COMPLETED",
                        title = "Research Complete",
                        message = "JARVIS finished researching: ${task.goal}\n${researchResult.summary.take(120)}..."
                    )

                    return@withContext finalStatus
                }

                AutonomousTaskType.MEMORY_MAINTENANCE -> {
                    taskQueue.appendExecutionLog(task.id, "Running background memory deduplication and database indexing...")
                    val maintenanceSummary = maintenanceWorker.runMaintenance()
                    val duration = System.currentTimeMillis() - startTime

                    val summaryText = "Deduplicated ${maintenanceSummary.deduplicatedMemoriesCount} memories, validated ${maintenanceSummary.validatedSkillsCount} skills."
                    taskQueue.updateTaskStatus(
                        taskId = task.id,
                        status = AutonomousTaskStatus.COMPLETED,
                        resultSummary = summaryText,
                        verificationProof = "Database maintenance finished in ${duration}ms",
                        durationMs = duration
                    )
                    taskQueue.appendExecutionLog(task.id, "✅ Maintenance complete: $summaryText")
                    return@withContext AutonomousTaskStatus.COMPLETED
                }

                else -> {
                    // Standard multi-step agent core execution (PLAN -> EXECUTE -> OBSERVE -> VERIFY -> LEARN)
                    taskQueue.appendExecutionLog(task.id, "Planning task steps using JARVIS Agent Core...")
                    val agentCore = agentCoreProvider()
                    if (agentCore == null) {
                        taskQueue.updateTaskStatus(task.id, AutonomousTaskStatus.FAILED, failureReason = "JarvisAgentCore uninitialized")
                        return@withContext AutonomousTaskStatus.FAILED
                    }

                    val summary = agentCore.executeGoal(task.goal, coroutineScope)
                    val duration = System.currentTimeMillis() - startTime
                    val finalStatus = if (summary.success) AutonomousTaskStatus.COMPLETED else AutonomousTaskStatus.FAILED

                    taskQueue.updateTaskStatus(
                        taskId = task.id,
                        status = finalStatus,
                        failureReason = if (!summary.success) summary.finalOutput else null,
                        resultSummary = summary.finalOutput,
                        verificationProof = "Executed ${summary.stepRecords.size} steps. Verified: ${summary.success}",
                        durationMs = duration
                    )
                    taskQueue.appendExecutionLog(task.id, if (summary.success) "✅ Task successfully completed and verified." else "❌ Task execution failed.")

                    if (summary.success) {
                        proactiveAssistant.notifyUser(
                            category = "AUTONOMOUS_TASK_SUCCESS",
                            title = "Autonomous Task Done",
                            message = "Finished: ${task.goal}"
                        )
                    }

                    return@withContext finalStatus
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unexpected error during execution"
            taskQueue.updateTaskStatus(
                taskId = task.id,
                status = AutonomousTaskStatus.FAILED,
                failureReason = errorMsg,
                durationMs = System.currentTimeMillis() - startTime
            )
            taskQueue.appendExecutionLog(task.id, "❌ Error: $errorMsg")
            healthMonitor.recordIssue("AUTONOMOUS_TASK_ENGINE", HealthSeverity.ERROR, "Task #${task.id} failed: $errorMsg")
            return@withContext AutonomousTaskStatus.FAILED
        } finally {
            // Always release task locks
            TaskLockManager.releaseAllForTask(task.id)
        }
    }
}
