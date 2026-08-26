package com.example.core.model

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ModelSizeProfile(
    val title: String,
    val modelName: String,
    val parameterCount: String,
    val quantization: String,
    val fileSizeMb: Int,
    val estimatedRamMb: Int,
    val contextLength: Int,
    val description: String,
    val minRamRecommendedGb: Int
) {
    LOW_MEMORY(
        title = "Low Memory Profile",
        modelName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        parameterCount = "0.5 Billion",
        quantization = "Q4_K_M (4-bit)",
        fileSizeMb = 380,
        estimatedRamMb = 450,
        contextLength = 1024,
        description = "Lightweight footprint for entry-level devices (< 4GB RAM). Zero OOM risk.",
        minRamRecommendedGb = 2
    ),
    BALANCED(
        title = "Balanced Profile (Default)",
        modelName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
        parameterCount = "1.5 Billion",
        quantization = "Q4_K_M (4-bit Mobile)",
        fileSizeMb = 980,
        estimatedRamMb = 850,
        contextLength = 2048,
        description = "Optimal balance of multi-turn reasoning and speed for standard Android phones (4–8 GB RAM).",
        minRamRecommendedGb = 4
    ),
    PERFORMANCE(
        title = "High Performance Profile",
        modelName = "Qwen2.5-3B-Instruct-Q5_K_M.gguf",
        parameterCount = "3.0 Billion",
        quantization = "Q5_K_M (5-bit High Precision)",
        fileSizeMb = 2150,
        estimatedRamMb = 1800,
        contextLength = 4096,
        description = "Deep multi-step reasoning and rich Banglish/Bengali nuance for flagship devices (> 8 GB RAM).",
        minRamRecommendedGb = 8
    )
}

data class DeviceHardwareReport(
    val totalPhysicalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDevice: Boolean,
    val recommendedProfile: ModelSizeProfile,
    val detectedDeviceName: String
)

/**
 * ModelProfileManager.
 * Detects device hardware RAM and automatically recommends/configures the optimal
 * model size, quantization format (Q4/Q5), and context length.
 */
class ModelProfileManager(private val context: Context) {

    private val _currentProfile = MutableStateFlow(detectRecommendedProfile())
    val currentProfile: StateFlow<ModelSizeProfile> = _currentProfile.asStateFlow()

    private val _hardwareReport = MutableStateFlow(generateHardwareReport())
    val hardwareReport: StateFlow<DeviceHardwareReport> = _hardwareReport.asStateFlow()

    fun setProfile(profile: ModelSizeProfile) {
        _currentProfile.value = profile
    }

    fun refreshHardwareReport() {
        _hardwareReport.value = generateHardwareReport()
    }

    private fun detectRecommendedProfile(): ModelSizeProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        return when {
            totalRamMb < 3800 || am?.isLowRamDevice == true -> ModelSizeProfile.LOW_MEMORY
            totalRamMb <= 8192 -> ModelSizeProfile.BALANCED
            else -> ModelSizeProfile.PERFORMANCE
        }
    }

    private fun generateHardwareReport(): DeviceHardwareReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val recommended = when {
            totalRamMb < 3800 || am?.isLowRamDevice == true -> ModelSizeProfile.LOW_MEMORY
            totalRamMb <= 8192 -> ModelSizeProfile.BALANCED
            else -> ModelSizeProfile.PERFORMANCE
        }

        return DeviceHardwareReport(
            totalPhysicalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            isLowRamDevice = am?.isLowRamDevice == true,
            recommendedProfile = recommended,
            detectedDeviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
        )
    }
}
