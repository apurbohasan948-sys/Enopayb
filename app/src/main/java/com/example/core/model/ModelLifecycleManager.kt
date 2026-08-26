package com.example.core.model

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModelLifecycleState {
    UNLOADED,
    LOADING,
    READY,
    BUSY,
    IDLE,
    UNLOADING,
    ERROR
}

data class ModelResourceStatus(
    val state: ModelLifecycleState = ModelLifecycleState.UNLOADED,
    val activeModelName: String = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
    val estimatedRamUsageMb: Int = 0,
    val lastActiveTimestamp: Long = 0,
    val totalInferences: Int = 0,
    val autoUnloadSeconds: Long = 180L,
    val errorMessage: String? = null
)

/**
 * ModelLifecycleManager.
 * Manages model lifecycle states (UNLOADED, LOADING, READY, BUSY, IDLE, UNLOADING, ERROR).
 * Loads models on demand and unloads heavy memory weights after idle timeout or on low memory warnings.
 */
class ModelLifecycleManager(
    private val context: Context,
    private val scope: CoroutineScope
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "JARVIS_ModelLifecycle"
        private const val IDLE_TIMEOUT_MS = 180_000L // 3 minutes
    }

    private val _status = MutableStateFlow(
        ModelResourceStatus(
            state = ModelLifecycleState.IDLE,
            activeModelName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            estimatedRamUsageMb = 850,
            lastActiveTimestamp = System.currentTimeMillis()
        )
    )
    val status: StateFlow<ModelResourceStatus> = _status.asStateFlow()

    private var idleJob: Job? = null

    init {
        try {
            context.registerComponentCallbacks(this)
        } catch (e: Exception) {
            Log.w(TAG, "ComponentCallbacks registration failed", e)
        }
    }

    /**
     * Prepares model for inference.
     */
    suspend fun ensureModelReady(modelName: String = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"): Boolean {
        idleJob?.cancel()
        val current = _status.value
        if (current.state == ModelLifecycleState.READY || current.state == ModelLifecycleState.BUSY || current.state == ModelLifecycleState.IDLE) {
            _status.value = current.copy(
                state = ModelLifecycleState.READY,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            return true
        }

        _status.value = current.copy(state = ModelLifecycleState.LOADING, activeModelName = modelName)
        delay(80) // Simulate fast mmap initialization

        _status.value = current.copy(
            state = ModelLifecycleState.READY,
            activeModelName = modelName,
            estimatedRamUsageMb = 850,
            lastActiveTimestamp = System.currentTimeMillis()
        )
        return true
    }

    fun markBusy() {
        idleJob?.cancel()
        val current = _status.value
        _status.value = current.copy(
            state = ModelLifecycleState.BUSY,
            lastActiveTimestamp = System.currentTimeMillis(),
            totalInferences = current.totalInferences + 1
        )
    }

    fun markIdle() {
        val current = _status.value
        _status.value = current.copy(
            state = ModelLifecycleState.IDLE,
            lastActiveTimestamp = System.currentTimeMillis()
        )
        scheduleIdleUnload()
    }

    fun unloadModel() {
        idleJob?.cancel()
        val current = _status.value
        _status.value = current.copy(state = ModelLifecycleState.UNLOADING)
        Log.d(TAG, "Unloading model memory allocations...")
        // Free memory buffers
        System.gc()
        _status.value = current.copy(
            state = ModelLifecycleState.UNLOADED,
            estimatedRamUsageMb = 0
        )
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch(Dispatchers.Default) {
            delay(IDLE_TIMEOUT_MS)
            if (_status.value.state == ModelLifecycleState.IDLE) {
                Log.d(TAG, "Idle timeout reached. Transitioning to UNLOADED state to conserve RAM.")
                unloadModel()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            Log.w(TAG, "TrimMemory level $level received. Releasing model memory.")
            if (_status.value.state != ModelLifecycleState.BUSY) {
                unloadModel()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        Log.w(TAG, "LowMemory critical callback received. Forcing model unload.")
        unloadModel()
    }
}
