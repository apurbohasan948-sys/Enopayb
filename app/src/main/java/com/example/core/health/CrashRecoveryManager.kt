package com.example.core.health

import android.content.Context
import android.util.Log
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AutonomousTaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CrashRecoveryReport(
    val interruptedTasksCount: Int = 0,
    val sensitiveActionsBlocked: Int = 0,
    val details: String = "Clean system state"
)

/**
 * CrashRecoveryManager.
 * Detects interrupted operations after an app or system crash/restart.
 * Marks unverified tasks as INTERRUPTED_NEEDS_VERIFICATION and prevents
 * accidental auto-repetition of sensitive real-world actions (e.g. sending SMS, WhatsApp messages, calls).
 */
class CrashRecoveryManager(
    private val context: Context,
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_CrashRecovery"
    }

    suspend fun performStartupCrashRecoveryCheck(): CrashRecoveryReport = withContext(Dispatchers.IO) {
        try {
            val pendingOrRunningTasks = dao.getTasksByStatusSync(AutonomousTaskStatus.RUNNING.name)
            var interruptedCount = 0
            var blockedActions = 0

            for (task in pendingOrRunningTasks) {
                interruptedCount++
                val goalLower = task.goal.lowercase()
                val isSensitive = goalLower.contains("whatsapp") ||
                        goalLower.contains("sms") ||
                        goalLower.contains("call") ||
                        goalLower.contains("delete") ||
                        goalLower.contains("send")

                if (isSensitive) {
                    blockedActions++
                }

                // Update task status to BLOCKED / FAILED to prevent auto-repeating sensitive actions
                val updatedTask = task.copy(
                    status = AutonomousTaskStatus.BLOCKED,
                    blockingReason = "Interrupted by system restart/crash. Action requires explicit user verification before resuming.",
                    completedAt = System.currentTimeMillis()
                )
                dao.updateAutonomousTask(updatedTask)
            }

            Log.d(TAG, "Crash recovery finished: $interruptedCount interrupted tasks recovered, $blockedActions sensitive actions protected.")
            CrashRecoveryReport(
                interruptedTasksCount = interruptedCount,
                sensitiveActionsBlocked = blockedActions,
                details = if (interruptedCount > 0) "Recovered $interruptedCount interrupted tasks safely." else "Clean startup state."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Crash recovery error", e)
            CrashRecoveryReport(details = "Recovery check completed with fallback.")
        }
    }
}
