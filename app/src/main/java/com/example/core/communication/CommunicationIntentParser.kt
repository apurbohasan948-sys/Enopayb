package com.example.core.communication

sealed class CommunicationIntent {
    data class MakeCall(
        val contactQuery: String,
        val directNumber: String? = null,
        val requiresConfirmation: Boolean = true
    ) : CommunicationIntent()

    data class SendSms(
        val contactQuery: String,
        val messageText: String,
        val directNumber: String? = null,
        val requiresConfirmation: Boolean = true
    ) : CommunicationIntent()

    data class SendWhatsAppMessage(
        val contactQuery: String,
        val messageText: String,
        val directNumber: String? = null,
        val requiresConfirmation: Boolean = true
    ) : CommunicationIntent()

    data class FindContact(
        val contactQuery: String
    ) : CommunicationIntent()

    data class OpenWhatsApp(
        val contactQuery: String? = null
    ) : CommunicationIntent()

    data class OpenContact(
        val contactQuery: String
    ) : CommunicationIntent()

    data class UserConfirmation(
        val confirmed: Boolean,
        val originalIntent: String? = null
    ) : CommunicationIntent()

    object Unknown : CommunicationIntent()
}

object CommunicationIntentParser {

    private val CONFIRMATION_YES_PATTERNS = listOf(
        "yes", "send it", "send", "confirm", "proceed", "do it", "approve", "ok", "sure", "yeah", "yep",
        "হ্যাঁ", "পাঠাও", "নিশ্চিত করো", "দাও", "করো", "পাঠিয়ে দাও",
        "haa", "ha", "pathao", "send koro", "confirm koro", "shuru koro"
    )

    private val CONFIRMATION_NO_PATTERNS = listOf(
        "no", "cancel", "don't send", "abort", "stop", "reject", "deny",
        "না", "বাতিল", "পাঠাবে না", "বন্ধ করো", "থাক",
        "na", "cancel koro", "pathio na", "stop koro", "thak"
    )

    /**
     * Parses natural language input across English, Bangla, and Banglish into a structured intent.
     */
    fun parse(rawInput: String): CommunicationIntent {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase()

        // 1. Check direct user confirmation responses
        if (CONFIRMATION_YES_PATTERNS.any { lower == it || lower == "$it." || lower == "$it!" }) {
            return CommunicationIntent.UserConfirmation(confirmed = true)
        }
        if (CONFIRMATION_NO_PATTERNS.any { lower == it || lower == "$it." || lower == "$it!" }) {
            return CommunicationIntent.UserConfirmation(confirmed = false)
        }

        // 2. Parse WhatsApp Messaging
        // Examples:
        // "Send Hammad a WhatsApp message saying I'm on my way"
        // "Send Hammad a WhatsApp message saying I will call later"
        // "WhatsApp e Hammad ke bolo ami ashtesi"
        // "হোয়াটসঅ্যাপে হাম্মাদকে মেসেজ দাও আমি আসছি"
        if (isWhatsAppMessageIntent(lower)) {
            val (contact, message) = extractWhatsAppMessageParams(trimmed)
            if (contact.isNotBlank()) {
                return CommunicationIntent.SendWhatsAppMessage(
                    contactQuery = contact,
                    messageText = message.ifBlank { "Hello" }
                )
            }
        }

        // 3. Parse Open WhatsApp & Find Contact
        // Examples: "Open WhatsApp and find Hammad", "Open WhatsApp", "WhatsApp open koro ar Hammad ke khujo"
        if (isOpenWhatsAppIntent(lower)) {
            val contact = extractWhatsAppFindContact(trimmed)
            return CommunicationIntent.OpenWhatsApp(contactQuery = contact.ifBlank { null })
        }

        // 4. Parse SMS Messaging
        // Examples:
        // "Send Hammad an SMS saying I will call later"
        // "Send an SMS to Mom saying I arrived"
        // "Hammad ke SMS pathao bol ami pore call dibo"
        // "হাম্মাদকে একটা মেসেজ পাঠাও আমি পরে কল দিব"
        if (isSmsIntent(lower)) {
            val (contact, message) = extractSmsParams(trimmed)
            if (contact.isNotBlank()) {
                return CommunicationIntent.SendSms(
                    contactQuery = contact,
                    messageText = message.ifBlank { "Hello" }
                )
            }
        }

        // 5. Parse Phone Calls
        // Examples:
        // "Call Hammad", "Call Mom", "Call 01712345678"
        // "Hammad ke call dao", "Ma ke phone koro"
        // "হাম্মাদকে কল দাও", "মাকে ফোন করো"
        if (isCallIntent(lower)) {
            val contact = extractCallTarget(trimmed)
            if (contact.isNotBlank()) {
                val directNum = if (contact.replace("[^0-9+]".toRegex(), "").length >= 7) contact else null
                return CommunicationIntent.MakeCall(
                    contactQuery = contact,
                    directNumber = directNum
                )
            }
        }

        // 6. Parse Contact Search / Lookup
        // Examples:
        // "Who is Hammad?", "Find Hammad in my contacts", "Search for Hammad",
        // "কন্টাক্টে হাম্মাদকে খুঁজুন", "হাম্মাদ কে?", "Hammad ke search koro"
        if (isFindContactIntent(lower)) {
            val contact = extractFindContactQuery(trimmed)
            if (contact.isNotBlank()) {
                return CommunicationIntent.FindContact(contactQuery = contact)
            }
        }

        return CommunicationIntent.Unknown
    }

