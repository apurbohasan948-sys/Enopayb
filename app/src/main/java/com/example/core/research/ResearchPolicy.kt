package com.example.core.research

import com.example.core.health.NetworkStatus

enum class ResearchPolicyMode {
    OFF,
    MANUAL,
    WIFI_ONLY,
    SCHEDULED,
    AUTOMATIC_LOW_RISK
}

data class ResearchPolicyConfig(
    val mode: ResearchPolicyMode = ResearchPolicyMode.MANUAL,
    val maxDailyWebQueries: Int = 15,
    val maxSourcesPerQuery: Int = 5,
    val minConfidenceToStore: Float = 0.70f,
    val requireWifiForBackground: Boolean = true
)

class ResearchPolicy(
    var config: ResearchPolicyConfig = ResearchPolicyConfig()
) {
    fun canPerformResearch(
        isUserTriggered: Boolean,
        networkStatus: NetworkStatus,
        queriesUsedToday: Int
    ): Pair<Boolean, String> {
        if (queriesUsedToday >= config.maxDailyWebQueries) {
            return Pair(false, "Daily web research quota reached (${config.maxDailyWebQueries})")
        }

        if (networkStatus == NetworkStatus.OFFLINE) {
            return Pair(false, "Device is offline. Local brain retrieval only.")
        }

        if (isUserTriggered) {
            return Pair(true, "User-initiated research permitted.")
        }

        return when (config.mode) {
            ResearchPolicyMode.OFF -> Pair(false, "Research policy is set to OFF.")
            ResearchPolicyMode.MANUAL -> Pair(false, "Autonomous research disabled (MANUAL mode). User trigger required.")
            ResearchPolicyMode.WIFI_ONLY -> {
                if (networkStatus == NetworkStatus.ONLINE) {
                    Pair(true, "Autonomous research permitted on network.")
                } else {
                    Pair(false, "WIFI_ONLY mode requires active connection.")
                }
            }
            ResearchPolicyMode.SCHEDULED -> Pair(true, "Scheduled maintenance research permitted.")
            ResearchPolicyMode.AUTOMATIC_LOW_RISK -> Pair(true, "Automatic low-risk research permitted.")
        }
    }
}
