package com.example.core.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class CloudPolicyConfig(
    val isGeminiEnabled: Boolean = true,
    val isWifiOnly: Boolean = false,
    val dailyRequestLimit: Int = 100,
    val requestsUsedToday: Int = 0,
    val webResearchUsedToday: Int = 0,
    val localRetrievalsCount: Int = 0,
    val maxResponseTokens: Int = 1024,
    val isVisionAllowed: Boolean = true,
    val isBackgroundGeminiAllowed: Boolean = false,
    val isRateLimited: Boolean = false,
    val rateLimitResetTimestamp: Long = 0L
)

data class CloudUsageStats(
    val requestsUsedToday: Int,
    val webResearchRequests: Int,
    val localRetrievalsCount: Int,
    val cloudCallsRemaining: Int,
    val localRetrievalRatio: Float
)

/**
 * CloudUsagePolicy.
 * Controls API budgets, network constraints (Wi-Fi only), rate limiting, and fallback triggers
 * to prevent accidental overuse of Gemini cloud resources and prevent infinite API retry loops.
 */
class CloudUsagePolicy(private val context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_cloud_policy", Context.MODE_PRIVATE)

    private val _policy = MutableStateFlow(loadPolicy())
    val policy: StateFlow<CloudPolicyConfig> = _policy.asStateFlow()

    private var lastRecordedDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    fun canMakeCloudCall(): Boolean {
        return isCloudRequestPermitted().first
    }

    fun getStats(): CloudUsageStats {
        val current = _policy.value
        val totalQueries = current.requestsUsedToday + current.localRetrievalsCount
        val localRatio = if (totalQueries > 0) current.localRetrievalsCount.toFloat() / totalQueries else 1.0f
        val remaining = (current.dailyRequestLimit - current.requestsUsedToday).coerceAtLeast(0)
        return CloudUsageStats(
            requestsUsedToday = current.requestsUsedToday,
            webResearchRequests = current.webResearchUsedToday,
            localRetrievalsCount = current.localRetrievalsCount,
            cloudCallsRemaining = remaining,
            localRetrievalRatio = localRatio
        )
    }

    fun recordWebResearchRequest() {
        checkAndResetDailyUsage()
        val current = _policy.value
        val updated = current.copy(webResearchUsedToday = current.webResearchUsedToday + 1)
        _policy.value = updated
        savePolicy(updated)
    }

    fun recordLocalRetrieval() {
        val current = _policy.value
        val updated = current.copy(localRetrievalsCount = current.localRetrievalsCount + 1)
        _policy.value = updated
    }

    fun updatePolicy(
        isGeminiEnabled: Boolean = _policy.value.isGeminiEnabled,
        isWifiOnly: Boolean = _policy.value.isWifiOnly,
        dailyRequestLimit: Int = _policy.value.dailyRequestLimit,
        isVisionAllowed: Boolean = _policy.value.isVisionAllowed,
        isBackgroundGeminiAllowed: Boolean = _policy.value.isBackgroundGeminiAllowed
    ) {
        val updated = _policy.value.copy(
            isGeminiEnabled = isGeminiEnabled,
            isWifiOnly = isWifiOnly,
            dailyRequestLimit = dailyRequestLimit,
            isVisionAllowed = isVisionAllowed,
            isBackgroundGeminiAllowed = isBackgroundGeminiAllowed
        )
        _policy.value = updated
        savePolicy(updated)
    }

    /**
     * Checks if a cloud Gemini request is permitted under active policies.
     */
    fun isCloudRequestPermitted(isVision: Boolean = false, isBackground: Boolean = false): Pair<Boolean, String> {
        checkAndResetDailyUsage()

        val current = _policy.value
        if (!current.isGeminiEnabled) {
            return Pair(false, "Gemini Cloud is disabled in Cloud Usage Policy.")
        }

        if (current.isRateLimited) {
            if (System.currentTimeMillis() < current.rateLimitResetTimestamp) {
                val remainingSec = ((current.rateLimitResetTimestamp - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
                return Pair(false, "Free Tier rate-limit cooling down ($remainingSec s remaining). Using local brain.")
            } else {
                _policy.value = current.copy(isRateLimited = false)
            }
        }

        if (isVision && !current.isVisionAllowed) {
            return Pair(false, "Cloud Vision is disabled by policy. Using on-device vision.")
        }

        if (isBackground && !current.isBackgroundGeminiAllowed) {
            return Pair(false, "Background Cloud Gemini operations are disabled to conserve data.")
        }

        if (current.dailyRequestLimit in 1..current.requestsUsedToday) {
            return Pair(false, "Daily Gemini quota reached (${current.requestsUsedToday}/${current.dailyRequestLimit}). Fallback to local brain.")
        }

        if (current.isWifiOnly && !isWifiConnected()) {
            return Pair(false, "Cloud requests restricted to Wi-Fi. Connected via Cellular.")
        }

        return Pair(true, "Permitted")
    }

    fun recordCloudRequest() {
        checkAndResetDailyUsage()
        val current = _policy.value
        val updated = current.copy(requestsUsedToday = current.requestsUsedToday + 1)
        _policy.value = updated
        savePolicy(updated)
    }

    fun handleRateLimitHit(coolingSeconds: Long = 60L) {
        val current = _policy.value
        val resetTime = System.currentTimeMillis() + (coolingSeconds * 1000)
        _policy.value = current.copy(
            isRateLimited = true,
            rateLimitResetTimestamp = resetTime
        )
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun checkAndResetDailyUsage() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today != lastRecordedDay) {
            lastRecordedDay = today
            val reset = _policy.value.copy(requestsUsedToday = 0, isRateLimited = false)
            _policy.value = reset
            savePolicy(reset)
        }
    }

    private fun loadPolicy(): CloudPolicyConfig {
        return CloudPolicyConfig(
            isGeminiEnabled = prefs.getBoolean("gemini_enabled", true),
            isWifiOnly = prefs.getBoolean("wifi_only", false),
            dailyRequestLimit = prefs.getInt("daily_limit", 100),
            requestsUsedToday = prefs.getInt("used_today", 0),
            isVisionAllowed = prefs.getBoolean("vision_allowed", true),
            isBackgroundGeminiAllowed = prefs.getBoolean("bg_allowed", false)
        )
    }

    private fun savePolicy(config: CloudPolicyConfig) {
        prefs.edit()
            .putBoolean("gemini_enabled", config.isGeminiEnabled)
            .putBoolean("wifi_only", config.isWifiOnly)
            .putInt("daily_limit", config.dailyRequestLimit)
            .putInt("used_today", config.requestsUsedToday)
            .putBoolean("vision_allowed", config.isVisionAllowed)
            .putBoolean("bg_allowed", config.isBackgroundGeminiAllowed)
            .apply()
    }
}
