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
    val type: String = "Mobile"
)

sealed class ContactResolutionResult {
    data class SingleMatch(val contact: ContactInfo) : ContactResolutionResult()
    data class MultipleMatches(val query: String, val matches: List<ContactInfo>) : ContactResolutionResult()
    data class NoMatch(val query: String) : ContactResolutionResult()
    data class PermissionRequired(val message: String) : ContactResolutionResult()
    data class Error(val message: String) : ContactResolutionResult()
}

object ContactResolver {

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun searchContacts(context: Context, query: String): ContactResolutionResult {
        if (!hasContactsPermission(context)) {
            return ContactResolutionResult.PermissionRequired(
                "Contacts permission (READ_CONTACTS) is required to search and resolve phone numbers."
            )
        }

        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return ContactResolutionResult.NoMatch(query)
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
        val selectionArgs = arrayOf("%$trimmedQuery%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                val seenNumbers = mutableSetOf<String>()
                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx != -1) it.getString(nameIdx) ?: "" else ""
                    val rawNumber = if (numberIdx != -1) it.getString(numberIdx) ?: "" else ""
                    val type = if (typeIdx != -1) it.getInt(typeIdx).toString() else "Mobile"

                    val cleanNumber = rawNumber.replace("\\s|-".toRegex(), "")
                    if (cleanNumber.isNotEmpty() && !seenNumbers.contains(cleanNumber)) {
                        seenNumbers.add(cleanNumber)
                        matches.add(ContactInfo(id, name, rawNumber, type))
                    }
                }
            }
        } catch (e: Exception) {
            return ContactResolutionResult.Error("Failed to query device contacts: ${e.localizedMessage}")
        } finally {
            cursor?.close()
        }

        return when {
            matches.isEmpty() -> ContactResolutionResult.NoMatch(trimmedQuery)
            matches.size == 1 -> ContactResolutionResult.SingleMatch(matches.first())
            else -> ContactResolutionResult.MultipleMatches(trimmedQuery, matches)
        }
    }
}