    private fun isWhatsAppMessageIntent(lower: String): Boolean {
        val hasWa = lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ") || lower.contains("whatapp") || lower.contains("what's app")
        val hasMsg = lower.contains("message") || lower.contains("saying") || lower.contains("text") ||
                lower.contains("মেসেজ") || lower.contains("বলো") || lower.contains("bolo") || lower.contains("pathao")
        return hasWa && hasMsg
    }

    private fun extractWhatsAppMessageParams(raw: String): Pair<String, String> {
        var text = raw

        // Patterns: "Send [Name] a WhatsApp message saying [Msg]"
        val sayingRegex = Regex("(?i)saying\\s+(.+)$")
        val boloRegex = Regex("(?i)(?:bolo|bole|bol|বলো|বার্তা দাও)\\s+(.+)$")
        val messageRegex = Regex("(?i)(?:message|মেসেজ পাঠাও|মেসেজ দাও|মেসেজ)\\s+(.+)$")

        var messageBody = ""
        sayingRegex.find(text)?.let {
            messageBody = it.groupValues[1].trim()
            text = text.substring(0, it.range.first).trim()
        } ?: boloRegex.find(text)?.let {
            messageBody = it.groupValues[1].trim()
            text = text.substring(0, it.range.first).trim()
        }

        // Clean contact part
        var contactPart = text
            .replace(Regex("(?i)send a whatsapp message to"), "")
            .replace(Regex("(?i)send whatsapp message to"), "")
            .replace(Regex("(?i)send a whatsapp message"), "")
            .replace(Regex("(?i)send whatsapp to"), "")
            .replace(Regex("(?i)a whatsapp message to"), "")
            .replace(Regex("(?i)a whatsapp message"), "")
            .replace(Regex("(?i)whatsapp message to"), "")
            .replace(Regex("(?i)whatsapp e"), "")
            .replace(Regex("(?i)whatsapp"), "")
            .replace(Regex("(?i)হোয়াটসঅ্যাপে"), "")
            .replace(Regex("(?i)মেসেজ দাও"), "")
            .replace(Regex("(?i)মেসেজ পাঠাও"), "")
            .replace(Regex("(?i)send"), "")
            .trim()

        return Pair(contactPart, messageBody)
    }

    private fun isOpenWhatsAppIntent(lower: String): Boolean {
        val hasWa = lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ")
        val hasOpenOrFind = lower.contains("open") || lower.contains("launch") || lower.contains("find") ||
                lower.contains("ওপেন") || lower.contains("খুঁজ") || lower.contains("khujo")
        return hasWa && hasOpenOrFind
    }

