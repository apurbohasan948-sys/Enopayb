package com.example.core.model

import android.content.Context
import com.example.core.health.NetworkStateMonitor
import com.example.core.health.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * OfflineManager.
 * Enforces offline-first intelligence.
 * Detects whether an operation can be resolved locally vs requiring external web internet.
 */
class OfflineManager(
    private val context: Context,
    private val networkStateMonitor: NetworkStateMonitor
) {
    val networkStatus: StateFlow<NetworkStatus> = networkStateMonitor.networkStatus

    fun isOnline(): Boolean = networkStatus.value == NetworkStatus.ONLINE || networkStatus.value == NetworkStatus.LIMITED

    /**
     * Inspects a user goal to determine if it strictly requires internet access.
     */
    fun requiresInternet(goal: String): Boolean {
        val lower = goal.lowercase().trim()
        val webKeywords = listOf(
            "search web", "google search", "latest news", "weather online",
            "wikipedia", "stock price", "research online", "browse web",
            "current time in", "today's news", "live score"
        )
        return webKeywords.any { lower.contains(it) }
    }

    /**
     * Formats clean offline advisory message.
     */
    fun getOfflineNotice(goal: String): String {
        return if (requiresInternet(goal)) {
            "Internet is required for this task. Please connect to Wi-Fi or Mobile Data to perform live web research."
        } else {
            "Offline Mode Active: Executing task locally using on-device skills, apps, and SLM engine."
        }
    }
}
