package com.example.core.autonomy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages resource mutual exclusion to prevent conflicting autonomous tasks
 * (e.g. two tasks attempting to automate WhatsApp or telephony simultaneously).
 */
object TaskLockManager {

    private val resourceLocks = ConcurrentHashMap<String, Mutex>()
    private val activeHolderTasks = ConcurrentHashMap<String, Long>()

    private fun getLockForResource(resourceKey: String): Mutex {
        return resourceLocks.computeIfAbsent(resourceKey) { Mutex() }
    }

    /**
     * Tries to acquire exclusive lock for a resource (e.g. appPackage, "SCREEN_UI", "TELEPHONY", "WEB_RESEARCH").
     * Returns true if acquired.
     */
    fun tryAcquire(resourceKey: String, taskId: Long): Boolean {
        val mutex = getLockForResource(resourceKey)
        val acquired = mutex.tryLock()
        if (acquired) {
            activeHolderTasks[resourceKey] = taskId
        }
        return acquired
    }

    /**
     * Releases resource lock.
     */
    fun release(resourceKey: String, taskId: Long) {
        val holder = activeHolderTasks[resourceKey]
        if (holder == taskId) {
            activeHolderTasks.remove(resourceKey)
            val mutex = resourceLocks[resourceKey]
            if (mutex != null && mutex.isLocked) {
                try {
                    mutex.unlock()
                } catch (e: Exception) {}
            }
        }
    }

    /**
     * Releases all locks held by a specific task (used on task complete, fail, or emergency stop).
     */
    fun releaseAllForTask(taskId: Long) {
        val keysToRelease = activeHolderTasks.filter { it.value == taskId }.keys
        for (key in keysToRelease) {
            release(key, taskId)
        }
    }

    /**
     * Clears all locks during Emergency Master Stop.
     */
    fun emergencyReleaseAll() {
        for ((key, holder) in activeHolderTasks) {
            val mutex = resourceLocks[key]
            if (mutex != null && mutex.isLocked) {
                try {
                    mutex.unlock()
                } catch (e: Exception) {}
            }
        }
        activeHolderTasks.clear()
    }
}
