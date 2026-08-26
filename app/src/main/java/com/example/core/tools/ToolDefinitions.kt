package com.example.core.tools

import com.example.data.local.entity.SkillRiskLevel

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: Map<String, String>, // paramName -> description / type
    val requiredPermissions: List<String>,
    val riskLevel: SkillRiskLevel,
    val requiresConfirmation: Boolean
)

object ToolDefinitions {

    val CATALOG: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "open_app",
            description = "Launch an installed Android application by name (e.g. WhatsApp, YouTube, Settings).",
            parametersSchema = mapOf("app_name" to "Name of the app (string)"),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "close_app",
            description = "Navigate back to Home to exit or close the current app interface.",
            parametersSchema = emptyMap(),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "press_back",
            description = "Simulate the Android system Back navigation action.",
            parametersSchema = emptyMap(),
            requiredPermissions = listOf("AccessibilityService (optional)"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "press_home",
            description = "Simulate the Android system Home button navigation action.",
            parametersSchema = emptyMap(),
            requiredPermissions = listOf("AccessibilityService (optional)"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "read_screen",
            description = "Inspect the active foreground screen and extract visible text elements.",
            parametersSchema = emptyMap(),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "find_text",
            description = "Search for a specific piece of text or UI label currently visible on screen.",
            parametersSchema = mapOf("query" to "Text query to locate"),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "tap",
            description = "Click or tap a visible button or text element on the active screen.",
            parametersSchema = mapOf("target_text" to "Text or label of the UI element to tap"),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "long_press",
            description = "Perform a long-press gesture on a visible UI element.",
            parametersSchema = mapOf("target_text" to "Target element label"),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "swipe",
            description = "Perform a swipe gesture across screen coordinates.",
            parametersSchema = mapOf("direction" to "UP | DOWN | LEFT | RIGHT"),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "scroll",
            description = "Scroll the active view forward or backward.",
            parametersSchema = mapOf("direction" to "FORWARD | BACKWARD"),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "type_text",
            description = "Type text into the currently focused input field.",
            parametersSchema = mapOf("text" to "Text string to input"),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "take_screenshot",
            description = "Capture an on-demand screen image for analysis.",
            parametersSchema = emptyMap(),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "read_notifications",
            description = "Read unread active status notifications.",
            parametersSchema = emptyMap(),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_settings",
            description = "Open the Android system Settings application.",
            parametersSchema = mapOf("setting_type" to "general | wifi | bluetooth | accessibility"),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "search_web",
            description = "Launch default browser with a search query.",
            parametersSchema = mapOf("query" to "Search query string"),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "find_contact",
            description = "Search local device contacts by name without inventing phone numbers.",
            parametersSchema = mapOf("name_query" to "Contact display name or query"),
            requiredPermissions = listOf("READ_CONTACTS"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "get_contacts",
            description = "Search local device contacts by name without inventing phone numbers.",
            parametersSchema = mapOf("name_query" to "Contact display name"),
            requiredPermissions = listOf("READ_CONTACTS"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "make_phone_call",
            description = "Initiate an outgoing phone call to a resolved contact or number.",
            parametersSchema = mapOf("contact_name" to "Name or phone number to call"),
            requiredPermissions = listOf("CALL_PHONE", "READ_CONTACTS"),
            riskLevel = SkillRiskLevel.HIGH,
            requiresConfirmation = true
        ),
        ToolDefinition(
            name = "get_call_state",
            description = "Query current telephony call state (IDLE, DIALING, RINGING, ACTIVE, DISCONNECTED).",
            parametersSchema = emptyMap(),
            requiredPermissions = listOf("READ_PHONE_STATE"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "prepare_sms",
            description = "Prepare an SMS draft for user review before confirmation.",
            parametersSchema = mapOf(
                "recipient" to "Contact name or phone number",
                "message" to "Text body of the SMS"
            ),
            requiredPermissions = listOf("READ_CONTACTS"),
            riskLevel = SkillRiskLevel.MEDIUM,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "send_sms",
            description = "Send an SMS text message to a contact.",
            parametersSchema = mapOf(
                "recipient" to "Contact name or phone number",
                "message" to "Text body of the SMS"
            ),
            requiredPermissions = listOf("SEND_SMS", "READ_CONTACTS"),
            riskLevel = SkillRiskLevel.HIGH,
            requiresConfirmation = true
        ),
        ToolDefinition(
            name = "send_whatsapp_message",
            description = "Send a WhatsApp message to a named contact or phone number.",
            parametersSchema = mapOf(
                "contact_name" to "Name of contact or phone number",
                "message" to "Text content of the message"
            ),
            requiredPermissions = listOf("READ_CONTACTS", "AccessibilityService"),
            riskLevel = SkillRiskLevel.HIGH,
            requiresConfirmation = true
        ),
        ToolDefinition(
            name = "verify_message_sent",
            description = "Verify that a sent message is visible in active conversation window.",
            parametersSchema = mapOf("message" to "Expected message text to verify"),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_whatsapp_chat",
            description = "Open WhatsApp directly to the chat conversation with a specific contact.",
            parametersSchema = mapOf("contact_name" to "Contact name or number"),
            requiredPermissions = listOf("READ_CONTACTS"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "get_current_app",
            description = "Identify the active foreground application package name.",
            parametersSchema = emptyMap(),
            requiredPermissions = listOf("AccessibilityService"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "get_device_status",
            description = "Query battery percentage, charging state, network state, and thermals.",
            parametersSchema = emptyMap(),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "toggle_flashlight",
            description = "Turn device rear camera flashlight torch ON or OFF.",
            parametersSchema = mapOf("state" to "true | false"),
            requiredPermissions = listOf("CAMERA"),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "clipboard_copy",
            description = "Copy text to device clipboard.",
            parametersSchema = mapOf("text" to "Text to copy"),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        ),
        ToolDefinition(
            name = "security_audit_check",
            description = "Run defensive security and integrity check on the assistant.",
            parametersSchema = emptyMap(),
            requiredPermissions = emptyList(),
            riskLevel = SkillRiskLevel.LOW,
            requiresConfirmation = false
        )
    )

    /**
     * Generates a concise system prompt description of all available tools for the AI reasoning brain.
     */
    fun generateSystemPromptToolDescriptions(): String {
        return buildString {
            appendLine("=== AVAILABLE ANDROID HANDS (TOOLS) ===")
            appendLine("You are the BRAIN of JARVIS. You do NOT have direct access to the phone or third-party apps.")
            appendLine("To perform any phone action, output a structured JSON tool call:")
            appendLine("```json")
            appendLine("{\"tool\": \"<tool_name>\", \"arguments\": {\"<key>\": \"<value>\"}}")
            appendLine("```")
            appendLine("NEVER say 'I cannot access WhatsApp', 'I cannot make calls', or pretend you cannot control the phone.")
            appendLine("Instead, ALWAYS choose and emit the appropriate tool call so the Android controller can execute it.")
            appendLine("\nTool Catalog:")
            CATALOG.forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description} (Risk: ${tool.riskLevel}, Needs Confirmation: ${tool.requiresConfirmation})")
                if (tool.parametersSchema.isNotEmpty()) {
                    appendLine("  Arguments: ${tool.parametersSchema.entries.joinToString(", ") { "${it.key}: ${it.value}" }}")
                }
            }
        }
    }
}
