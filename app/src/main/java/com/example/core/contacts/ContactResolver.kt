package com.example.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val type: String = "Mobile",
    val normalizedQuery: String = ""
)

sealed class ContactResolutionResult {
    data class SingleMatch(val contact: ContactInfo) : ContactResolutionResult()
    data class MultipleMatches(val query: String, val matches: List<ContactInfo>) : ContactResolutionResult()
    data class NoMatch(val query: String, val reason: String = "No contacts found matching query.") : ContactResolutionResult()
    data class PermissionRequired(val message: String) : ContactResolutionResult()
    data class Error(val message: String) : ContactResolutionResult()
}

object ContactResolver {

    /**
     * Common name translations between Bangla, Banglish, and English aliases.
     */
    private val KNOWN_ALIASES = mapOf(
        "mom" to listOf("mom", "mother", "মা", "আম্মু", "amma", "ammu", "ma"),
        "dad" to listOf("dad", "father", "বাবা", "আব্বু", "abba", "abbu", "baba"),
        "brother" to listOf("brother", "ভাই", "ভাইয়া", "bhai", "bhaiya"),
        "sister" to listOf("sister", "বোন", "আপু", "bon", "apu"),
        "hammad" to listOf("hammad", "হাম্মাদ্", "হাম্মাদ", "hamad", "hmd"),
        "apurbo" to listOf("apurbo", "অপূর্ব", "apurba")
    )

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Normalizes raw natural language queries in English, Bangla, or Banglish
     * by stripping conversational affixes, case markers, and carrier words.
     */
    fun normalizeContactName(rawInput: String): String {
        var query = rawInput.trim()

        // 1. Strip English carrier phrases
        val englishPrefixes = listOf(
            "call ", "phone ", "dial ", "ring ",
            "send sms to ", "send an sms to ", "sms to ", "text to ", "text ", "message to ", "msg to ",
            "whatsapp to ", "send a whatsapp message to ", "send whatsapp to ", "whatsapp message to ",
            "find ", "search for ", "search ", "lookup ", "look up ",
            "who is ", "who's ", "where is ", "contact for ", "number of ", "number for "
        )
        for (prefix in englishPrefixes) {
            if (query.startsWith(prefix, ignoreCase = true)) {
                query = query.substring(prefix.length).trim()
            }
        }

        // 2. Strip English suffix phrases
        val englishSuffixes = listOf(
            " on whatsapp", " in whatsapp", " via whatsapp",
            " in my contacts", " from contacts", " in contacts", " on phone",
            " please", " now"
        )
        for (suffix in englishSuffixes) {
            if (query.endsWith(suffix, ignoreCase = true)) {
                query = query.substring(0, query.length - suffix.length).trim()
            }
        }

        // 3. Strip Bangla carrier phrases
        val banglaPrefixes = listOf(
            "কল দাও ", "ফোন করো ", "মেসেজ পাঠাও ", "মেসেজ দাও ", "হোয়াটসঅ্যাপে বলো ", "হোয়াটসঅ্যাপে মেসেজ দাও ",
            "খুঁজে বের করো ", "খুঁজুন ", "কে "
        )
        for (prefix in banglaPrefixes) {
            if (query.startsWith(prefix)) {
                query = query.substring(prefix.length).trim()
            }
        }

        // 4. Strip Bangla/Banglish object/possessive suffixes like -কে, -এর, ke, er
        query = query
            .replace(Regex("(?i)\\s+ke$"), "") // e.g. "Hammad ke" -> "Hammad"
            .replace(Regex("(?i)ke$"), "")    // e.g. "Hammadke" -> "Hammad"
            .replace(Regex("কে$"), "")        // e.g. "হাম্মাদকে" -> "হাম্মাদ"
            .replace(Regex("ের$"), "")        // e.g. "মায়ের" -> "মা"
            .replace(Regex("(?i)\\s+er$"), "")
            .replace(Regex("(?i)\\s+in my contacts$"), "")
            .replace(Regex("(?i)\\s+on whatsapp$"), "")
            .replace(Regex("(?i)^who is\\s+"), "")
            .trim()

        return query
    }

