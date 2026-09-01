package com.example.core.agent.nlu

/**
 * Extracted entities from user utterance across multilingual inputs.
 */
data class ExtractedEntities(
    val appName: String? = null,
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val searchQuery: String? = null,
    val mediaTitle: String? = null,
    val messageBody: String? = null,
    val targetText: String? = null,
    val direction: String? = null, // UP, DOWN, LEFT, RIGHT
    val ordinalIndex: Int? = null, // 0-indexed: 0 for first, 1 for second, etc.
    val settingName: String? = null,
    val toggleState: Boolean? = null,
    val mathExpression: String? = null,
    val rawMap: Map<String, String> = emptyMap()
)

/**
 * Multilingual entity extraction engine for English, Bengali (বাংলা), and Banglish inputs.
 */
object EntityExtractor {

    private val ORDINAL_PATTERNS = mapOf(
        0 to listOf("first", "1st", "প্রথম", "১ম", "prothom", "first one", "1st one", "prothom ta", "top one"),
        1 to listOf("second", "2nd", "দ্বিতীয়", "২য়", "ditiyo", "second one", "2nd one", "ditiyo ta", "majher ta"),
        2 to listOf("third", "3rd", "তৃতীয়", "৩য়", "tritiyo", "third one", "3rd one", "tritiyo ta"),
        3 to listOf("fourth", "4th", "চতুর্থ", "৪র্থ", "choturtho", "fourth one"),
        4 to listOf("fifth", "5th", "পঞ্চম", "৫ম", "ponchom", "fifth one"),
        -1 to listOf("last", "শেষ", "shesh", "shesh ta", "last one", "bottom one")
    )

    private val APP_ALIASES = mapOf(
        "youtube" to listOf("youtube", "yt", "ইউটিউব", "ইউটুউব"),
        "chrome" to listOf("chrome", "browser", "ক্রোম", "ব্রাউজার", "google chrome"),
        "whatsapp" to listOf("whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটস্যাপ", "wapp"),
        "settings" to listOf("settings", "setting", "সেটিংস", "সেটিং"),
        "calculator" to listOf("calculator", "calc", "ক্যালকুলেটর", "হিসাব"),
        "camera" to listOf("camera", "ক্যামেরা"),
        "gallery" to listOf("gallery", "photos", "গ্যালারি", "ছবি", "গ্যালারী"),
        "contacts" to listOf("contacts", "address book", "কন্টাক্ট", "যোগাযোগ"),
        "messages" to listOf("messages", "sms", "মেসেজ"),
        "clock" to listOf("clock", "alarm", "ঘড়ি", "ঘড়ি", "অ্যালার্ম"),
        "maps" to listOf("maps", "google maps", "ম্যাপ", "ম্যাপস"),
        "play store" to listOf("play store", "প্লে স্টোর", "প্লেস্টোর")
    )

    fun extract(input: String, currentApp: String? = null): ExtractedEntities {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()
        val map = mutableMapOf<String, String>()

        // 1. Ordinal / Position Extraction
        var ordinal: Int? = null
        for ((idx, words) in ORDINAL_PATTERNS) {
            if (words.any { lower.contains(it) }) {
                ordinal = idx
                map["ordinal"] = idx.toString()
                break
            }
        }

        // 2. App Name Extraction
        var matchedApp: String? = null
        for ((canonicalApp, aliases) in APP_ALIASES) {
            if (aliases.any { alias ->
                    lower.contains("open $alias") || lower.contains("launch $alias") ||
                    lower.contains("$alias খোল") || lower.contains("$alias ওপেন") ||
                    lower.contains("$alias চালু") || lower == alias || lower.startsWith("$alias e ")
                }
            ) {
                matchedApp = canonicalApp
                map["app_name"] = canonicalApp
                break
            }
        }

        if (matchedApp == null) {
            // General "open X" extraction
            if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("খোল ") || lower.startsWith("ওপেন করো ")) {
                val candidate = trimmed
                    .substringAfter("open ", "")
                    .substringAfter("launch ", "")
                    .substringAfter("খোল ", "")
                    .substringAfter("ওপেন করো ", "")
                    .substringBefore(" and ")
                    .substringBefore(" then ")
                    .substringBefore(" আর ")
                    .trim()
                if (candidate.isNotBlank()) {
                    matchedApp = candidate
                    map["app_name"] = candidate
                }
            }
        }

        // 3. Direction Extraction
        val direction = when {
            lower.contains("down") || lower.contains("নিচে") || lower.contains("niche") -> "DOWN"
            lower.contains("up") || lower.contains("উপরে") || lower.contains("upore") -> "UP"
            lower.contains("left") || lower.contains("বামে") || lower.contains("bame") -> "LEFT"
            lower.contains("right") || lower.contains("ডানে") || lower.contains("dane") -> "RIGHT"
            else -> null
        }
        if (direction != null) map["direction"] = direction

        // 4. Toggle State Extraction
        val toggleState = when {
            lower.contains("turn on") || lower.contains("enable") || lower.contains("চালু") || lower.contains("chalu") || lower.contains("on") || lower.contains("জ্বালাও") -> true
            lower.contains("turn off") || lower.contains("disable") || lower.contains("বন্ধ") || lower.contains("bondho") || lower.contains("off") || lower.contains("নিভাও") -> false
            else -> null
        }
        if (toggleState != null) map["toggle_state"] = toggleState.toString()

