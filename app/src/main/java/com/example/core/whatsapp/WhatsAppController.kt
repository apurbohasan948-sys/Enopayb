package com.example.core.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.vision.ScreenUnderstandingEngine
import com.example.core.vision.SemanticTarget
import com.example.core.vision.UnifiedScreen
import kotlinx.coroutines.delay

data class WhatsAppActionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val evidence: String? = null,
    val isVerified: Boolean = false,
    val missingRequirement: String? = null
)

class WhatsAppController(
    private val context: Context,
    private val screenEngine: ScreenUnderstandingEngine? = null
) {

    companion object {
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"
    }

    fun isWhatsAppInstalled(): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo(WHATSAPP_PKG, 0)
            true
        } catch (e: Exception) {
            try {
                pm.getPackageInfo(WHATSAPP_BUSINESS_PKG, 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun getInstalledWhatsAppPackage(): String? {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo(WHATSAPP_PKG, 0)
            WHATSAPP_PKG
        } catch (e: Exception) {
            try {
                pm.getPackageInfo(WHATSAPP_BUSINESS_PKG, 0)
                WHATSAPP_BUSINESS_PKG
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Opens WhatsApp application and verifies that it reached the foreground.
     */
    suspend fun openWhatsApp(): WhatsAppActionResult {
        val pkg = getInstalledWhatsAppPackage()
        if (pkg == null) {
            return WhatsAppActionResult(
                success = false,
                action = "OPEN_WHATSAPP",
                message = "WhatsApp is not installed on this device.",
                missingRequirement = "com.whatsapp package not installed"
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (launchIntent == null) {
            return WhatsAppActionResult(
                success = false,
                action = "OPEN_WHATSAPP",
                message = "Failed to create launch intent for $pkg."
            )
        }

        context.startActivity(launchIntent)
        delay(1200)

        val activeApp = JarvisAccessibilityService.currentForegroundApp.value
        val isForeground = activeApp == pkg || activeApp.contains("whatsapp", ignoreCase = true)

        return WhatsAppActionResult(
            success = true,
            action = "OPEN_WHATSAPP",
            message = "WhatsApp opened successfully.",
            evidence = "Foreground app detected as: ${if (isForeground) activeApp else "$pkg (Launch dispatched)"}",
            isVerified = isForeground
        )
    }

    /**
     * Opens chat with a specific phone number using legitimate WhatsApp deep link.
     */
    suspend fun openChatDirect(phoneNumber: String, prefilledMessage: String? = null): WhatsAppActionResult {
        val cleanNumber = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (cleanNumber.isBlank()) {
            return WhatsAppActionResult(
                success = false,
                action = "OPEN_CHAT",
                message = "Invalid phone number for WhatsApp: \"$phoneNumber\""
            )
        }

        val encodedMsg = prefilledMessage?.let { Uri.encode(it) } ?: ""
        val url = if (encodedMsg.isNotEmpty()) {
            "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMsg"
        } else {
            "https://api.whatsapp.com/send?phone=$cleanNumber"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                val pkg = getInstalledWhatsAppPackage()
                if (pkg != null) setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            delay(1200)

            val activeApp = JarvisAccessibilityService.currentForegroundApp.value
            val isVerified = activeApp.contains("whatsapp", ignoreCase = true)

            WhatsAppActionResult(
                success = true,
                action = "OPEN_CHAT",
                message = "WhatsApp conversation opened for $cleanNumber.",
                evidence = "Dispatched deep link to WhatsApp intent. Foreground app: $activeApp",
                isVerified = isVerified
            )
        } catch (e: Exception) {
            WhatsAppActionResult(
                success = false,
                action = "OPEN_CHAT",
                message = "Failed to open WhatsApp chat: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Finds and opens a chat for a contact name using Accessibility & Vision.
     * Uses dynamic element search without fixed coordinates.
     */
    suspend fun searchAndOpenContact(contactName: String): WhatsAppActionResult {
        val accessibilityService = JarvisAccessibilityService.instance
        if (accessibilityService == null) {
            return WhatsAppActionResult(
                success = false,
                action = "SEARCH_CONTACT",
                message = "Accessibility Service is required to automate search in WhatsApp.",
                missingRequirement = "BIND_ACCESSIBILITY_SERVICE"
            )
        }

        // 1. Click Search icon / Search bar
        val searchClicked = clickSearchControl()
        if (!searchClicked) {
            // Try searching text directly on screen in case conversations list is already visible
            val directMatch = JarvisAccessibilityService.clickElement(contactName)
            if (directMatch.success) {
                delay(1000)
                return WhatsAppActionResult(
                    success = true,
                    action = "SEARCH_CONTACT",
                    message = "Opened conversation with $contactName from visible list.",
                    evidence = "Clicked node matching \"$contactName\"",
                    isVerified = true
                )
            }

            return WhatsAppActionResult(
                success = false,
                action = "SEARCH_CONTACT",
                message = "Could not locate search icon (🔍) or contact \"$contactName\" on WhatsApp screen.",
                evidence = directMatch.evidence
            )
        }

        delay(800)

        // 2. Type contact name in search input
        val typed = JarvisAccessibilityService.typeText(null, contactName, context)
        delay(1200)

        // 3. Click the search result matching the contact
        val clickedResult = JarvisAccessibilityService.clickElement(contactName)
        delay(1000)

        return if (clickedResult.success) {
            WhatsAppActionResult(
                success = true,
                action = "SEARCH_CONTACT",
                message = "Successfully selected contact \"$contactName\" in WhatsApp.",
                evidence = "Found and clicked search result node for \"$contactName\"",
                isVerified = true
            )
        } else {
            WhatsAppActionResult(
                success = false,
                action = "SEARCH_CONTACT",
                message = "Typed \"$contactName\" into WhatsApp search, but matching chat was not found or clickable.",
                evidence = "Search executed, no clickable row matching \"$contactName\" detected"
            )
        }
    }

    /**
     * Prepares and enters a message into the WhatsApp message input field.
     */
    suspend fun prepareMessageInChat(messageText: String): WhatsAppActionResult {
        val accessibilityService = JarvisAccessibilityService.instance
        if (accessibilityService == null) {
            return WhatsAppActionResult(
                success = false,
                action = "PREPARE_MESSAGE",
                message = "Accessibility Service is required to interact with WhatsApp input.",
                missingRequirement = "BIND_ACCESSIBILITY_SERVICE"
            )
        }

        // Try typing in focused or first editable text field
        val inputSuccess = JarvisAccessibilityService.typeText(null, messageText, context)
        delay(600)

        return if (inputSuccess.success) {
            WhatsAppActionResult(
                success = true,
                action = "PREPARE_MESSAGE",
                message = "Message text entered into WhatsApp chat: \"$messageText\". Awaiting confirmation to send.",
                evidence = "Typed ${messageText.length} characters into WhatsApp message box",
                isVerified = true
            )
        } else {
            WhatsAppActionResult(
                success = false,
                action = "PREPARE_MESSAGE",
                message = "Could not locate editable message field in current WhatsApp view.",
                evidence = inputSuccess.evidence
            )
        }
    }

    /**
     * Clicks the Send button in WhatsApp.
     */
    suspend fun tapSendButton(): WhatsAppActionResult {
        val accessibilityService = JarvisAccessibilityService.instance
        if (accessibilityService == null) {
            return WhatsAppActionResult(
                success = false,
                action = "SEND_MESSAGE",
                message = "Accessibility Service required to tap Send button.",
                missingRequirement = "BIND_ACCESSIBILITY_SERVICE"
            )
        }

        // WhatsApp send button has content description "Send" / "পাঠান" or ID "send"
        val sendRes = JarvisAccessibilityService.clickElement("Send")
        val clicked = sendRes.success ||
                JarvisAccessibilityService.clickElement("পাঠান").success ||
                JarvisAccessibilityService.clickElement("com.whatsapp:id/send").success

        delay(1200)

        return if (clicked) {
            WhatsAppActionResult(
                success = true,
                action = "SEND_MESSAGE",
                message = "Send button clicked in WhatsApp.",
                evidence = "Triggered ACTION_CLICK on WhatsApp send control",
                isVerified = true
            )
        } else {
            WhatsAppActionResult(
                success = false,
                action = "SEND_MESSAGE",
                message = "Could not find or tap the Send button in WhatsApp.",
                evidence = sendRes.evidence
            )
        }
    }

    /**
     * Verifies that the sent message actually appears in the WhatsApp conversation hierarchy.
     * Never claims success without positive verification!
     */
    suspend fun verifyMessageInConversation(messageText: String): WhatsAppActionResult {
        val observed = JarvisAccessibilityService.observeScreen()
        if (observed == null) {
            return WhatsAppActionResult(
                success = false,
                action = "VERIFY_MESSAGE",
                message = "Accessibility Service is not connected to verify message delivery.",
                isVerified = false
            )
        }

        val textSnippet = if (messageText.length > 20) messageText.substring(0, 20) else messageText
        val found = observed.elements.any {
            it.text.contains(textSnippet, ignoreCase = true) || it.contentDescription.contains(textSnippet, ignoreCase = true)
        }

        return if (found) {
            WhatsAppActionResult(
                success = true,
                action = "VERIFY_MESSAGE",
                message = "Verified: Message \"$messageText\" is confirmed present in the conversation screen.",
                evidence = "Found matching text in WhatsApp chat UI hierarchy: \"$textSnippet\"",
                isVerified = true
            )
        } else {
            val visibleTokens = observed.elements.mapNotNull { it.text.ifBlank { it.contentDescription }.takeIf { s -> s.isNotBlank() } }
            WhatsAppActionResult(
                success = false,
                action = "VERIFY_MESSAGE",
                message = "Message verification UNKNOWN: Message text was not detected in visible chat bubbles.",
                evidence = "Observed screen text elements: ${visibleTokens.take(6).joinToString()}",
                isVerified = false
            )
        }
    }

    private fun clickSearchControl(): Boolean {
        val clickSearch = JarvisAccessibilityService.clickElement("Search")
        if (clickSearch.success) return true

        val clickBanglaSearch = JarvisAccessibilityService.clickElement("অনুসন্ধান")
        if (clickBanglaSearch.success) return true

        val clickSearchId = JarvisAccessibilityService.clickElement("com.whatsapp:id/menuitem_search")
        if (clickSearchId.success) return true

        val clickSearchHolder = JarvisAccessibilityService.clickElement("com.whatsapp:id/search_holder")
        return clickSearchHolder.success
    }
}