    /**
     * Searches device contacts using Android ContactsContract with real ContentResolver.
     * NEVER hallucinates or creates fake phone numbers.
     */
    fun searchContacts(context: Context, rawQuery: String): ContactResolutionResult {
        if (!hasContactsPermission(context)) {
            return ContactResolutionResult.PermissionRequired(
                "Contacts permission (READ_CONTACTS) is required to query device contacts. Please grant Contacts permission in Settings."
            )
        }

        val normalizedQuery = normalizeContactName(rawQuery)
        if (normalizedQuery.isEmpty()) {
            return ContactResolutionResult.NoMatch(rawQuery, "Search query is empty.")
        }

        val matches = mutableListOf<ContactInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )

        // Query by display name matching
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$normalizedQuery%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            val seenNumbers = mutableSetOf<String>()
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx != -1) it.getString(nameIdx) ?: "" else ""
                    val rawNumber = if (numberIdx != -1) it.getString(numberIdx) ?: "" else ""
                    val type = if (typeIdx != -1) it.getInt(typeIdx).toString() else "Mobile"

                    val cleanNumber = rawNumber.replace("\\s|-".toRegex(), "")
                    if (cleanNumber.isNotEmpty() && !seenNumbers.contains(cleanNumber)) {
                        seenNumbers.add(cleanNumber)
                        matches.add(ContactInfo(id, name, rawNumber, type, normalizedQuery))
                    }
                }
            }

            // Fallback: If no direct match, check phonetic and alias mappings (e.g. "মা" -> "Mom", "Mom" -> "Ma")
            if (matches.isEmpty()) {
                val lowerQuery = normalizedQuery.lowercase()
                for ((canonicalKey, aliases) in KNOWN_ALIASES) {
                    if (aliases.any { it.equals(lowerQuery, ignoreCase = true) }) {
                        for (alias in aliases) {
                            if (alias != lowerQuery) {
                                val aliasCursor = context.contentResolver.query(
                                    uri,
                                    projection,
                                    selection,
                                    arrayOf("%$alias%"),
                                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                                )
                                aliasCursor?.use { ac ->
                                    val idIdx = ac.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                                    val nameIdx = ac.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                                    val numberIdx = ac.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    val typeIdx = ac.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                                    while (ac.moveToNext()) {
                                        val id = if (idIdx != -1) ac.getString(idIdx) ?: "" else ""
                                        val name = if (nameIdx != -1) ac.getString(nameIdx) ?: "" else ""
                                        val rawNumber = if (numberIdx != -1) ac.getString(numberIdx) ?: "" else ""
                                        val type = if (typeIdx != -1) ac.getInt(typeIdx).toString() else "Mobile"

                                        val cleanNumber = rawNumber.replace("\\s|-".toRegex(), "")
                                        if (cleanNumber.isNotEmpty() && !seenNumbers.contains(cleanNumber)) {
                                            seenNumbers.add(cleanNumber)
                                            matches.add(ContactInfo(id, name, rawNumber, type, normalizedQuery))
                                        }
                                    }
                                }
                                if (matches.isNotEmpty()) break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return ContactResolutionResult.Error("Failed to query device contacts: ${e.localizedMessage}")
        } finally {
            cursor?.close()
        }

        return when {
            matches.isEmpty() -> ContactResolutionResult.NoMatch(
                query = normalizedQuery,
                reason = "No contact found on this device matching \"$normalizedQuery\"."
            )
            matches.size == 1 -> ContactResolutionResult.SingleMatch(matches.first())
            else -> ContactResolutionResult.MultipleMatches(
                query = normalizedQuery,
                matches = matches
            )
        }
    }
}
