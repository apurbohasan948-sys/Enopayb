package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_capabilities")
data class DeviceCapabilityEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String = "SYSTEM",
    val available: Boolean = true,
    val permission: String = "NONE",
    val enabled: Boolean = true,
    val restricted: Boolean = false,
    val reason: String = "Operational",
    val lastChecked: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_registry")
data class AppRegistryEntity(
    @PrimaryKey
    val packageName: String,
    val applicationLabel: String,
    val versionName: String = "1.0",
    val versionCode: Long = 1L,
    val isSystemApp: Boolean = false,
    val launchIntentAvailable: Boolean = true,
    val category: String = "Application",
    val lastScannedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "device_action_history")
data class DeviceActionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val toolName: String,
    val action: String,
    val target: String,
    val argumentsJson: String = "{}",
    val success: Boolean,
    val riskLevel: String = "LOW",
    val failureReason: String? = null,
    val verificationProof: String? = null,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