        // 5. Setting Name Extraction
        val settingName = when {
            lower.contains("bluetooth") || lower.contains("ব্লুটুথ") -> "Bluetooth"
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("ওয়াইফাই") -> "Wi-Fi"
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("টর্চ") || lower.contains("ফ্ল্যাশলাইট") -> "Flashlight"
            lower.contains("display") || lower.contains("brightness") || lower.contains("উজ্জ্বলতা") -> "Display"
            lower.contains("sound") || lower.contains("volume") || lower.contains("ভলিউম") -> "Sound"
            lower.contains("battery") || lower.contains("ব্যাটারি") -> "Battery"
            else -> null
        }
        if (settingName != null) map["setting_name"] = settingName

        // 6. Search / Media Query Extraction
        var searchQuery: String? = null
        var mediaTitle: String? = null

        val searchPrefixes = listOf(
            "search for ", "search ", "google ", "find ", "খুঁজো ", "খুঁজে বের করো ", "সার্চ করো ",
            "search koro ", "khoj ", "play ", "চালাও ", "বাজাও ", "play koro ", "chalao "
        )

        for (prefix in searchPrefixes) {
            if (lower.contains(prefix)) {
                val candidate = trimmed.substring(trimmed.indexOf(prefix, ignoreCase = true) + prefix.length)
                    .substringBefore(" on ")
                    .substringBefore(" in ")
                    .substringBefore(" e ")
                    .substringBefore(" এ ")
                    .trim()
                if (candidate.isNotBlank()) {
                    if (lower.contains("play") || lower.contains("চালাও") || lower.contains("বাজাও") || lower.contains("chalao") || lower.contains("video")) {
                        mediaTitle = candidate
                        map["media_title"] = candidate
                    } else {
                        searchQuery = candidate
                        map["search_query"] = candidate
                    }
                    break
                }
            }
        }

        // 7. Communication Entities (Contact Name, Phone Number, Message Body)
        var contactName: String? = null
        var phoneNumber: String? = null
        var messageBody: String? = null

        // Phone number regex
        val phoneMatch = Regex("(\\+?[0-9]{6,15})").find(trimmed)
        if (phoneMatch != null) {
            phoneNumber = phoneMatch.value
            map["phone_number"] = phoneNumber
        }

        // Contact extraction
        if (lower.contains("call ") || lower.contains("কল করো") || lower.contains("ফোন করো") || lower.contains("phone koro") || lower.contains("call to ")) {
            val contact = trimmed
                .replace("(?i)call to |call |ফোন করো |কল করো |ফোন দাও |phone koro |কে কল করো|ke call dao".toRegex(), "")
                .trim()
            if (contact.isNotBlank()) {
                contactName = contact
                map["contact_name"] = contact
            }
        } else if (lower.contains("whatsapp") || lower.contains("sms") || lower.contains("message") || lower.contains("মেসেজ") || lower.contains("text")) {
            if (trimmed.contains("-কে বলো") || trimmed.contains("-কে বল") || trimmed.contains(" ke bolo ")) {
                contactName = trimmed.substringBefore("-কে").substringBefore(" ke ").substringAfter("খুলে ").substringAfter("WhatsApp ").substringAfter("to ").trim()
                messageBody = trimmed.substringAfter("বলো ").substringAfter("বল ").substringAfter("bolo ").trim()
            } else if (trimmed.contains(" to ") && trimmed.contains(":")) {
                contactName = trimmed.substringAfter(" to ").substringBefore(":").trim()
                messageBody = trimmed.substringAfter(":").trim()
            } else if (trimmed.contains(" to ") && trimmed.contains(" that ")) {
                contactName = trimmed.substringAfter(" to ").substringBefore(" that ").trim()
                messageBody = trimmed.substringAfter(" that ").trim()
            } else if (trimmed.contains(" to ")) {
                contactName = trimmed.substringAfter(" to ").trim()
            }
            if (contactName != null) map["contact_name"] = contactName
            if (messageBody != null) map["message_body"] = messageBody
        }

        // 8. Target UI Text / Click target
        var targetText: String? = null
        val clickPrefixes = listOf(
            "click on ", "click ", "tap on ", "tap ", "press ", "ক্লিক করো ", "ট্যাপ করো ", "চাপ দাও ", "press koro "
        )
        for (prefix in clickPrefixes) {
            if (lower.contains(prefix)) {
                val candidate = trimmed.substring(trimmed.indexOf(prefix, ignoreCase = true) + prefix.length).trim()
                if (candidate.isNotBlank()) {
                    targetText = candidate
                    map["target_text"] = candidate
                    break
                }
            }
        }

        // 9. Math expression
        var mathExpr: String? = null
        if (lower.contains("calculate") || lower.contains("হিসাব") || lower.contains("+") || lower.contains("×") || lower.contains("*") || lower.contains("/") || lower.contains("-")) {
            val exprDigits = trimmed.replace(Regex("[^0-9+\\-*/xX÷.]"), " ").trim()
            val tokens = exprDigits.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (tokens.size >= 2) {
                mathExpr = tokens.joinToString("")
                map["math_expression"] = mathExpr
            }
        }

        return ExtractedEntities(
            appName = matchedApp,
            contactName = contactName,
            phoneNumber = phoneNumber,
            searchQuery = searchQuery,
            mediaTitle = mediaTitle,
            messageBody = messageBody,
            targetText = targetText,
            direction = direction,
            ordinalIndex = ordinal,
            settingName = settingName,
            toggleState = toggleState,
            mathExpression = mathExpr,
            rawMap = map
        )
    }
}