    private fun extractWhatsAppFindContact(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("find") -> raw.substring(lower.indexOf("find") + 4).replace("on whatsapp", "", ignoreCase = true).replace("in whatsapp", "", ignoreCase = true).trim()
            lower.contains("খুঁজে") || lower.contains("খুঁজুন") -> {
                raw.replace("হোয়াটসঅ্যাপ", "").replace("ওপেন করো", "").replace("খুঁজে বের করো", "").replace("খুঁজুন", "").replace("আর", "").trim()
            }
            lower.contains("khujo") -> {
                raw.replace("whatsapp", "", ignoreCase = true).replace("open koro", "", ignoreCase = true).replace("ar", "", ignoreCase = true).replace("khujo", "", ignoreCase = true).trim()
            }
            else -> ""
        }
    }

    private fun isSmsIntent(lower: String): Boolean {
        val hasSms = lower.contains("sms") || lower.contains("text message") || lower.contains("মেসেজ পাঠাও") || lower.contains("এসএমএস")
        return hasSms && (lower.contains("send") || lower.contains("saying") || lower.contains("পাঠাও") || lower.contains("pathao") || lower.contains("to"))
    }

    private fun extractSmsParams(raw: String): Pair<String, String> {
        var text = raw

        val sayingRegex = Regex("(?i)saying\\s+(.+)$")
        val bolRegex = Regex("(?i)(?:bol|bolo|বলে|বলো)\\s+(.+)$")

        var messageBody = ""
        sayingRegex.find(text)?.let {
            messageBody = it.groupValues[1].trim()
            text = text.substring(0, it.range.first).trim()
        } ?: bolRegex.find(text)?.let {
            messageBody = it.groupValues[1].trim()
            text = text.substring(0, it.range.first).trim()
        }

        var contactPart = text
            .replace(Regex("(?i)send an sms to"), "")
            .replace(Regex("(?i)send sms to"), "")
            .replace(Regex("(?i)send an sms"), "")
            .replace(Regex("(?i)send sms"), "")
            .replace(Regex("(?i)an sms to"), "")
            .replace(Regex("(?i)sms to"), "")
            .replace(Regex("(?i)sms pathao"), "")
            .replace(Regex("(?i)এসএমএস পাঠাও"), "")
            .replace(Regex("(?i)একটা মেসেজ পাঠাও"), "")
            .replace(Regex("(?i)মেসেজ পাঠাও"), "")
            .replace(Regex("(?i)send"), "")
            .trim()

        return Pair(contactPart, messageBody)
    }

    private fun isCallIntent(lower: String): Boolean {
        val hasCall = lower.startsWith("call ") || lower.startsWith("phone ") || lower.startsWith("dial ") ||
                lower.endsWith(" call dao") || lower.endsWith(" phone koro") || lower.endsWith("কে কল দাও") || lower.endsWith("কে ফোন করো") ||
                lower.contains("কল দাও") || lower.contains("ফোন করো") || lower.contains("call ")
        return hasCall && !lower.contains("whatsapp") && !lower.contains("sms")
    }

    private fun extractCallTarget(raw: String): String {
        return raw
            .replace(Regex("(?i)^call\\s+"), "")
            .replace(Regex("(?i)^phone\\s+"), "")
            .replace(Regex("(?i)^dial\\s+"), "")
            .replace(Regex("(?i)\\s+call dao$"), "")
            .replace(Regex("(?i)\\s+phone koro$"), "")
            .replace(Regex("কল দাও$"), "")
            .replace(Regex("ফোন করো$"), "")
            .trim()
    }

    private fun isFindContactIntent(lower: String): Boolean {
        return lower.startsWith("who is ") || lower.startsWith("who's ") ||
                lower.contains("find ") || lower.contains("search for ") || lower.contains("in my contacts") ||
                lower.contains("খুঁজুন") || lower.contains("খুঁজে") || lower.contains("কে?") || lower.contains("search koro")
    }

    private fun extractFindContactQuery(raw: String): String {
        return raw
            .replace(Regex("(?i)^who is\\s+"), "")
            .replace(Regex("(?i)^who's\\s+"), "")
            .replace(Regex("(?i)^find\\s+"), "")
            .replace(Regex("(?i)^search for\\s+"), "")
            .replace(Regex("(?i)\\s+in my contacts$"), "")
            .replace(Regex("(?i)\\s+search koro$"), "")
            .replace(Regex("কন্টাক্টে"), "")
            .replace(Regex("খুঁজুন"), "")
            .replace(Regex("খুঁজে বের করো"), "")
            .replace(Regex("\\?"), "")
            .trim()
    }
}
