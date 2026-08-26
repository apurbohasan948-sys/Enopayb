package com.example.core.health

import com.example.core.model.ActiveModelType
import com.example.core.model.GeminiModelProvider
import com.example.core.model.LocalSLMModelProvider
import com.example.data.local.entity.HealthSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FailoverRoute {
    LOCAL_PRIMARY,
    GEMINI_CLOUD,
    LOCAL_RULE_FALLBACK
}

data class FailoverStatus(
    val activeRoute: FailoverRoute = FailoverRoute.GEMINI_CLOUD,
    val localAvailable: Boolean = true,
    val geminiAvailable: Boolean = true,
    val isNetworkOnline: Boolean = true,
    val lastFailoverReason: String? = null,
    val fallbackPlanCount: Int = 0
)

class ModelFailoverManager(
    private val localSLMProvider: LocalSLMModelProvider,
    private val geminiProvider: GeminiModelProvider,
    private val networkMonitor: NetworkStateMonitor
) {
    private val _status = MutableStateFlow(FailoverStatus())
    val status: StateFlow<FailoverStatus> = _status.asStateFlow()

    fun determineOptimalRoute(preferredModel: ActiveModelType, requiresVisionOrComplexReasoning: Boolean = false): FailoverRoute {
        val isOnline = networkMonitor.isOnline()
        val isGeminiReady = geminiProvider.isConfigured()

        val route = when {
            // If offline, MUST use local
            !isOnline -> {
                if (localSLMProvider.isModelLoaded()) {
                    FailoverRoute.LOCAL_PRIMARY
                } else {
                    FailoverRoute.LOCAL_RULE_FALLBACK
                }
            }
            // If user explicitly selected Gemini and it's configured
            preferredModel == ActiveModelType.GEMINI_FLASH && isGeminiReady -> {
                FailoverRoute.GEMINI_CLOUD
            }
            // If local SLM is loaded and preferred
            preferredModel == ActiveModelType.LOCAL_SLM && localSLMProvider.isModelLoaded() -> {
                FailoverRoute.LOCAL_PRIMARY
            }
            // If Gemini is ready and complex vision is needed
            requiresVisionOrComplexReasoning && isGeminiReady -> {
                FailoverRoute.GEMINI_CLOUD
            }
            // Fallback chain: Gemini -> Local SLM -> Rule engine
            isGeminiReady -> FailoverRoute.GEMINI_CLOUD
            localSLMProvider.isModelLoaded() -> FailoverRoute.LOCAL_PRIMARY
            else -> FailoverRoute.LOCAL_RULE_FALLBACK
        }

        _status.value = _status.value.copy(
            activeRoute = route,
            localAvailable = localSLMProvider.isModelLoaded(),
            geminiAvailable = isGeminiReady,
            isNetworkOnline = isOnline
        )

        return route
    }

    fun recordFailoverEvent(from: String, to: String, reason: String) {
        _status.value = _status.value.copy(
            lastFailoverReason = "Switched from $from to $to: $reason",
            fallbackPlanCount = _status.value.fallbackPlanCount + 1
        )
    }
}
