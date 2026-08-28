package com.example.core.voice

import android.content.Context
import android.util.Log
import com.example.core.agent.JarvisAgentCore
import com.example.core.communication.CommunicationIntent
import com.example.core.communication.CommunicationIntentParser
import com.example.core.contacts.ContactResolutionResult
import com.example.core.contacts.ContactResolver
import com.example.core.model.ToolIntent
import com.example.core.tools.ToolRouter
import com.example.core.voice.context.VoiceConversationContext
import com.example.core.voice.service.JarvisOverlayService
import com.example.core.voice.service.JarvisVoiceForegroundService
import com.example.core.voice.stt.SpeechRecognitionManager
import com.example.core.voice.tts.TextToSpeechManager
import com.example.core.voice.wake.WakeSensitivity
import com.example.core.voice.wake.WakeWordManager
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceInteractionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val agentCoreProvider: () -> JarvisAgentCore?,
    private val toolRouterProvider: () -> ToolRouter?,
    private val repositoryProvider: () -> JarvisRepository?
) {
    private val ttsManager = TextToSpeechManager(context)
    private val sttManager = SpeechRecognitionManager(context)
    private val wakeManager = WakeWordManager(context, coroutineScope)

    private val _voiceState = MutableStateFlow(VoiceState.SLEEPING)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _audioWaveLevel = MutableStateFlow(0.05f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    private val _conversationContext = MutableStateFlow(VoiceConversationContext())
    val conversationContext: StateFlow<VoiceConversationContext> = _conversationContext.asStateFlow()

    private val _pendingConfirmationIntent = MutableStateFlow<ToolIntent?>(null)
    val pendingConfirmationIntent: StateFlow<ToolIntent?> = _pendingConfirmationIntent.asStateFlow()

    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

    private val _isCloudAllowed = MutableStateFlow(true)
    val isCloudAllowed: StateFlow<Boolean> = _isCloudAllowed.asStateFlow()

    val isWakeWordEnabled: StateFlow<Boolean> = wakeManager.isWakeWordEnabled
    val sensitivity: StateFlow<WakeSensitivity> = wakeManager.sensitivity

    private var activeExecutionJob: Job? = null

    init {
        // Wire Wake Word Trigger
        wakeManager.setOnWakeWordListener {
            onWakeWordDetected()
        }

        // Collect STT RMS audio levels for visualizer
        coroutineScope.launch {
            sttManager.rmsLevel.collect { rms ->
                if (_voiceState.value == VoiceState.LISTENING) {
                    _audioWaveLevel.value = rms
                    JarvisOverlayService.overlayWaveLevel.value = rms
                }
            }
        }

        // Collect Wake Acoustic energy when in SLEEPING
        coroutineScope.launch {
            wakeManager.audioWaveLevel.collect { wave ->
                if (_voiceState.value == VoiceState.SLEEPING) {
                    _audioWaveLevel.value = wave
                    JarvisOverlayService.overlayWaveLevel.value = wave
                }
            }
        }
    }

    private fun setVoiceState(newState: VoiceState) {
        _voiceState.value = newState
        JarvisOverlayService.currentOverlayState.value = newState
    }

    fun startBackgroundWakeMonitoring() {
        if (!_isMicrophoneMuted.value && wakeManager.isWakeWordEnabled.value) {
            setVoiceState(VoiceState.SLEEPING)
            wakeManager.startMonitoring()
            JarvisVoiceForegroundService.startService(context)
        }
    }

    fun stopBackgroundWakeMonitoring() {
        wakeManager.stopMonitoring()
        setVoiceState(VoiceState.SLEEPING)
        JarvisVoiceForegroundService.stopService(context)
    }

    fun toggleMicrophone(muted: Boolean) {
        _isMicrophoneMuted.value = muted
        if (muted) {
            wakeManager.stopMonitoring()
            sttManager.cancel()
            setVoiceState(VoiceState.SLEEPING)
        } else {
            if (wakeManager.isWakeWordEnabled.value) {
                wakeManager.startMonitoring()
            }
        }
    }

    fun setWakeSensitivity(sens: WakeSensitivity) {
        wakeManager.setSensitivity(sens)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        wakeManager.setWakeWordEnabled(enabled)
    }

    fun setCloudAllowed(allowed: Boolean) {
        _isCloudAllowed.value = allowed
    }

    fun setSpeechRate(rate: Float) {
        ttsManager.speechRate = rate
    }

    fun onAudioPermissionGranted() {
        try {
            sttManager.ensureRecognizer()
            if (wakeManager.isWakeWordEnabled.value && !_isMicrophoneMuted.value) {
                wakeManager.startMonitoring()
            }
        } catch (e: Throwable) {
            Log.w("VoiceInteractionManager", "Error handling audio permission: ${e.message}")
        }
    }

    fun setSpeechPitch(pitch: Float) {
        ttsManager.speechPitch = pitch
    }

    /**
     * WAKE WORD DETECTED:
     * Plays gentle tone / speaks "Yes?" and transitions to active listening.
     */
    private fun onWakeWordDetected() {
        wakeManager.stopMonitoring()
        setVoiceState(VoiceState.WAKE_DETECTED)

        coroutineScope.launch {
            val reply = if (ttsManager.spokenLanguage.value == "BN") "বলুন?" else "Yes?"
            speakDirect(reply, onDone = {
                startListeningForCommand()
            })
        }
    }

    /**
     * Activates speech-to-text to capture user command.
     */
    fun startListeningForCommand() {
        if (_isMicrophoneMuted.value) return

        ttsManager.stop()
        setVoiceState(VoiceState.LISTENING)
        _liveSpokenText.value = ""

        sttManager.startListening(
            language = "auto",
            continuous = false,
            onPartial = { partial ->
                _liveSpokenText.value = partial
            },
            onResult = { result ->
                _liveSpokenText.value = result
                processSpokenCommand(result)
            },
            onError = { code, error ->
                Log.w("VoiceInteractionManager", "STT Error: $code ($error)")
                if (_voiceState.value == VoiceState.LISTENING) {
                    setVoiceState(VoiceState.SLEEPING)
                    if (wakeManager.isWakeWordEnabled.value) {
                        wakeManager.startMonitoring()
                    }
                }
            }
        )
    }

    /**
     * Processes transcribed user utterance through the unified JARVIS Agent pipeline.
     */
    fun processSpokenCommand(rawUtterance: String) {
        val trimmed = rawUtterance.trim()
        if (trimmed.isBlank()) {
            setVoiceState(VoiceState.SLEEPING)
            wakeManager.startMonitoring()
            return
        }

        // Check for Emergency Master Stop / Interruption / Barge-in
        if (com.example.core.autonomy.MasterStopManager.isEmergencyStopVoiceCommand(trimmed) || VoiceConversationContext.isStopOrCancelCommand(trimmed)) {
            com.example.core.autonomy.MasterStopManager.triggerEmergencyStop("Voice Stop Triggered: $trimmed")
            handleBargeInStop()
            return
        }

        // Check for Voice Confirmation of pending sensitive tool
        val pendingIntent = _pendingConfirmationIntent.value
        if (pendingIntent != null) {
            if (VoiceConversationContext.isAffirmative(trimmed)) {
                approvePendingConfirmation()
                return
            } else if (VoiceConversationContext.isNegative(trimmed)) {
                cancelPendingConfirmation()
                return
            }
        }

        // Context continuation enrichment (e.g. "Search for Tom and Jerry" -> YouTube)
        val enrichedQuery = VoiceConversationContext.enrichFollowUpQuery(trimmed, _conversationContext.value)

        activeExecutionJob?.cancel()
        activeExecutionJob = coroutineScope.launch {
            setVoiceState(VoiceState.PROCESSING)

            // Insert user speech into chat
            val repo = repositoryProvider()
            repo?.insertChatMessage(
                ChatMessageEntity(
                    role = "USER",
                    message = enrichedQuery,
                    providerType = "VOICE_INPUT"
                )
            )

            // Update Context turn
            _conversationContext.value = _conversationContext.value.copy(
                previousCommand = enrichedQuery,
                lastInteractionTimestamp = System.currentTimeMillis(),
                turnCount = _conversationContext.value.turnCount + 1
            )

            // 1. FAST OFFLINE LOCAL COMMANDS (No Gemini API needed)
            val handledLocally = handleLocalVoiceCommand(enrichedQuery)
            if (handledLocally) {
                return@launch
            }

            // 2. COMMUNICATION INTENT CHECK (WhatsApp, Calling, SMS)
            val commIntent = CommunicationIntentParser.parse(enrichedQuery)
            val handledComm = handleCommunicationIntent(commIntent, enrichedQuery)
            if (handledComm) {
                return@launch
            }

            // 3. MULTI-STEP AGENT CORE EXECUTION
            val agent = agentCoreProvider()
            if (agent != null) {
                setVoiceState(VoiceState.ACTING)
                val summary = agent.executeGoal(enrichedQuery, coroutineScope)
                val finalOutput = summary.finalOutput

                // Update conversational context
                _conversationContext.value = _conversationContext.value.copy(
                    recentResult = finalOutput,
                    lastInteractionTimestamp = System.currentTimeMillis()
                )

                repo?.insertChatMessage(
                    ChatMessageEntity(
                        role = "JARVIS",
                        message = finalOutput,
                        providerType = "AGENT_CORE"
                    )
                )

                // 4. Concise Voice Response Policy
                val spokenSummary = generateConciseVoiceSummary(finalOutput, enrichedQuery)
                speakDirect(spokenSummary, onDone = {
                    setVoiceState(VoiceState.SLEEPING)
                    if (wakeManager.isWakeWordEnabled.value) {
                        wakeManager.startMonitoring()
                    }
                })
            } else {
                speakDirect("Engine ready.") {
                    setVoiceState(VoiceState.SLEEPING)
                    if (wakeManager.isWakeWordEnabled.value) {
                        wakeManager.startMonitoring()
                    }
                }
            }
        }
    }

    /**
     * Resolves high-frequency local commands instantly without internet or Gemini.
     */
    private suspend fun handleLocalVoiceCommand(query: String): Boolean {
        val router = toolRouterProvider() ?: return false
        val lower = query.lowercase().trim()

        // Navigation: Go Back
        if (lower == "go back" || lower == "back" || lower == "পেছনে যাও" || lower == "পিছনে যাও") {
            setVoiceState(VoiceState.ACTING)
            val result = router.executeTool(ToolIntent("go_back", emptyMap(), "LOW"))
            val reply = if (isBangla(query)) "পেছনে যাচ্ছি।" else "Going back."
            speakDirect(reply) { setVoiceState(VoiceState.SLEEPING); wakeManager.startMonitoring() }
            return true
        }

        // Navigation: Home
        if (lower == "go home" || lower == "home" || lower == "হোমে যাও") {
            setVoiceState(VoiceState.ACTING)
            val result = router.executeTool(ToolIntent("go_home", emptyMap(), "LOW"))
            val reply = if (isBangla(query)) "হোম স্ক্রিনে যাচ্ছি।" else "Returning to home screen."
            speakDirect(reply) { setVoiceState(VoiceState.SLEEPING); wakeManager.startMonitoring() }
            return true
        }

        // Device: Flashlight
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশলাইট")) {
            val turnOn = !lower.contains("off") && !lower.contains("বন্ধ")
            setVoiceState(VoiceState.ACTING)
            val result = router.executeTool(ToolIntent("toggle_flashlight", mapOf("enable" to turnOn.toString()), "LOW"))
            val reply = if (turnOn) "Flashlight turned on." else "Flashlight turned off."
            speakDirect(reply) { setVoiceState(VoiceState.SLEEPING); wakeManager.startMonitoring() }
            return true
        }

        // Device: Battery
        if (lower.contains("battery") || lower.contains("চার্জ") || lower.contains("ব্যাটারি")) {
            val result = router.executeTool(ToolIntent("query_battery_status", emptyMap(), "LOW"))
            speakDirect(result.output) { setVoiceState(VoiceState.SLEEPING); wakeManager.startMonitoring() }
            return true
        }

        // App Launching: "Open YouTube", "Open WhatsApp", "Open Settings"
        val openAppMatch = Regex("(?:open|launch|চালু করো|ওপেন করো)\\s+([a-zA-Z0-9\\s]+)", RegexOption.IGNORE_CASE).find(lower)
        if (openAppMatch != null && !lower.contains("play ") && !lower.contains("search ")) {
            val targetApp = openAppMatch.groupValues[1].trim()
            if (targetApp.isNotBlank()) {
                setVoiceState(VoiceState.ACTING)
                val result = router.executeTool(ToolIntent("open_app", mapOf("app_name" to targetApp), "LOW"))
                _conversationContext.value = _conversationContext.value.copy(
                    currentApp = targetApp,
                    currentTask = "OPEN_APP"
                )
                val reply = if (isBangla(query)) "$targetApp ওপেন করছি।" else "Opening $targetApp."
                speakDirect(reply) { setVoiceState(VoiceState.SLEEPING); wakeManager.startMonitoring() }
                return true
            }
        }

        return false
    }

    /**
     * Handles communication intents with explicit confirmation safety check.
     */
    private suspend fun handleCommunicationIntent(intent: CommunicationIntent, rawQuery: String): Boolean {
        when (intent) {
            is CommunicationIntent.MakeCall -> {
                val contactRes = ContactResolver.searchContacts(context, intent.contactQuery)
                var targetNumber = intent.directNumber
                var targetName = intent.contactQuery

                if (contactRes is ContactResolutionResult.SingleMatch) {
                    targetNumber = contactRes.contact.phoneNumber
                    targetName = contactRes.contact.name
                }

                val finalNumber = targetNumber ?: intent.contactQuery
                val toolIntent = ToolIntent("make_phone_call", mapOf("contact_name" to targetName, "number" to finalNumber), "HIGH")
                _pendingConfirmationIntent.value = toolIntent
                setVoiceState(VoiceState.WAITING_FOR_CONFIRMATION)

                val prompt = "Ready to call $targetName ($finalNumber). Should I proceed?"
                speakDirect(prompt)
                return true
            }

            is CommunicationIntent.SendSms -> {
                val contactRes = ContactResolver.searchContacts(context, intent.contactQuery)
                var targetNumber = intent.directNumber
                var targetName = intent.contactQuery

                if (contactRes is ContactResolutionResult.SingleMatch) {
                    targetNumber = contactRes.contact.phoneNumber
                    targetName = contactRes.contact.name
                }

                val finalNumber = targetNumber ?: intent.contactQuery
                val toolIntent = ToolIntent(
                    "send_sms",
                    mapOf("recipient" to targetName, "number" to finalNumber, "message" to intent.messageText),
                    "HIGH"
                )
                _pendingConfirmationIntent.value = toolIntent
                setVoiceState(VoiceState.WAITING_FOR_CONFIRMATION)

                val prompt = "Ready to send SMS to $targetName: \"${intent.messageText}\". Should I send it?"
                speakDirect(prompt)
                return true
            }

            is CommunicationIntent.SendWhatsAppMessage -> {
                val contactRes = ContactResolver.searchContacts(context, intent.contactQuery)
                var targetName = intent.contactQuery

                if (contactRes is ContactResolutionResult.SingleMatch) {
                    targetName = contactRes.contact.name
                }

                val toolIntent = ToolIntent(
                    "send_whatsapp_message",
                    mapOf("contact_name" to targetName, "message" to intent.messageText),
                    "HIGH"
                )
                _pendingConfirmationIntent.value = toolIntent
                setVoiceState(VoiceState.WAITING_FOR_CONFIRMATION)

                val prompt = "Ready to send WhatsApp message to $targetName: \"${intent.messageText}\". Send now?"
                speakDirect(prompt)
                return true
            }

            else -> return false
        }
    }

    /**
     * Executes pending tool after explicit affirmative voice confirmation ("Yes", "Confirm").
     */
    fun approvePendingConfirmation() {
        val toolIntent = _pendingConfirmationIntent.value ?: return
        val router = toolRouterProvider() ?: return
        val repo = repositoryProvider()
        _pendingConfirmationIntent.value = null

        coroutineScope.launch {
            setVoiceState(VoiceState.ACTING)
            val result = router.executeTool(toolIntent)
            repo?.insertChatMessage(
                ChatMessageEntity(
                    role = "JARVIS",
                    message = "Approved & Executed:\n${result.output}",
                    providerType = "VOICE_CONFIRMATION"
                )
            )

            val confirmReply = if (result.success) "Done." else "Failed: ${result.output}"
            speakDirect(confirmReply) {
                setVoiceState(VoiceState.SLEEPING)
                wakeManager.startMonitoring()
            }
        }
    }

    /**
     * Cancels pending tool after negative voice confirmation ("No", "Cancel").
     */
    fun cancelPendingConfirmation() {
        _pendingConfirmationIntent.value = null
        setVoiceState(VoiceState.CANCELLED)

        coroutineScope.launch {
            speakDirect("Cancelled.") {
                setVoiceState(VoiceState.SLEEPING)
                wakeManager.startMonitoring()
            }
        }
    }

    /**
     * Immediate Barge-In Stop: Stops TTS and cancels active execution job.
     */
    fun handleBargeInStop() {
        ttsManager.stop()
        activeExecutionJob?.cancel()
        _pendingConfirmationIntent.value = null
        setVoiceState(VoiceState.CANCELLED)

        coroutineScope.launch {
            speakDirect("Stopped.") {
                setVoiceState(VoiceState.SLEEPING)
                wakeManager.startMonitoring()
            }
        }
    }

    fun speakDirect(text: String, onDone: (() -> Unit)? = null) {
        setVoiceState(VoiceState.SPEAKING)
        ttsManager.speak(
            text = text,
            onStart = {
                setVoiceState(VoiceState.SPEAKING)
                JarvisOverlayService.overlayWaveLevel.value = 0.8f
            },
            onFinished = {
                JarvisOverlayService.overlayWaveLevel.value = 0.05f
                onDone?.invoke()
            }
        )
    }

    fun stopSpeaking() {
        ttsManager.stop()
        setVoiceState(VoiceState.SLEEPING)
    }

    private fun generateConciseVoiceSummary(agentResponse: String, query: String): String {
        val clean = agentResponse.replace(Regex("(?m)^#+.*"), "").trim()
        val sentences = clean.split(Regex("[.!?\n]")).map { it.trim() }.filter { it.isNotBlank() }

        return if (sentences.isNotEmpty()) {
            val firstSentence = sentences.first()
            if (firstSentence.length < 90) firstSentence else firstSentence.substring(0, 87) + "..."
        } else {
            "Task completed."
        }
    }

    private fun isBangla(text: String): Boolean {
        return text.any { it.code in 0x0980..0x09FF }
    }

    fun shutdown() {
        wakeManager.shutdown()
        sttManager.shutdown()
        ttsManager.shutdown()
        JarvisVoiceForegroundService.stopService(context)
        JarvisOverlayService.stopOverlay(context)
    }
}
