package com.example.core.communication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommunicationTelemetry(
    val timestamp: Long = System.currentTimeMillis(),
    val intentType: String = "NONE",
    val contactQuery: String = "",
    val matchesFound: Int = 0,
    val selectedContact: String = "",
    val targetApp: String = "System",
    val requiredCapabilities: List<String> = emptyList(),
    val securityRiskLevel: String = "LOW",
    val confirmationStatus: String = "NOT_REQUIRED",
    val actionName: String = "IDLE",
    val executionResult: String = "READY",
    val isVerified: Boolean = false,
    val evidence: String = "",
    val errorDetails: String? = null,
    val callState: String = "IDLE",
    val isPhysicalDevice: Boolean = false
)

object CommunicationHistoryTracker {

    private val _telemetry = MutableStateFlow(CommunicationTelemetry())
    val telemetry: StateFlow<CommunicationTelemetry> = _telemetry.asStateFlow()

    private val _history = MutableStateFlow<List<CommunicationTelemetry>>(emptyList())
    val history: StateFlow<List<CommunicationTelemetry>> = _history.asStateFlow()

    fun updateTelemetry(update: CommunicationTelemetry.() -> CommunicationTelemetry) {
        val newTelemetry = update(_telemetry.value)
        _telemetry.value = newTelemetry
        val currentList = _history.value.toMutableList()
        currentList.add(0, newTelemetry)
        if (currentList.size > 50) currentList.removeAt(currentList.lastIndex)
        _history.value = currentList
    }

    fun recordEvent(
        intentType: String,
        contactQuery: String,
        selectedContact: String,
        targetApp: String,
        securityRiskLevel: String,
        actionName: String,
        executionResult: String,
        isVerified: Boolean,
        evidence: String = "",
        errorDetails: String? = null
    ) {
        updateTelemetry {
            copy(
                timestamp = System.currentTimeMillis(),
                intentType = intentType,
                contactQuery = contactQuery,
                selectedContact = selectedContact,
                targetApp = targetApp,
                securityRiskLevel = securityRiskLevel,
                actionName = actionName,
                executionResult = executionResult,
                isVerified = isVerified,
                evidence = evidence,
                errorDetails = errorDetails
            )
        }
    }
}
