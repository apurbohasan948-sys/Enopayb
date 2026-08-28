package com.example.core.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.model.GeminiModelProvider
import com.example.core.model.LocalSLMModelProvider
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.HealthEventEntity
import com.example.data.local.entity.HealthSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class SystemHealthReport(
    val isSystemHealthy: Boolean = true,
    val agentState: String = "IDLE",
    val databaseStatus: String = "HEALTHY",
    val ramAllocatedMb: Int = 0,
    val ramMaxMb: Int = 0,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val isGeminiConfigured: Boolean = false,
    val isLocalModelLoaded: Boolean = true,
    val isAccessibilityActive: Boolean = false,
    val isMicrophoneGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val failedTaskCount: Int = 0,
    val lastCheckTimestamp: Long = System.currentTimeMillis()
)

class JarvisHealthMonitor(
    private val context: Context,
    private val dao: JarvisDao,
    private val geminiProvider: GeminiModelProvider? = null,
    private val localSLMProvider: LocalSLMModelProvider? = null,
    private val networkMonitor: NetworkStateMonitor,
    private val resourceManager: ResourceManager,
    val recoveryManager: SafeRecoveryManager = SafeRecoveryManager(context, dao, geminiProvider, localSLMProvider)
) {
    val healthEvents: Flow<List<HealthEventEntity>> = dao.getAllHealthEvents()

    private val _healthReport = MutableStateFlow(SystemHealthReport())
    val healthReport: StateFlow<SystemHealthReport> = _healthReport.asStateFlow()

    suspend fun performDiagnosticCheck(agentState: String = "IDLE"): SystemHealthReport = withContext(Dispatchers.IO) {
        val runtime = Runtime.getRuntime()
        val usedRam = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val maxRam = (runtime.maxMemory() / (1024 * 1024)).toInt()

        val snap = resourceManager.refreshSnapshot()
        val netStatus = networkMonitor.checkCurrentStatus()

        val accessibilityOn = JarvisAccessibilityService.isAccessibilityEnabled(context)
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Settings.canDrawOverlays(context)

        val geminiReady = geminiProvider?.isConfigured() ?: false
        val localReady = localSLMProvider?.isModelLoaded() ?: false

        var dbStatus = "HEALTHY"
        try {
            dao.getTopMemories(1)
        } catch (e: Exception) {
            dbStatus = "DEGRADED: ${e.message}"
            recordIssue("DATABASE", HealthSeverity.ERROR, "Database query validation failed: ${e.message}")
        }

        val allOk = dbStatus == "HEALTHY" && (geminiReady || localReady) && accessibilityOn

        val report = SystemHealthReport(
            isSystemHealthy = allOk,
            agentState = agentState,
            databaseStatus = dbStatus,
            ramAllocatedMb = usedRam,
            ramMaxMb = maxRam,
            batteryPercent = snap.batteryPercent,
            isCharging = snap.isCharging,
            networkStatus = netStatus,
            isGeminiConfigured = geminiReady,
            isLocalModelLoaded = localReady,
            isAccessibilityActive = accessibilityOn,
            isMicrophoneGranted = micGranted,
            isOverlayGranted = overlayGranted,
            failedTaskCount = 0,
            lastCheckTimestamp = System.currentTimeMillis()
        )

        _healthReport.value = report
        report
    }

    suspend fun recordIssue(component: String, severity: HealthSeverity, description: String) = withContext(Dispatchers.IO) {
        dao.insertHealthEvent(
            HealthEventEntity(
                component = component,
                severity = severity,
                description = description,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHealthEvents() = withContext(Dispatchers.IO) {
        dao.clearHealthEvents()
    }
}
