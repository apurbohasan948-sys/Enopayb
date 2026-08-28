package com.example.core.health

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ResourceMode(val label: String, val description: String) {
    PERFORMANCE("PERFORMANCE", "Maximum vision fps, immediate research, full parallel processing."),
    BALANCED("BALANCED", "Standard power optimization, responsive UI, adaptive background execution."),
    BATTERY_SAVER("BATTERY SAVER", "Throttled background workers, paused heavy web research, reduced vision sampling to conserve battery.")
}

data class ResourceSnapshot(
    val mode: ResourceMode = ResourceMode.BALANCED,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val ramUsedMb: Int = 0,
    val ramAvailableMb: Int = 0,
    val isThermalThrottling: Boolean = false
)

class ResourceManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _resourceMode = MutableStateFlow(ResourceMode.BALANCED)
    val resourceMode: StateFlow<ResourceMode> = _resourceMode.asStateFlow()

    private val _currentSnapshot = MutableStateFlow(createInitialSnapshot())
    val currentSnapshot: StateFlow<ResourceSnapshot> = _currentSnapshot.asStateFlow()

    fun setResourceMode(mode: ResourceMode) {
        _resourceMode.value = mode
        refreshSnapshot()
    }

    private fun createInitialSnapshot(): ResourceSnapshot {
        var batteryPct = 100
        var isCharging = false

        try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryPct = (level * 100 / scale.toFloat()).toInt()
            }
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {}

        val isSystemPowerSave = powerManager?.isPowerSaveMode == true
        val runtime = Runtime.getRuntime()
        val usedRam = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val maxRam = (runtime.maxMemory() / (1024 * 1024)).toInt()

        val effectiveMode = when {
            (batteryPct <= 15 && !isCharging) || isSystemPowerSave -> ResourceMode.BATTERY_SAVER
            else -> ResourceMode.BALANCED
        }

        return ResourceSnapshot(
            mode = effectiveMode,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            isPowerSaveMode = isSystemPowerSave,
            ramUsedMb = usedRam,
            ramAvailableMb = maxRam - usedRam,
            isThermalThrottling = false
        )
    }

    fun captureSnapshot(): ResourceSnapshot {
        var batteryPct = 100
        var isCharging = false

        try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryPct = (level * 100 / scale.toFloat()).toInt()
            }
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {}

        val isSystemPowerSave = powerManager?.isPowerSaveMode == true
        val runtime = Runtime.getRuntime()
        val usedRam = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val maxRam = (runtime.maxMemory() / (1024 * 1024)).toInt()

        // Auto-switch to battery saver if battery is low and not charging
        val effectiveMode = when {
            _resourceMode.value == ResourceMode.BATTERY_SAVER || (batteryPct <= 15 && !isCharging) || isSystemPowerSave -> ResourceMode.BATTERY_SAVER
            _resourceMode.value == ResourceMode.PERFORMANCE -> ResourceMode.PERFORMANCE
            else -> ResourceMode.BALANCED
        }

        val snapshot = ResourceSnapshot(
            mode = effectiveMode,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            isPowerSaveMode = isSystemPowerSave,
            ramUsedMb = usedRam,
            ramAvailableMb = maxRam - usedRam,
            isThermalThrottling = false
        )
        _currentSnapshot?.value = snapshot
        return snapshot
    }

    fun refreshSnapshot(): ResourceSnapshot = captureSnapshot()

    fun shouldThrottleHeavyTasks(): Boolean {
        val snap = captureSnapshot()
        return snap.mode == ResourceMode.BATTERY_SAVER || (snap.batteryPercent < 15 && !snap.isCharging)
    }
}
