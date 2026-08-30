package com.example.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.agent.JarvisAgentCore
import com.example.core.agent.UniversalActionExecutor
import com.example.core.agent.UniversalTask
import com.example.core.agent.UniversalTaskPlanner
import com.example.core.autonomy.MasterStopManager
import com.example.core.communication.CommunicationIntent
import com.example.core.communication.CommunicationIntentParser
import com.example.core.contacts.ContactResolutionResult
import com.example.core.contacts.ContactResolver
import com.example.core.model.ToolIntent
import com.example.core.tools.ToolRouter
import com.example.core.voice.context.ConversationManager
import com.example.core.voice.service.JarvisOverlayService
import com.example.core.voice.service.JarvisVoiceForegroundService
import com.example.core.voice.stt.SpeechRecognitionManager
import com.example.core.voice.tts.ResponseMode
import com.example.core.voice.tts.TTSManager
import com.example.core.voice.wake.WakeSensitivity
import com.example.core.voice.wake.WakeWordEngine
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 12 VoiceAssistantManager.
 * Central Orchestrator for Voice, Wake-Word, Speech Recognition,
 * Conversational Memory, Audio Focus, Security Confirmation, and Universal Agent Task Execution.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val agentCoreProvider: () -> JarvisAgentCore?,
    private val toolRouterProvider: () -> ToolRouter?,
    private val repositoryProvider: () -> JarvisRepository?
) {
    val wakeWordEngine = WakeWordEngine(context, coroutineScope)
    val speechRecognitionManager = SpeechRecognitionManager(context)
    val ttsManager = TTSManager(context)
    val conversationManager = ConversationManager()
    val stateController = VoiceStateController()

    val voiceState: StateFlow<VoiceState> = stateController.state

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _audioWaveLevel = MutableStateFlow(0.05f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    private val _lastRecognitionError = MutableStateFlow<String?>(null)
    val lastRecognitionError: StateFlow<String?> = _lastRecognitionError.asStateFlow()

    private val _pendingConfirmationIntent = MutableStateFlow<ToolIntent?>(null)
    val pendingConfirmationIntent: StateFlow<ToolIntent?> = _pendingConfirmationIntent.asStateFlow()

    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

    private var activeExecutionJob: Job? = null
    private var followUpTimerJob: Job? = null

    init {
        // Wire Wake Word Trigger
        wakeWordEngine.setOnWakeWordListener {
            onWakeWordDetected()
        }

        // Collect STT RMS audio levels for visualizer
        coroutineScope.launch {
            speechRecognitionManager.rmsLevel.collect { rms ->
                if (stateController.isListening()) {
                    _audioWaveLevel.value = rms
                    JarvisOverlayService.overlayWaveLevel.value = rms
                }
            }
        }

        // Collect Wake Acoustic energy when idle / listening for wake word
        coroutineScope.launch {
            wakeWordEngine.audioWaveLevel.collect { wave ->
                if (stateController.state.value == VoiceState.IDLE ||
                    stateController.state.value == VoiceState.LISTENING_FOR_WAKEWORD
                ) {
                    _audioWaveLevel.value = wave
                    JarvisOverlayService.overlayWaveLevel.value = wave
                }
            }
        }
    }

    // === Wake Word Controls ===

    fun setWakeWordEnabled(enabled: Boolean) {
        wakeWordEngine.setWakeWordEnabled(enabled)
        if (enabled && !_isMicrophoneMuted.value) {
            startWakeMonitoring()
        } else {
            wakeWordEngine.stopMonitoring()
        }
    }

    fun setWakeSensitivity(sensitivity: WakeSensitivity) {
        wakeWordEngine.setSensitivity(sensitivity)
    }

    fun setSelectedWakePhrase(phrase: String) {
        wakeWordEngine.setSelectedWakePhrase(phrase)
    }

    fun startWakeMonitoring() {
        if (_isMicrophoneMuted.value) return
        if (wakeWordEngine.isWakeWordEnabled.value) {
            stateController.transitionTo(VoiceState.LISTENING_FOR_WAKEWORD)
            wakeWordEngine.startMonitoring()
        }
    }

    fun stopWakeMonitoring() {
        wakeWordEngine.stopMonitoring()
        stateController.transitionTo(VoiceState.IDLE)
    }

    fun startBackgroundWakeService() {
        if (!_isMicrophoneMuted.value && wakeWordEngine.isWakeWordEnabled.value) {
            startWakeMonitoring()
            JarvisVoiceForegroundService.startService(context)
        }
    }

    fun stopBackgroundWakeService() {
        stopWakeMonitoring()
        JarvisVoiceForegroundService.stopService(context)
    }

    fun toggleMicrophone(muted: Boolean) {
        _isMicrophoneMuted.value = muted
        if (muted) {
            wakeWordEngine.stopMonitoring()
            speechRecognitionManager.cancel()
            ttsManager.stop()
            stateController.transitionTo(VoiceState.IDLE, "Microphone Muted")
        } else {
            if (wakeWordEngine.isWakeWordEnabled.value) {
                startWakeMonitoring()
            }
        }
    }

    fun onAudioPermissionGranted() {
        speechRecognitionManager.ensureRecognizer()
        if (wakeWordEngine.isWakeWordEnabled.value && !_isMicrophoneMuted.value) {
            startWakeMonitoring()
        }
    }

    // === Core Voice Assistant State Transitions ===

    private fun onWakeWordDetected() {
        wakeWordEngine.stopMonitoring()
        followUpTimerJob?.cancel()
        stateController.transitionTo(VoiceState.WAKE_DETECTED)

        coroutineScope.launch {
            val isBn = ttsManager.currentLanguage.value == "BN"
            val prompt = if (isBn) "বলুন?" else "Yes?"
            ttsManager.speak(prompt, isVerifiedSuccess = true, onDone = {
                startListeningForCommand()
            })
        }
    }

    fun startListeningForCommand(isFollowUp: Boolean = false) {
        if (_isMicrophoneMuted.value) return

        ttsManager.stop()
        _liveSpokenText.value = ""
        val targetState = if (isFollowUp) VoiceState.LISTENING_FOR_FOLLOWUP else VoiceState.LISTENING_FOR_COMMAND
        stateController.transitionTo(targetState)

        speechRecognitionManager.startListening(
            language = "auto",
            continuous = false,
            onPartial = { partial ->
                _liveSpokenText.value = partial
                if (stateController.state.value == VoiceState.LISTENING_FOR_COMMAND ||
                    stateController.state.value == VoiceState.LISTENING_FOR_FOLLOWUP
                ) {
                    stateController.transitionTo(VoiceState.TRANSCRIBING, partial)
                }
            },
            onResult = { result ->
                _liveSpokenText.value = result
                processSpokenUtterance(result)
            },
            onError = { code, error ->
                Log.w("VoiceAssistantManager", "Speech recognition error: $code ($error)")
                _lastRecognitionError.value = error

                if (stateController.isListening()) {
                    if (isFollowUp) {
                        returnToIdleOrWake()
                    } else {
                        // Prompt retry once if speech was not heard
                        coroutineScope.launch {
                            val msg = if (ttsManager.currentLanguage.value == "BN") {
                                "আমি কথাটা বুঝতে পারিনি। আবার বলুন।"
                            } else {
                                "I didn't catch that. Please repeat."
                            }
                            ttsManager.speak(msg, isVerifiedSuccess = false, onDone = {
                                returnToIdleOrWake()
                            })
                        }
                    }
                }
            }
        )
    }

    fun processSpokenUtterance(rawUtterance: String) {
        val trimmed = rawUtterance.trim()
        if (trimmed.isBlank()) {
            returnToIdleOrWake()
            return
        }

        // Parse Multilingual Command & Intent
        val parsed = VoiceCommandParser.parse(trimmed)

        // 1. Interruption / Barge-in Stop Check
        if (parsed.isEmergencyStop || MasterStopManager.isEmergencyStopVoiceCommand(trimmed)) {
            MasterStopManager.triggerEmergencyStop("Voice Interruption: $trimmed")
            handleBargeInStop()
            return
        }

        // 2. Pending Sensitive Tool Voice Confirmation Check
        val pendingIntent = _pendingConfirmationIntent.value
        if (pendingIntent != null) {
            if (parsed.isAffirmative) {
                approvePendingConfirmation()
                return
            } else if (parsed.isNegative) {
                cancelPendingConfirmation()
                return
            }
        }

        // 3. Conversational Contextual Follow-Up Enrichment
        val enrichedUtterance = conversationManager.enrichFollowUpUtterance(parsed.normalizedGoal)

        activeExecutionJob?.cancel()
        activeExecutionJob = coroutineScope.launch {
            stateController.transitionTo(VoiceState.UNDERSTANDING, "Understanding: $enrichedUtterance")

            val repo = repositoryProvider()
            repo?.insertChatMessage(
                ChatMessageEntity(
                    role = "USER",
                    message = enrichedUtterance,
                    providerType = "VOICE_ASSISTANT"
                )
            )

            // 4. Fast Local Tool Interception
            val handledLocally = handleLocalFastTools(enrichedUtterance)
            if (handledLocally) {
                return@launch
            }

            // 5. Communication Intent Check (Call, SMS, WhatsApp - Security Gated)
            val commIntent = CommunicationIntentParser.parse(enrichedUtterance)
            val handledComm = handleCommunicationIntent(commIntent, enrichedUtterance)
            if (handledComm) {
                return@launch
            }

            // 6. Universal Agent Planning & Execution Loop
            val agentCore = agentCoreProvider()
            if (agentCore != null) {
                stateController.transitionTo(VoiceState.EXECUTING, "Executing Universal Task")

                val planner = agentCore.universalPlanner
                val universalTask = planner.planUniversalTask(enrichedUtterance)

                // Execute via Universal Action Engine
                val executedTask = agentCore.executeUniversalTask(universalTask, coroutineScope)
                val isSuccess = executedTask.status == com.example.core.agent.UniversalTaskStatus.COMPLETED
                val resultText = executedTask.result ?: executedTask.failureReason ?: "Task processed."

                // Record in Conversational Memory
                conversationManager.recordTurn(
                    utterance = enrichedUtterance,
                    targetApp = executedTask.targetApp ?: parsed.suggestedApp,
                    taskName = executedTask.intent,
                    resultSummary = resultText
                )

                repo?.insertChatMessage(
                    ChatMessageEntity(
                        role = "JARVIS",
                        message = resultText,
                        providerType = "UNIVERSAL_AGENT"
                    )
                )

                // 7. Conversational Voice Response (Respects ResponseMode)
                stateController.transitionTo(VoiceState.RESPONDING)
                val voiceReply = if (isSuccess) {
                    when (ttsManager.responseMode.value) {
                        ResponseMode.BRIEF -> "Done."
                        ResponseMode.NORMAL -> resultText.take(120)
                        ResponseMode.DETAILED -> resultText
                    }
                } else {
                    executedTask.failureReason ?: "Could not complete task."
                }

                ttsManager.speak(voiceReply, isVerifiedSuccess = isSuccess, onDone = {
                    startFollowUpWindow()
                })
            } else {
                ttsManager.speak("System ready.", isVerifiedSuccess = true) {
                    returnToIdleOrWake()
                }
            }
        }
    }

    private fun startFollowUpWindow() {
        stateController.transitionTo(VoiceState.LISTENING_FOR_FOLLOWUP)
        conversationManager.setAwaitingFollowUp(true)

        followUpTimerJob?.cancel()
        followUpTimerJob = coroutineScope.launch {
            delay(ConversationManager.FOLLOW_UP_WINDOW_MS)
            if (stateController.state.value == VoiceState.LISTENING_FOR_FOLLOWUP) {
                returnToIdleOrWake()
            }
        }
    }

    private fun returnToIdleOrWake() {
        conversationManager.setAwaitingFollowUp(false)
        if (wakeWordEngine.isWakeWordEnabled.value && !_isMicrophoneMuted.value) {
            stateController.transitionTo(VoiceState.LISTENING_FOR_WAKEWORD)
            wakeWordEngine.startMonitoring()
        } else {
            stateController.transitionTo(VoiceState.IDLE)
        }
    }

    // === Local Fast Tools (Instant Zero-Latency Execution) ===

    private suspend fun handleLocalFastTools(query: String): Boolean {
        val router = toolRouterProvider() ?: return false
        val lower = query.lowercase().trim()

        if (lower == "go back" || lower == "back" || lower == "পেছনে যাও" || lower == "পিছনে যাও" || lower == "back e jao") {
            stateController.transitionTo(VoiceState.EXECUTING)
            val res = router.executeTool(ToolIntent("go_back", emptyMap(), "LOW"))
            val reply = if (VoiceCommandParser.detectLanguage(query) == "BN") "পেছনে যাচ্ছি।" else "Going back."
            ttsManager.speak(reply, isVerifiedSuccess = res.success) { returnToIdleOrWake() }
            return true
        }

        if (lower == "go home" || lower == "home" || lower == "হোমে যাও" || lower == "home e jao") {
            stateController.transitionTo(VoiceState.EXECUTING)
            val res = router.executeTool(ToolIntent("go_home", emptyMap(), "LOW"))
            val reply = if (VoiceCommandParser.detectLanguage(query) == "BN") "হোম স্ক্রিনে যাচ্ছি।" else "Going home."
            ttsManager.speak(reply, isVerifiedSuccess = res.success) { returnToIdleOrWake() }
            return true
        }

        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশলাইট")) {
            val turnOn = !lower.contains("off") && !lower.contains("বন্ধ") && !lower.contains("bondho")
            stateController.transitionTo(VoiceState.EXECUTING)
            val res = router.executeTool(ToolIntent("toggle_flashlight", mapOf("enable" to turnOn.toString()), "LOW"))
            val reply = if (turnOn) "Flashlight turned on." else "Flashlight turned off."
            ttsManager.speak(reply, isVerifiedSuccess = res.success) { returnToIdleOrWake() }
            return true
        }

        if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("চার্জ")) {
            stateController.transitionTo(VoiceState.EXECUTING)
            val res = router.executeTool(ToolIntent("query_battery_status", emptyMap(), "LOW"))
            ttsManager.speak(res.output, isVerifiedSuccess = res.success) { returnToIdleOrWake() }
            return true
        }

        return false
    }

    // === Communication Tools with Explicit Voice Security Gate ===

    private suspend fun handleCommunicationIntent(intent: CommunicationIntent, rawQuery: String): Boolean {
        when (intent) {
            is CommunicationIntent.MakeCall -> {
                val contactRes = ContactResolver.searchContacts(context, intent.contactQuery)
                val targetNumber = if (contactRes is ContactResolutionResult.SingleMatch) contactRes.contact.phoneNumber else intent.directNumber ?: intent.contactQuery
                val targetName = if (contactRes is ContactResolutionResult.SingleMatch) contactRes.contact.name else intent.contactQuery

                val toolIntent = ToolIntent("make_phone_call", mapOf("contact_name" to targetName, "number" to targetNumber), "HIGH")
                _pendingConfirmationIntent.value = toolIntent
                conversationManager.setPendingConfirmation(toolIntent, "Ready to call $targetName ($targetNumber). Should I proceed?")
                stateController.transitionTo(VoiceState.WAITING_FOR_CONFIRMATION)

                ttsManager.speak("Ready to call $targetName. Should I call?")
                return true
            }

            is CommunicationIntent.SendSms -> {
                val contactRes = ContactResolver.searchContacts(context, intent.contactQuery)
                val targetNumber = if (contactRes is ContactResolutionResult.SingleMatch) contactRes.contact.phoneNumber else intent.directNumber ?: intent.contactQuery
                val targetName = if (contactRes is ContactResolutionResult.SingleMatch) contactRes.contact.name else intent.contactQuery

                val toolIntent = ToolIntent("send_sms", mapOf("recipient" to targetName, "number" to targetNumber, "message" to intent.messageText), "HIGH")
                _pendingConfirmationIntent.value = toolIntent
                conversationManager.setPendingConfirmation(toolIntent, "Ready to send SMS to $targetName. Send now?")
                stateController.transitionTo(VoiceState.WAITING_FOR_CONFIRMATION)

                ttsManager.speak("Ready to send SMS to $targetName: \"${intent.messageText}\". Should I send it?")
                return true
            }

            is CommunicationIntent.SendWhatsAppMessage -> {
                val contactRes = ContactResolver.searchContacts(context, intent.contactQuery)
                val targetName = if (contactRes is ContactResolutionResult.SingleMatch) contactRes.contact.name else intent.contactQuery

                val toolIntent = ToolIntent("send_whatsapp_message", mapOf("contact_name" to targetName, "message" to intent.messageText), "HIGH")
                _pendingConfirmationIntent.value = toolIntent
                conversationManager.setPendingConfirmation(toolIntent, "Ready to send WhatsApp message to $targetName. Send now?")
                stateController.transitionTo(VoiceState.WAITING_FOR_CONFIRMATION)

                ttsManager.speak("Ready to send WhatsApp to $targetName: \"${intent.messageText}\". Send now?")
                return true
            }

            else -> return false
        }
    }

    fun approvePendingConfirmation() {
        val toolIntent = _pendingConfirmationIntent.value ?: return
        val router = toolRouterProvider() ?: return
        val repo = repositoryProvider()
        _pendingConfirmationIntent.value = null
        conversationManager.clearPendingConfirmation()

        coroutineScope.launch {
            stateController.transitionTo(VoiceState.EXECUTING)
            val result = router.executeTool(toolIntent)
            repo?.insertChatMessage(
                ChatMessageEntity(
                    role = "JARVIS",
                    message = "Approved & Executed:\n${result.output}",
                    providerType = "VOICE_CONFIRMATION"
                )
            )

            val reply = if (result.success) "Done." else "Action failed."
            ttsManager.speak(reply, isVerifiedSuccess = result.success) {
                returnToIdleOrWake()
            }
        }
    }

    fun cancelPendingConfirmation() {
        _pendingConfirmationIntent.value = null
        conversationManager.clearPendingConfirmation()
        stateController.transitionTo(VoiceState.CANCELLED)

        coroutineScope.launch {
            ttsManager.speak("Cancelled.", isVerifiedSuccess = true) {
                returnToIdleOrWake()
            }
        }
    }

    fun handleBargeInStop() {
        ttsManager.stop()
        activeExecutionJob?.cancel()
        followUpTimerJob?.cancel()
        _pendingConfirmationIntent.value = null
        conversationManager.clearPendingConfirmation()
        stateController.transitionTo(VoiceState.CANCELLED)

        coroutineScope.launch {
            ttsManager.speak("Stopped.", isVerifiedSuccess = true) {
                returnToIdleOrWake()
            }
        }
    }

    // === Diagnostics ===

    fun getVoiceDiagnostics(): Map<String, String> {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val isFgRunning = JarvisVoiceForegroundService.isRunning.value
        val isOverlayOn = JarvisOverlayService.isOverlayActive.value

        return mapOf(
            "Wake Word Enabled" to if (wakeWordEngine.isWakeWordEnabled.value) "Active (${wakeWordEngine.selectedWakePhrase.value})" else "Disabled",
            "Sensitivity" to wakeWordEngine.sensitivity.value.label,
            "Microphone Permission" to if (hasMic) "GRANTED" else "DENIED",
            "Foreground Service" to if (isFgRunning) "RUNNING" else "STOPPED",
            "HUD Floating Overlay" to if (isOverlayOn) "ACTIVE" else "INACTIVE",
            "Speech Recognizer" to if (speechRecognitionManager.ensureRecognizer()) "AVAILABLE (Offline Ready)" else "UNAVAILABLE",
            "TTS Engine" to "ACTIVE (${ttsManager.currentLanguage.value})",
            "Response Mode" to ttsManager.responseMode.value.label,
            "Current State" to stateController.state.value.toDisplayLabel(),
            "Last Recognition Error" to (_lastRecognitionError.value ?: "None"),
            "Battery Impact Estimate" to "Ultra-Low (Acoustic Trigger Idle)"
        )
    }

    fun shutdown() {
        wakeWordEngine.shutdown()
        speechRecognitionManager.shutdown()
        ttsManager.shutdown()
        JarvisVoiceForegroundService.stopService(context)
        JarvisOverlayService.stopOverlay(context)
    }
}
