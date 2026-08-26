package com.example.core.autonomy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

object MasterStopManager {

    private val _isEmergencyStopActive = MutableStateFlow(false)
    val isEmergencyStopActive: StateFlow<Boolean> = _isEmergencyStopActive.asStateFlow()

    private val _lastStopReason = MutableStateFlow<String?>(null)
    val lastStopReason: StateFlow<String?> = _lastStopReason.asStateFlow()

    private val _stopTimestamp = MutableStateFlow(0L)
    val stopTimestamp: StateFlow<Long> = _stopTimestamp.asStateFlow()

    private val cancelCallbacks = CopyOnWriteArrayList<() -> Unit>()

    fun registerCancelCallback(callback: () -> Unit) {
        if (!cancelCallbacks.contains(callback)) {
            cancelCallbacks.add(callback)
        }
    }

    fun unregisterCancelCallback(callback: () -> Unit) {
        cancelCallbacks.remove(callback)
    }

    /**
     * Immediately stops all active and cancellable autonomous tasks, web research jobs, and workers.
     */
    fun triggerEmergencyStop(reason: String = "User Triggered Emergency Stop") {
        _isEmergencyStopActive.value = true
        _lastStopReason.value = reason
        _stopTimestamp.value = System.currentTimeMillis()

        // Release all locks immediately
        TaskLockManager.emergencyReleaseAll()

        // Execute all registered cancellation hooks
        for (callback in cancelCallbacks) {
            try {
                callback.invoke()
            } catch (e: Exception) {}
        }
    }

    /**
     * Resets the emergency stop state so new tasks can be run safely.
     */
    fun resetEmergencyStop() {
        _isEmergencyStopActive.value = false
        _lastStopReason.value = null
    }

    /**
     * Checks if a voice command or text matches an Emergency Stop request.
     */
    fun isEmergencyStopVoiceCommand(text: String): Boolean {
        val lower = text.lowercase().trim()
        val patterns = listOf(
            "stop jarvis",
            "cancel everything",
            "emergency stop",
            "halt all tasks",
            "abort all",
            "stop all tasks",
            "kill all",
            "জরুরিভাবে থামো",
            "সব বন্ধ করো",
            "জার্ভিস থামো"
        )
        return patterns.any { lower.contains(it) }
    }
}
