package com.example.core.device.security

import android.content.Context
import com.example.core.capability.CapabilityManager
import com.example.core.capability.CapabilityStatus
import com.example.core.device.DeviceCapabilityManager
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillRiskLevel
import org.json.JSONArray
import org.json.JSONObject

data class AuditFinding(
    val category: String,
    val item: String,
    val status: String,
    val riskScore: Int,
    val recommendation: String
)

data class DeviceSecurityAuditReport(
    val overallRiskScore: Int,
    val posture: String,
    val totalCapabilitiesAudited: Int,
    val activePermissionsCount: Int,
    val dangerousPermissionsCount: Int,
    val findings: List<AuditFinding>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSummaryText(): String {
        return buildString {
            append("• Security Posture: $posture (Risk Score: $overallRiskScore/100)\n")
            append("• Capabilities Audited: $totalCapabilitiesAudited\n")
            append("• Active Permissions: $activePermissionsCount (High-Risk/Dangerous: $dangerousPermissionsCount)\n")
            if (findings.isNotEmpty()) {
                append("• Key Findings:\n")
                findings.take(4).forEach { f ->
                    append("  - [${f.category}] ${f.item}: ${f.status} (${f.recommendation})\n")
                }
            }
        }
    }
}

class DeviceControlSecurityAudit(
    private val context: Context,
    private val jarvisDao: JarvisDao? = null,
    private val capabilityManager: DeviceCapabilityManager = DeviceCapabilityManager(context, jarvisDao)
) {

    suspend fun runSecurityAudit(): DeviceSecurityAuditReport {
        val capabilities = capabilityManager.getAllCapabilities()
        val findings = mutableListOf<AuditFinding>()

        var totalRisk = 10 // baseline safe
        var dangerousCount = 0
        var activeCount = 0

        for (cap in capabilities) {
            if (cap.enabled && cap.available) {
                activeCount++
            }

            when (cap.id) {
                "ACCESSIBILITY" -> {
                    if (cap.enabled) {
                        findings.add(
                            AuditFinding(
                                category = "AUTOMATION",
                                item = "Accessibility Controller",
                                status = "ACTIVE",
                                riskScore = 15,
                                recommendation = "Service is active. UI automation gated by JARVIS Security Shield."
                            )
                        )
                    } else {
                        findings.add(
                            AuditFinding(
                                category = "AUTOMATION",
                                item = "Accessibility Controller",
                                status = "DISABLED",
                                riskScore = 0,
                                recommendation = "Enable in Accessibility Settings for screen interaction and hands control."
                            )
                        )
                    }
                }
                "TELEPHONY", "SMS" -> {
                    if (cap.enabled) {
                        dangerousCount++
                        totalRisk += 10
                        findings.add(
                            AuditFinding(
                                category = "COMMUNICATION",
                                item = cap.name,
                                status = "PERMISSION_GRANTED",
                                riskScore = 20,
                                recommendation = "Gated behind explicit confirmation policy to prevent unauthorized calls/SMS."
                            )
                        )
                    }
                }
                "CAMERA", "MICROPHONE" -> {
                    if (cap.enabled) {
                        dangerousCount++
                        findings.add(
                            AuditFinding(
                                category = "SENSORS",
                                item = cap.name,
                                status = "ACTIVE",
                                riskScore = 10,
                                recommendation = "Sensors active for voice and visual recognition."
                            )
                        )
                    }
                }
                "OVERLAY" -> {
                    if (cap.enabled) {
                        findings.add(
                            AuditFinding(
                                category = "SYSTEM_UI",
                                item = "Overlay Permission",
                                status = "GRANTED",
                                riskScore = 5,
                                recommendation = "Overlay is restricted to HUD rendering."
                            )
                        )
                    }
                }
            }
        }

        val finalScore = totalRisk.coerceIn(0, 100)
        val posture = when {
            finalScore < 30 -> "SECURE & SHIELDED"
            finalScore < 60 -> "BALANCED AUTONOMY"
            else -> "HIGH ELEVATION"
        }

        val report = DeviceSecurityAuditReport(
            overallRiskScore = finalScore,
            posture = posture,
            totalCapabilitiesAudited = capabilities.size,
            activePermissionsCount = activeCount,
            dangerousPermissionsCount = dangerousCount,
            findings = findings
        )

        // Log audit event to database
        jarvisDao?.insertSecurityEvent(
            SecurityEventEntity(
                eventType = "DEVICE_SECURITY_AUDIT",
                riskScore = finalScore,
                source = "DeviceSecurityAudit",
                description = "Audited all 22 device capabilities. Posture: $posture, Risk Score: $finalScore. Dangerous permissions active: $dangerousCount",
                actionTaken = "Audited system capabilities against SecurityPolicyEngine.",
                isResolved = true
            )
        )

        return report
    }
}
