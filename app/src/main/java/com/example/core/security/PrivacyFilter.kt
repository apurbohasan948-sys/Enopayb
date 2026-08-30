package com.example.core.security

import java.util.regex.Pattern

data class PrivacySanitizationResult(
    val sanitizedText: String,
    val redactorCount: Int,
    val hadSensitiveData: Boolean
)

object PrivacyFilter {
    private val API_KEY_PATTERN = Pattern.compile("(?:AIza[0-9A-Za-z-_]{35}|sk-[a-zA-Z0-9]{20,}|ghp_[a-zA-Z0-9]{20,}|Bearer\\s+[a-zA-Z0-9._\\-]{20,})", Pattern.CASE_INSENSITIVE)
    private val PASSWORD_PATTERN = Pattern.compile("(?i)(?:password|passwd|pin|secret|otp)[\\s:=]+([a-zA-Z0-9!@#\$%^&*()_+=\\-]{4,})")
    private val CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b")
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
    private val PHONE_PATTERN = Pattern.compile("\\b(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b")

    fun sanitizeForCloud(input: String): PrivacySanitizationResult {
        var count = 0
        var result = input

        // 1. Redact API Keys
        val apiKeyMatcher = API_KEY_PATTERN.matcher(result)
        if (apiKeyMatcher.find()) {
            result = apiKeyMatcher.replaceAll("[REDACTED_API_KEY]")
            count++
        }

        // 2. Redact Passwords / PINs
        val pwdMatcher = PASSWORD_PATTERN.matcher(result)
        if (pwdMatcher.find()) {
            result = pwdMatcher.replaceAll("$1: [REDACTED_SECRET]")
            count++
        }

        // 3. Redact Credit Cards
        val ccMatcher = CREDIT_CARD_PATTERN.matcher(result)
        if (ccMatcher.find()) {
            result = ccMatcher.replaceAll("[REDACTED_PAYMENT_CARD]")
            count++
        }

        return PrivacySanitizationResult(
            sanitizedText = result,
            redactorCount = count,
            hadSensitiveData = count > 0
        )
    }
}
