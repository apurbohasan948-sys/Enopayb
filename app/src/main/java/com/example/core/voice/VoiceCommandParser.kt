package com.example.core.voice

/**
 * Phase 12 VoiceCommandParser.
 * Handles multilingual intent normalization across English, Bangla, and Banglish.
 * Maps spoken utterances into normalized universal goals or tool actions.
 */
object VoiceCommandParser {

    data class ParsedVoiceCommand(
        val originalUtterance: String,
        val normalizedGoal: String,
        val detectedLanguage: String, // "EN", "BN", "BANGLISH"
        val isEmergencyStop: Boolean = false,
        val isAffirmative: Boolean = false,
        val isNegative: Boolean = false,
        val suggestedApp: String? = null,
        val queryParameter: String? = null
    )

    fun parse(rawUtterance: String): ParsedVoiceCommand {
        val trimmed = rawUtterance.trim()
        val lower = trimmed.lowercase()
        val lang = detectLanguage(trimmed)

        // 1. Emergency Stop & Interruption
        if (isStopCommand(lower)) {
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "STOP",
                detectedLanguage = lang,
                isEmergencyStop = true
            )
        }

        // 2. Affirmative / Negative Confirmation
        if (isAffirmative(lower)) {
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "CONFIRM",
                detectedLanguage = lang,
                isAffirmative = true
            )
        }
        if (isNegative(lower)) {
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "CANCEL",
                detectedLanguage = lang,
                isNegative = true
            )
        }

        // 3. Multilingual Normalization: Open YouTube / Play / Search
        // e.g. "ইউটিউব ওপেন করো", "YouTube open koro", "Open YouTube"
        if (lower.contains("youtube") || lower.contains("ইউটিউব")) {
            val isSearchOrPlay = lower.contains("search") || lower.contains("play") || lower.contains("watch") ||
                    lower.contains("সার্চ") || lower.contains("বাজাও") || lower.contains("দেখাও") ||
                    lower.contains("chalao") || lower.contains("khujo") || lower.contains("dekhao")

            if (isSearchOrPlay) {
                val query = extractQueryAfterAction(trimmed, listOf(
                    "search for ", "search ", "play ", "watch ",
                    "সার্চ করো ", "খুঁজো ", "বাজাও ", "দেখাও ",
                    "search koro ", "play koro ", "chalao "
                ), fallback = "Tom and Jerry")
                return ParsedVoiceCommand(
                    originalUtterance = trimmed,
                    normalizedGoal = "Open YouTube and search $query",
                    detectedLanguage = lang,
                    suggestedApp = "YouTube",
                    queryParameter = query
                )
            } else {
                return ParsedVoiceCommand(
                    originalUtterance = trimmed,
                    normalizedGoal = "Open YouTube",
                    detectedLanguage = lang,
                    suggestedApp = "YouTube"
                )
            }
        }

        // 4. Chrome / Web Search: "Chrome open koro", "ক্রোম ওপেন করো", "search google for..."
        if (lower.contains("chrome") || lower.contains("ক্রোম") || lower.contains("browser") || lower.contains("ব্রাউজার")) {
            val query = extractQueryAfterAction(trimmed, listOf(
                "search for ", "search ", "find ", "google ",
                "সার্চ করো ", "খুঁজো ",
                "search koro ", "khujo "
            ), fallback = "HSC result")
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "Open Chrome and search for $query",
                detectedLanguage = lang,
                suggestedApp = "Chrome",
                queryParameter = query
            )
        }

        // 5. App Launch: "Open <app>", "<app> open koro", "<app> চালু করো / ওপেন করো"
        val openMatch = matchAppOpen(lower, trimmed)
        if (openMatch != null) {
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "Open $openMatch",
                detectedLanguage = lang,
                suggestedApp = openMatch
            )
        }

        // 6. Navigation: Home & Back
        if (lower == "back" || lower == "go back" || lower == "পেছনে যাও" || lower == "পিছনে যাও" || lower == "back e jao" || lower == "pechone jao") {
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "Go back",
                detectedLanguage = lang
            )
        }
        if (lower == "home" || lower == "go home" || lower == "হোমে যাও" || lower == "হোম স্ক্রিনে যাও" || lower == "home e jao") {
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "Go home",
                detectedLanguage = lang
            )
        }

        // 7. System Tools: Flashlight, Battery
        if (lower.contains("flashlight") || lower.contains("ফ্ল্যাশলাইট") || lower.contains("torch")) {
            val turnOn = !lower.contains("off") && !lower.contains("বন্ধ") && !lower.contains("bondho")
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = if (turnOn) "Turn on flashlight" else "Turn off flashlight",
                detectedLanguage = lang
            )
        }
        if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("চার্জ")) {
            return ParsedVoiceCommand(
                originalUtterance = trimmed,
                normalizedGoal = "Check battery status",
                detectedLanguage = lang
            )
        }

        // Default Universal Goal Pass-Through
        return ParsedVoiceCommand(
            originalUtterance = trimmed,
            normalizedGoal = trimmed,
            detectedLanguage = lang
        )
    }

    private fun matchAppOpen(lower: String, original: String): String? {
        // English: "Open Settings", "Launch WhatsApp"
        val enRegex = Regex("^(?:open|launch)\\s+([a-zA-Z0-9\\s]+)$", RegexOption.IGNORE_CASE)
        val enMatch = enRegex.find(lower)
        if (enMatch != null) {
            return enMatch.groupValues[1].trim()
        }

        // Banglish: "WhatsApp open koro", "Settings khulo", "Gallery chalu koro"
        val banglishRegex = Regex("^([a-zA-Z0-9\\s]+)\\s+(?:open koro|khulo|chalu koro|on koro)$", RegexOption.IGNORE_CASE)
        val banglishMatch = banglishRegex.find(lower)
        if (banglishMatch != null) {
            return banglishMatch.groupValues[1].trim()
        }

        // Bangla: "হোয়াটসঅ্যাপ ওপেন করো", "সেটিংস খোলো", "গ্যালারি চালু করো"
        val bnRegex = Regex("^(.+?)\\s+(?:ওপেন করো|খোলো|চালু করো)$")
        val bnMatch = bnRegex.find(lower)
        if (bnMatch != null) {
            val appRaw = bnMatch.groupValues[1].trim()
            return when {
                appRaw.contains("ইউটিউব") -> "YouTube"
                appRaw.contains("হোয়াটসঅ্যাপ") -> "WhatsApp"
                appRaw.contains("সেটিংস") -> "Settings"
                appRaw.contains("গ্যালারি") || appRaw.contains("ছবি") -> "Gallery"
                appRaw.contains("ক্রোম") -> "Chrome"
                appRaw.contains("ক্যালকুলেটর") -> "Calculator"
                else -> appRaw
            }
        }

        return null
    }

    private fun extractQueryAfterAction(text: String, keywords: List<String>, fallback: String): String {
        for (kw in keywords) {
            val idx = text.indexOf(kw, ignoreCase = true)
            if (idx != -1) {
                val candidate = text.substring(idx + kw.length).trim()
                if (candidate.isNotBlank()) return candidate
            }
        }
        return fallback
    }

    fun isStopCommand(text: String): Boolean {
        val lower = text.lowercase().trim()
        val stopKeywords = listOf(
            "stop", "cancel", "halt", "shut up", "quiet", "abort",
            "থামো", "বন্ধ করো", "চুপ করো", "বাতিল", "বাতিল করো",
            "thamo", "bondho koro", "stop koro", "chup koro"
        )
        return stopKeywords.any { lower == it || lower.startsWith(it) }
    }

    fun isAffirmative(text: String): Boolean {
        val lower = text.lowercase().trim()
        val affirmativeKeywords = listOf(
            "yes", "yeah", "yup", "sure", "proceed", "confirm", "send", "call", "do it", "ok", "okay",
            "হ্যাঁ", "হাঁ", "করো", "পাঠাও", "কল দাও", "ঠিক আছে",
            "ha", "hae", "koro", "pathao", "thik ache", "send koro"
        )
        return affirmativeKeywords.any { lower == it || lower.startsWith(it) }
    }

    fun isNegative(text: String): Boolean {
        val lower = text.lowercase().trim()
        val negativeKeywords = listOf(
            "no", "nope", "cancel", "don't", "abort", "reject", "never mind",
            "না", "করো না", "পাঠাবে না", "বাতিল", "বন্ধ করো",
            "na", "koro na", "pathio na", "batil"
        )
        return negativeKeywords.any { lower == it || lower.startsWith(it) }
    }

    fun detectLanguage(text: String): String {
        val containsBangla = text.any { it.code in 0x0980..0x09FF }
        if (containsBangla) return "BN"
        val lower = text.lowercase()
        val banglishTokens = listOf("koro", "jao", "khulo", "chalu", "pathao", "bolo", "thik", "ache", "ki", "kemon")
        if (banglishTokens.any { lower.contains(it) }) return "BANGLISH"
        return "EN"
    }
}
