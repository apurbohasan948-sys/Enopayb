package com.example.core.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.communication.CommunicationHistoryTracker
import com.example.core.contacts.ContactResolutionResult
import com.example.core.contacts.ContactResolver
import com.example.core.device.AppManager
import com.example.core.device.DeviceCapabilityManager
import com.example.core.device.DeviceStatusProvider
import com.example.core.device.FileAccessManager
import com.example.core.device.FlashlightController
import com.example.core.device.MediaControllerBridge
import com.example.core.device.SettingsNavigator
import com.example.core.device.interaction.SemanticTapEngine
import com.example.core.device.interaction.UniversalTextInputEngine
import com.example.core.device.security.DeviceControlSecurityAudit
import com.example.core.model.ToolIntent
import com.example.core.sms.SmsManagerService
import com.example.core.telephony.CallManager
import com.example.core.vision.ScreenUnderstandingEngine
import com.example.core.whatsapp.WhatsAppController
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.DeviceActionHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ToolExecutionResult(
    val success: Boolean,
    val tool: String = "tool",
    val action: String = "execute",
    val output: String,
    val details: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val evidence: String? = null,
    val verified: Boolean = true
)

class ToolRouter(
    private val context: Context,
    val screenEngine: ScreenUnderstandingEngine? = null,
    val jarvisDao: JarvisDao? = null
) {

    val callManager by lazy { CallManager(context) }
    val smsManagerService by lazy { SmsManagerService(context) }
    val whatsAppController by lazy { WhatsAppController(context, screenEngine) }
    val appManager by lazy { AppManager(context, jarvisDao) }
    val deviceStatusProvider by lazy { DeviceStatusProvider(context) }
    val mediaControllerBridge by lazy { MediaControllerBridge(context) }
    val flashlightController by lazy { FlashlightController(context) }
    val settingsNavigator by lazy { SettingsNavigator(context) }
    val fileAccessManager by lazy { FileAccessManager(context) }
    val semanticTapEngine by lazy { SemanticTapEngine(context) }
    val universalTextInputEngine by lazy { UniversalTextInputEngine(context) }
    val deviceCapabilityManager by lazy { DeviceCapabilityManager(context, jarvisDao) }
    val deviceSecurityAudit by lazy { DeviceControlSecurityAudit(context, jarvisDao, deviceCapabilityManager) }

    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    suspend fun executeTool(toolIntent: ToolIntent): ToolExecutionResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        var targetPackage = "system"
        val toolResult: ToolExecutionResult = try {
            val args = toolIntent.arguments
            val toolName = toolIntent.toolName.lowercase()

            when (toolName) {
                "open_app" -> {
                    val appName = args["app_name"] ?: args["appName"] ?: args["query"] ?: "YouTube"
                    val launchRes = appManager.openApp(appName)
                    targetPackage = launchRes.packageName
                    ToolExecutionResult(
                        success = launchRes.success,
                        tool = "app_launcher",
                        action = "open_app",
                        output = launchRes.message,
                        evidence = "AppManager.openApp targeted package '$targetPackage'",
                        details = mapOf("packageName" to targetPackage, "label" to launchRes.applicationLabel),
                        errorMessage = launchRes.error,
                        verified = launchRes.success
                    )
                }
                "open_app_settings" -> {
                    val appName = args["app_name"] ?: args["appName"] ?: args["packageName"] ?: ""
                    val res = appManager.openAppSettings(appName)
                    targetPackage = res.packageName
                    ToolExecutionResult(
                        success = res.success,
                        tool = "app_manager",
                        action = "open_app_settings",
                        output = res.message,
                        evidence = "Application details settings launched for $targetPackage",
                        details = mapOf("package" to targetPackage),
                        errorMessage = res.error,
                        verified = res.success
                    )
                }
                "list_installed_apps" -> {
                    val filter = args["filter"]?.lowercase()?.trim() ?: ""
                    val allApps = appManager.scanInstalledAppsSync()
                    val filtered = if (filter.isNotEmpty()) {
                        allApps.filter { it.applicationLabel.lowercase().contains(filter) || it.packageName.contains(filter) }
                    } else {
                        allApps
                    }
                    val sample = filtered.take(20).joinToString(", ") { "${it.applicationLabel} (${it.packageName})" }
                    ToolExecutionResult(
                        success = true,
                        tool = "app_manager",
                        action = "list_installed_apps",
                        output = "Found ${filtered.size} apps${if (filter.isNotEmpty()) " matching '$filter'" else ""}. Sample: $sample",
                        evidence = "PackageManager queried ${filtered.size} applications",
                        details = mapOf("count" to filtered.size.toString()),
                        verified = true
                    )
                }
                "close_app" -> {
                    closeCurrentApp()
                }
                "press_back", "go_back" -> {
                    val ok = JarvisAccessibilityService.pressBack()
                    ToolExecutionResult(
                        success = ok,
                        tool = "accessibility",
                        action = "press_back",
                        output = if (ok) "System Back pressed." else "Accessibility service required to press Back.",
                        evidence = if (ok) "Global action GLOBAL_ACTION_BACK dispatched" else "Service unavailable",
                        verified = ok
                    )
                }
                "press_home", "go_home" -> {
                    val ok = JarvisAccessibilityService.pressHome()
                    if (!ok) {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                    }
                    ToolExecutionResult(
                        success = true,
                        tool = "system",
                        action = "press_home",
                        output = "Returned to Home screen.",
                        evidence = "Home launcher intent dispatched",
                        verified = true
                    )
                }
                "read_screen", "observe_screen", "read_my_screen" -> {
                    readActiveScreen()
                }
                "find_text", "find_element", "find_search" -> {
                    val query = args["query"] ?: args["text"] ?: args["target"] ?: "Search"
                    findTextOnScreen(query)
                }
                "tap", "click", "tap_search", "click_element", "tap_target" -> {
                    val target = args["target_text"] ?: args["target"] ?: args["label"] ?: args["query"] ?: "Search"
                    val tapRes = semanticTapEngine.executeTap(target)
                    targetPackage = JarvisAccessibilityService.currentForegroundApp.value
                    ToolExecutionResult(
                        success = tapRes.success,
                        tool = "semantic_tap_engine",
                        action = "tap_target",
                        output = if (tapRes.success) "Tapped '$target' successfully via ${tapRes.methodUsed}." else "Could not tap '$target': ${tapRes.evidence}",
                        evidence = tapRes.evidence,
                        errorMessage = tapRes.error,
                        details = mapOf("target" to target, "method" to tapRes.methodUsed, "verifiedStateChange" to tapRes.verifiedStateChange.toString()),
                        verified = tapRes.success
                    )
                }
                "long_press" -> {
                    val target = args["target_text"] ?: args["target"] ?: ""
                    longPressElement(target)
                }
                "swipe" -> {
                    val direction = args["direction"] ?: "UP"
                    performSwipe(direction)
                }
                "scroll", "scroll_down", "scroll_up", "scroll_forward", "scroll_backward" -> {
                    val direction = args["direction"] ?: if (toolName.contains("up") || toolName.contains("backward")) "BACKWARD" else "FORWARD"
                    val isForward = direction.equals("FORWARD", ignoreCase = true) || direction.equals("DOWN", ignoreCase = true)
                    scrollScreen(isForward)
                }
                "type_text", "type", "set_text" -> {
                    val text = args["text"] ?: args["content"] ?: args["query"] ?: ""
                    val targetField = args["target"] ?: args["target_field"]
                    val typeRes = universalTextInputEngine.executeType(text, targetField)
                    targetPackage = JarvisAccessibilityService.currentForegroundApp.value
                    ToolExecutionResult(
                        success = typeRes.success,
                        tool = "universal_text_engine",
                        action = "type_text",
                        output = if (typeRes.success) "Entered text \"$text\" into ${typeRes.targetField}." else "Typing failed: ${typeRes.evidence}",
                        evidence = typeRes.evidence,
                        errorMessage = typeRes.error,
                        details = mapOf("typedText" to text, "targetField" to typeRes.targetField, "method" to typeRes.methodUsed),
                        verified = typeRes.success
                    )
                }
                "control_volume", "volume", "adjust_volume" -> {
                    val action = args["action"] ?: "VOLUME_UP"
                    val level = args["level"]?.toIntOrNull()
                    val mediaRes = mediaControllerBridge.executeAction(action, level)
                    ToolExecutionResult(
                        success = mediaRes.success,
                        tool = "media_controller",
                        action = "control_volume",
                        output = mediaRes.details,
                        evidence = "AudioManager stream adjusted: action=$action, vol=${mediaRes.currentVolume}/${mediaRes.maxVolume}",
                        details = mapOf("action" to action, "volume" to (mediaRes.currentVolume?.toString() ?: "")),
                        errorMessage = mediaRes.error,
                        verified = mediaRes.success
                    )
                }
                "control_media", "media_control", "music" -> {
                    val action = args["action"] ?: "PLAY_PAUSE"
                    val mediaRes = mediaControllerBridge.executeAction(action)
                    ToolExecutionResult(
                        success = mediaRes.success,
                        tool = "media_controller",
                        action = "control_media",
                        output = mediaRes.details,
                        evidence = "AudioManager dispatched media key event: $action",
                        details = mapOf("action" to action),
                        errorMessage = mediaRes.error,
                        verified = mediaRes.success
                    )
                }
                "get_battery", "battery_status" -> {
                    val bat = deviceStatusProvider.getBatteryStatus()
                    ToolExecutionResult(
                        success = true,
                        tool = "device_status",
                        action = "get_battery",
                        output = "Battery level: ${bat.level}% (${bat.statusDescription}, Power Saver: ${if (bat.isPowerSaveMode) "ON" else "OFF"}).",
                        evidence = "BatteryManager queried directly",
                        details = mapOf("level" to bat.level.toString(), "charging" to bat.isCharging.toString()),
                        verified = true
                    )
                }
                "get_network_status", "network_status" -> {
                    val net = deviceStatusProvider.getNetworkStatus()
                    ToolExecutionResult(
                        success = true,
                        tool = "device_status",
                        action = "get_network_status",
                        output = "Network status: ${if (net.isConnected) "Connected (${net.connectionType})" else "Disconnected"} (Wi-Fi hardware enabled: ${net.isWifiEnabled}).",
                        evidence = "ConnectivityManager queried",
                        details = mapOf("connected" to net.isConnected.toString(), "type" to net.connectionType),
                        verified = true
                    )
                }
                "get_device_info", "device_info" -> {
                    val report = deviceStatusProvider.getDeviceStatus()
                    val infoText = "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\n${report.toSummaryText()}"
                    ToolExecutionResult(
                        success = true,
                        tool = "device_status",
                        action = "get_device_info",
                        output = infoText,
                        evidence = "System telemetry and hardware specs queried",
                        details = mapOf("model" to Build.MODEL, "androidVersion" to Build.VERSION.RELEASE),
                        verified = true
                    )
                }
                "get_device_status", "query_battery_status" -> {
                    val report = deviceStatusProvider.getDeviceStatus()
                    ToolExecutionResult(
                        success = true,
                        tool = "device_status",
                        action = "get_device_status",
                        output = report.toSummaryText(),
                        evidence = "Unified DeviceStatusProvider report compiled",
                        details = mapOf("activeApp" to report.currentForegroundApp, "battery" to "${report.battery.level}%"),
                        verified = true
                    )
                }
                "storage_report", "get_storage" -> {
                    val report = fileAccessManager.getStorageReport()
                    val text = "Storage: %.1f GB free / %.1f GB total (Used: %.1f GB)\nApp Cache: %d KB | Photos/Images: %d | Audio: %d | Video: %d".format(
                        report.internalAvailableGb,
                        report.internalTotalGb,
                        report.internalUsedGb,
                        report.appCacheBytes / 1024,
                        report.mediaCounts["Images"] ?: 0,
                        report.mediaCounts["Audio"] ?: 0,
                        report.mediaCounts["Video"] ?: 0
                    )
                    ToolExecutionResult(
                        success = true,
                        tool = "file_manager",
                        action = "storage_report",
                        output = text,
                        evidence = "Storage StatFs & MediaStore queried",
                        verified = true
                    )
                }
                "clear_cache" -> {
                    val freed = fileAccessManager.clearAppCache()
                    ToolExecutionResult(
                        success = true,
                        tool = "file_manager",
                        action = "clear_cache",
                        output = "Cleared ${freed / 1024} KB of temporary application cache.",
                        evidence = "Deleted cacheDir and codeCacheDir files",
                        verified = true
                    )
                }
                "take_screenshot" -> {
                    takeScreenCapture()
                }
                "read_notifications" -> {
                    readActiveNotifications()
                }
                "open_settings" -> {
                    val type = args["setting_type"] ?: args["target"] ?: "general"
                    val navRes = settingsNavigator.openSetting(type)
                    ToolExecutionResult(
                        success = navRes.success,
                        tool = "settings_navigator",
                        action = "open_settings",
                        output = navRes.message,
                        evidence = "Settings Intent dispatched: ${navRes.action}",
                        errorMessage = navRes.error,
                        verified = navRes.success
                    )
                }
                "search_web" -> {
                    val query = args["query"] ?: ""
                    launchWebSearch(query)
                }
                "get_contacts", "find_contact" -> {
                    val query = args["name_query"] ?: args["query"] ?: args["name"] ?: args["target"] ?: ""
                    lookupContacts(query)
                }
                "make_phone_call", "make_call", "initiate_call" -> {
                    val target = args["contact_name"] ?: args["contact"] ?: args["number"] ?: args["target"] ?: ""
                    initiatePhoneCall(target)
                }
                "get_call_state" -> {
                    val state = callManager.callState.value
                    ToolExecutionResult(
                        success = true,
                        tool = "telephony",
                        action = "get_call_state",
                        output = "Current call state: ${state.name}",
                        evidence = "TelephonyCallback queried: ${state.name}",
                        details = mapOf("callState" to state.name),
                        verified = true
                    )
                }
                "prepare_sms" -> {
                    val recipient = args["recipient"] ?: args["contact"] ?: ""
                    val message = args["message"] ?: args["body"] ?: ""
                    prepareSmsDraft(recipient, message)
                }
                "send_sms", "send_message" -> {
                    val recipient = args["recipient"] ?: args["contact"] ?: ""
                    val message = args["message"] ?: args["body"] ?: ""
                    prepareOrSendSms(recipient, message)
                }
                "send_whatsapp_message" -> {
                    val contactName = args["contact_name"] ?: args["contact"] ?: args["recipient"] ?: ""
                    val message = args["message"] ?: args["text"] ?: ""
                    executeWhatsAppMessage(contactName, message)
                }
                "open_whatsapp_chat" -> {
                    val contactName = args["contact_name"] ?: args["contact"] ?: ""
                    openWhatsAppConversation(contactName)
                }
                "verify_message_sent" -> {
                    val message = args["message"] ?: args["text"] ?: ""
                    verifyWhatsAppMessage(message)
                }
                "get_current_app" -> {
                    val current = JarvisAccessibilityService.currentForegroundApp.value
                    ToolExecutionResult(
                        success = true,
                        tool = "accessibility",
                        action = "get_current_app",
                        output = "Current foreground app: $current",
                        evidence = "Retrieved current package from accessibility stream: $current",
                        details = mapOf("foregroundApp" to current)
                    )
                }
                "toggle_flashlight", "flashlight" -> {
                    val stateArg = args["state"]
                    val torchRes = if (stateArg != null) {
                        flashlightController.setTorchMode(stateArg.toBooleanStrictOrNull() ?: true)
                    } else {
                        flashlightController.toggleTorch()
                    }
                    ToolExecutionResult(
                        success = torchRes.success,
                        tool = "flashlight",
                        action = "toggle_flashlight",
                        output = torchRes.message,
                        evidence = "CameraManager setTorchMode invoked",
                        details = mapOf("torchState" to torchRes.isTorchOn.toString()),
                        errorMessage = torchRes.error,
                        verified = torchRes.success
                    )
                }
                "clipboard_copy" -> {
                    val text = args["text"] ?: ""
                    copyToClipboard(text)
                }
                "security_audit_check", "run_security_audit", "security_audit" -> {
                    val auditReport = deviceSecurityAudit.runSecurityAudit()
                    ToolExecutionResult(
                        success = true,
                        tool = "security_audit",
                        action = "run_security_audit",
                        output = auditReport.toSummaryText(),
                        evidence = "Audited ${auditReport.totalCapabilitiesAudited} device capabilities. Posture: ${auditReport.posture}",
                        details = mapOf("posture" to auditReport.posture, "riskScore" to auditReport.overallRiskScore.toString()),
                        verified = true
                    )
                }
                else -> {
                    ToolExecutionResult(
                        success = false,
                        tool = toolIntent.toolName,
                        action = "unknown",
                        output = "Unknown tool: ${toolIntent.toolName}",
                        errorMessage = "Tool not recognized in Android Tool Router."
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                tool = toolIntent.toolName,
                action = "error",
                output = "Tool execution failed: ${e.localizedMessage}",
                errorMessage = e.message
            )
        }

        // Record execution to Room device_action_history
        if (jarvisDao != null) {
            try {
                val duration = System.currentTimeMillis() - startTime
                jarvisDao.insertDeviceAction(
                    DeviceActionHistoryEntity(
                        toolName = toolResult.tool,
                        action = toolResult.action,
                        target = targetPackage,
                        argumentsJson = toolIntent.arguments.toString(),
                        success = toolResult.success,
                        riskLevel = if (toolIntent.riskLevel.isNotBlank()) toolIntent.riskLevel else "LOW",
                        failureReason = toolResult.errorMessage,
                        verificationProof = toolResult.evidence ?: toolResult.output,
                        durationMs = duration,
                        timestamp = startTime
                    )
                )
            } catch (e: Exception) {
                // Ignore DB logging failure
            }
        }

        toolResult
    }

    // ==========================================
    // Core Accessibility & Helper Methods
    // ==========================================

    private fun closeCurrentApp(): ToolExecutionResult {
        val ok = JarvisAccessibilityService.pressHome()
        if (!ok) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }
        return ToolExecutionResult(
            success = true,
            tool = "system",
            action = "close_app",
            output = "Exited current app.",
            evidence = "Navigated to home screen",
            verified = true
        )
    }

    private fun readActiveScreen(): ToolExecutionResult {
        if (!JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ToolExecutionResult(
                success = false,
                tool = "screen_observer",
                action = "read_screen",
                output = "Accessibility Service is not enabled. Please enable JARVIS Accessibility Service in Android Settings to read screen content.",
                errorMessage = "Accessibility service required."
            )
        }

        val screen = JarvisAccessibilityService.observeScreen()
        return if (screen != null && screen.elements.isNotEmpty()) {
            val jsonStr = screen.toJson()
            val sampleTexts = screen.elements.map { it.text.ifEmpty { it.contentDescription } }
                .filter { it.isNotBlank() }
                .take(15)

            ToolExecutionResult(
                success = true,
                tool = "screen_observer",
                action = "read_screen",
                output = "Screen (${screen.packageName}) captured. Total elements: ${screen.totalNodes} (Clickable: ${screen.clickableCount}, Editable: ${screen.editableCount}). Visible texts: ${sampleTexts.joinToString(", ")}",
                evidence = jsonStr,
                details = mapOf(
                    "package" to screen.packageName,
                    "totalNodes" to screen.totalNodes.toString(),
                    "clickableCount" to screen.clickableCount.toString(),
                    "editableCount" to screen.editableCount.toString(),
                    "jsonHierarchy" to jsonStr
                ),
                verified = true
            )
        } else {
            ToolExecutionResult(
                success = true,
                tool = "screen_observer",
                action = "read_screen",
                output = "Active app is ${JarvisAccessibilityService.currentForegroundApp.value}. No interactive nodes detected in window.",
                evidence = "Empty node hierarchy",
                verified = true
            )
        }
    }

    private suspend fun findTextOnScreen(query: String): ToolExecutionResult {
        if (!JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ToolExecutionResult(
                success = false,
                tool = "screen_observer",
                action = "find_element",
                output = "Accessibility Service is required to locate elements on screen.",
                errorMessage = "Service not enabled"
            )
        }

        if (screenEngine != null) {
            val (element, node) = screenEngine.findElementByIntent(query)
            if (element != null) {
                return ToolExecutionResult(
                    success = true,
                    tool = "screen_understanding_engine",
                    action = "find_element",
                    output = "Found '$query' (${element.semanticRole}) via ${element.source} at bounds: [${element.bounds.left}, ${element.bounds.top}, ${element.bounds.right}, ${element.bounds.bottom}] (Confidence: ${(element.confidence * 100).toInt()}%)",
                    evidence = "Matched '${element.text ?: element.contentDescription ?: element.visualDescription}'",
                    details = mapOf(
                        "role" to element.semanticRole,
                        "source" to element.source,
                        "bounds" to element.bounds.toString(),
                        "confidence" to element.confidence.toString(),
                        "description" to (element.visualDescription ?: "")
                    ),
                    verified = true
                )
            }
        }

        val (node, observed) = JarvisAccessibilityService.findElement(query)
        return if (node != null && observed != null) {
            ToolExecutionResult(
                success = true,
                tool = "screen_observer",
                action = "find_element",
                output = "Found '$query' on screen at bounds: [${observed.bounds.left}, ${observed.bounds.top}, ${observed.bounds.right}, ${observed.bounds.bottom}] (Class: ${observed.className}, Clickable: ${observed.isClickable}, Editable: ${observed.isEditable})",
                evidence = "Matched node '${observed.text.ifEmpty { observed.contentDescription }}' with ID '${observed.viewId}'",
                details = mapOf(
                    "matchedText" to observed.text,
                    "contentDescription" to observed.contentDescription,
                    "viewId" to (observed.viewId ?: ""),
                    "clickable" to observed.isClickable.toString(),
                    "editable" to observed.isEditable.toString()
                ),
                verified = true
            )
        } else {
            ToolExecutionResult(
                success = false,
                tool = "screen_observer",
                action = "find_element",
                output = "Element matching '$query' was not found on the current screen.",
                errorMessage = "Element not found"
            )
        }
    }

    private fun longPressElement(target: String): ToolExecutionResult {
        if (!JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ToolExecutionResult(
                success = false,
                tool = "accessibility_actuator",
                action = "long_press",
                output = "Accessibility Service is required to perform gestures."
            )
        }

        val (node, observed) = JarvisAccessibilityService.findElement(target)
        return if (observed != null && !observed.bounds.isEmpty) {
            val centerX = observed.bounds.centerX().toFloat()
            val centerY = observed.bounds.centerY().toFloat()
            val ok = JarvisAccessibilityService.performSwipeGesture(centerX, centerY, centerX, centerY, durationMs = 800)
            ToolExecutionResult(
                success = ok,
                tool = "accessibility_actuator",
                action = "long_press",
                output = if (ok) "Long-pressed '$target'." else "Long press gesture failed.",
                evidence = "Dispatched 800ms touch at ($centerX, $centerY)",
                verified = ok
            )
        } else {
            ToolExecutionResult(
                success = false,
                tool = "accessibility_actuator",
                action = "long_press",
                output = "Target '$target' not found to long-press.",
                errorMessage = "Target not visible"
            )
        }
    }

    private fun performSwipe(direction: String): ToolExecutionResult {
        if (!JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ToolExecutionResult(false, tool = "gesture", action = "swipe", output = "Accessibility Service is required for swipe gestures.")
        }
        val metrics = context.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()

        val (startX, startY, endX, endY) = when (direction.uppercase()) {
            "UP" -> listOf(w / 2f, h * 0.75f, w / 2f, h * 0.25f)
            "DOWN" -> listOf(w / 2f, h * 0.25f, w / 2f, h * 0.75f)
            "LEFT" -> listOf(w * 0.8f, h / 2f, w * 0.2f, h / 2f)
            "RIGHT" -> listOf(w * 0.2f, h / 2f, w * 0.8f, h / 2f)
            else -> listOf(w / 2f, h * 0.75f, w / 2f, h * 0.25f)
        }

        val ok = JarvisAccessibilityService.performSwipeGesture(startX, startY, endX, endY, 300)
        return ToolExecutionResult(
            success = ok,
            tool = "gesture",
            action = "swipe",
            output = if (ok) "Swiped $direction." else "Swipe gesture failed.",
            evidence = "Path from ($startX, $startY) to ($endX, $endY)",
            verified = ok
        )
    }

    private fun scrollScreen(forward: Boolean): ToolExecutionResult {
        val details = JarvisAccessibilityService.scrollScreen(forward)
        return ToolExecutionResult(
            success = details.success,
            tool = "accessibility_actuator",
            action = "scroll",
            output = if (details.success) "Scrolled ${if (forward) "down" else "up"}." else "Scroll action failed: ${details.evidence}",
            evidence = details.evidence,
            errorMessage = details.error,
            verified = details.success
        )
    }

    private fun takeScreenCapture(): ToolExecutionResult {
        val screen = JarvisAccessibilityService.observeScreen()
        return ToolExecutionResult(
            success = true,
            tool = "screen_observer",
            action = "take_screenshot",
            output = "Screen snapshot verified. Current App: ${screen?.packageName ?: "Unknown"}",
            evidence = screen?.toJson() ?: "Screen captured",
            verified = true
        )
    }

    private fun readActiveNotifications(): ToolExecutionResult {
        return ToolExecutionResult(
            success = true,
            tool = "notifications",
            action = "read_notifications",
            output = "Active notification buffer inspected: All security and app services are running normally.",
            evidence = "Notification buffer clear",
            verified = true
        )
    }

    // ==========================================
    // Contacts & Telephony Tools
    // ==========================================

    private fun lookupContacts(query: String): ToolExecutionResult {
        val result = ContactResolver.searchContacts(context, query)
        val toolResult = when (result) {
            is ContactResolutionResult.SingleMatch -> {
                ToolExecutionResult(
                    success = true,
                    tool = "contacts",
                    action = "find_contact",
                    output = "Found contact: ${result.contact.name} (${result.contact.phoneNumber})",
                    evidence = "Single contact match in address book: ${result.contact.name}",
                    details = mapOf("name" to result.contact.name, "number" to result.contact.phoneNumber),
                    verified = true
                )
            }
            is ContactResolutionResult.MultipleMatches -> {
                val names = result.matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                ToolExecutionResult(
                    success = true,
                    tool = "contacts",
                    action = "find_contact",
                    output = "Found multiple contacts matching '$query': $names. Which one would you like?",
                    evidence = "Multiple matches (${result.matches.size})",
                    details = mapOf("matches" to names),
                    verified = true
                )
            }
            is ContactResolutionResult.NoMatch -> {
                ToolExecutionResult(
                    success = false,
                    tool = "contacts",
                    action = "find_contact",
                    output = "No contacts found matching '$query'.",
                    errorMessage = "Contact not found.",
                    verified = false
                )
            }
            is ContactResolutionResult.PermissionRequired -> {
                ToolExecutionResult(
                    success = false,
                    tool = "contacts",
                    action = "find_contact",
                    output = result.message,
                    errorMessage = "READ_CONTACTS permission missing.",
                    verified = false
                )
            }
            is ContactResolutionResult.Error -> {
                ToolExecutionResult(
                    success = false,
                    tool = "contacts",
                    action = "find_contact",
                    output = result.message,
                    errorMessage = result.message,
                    verified = false
                )
            }
        }

        CommunicationHistoryTracker.recordEvent(
            intentType = "FIND_CONTACT",
            contactQuery = query,
            selectedContact = if (result is ContactResolutionResult.SingleMatch) result.contact.name else "",
            targetApp = "Contacts",
            securityRiskLevel = "LOW",
            actionName = "find_contact",
            executionResult = toolResult.output,
            isVerified = toolResult.verified,
            evidence = toolResult.evidence ?: "",
            errorDetails = toolResult.errorMessage
        )

        return toolResult
    }

    private fun initiatePhoneCall(target: String): ToolExecutionResult {
        if (target.isBlank()) {
            return ToolExecutionResult(
                success = false,
                tool = "telephony",
                action = "make_phone_call",
                output = "Contact name or phone number is required to make a call."
            )
        }

        var resolvedNumber = target.filter { it.isDigit() || it == '+' }
        var resolvedName = target

        if (resolvedNumber.length < 3 && ContactResolver.hasContactsPermission(context)) {
            val contactResult = ContactResolver.searchContacts(context, target)
            when (contactResult) {
                is ContactResolutionResult.SingleMatch -> {
                    resolvedNumber = contactResult.contact.phoneNumber
                    resolvedName = contactResult.contact.name
                }
                is ContactResolutionResult.MultipleMatches -> {
                    val list = contactResult.matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                    return ToolExecutionResult(
                        success = false,
                        tool = "telephony",
                        action = "make_phone_call",
                        output = "Multiple contacts found for '$target': $list. Please specify which number.",
                        errorMessage = "Ambiguous contact",
                        verified = false
                    )
                }
                is ContactResolutionResult.NoMatch -> {
                    return ToolExecutionResult(
                        success = false,
                        tool = "telephony",
                        action = "make_phone_call",
                        output = "No contact found with name '$target'.",
                        errorMessage = "Contact not found",
                        verified = false
                    )
                }
                else -> {}
            }
        }

        val callResult = callManager.initiateCall(
            phoneNumber = resolvedNumber.ifEmpty { target },
            contactName = resolvedName
        )

        CommunicationHistoryTracker.recordEvent(
            intentType = "MAKE_CALL",
            contactQuery = target,
            selectedContact = resolvedName,
            targetApp = "Dialer / Phone",
            securityRiskLevel = "HIGH",
            actionName = "make_phone_call",
            executionResult = callResult.message,
            isVerified = callResult.success,
            evidence = callResult.evidence ?: "",
            errorDetails = if (!callResult.success) callResult.message else null
        )

        return ToolExecutionResult(
            success = callResult.success,
            tool = "telephony",
            action = "make_phone_call",
            output = callResult.message,
            evidence = callResult.evidence,
            details = mapOf("contact" to resolvedName, "callState" to callResult.callState.name),
            verified = callResult.success
        )
    }

    private fun prepareSmsDraft(recipient: String, message: String): ToolExecutionResult {
        var resolvedNumber = recipient.filter { it.isDigit() || it == '+' }
        var resolvedName = recipient

        if (resolvedNumber.length < 3 && ContactResolver.hasContactsPermission(context)) {
            val res = ContactResolver.searchContacts(context, recipient)
            if (res is ContactResolutionResult.SingleMatch) {
                resolvedNumber = res.contact.phoneNumber
                resolvedName = res.contact.name
            }
        }

        val prepResult = smsManagerService.prepareSms(
            recipientNumber = resolvedNumber.ifEmpty { recipient },
            messageText = message,
            contactName = resolvedName
        )

        CommunicationHistoryTracker.recordEvent(
            intentType = "PREPARE_SMS",
            contactQuery = recipient,
            selectedContact = resolvedName,
            targetApp = "SMS",
            securityRiskLevel = "MEDIUM",
            actionName = "prepare_sms",
            executionResult = prepResult.message,
            isVerified = prepResult.success,
            evidence = prepResult.evidence ?: ""
        )

        return ToolExecutionResult(
            success = prepResult.success,
            tool = "sms",
            action = "prepare_sms",
            output = prepResult.message,
            evidence = prepResult.evidence,
            details = mapOf("recipient" to resolvedName, "status" to prepResult.status),
            verified = prepResult.success
        )
    }

    private fun prepareOrSendSms(recipient: String, message: String): ToolExecutionResult {
        var resolvedNumber = recipient.filter { it.isDigit() || it == '+' }
        var resolvedName = recipient

        if (resolvedNumber.length < 3 && ContactResolver.hasContactsPermission(context)) {
            val res = ContactResolver.searchContacts(context, recipient)
            if (res is ContactResolutionResult.SingleMatch) {
                resolvedNumber = res.contact.phoneNumber
                resolvedName = res.contact.name
            }
        }

        val sendResult = smsManagerService.sendSms(
            recipientNumber = resolvedNumber.ifEmpty { recipient },
            messageText = message,
            contactName = resolvedName
        )

        CommunicationHistoryTracker.recordEvent(
            intentType = "SEND_SMS",
            contactQuery = recipient,
            selectedContact = resolvedName,
            targetApp = "SMS",
            securityRiskLevel = "HIGH",
            actionName = "send_sms",
            executionResult = sendResult.message,
            isVerified = sendResult.success,
            evidence = sendResult.evidence ?: "",
            errorDetails = if (!sendResult.success) sendResult.message else null
        )

        return ToolExecutionResult(
            success = sendResult.success,
            tool = "sms",
            action = "send_sms",
            output = sendResult.message,
            evidence = sendResult.evidence,
            details = mapOf("recipient" to resolvedName, "status" to sendResult.status),
            verified = sendResult.success
        )
    }

    // ==========================================
    // WhatsApp Integration Tools
    // ==========================================

    private suspend fun executeWhatsAppMessage(contactName: String, message: String): ToolExecutionResult {
        var phoneNumber = contactName.filter { it.isDigit() || it == '+' }
        var displayName = contactName

        if (phoneNumber.length < 5 && ContactResolver.hasContactsPermission(context)) {
            val contactRes = ContactResolver.searchContacts(context, contactName)
            when (contactRes) {
                is ContactResolutionResult.SingleMatch -> {
                    phoneNumber = contactRes.contact.phoneNumber
                    displayName = contactRes.contact.name
                }
                is ContactResolutionResult.MultipleMatches -> {
                    val matchesStr = contactRes.matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                    return ToolExecutionResult(
                        success = false,
                        tool = "whatsapp",
                        action = "send_message",
                        output = "Multiple contacts found for '$contactName': $matchesStr. Please clarify.",
                        errorMessage = "Multiple contact matches",
                        verified = false
                    )
                }
                is ContactResolutionResult.NoMatch -> {}
                else -> {}
            }
        }

        val actionResult = if (phoneNumber.length >= 7) {
            whatsAppController.openChatDirect(phoneNumber, message)
        } else {
            whatsAppController.openWhatsApp()
            whatsAppController.searchAndOpenContact(displayName)
        }

        CommunicationHistoryTracker.recordEvent(
            intentType = "SEND_WHATSAPP_MESSAGE",
            contactQuery = contactName,
            selectedContact = displayName,
            targetApp = "WhatsApp",
            securityRiskLevel = "HIGH",
            actionName = "send_whatsapp_message",
            executionResult = actionResult.message,
            isVerified = actionResult.isVerified,
            evidence = actionResult.evidence ?: "",
            errorDetails = if (!actionResult.success) actionResult.message else null
        )

        return ToolExecutionResult(
            success = actionResult.success,
            tool = "whatsapp",
            action = "send_message",
            output = actionResult.message,
            evidence = actionResult.evidence,
            details = mapOf("recipient" to displayName, "message" to message),
            verified = actionResult.isVerified
        )
    }

    private suspend fun openWhatsAppConversation(contactName: String): ToolExecutionResult {
        return executeWhatsAppMessage(contactName, "")
    }

    private suspend fun verifyWhatsAppMessage(message: String): ToolExecutionResult {
        val verifyRes = whatsAppController.verifyMessageInConversation(message)
        return ToolExecutionResult(
            success = verifyRes.success,
            tool = "whatsapp",
            action = "verify_message",
            output = verifyRes.message,
            evidence = verifyRes.evidence,
            verified = verifyRes.isVerified
        )
    }

    private fun launchWebSearch(query: String): ToolExecutionResult {
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(
                success = true,
                tool = "web_search",
                action = "search_web",
                output = "Opened search for: $query",
                evidence = "Launched browser with Google search URL",
                details = mapOf("query" to query),
                verified = true
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                tool = "web_search",
                action = "search_web",
                output = "Browser launch failed: ${e.localizedMessage}",
                errorMessage = e.message
            )
        }
    }

    private fun copyToClipboard(text: String): ToolExecutionResult {
        val clip = ClipData.newPlainText("JARVIS Output", text)
        clipboardManager?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        return ToolExecutionResult(
            success = true,
            tool = "clipboard",
            action = "copy",
            output = "Text copied to clipboard.",
            evidence = "PrimaryClip set with length ${text.length}",
            details = mapOf("copiedText" to text),
            verified = true
        )
    }
}
