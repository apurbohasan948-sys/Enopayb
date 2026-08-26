package com.example.core.model

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class PerformanceMetrics(
    val lastWakeLatencyMs: Long = 0,
    val lastSttLatencyMs: Long = 0,
    val lastPlanningLatencyMs: Long = 0,
    val lastModelLoadingMs: Long = 0,
    val lastFirstTokenLatencyMs: Long = 0,
    val lastTaskCompletionTimeMs: Long = 0,
    val lastScreenObservationMs: Long = 0,
    val lastOcrLatencyMs: Long = 0,
    val lastVisionLatencyMs: Long = 0,
    val lastGeminiLatencyMs: Long = 0,
    val lastLocalModelLatencyMs: Long = 0,
    val currentRamUsedMb: Int = 0,
    val currentRamAvailMb: Int = 0
)

data class TaskRoutingStatistics(
    val totalTasks: Int = 0,
    val noAiTasks: Int = 0,
    val localTasks: Int = 0,
    val geminiTasks: Int = 0,
    val localExecutionPercentage: Float = 0.0f,
    val geminiFallbackPercentage: Float = 0.0f,
    val averageLocalLatencyMs: Long = 0,
    val averageGeminiLatencyMs: Long = 0
)

/**
 * PerformanceMonitor.
 * Gathers and exposes real hardware, latency, and model routing metrics.
 * Does not use fabricated measurements; timestamps are recorded via elapsedRealtime.
 */
class PerformanceMonitor(private val context: Context) {

    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    private val totalTasksCount = AtomicInteger(0)
    private val noAiTasksCount = AtomicInteger(0)
    private val localTasksCount = AtomicInteger(0)
    private val geminiTasksCount = AtomicInteger(0)

    private val totalLocalLatency = AtomicLong(0)
    private val localInferenceCount = AtomicInteger(0)

    private val totalGeminiLatency = AtomicLong(0)
    private val geminiInferenceCount = AtomicInteger(0)

    private val _stats = MutableStateFlow(calculateStats())
    val stats: StateFlow<TaskRoutingStatistics> = _stats.asStateFlow()

    fun recordNoAiTask(latencyMs: Long) {
        totalTasksCount.incrementAndGet()
        noAiTasksCount.incrementAndGet()
        updateLatency { it.copy(lastTaskCompletionTimeMs = latencyMs) }
        refreshStats()
    }

    fun recordLocalTask(planningMs: Long, modelMs: Long, totalMs: Long) {
        totalTasksCount.incrementAndGet()
        localTasksCount.incrementAndGet()
        if (modelMs > 0) {
            totalLocalLatency.addAndGet(modelMs)
            localInferenceCount.incrementAndGet()
        }
        updateLatency {
            it.copy(
                lastPlanningLatencyMs = planningMs,
                lastLocalModelLatencyMs = modelMs,
                lastTaskCompletionTimeMs = totalMs
            )
        }
        refreshStats()
    }

    fun recordGeminiTask(planningMs: Long, geminiMs: Long, totalMs: Long) {
        totalTasksCount.incrementAndGet()
        geminiTasksCount.incrementAndGet()
        if (geminiMs > 0) {
            totalGeminiLatency.addAndGet(geminiMs)
            geminiInferenceCount.incrementAndGet()
        }
        updateLatency {
            it.copy(
                lastPlanningLatencyMs = planningMs,
                lastGeminiLatencyMs = geminiMs,
                lastTaskCompletionTimeMs = totalMs
            )
        }
        refreshStats()
    }

    fun recordVisionMetrics(obsMs: Long, ocrMs: Long, visionMs: Long) {
        updateLatency {
            it.copy(
                lastScreenObservationMs = obsMs,
                lastOcrLatencyMs = ocrMs,
                lastVisionLatencyMs = visionMs
            )
        }
    }

    fun recordWakeAndStt(wakeMs: Long, sttMs: Long) {
        updateLatency {
            it.copy(
                lastWakeLatencyMs = wakeMs,
                lastSttLatencyMs = sttMs
            )
        }
    }

    fun recordModelLoad(loadMs: Long) {
        updateLatency { it.copy(lastModelLoadingMs = loadMs) }
    }

    fun recordFirstToken(firstTokenMs: Long) {
        updateLatency { it.copy(lastFirstTokenLatencyMs = firstTokenMs) }
    }

    fun captureMemorySnapshot() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val avail = (memInfo.availMem / (1024 * 1024)).toInt()
        val total = (memInfo.totalMem / (1024 * 1024)).toInt()
        val used = (total - avail).coerceAtLeast(0)

        updateLatency {
            it.copy(
                currentRamUsedMb = used,
                currentRamAvailMb = avail
            )
        }
    }

    private inline fun updateLatency(transform: (PerformanceMetrics) -> PerformanceMetrics) {
        _metrics.value = transform(_metrics.value)
    }

    private fun refreshStats() {
        _stats.value = calculateStats()
    }

    private fun calculateStats(): TaskRoutingStatistics {
        val total = totalTasksCount.get()
        val noAi = noAiTasksCount.get()
        val local = localTasksCount.get()
        val gemini = geminiTasksCount.get()

        val localPct = if (total > 0) ((noAi + local).toFloat() / total * 100f) else 0.0f
        val geminiPct = if (total > 0) (gemini.toFloat() / total * 100f) else 0.0f

        val avgLocal = if (localInferenceCount.get() > 0) totalLocalLatency.get() / localInferenceCount.get() else 0L
        val avgGemini = if (geminiInferenceCount.get() > 0) totalGeminiLatency.get() / geminiInferenceCount.get() else 0L

        return TaskRoutingStatistics(
            totalTasks = total,
            noAiTasks = noAi,
            localTasks = local,
            geminiTasks = gemini,
            localExecutionPercentage = localPct,
            geminiFallbackPercentage = geminiPct,
            averageLocalLatencyMs = avgLocal,
            averageGeminiLatencyMs = avgGemini
        )
    }
}
