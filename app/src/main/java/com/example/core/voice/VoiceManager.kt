package com.example.core.voice

import android.content.Context
import com.example.core.agent.JarvisAgentCore
import com.example.core.model.ToolIntent
import com.example.core.tools.ToolRouter
import com.example.core.voice.context.VoiceConversationContext
import com.example.core.voice.service.JarvisOverlayService
import com.example.core.voice.service.JarvisVoiceForegroundService
import com.example.core.voice.tts.ResponseMode
import com.example.core.voice.wake.WakeSensitivity
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 12 Unified VoiceManager facade.
 * Encapsulates the complete VoiceAssistantManager pipeline.
 */
class VoiceManager(
    private val context: Context,
    agentCoreProvider: (() -> JarvisAgentCore?)? = null,
    toolRouterProvider: (() -> ToolRouter?)? = null,
    repositoryProvider: (() -> JarvisRepository?)? = null,
    coroutineScope: CoroutineScope? = null
) {
    private val scope = coroutineScope ?: CoroutineScope(Dispatchers.Main)

    var agentCore: JarvisAgentCore? = null
    var toolRouter: ToolRouter? = null
    var repository: JarvisRepository? = null

    val assistantManager = VoiceAssistantManager(
        context = context,
        coroutineScope = scope,
        agentCoreProvider = { agentCore ?: agentCoreProvider?.invoke() },
        toolRouterProvider = { toolRouter ?: toolRouterProvider?.invoke() },
        repositoryProvider = { repository ?: repositoryProvider?.invoke() }
    )

    val interactionManager = VoiceInteractionManager(
        context = context,
        coroutineScope = scope,
        agentCoreProvider = { agentCore ?: agentCoreProvider?.invoke() },
        toolRouterProvider = { toolRouter ?: toolRouterProvider?.invoke() },
        repositoryProvider = { repository ?: repositoryProvider?.invoke() }
    )

    val voiceState: StateFlow<VoiceState> = assistantManager.voiceState
    val liveSpokenText: StateFlow<String> = assistantManager.liveSpokenText
    val audioWaveLevel: StateFlow<Float> = assistantManager.audioWaveLevel
    val conversationContext: StateFlow<VoiceConversationContext> = interactionManager.conversationContext
    val pendingConfirmationIntent: StateFlow<ToolIntent?> = assistantManager.pendingConfirmationIntent
    val isMicrophoneMuted: StateFlow<Boolean> = assistantManager.isMicrophoneMuted
    val isCloudAllowed: StateFlow<Boolean> = interactionManager.isCloudAllowed

    var speechRate: Float
        get() = assistantManager.ttsManager.speechRate
        set(value) {
            assistantManager.ttsManager.speechRate = value
            interactionManager.setSpeechRate(value)
        }

    var speechPitch: Float
        get() = assistantManager.ttsManager.speechPitch
        set(value) {
            assistantManager.ttsManager.speechPitch = value
            interactionManager.setSpeechPitch(value)
        }

    var responseMode: ResponseMode
        get() = assistantManager.ttsManager.responseMode.value
        set(value) {
            assistantManager.ttsManager.setResponseMode(value)
        }

    var currentLanguage: String
        get() = assistantManager.ttsManager.currentLanguage.value
        set(value) {
            assistantManager.ttsManager.setLanguage(value)
        }

    fun speak(text: String, isVerifiedSuccess: Boolean = true, onFinished: (() -> Unit)? = null) {
        assistantManager.ttsManager.speak(text, isVerifiedSuccess, onDone = onFinished)
    }

    fun stopSpeaking() {
        assistantManager.ttsManager.stop()
    }

    fun startListeningForCommand(isFollowUp: Boolean = false) {
        assistantManager.startListeningForCommand(isFollowUp)
    }

    fun startListeningSimulation(onResult: (String) -> Unit) {
        assistantManager.startListeningForCommand()
    }

    fun simulateVoiceInput(text: String) {
        assistantManager.processSpokenUtterance(text)
    }

    fun setIdle() {
        assistantManager.stateController.reset()
    }

    fun setWaveform(level: Float) {
        // Updated dynamically via audio wave flows
    }

    fun setWakeSensitivity(sens: WakeSensitivity) {
        assistantManager.setWakeSensitivity(sens)
    }

    fun setSelectedWakePhrase(phrase: String) {
        assistantManager.setSelectedWakePhrase(phrase)
    }

    fun onAudioPermissionGranted() {
        assistantManager.onAudioPermissionGranted()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        assistantManager.setWakeWordEnabled(enabled)
    }

    fun toggleMicrophone(muted: Boolean) {
        assistantManager.toggleMicrophone(muted)
    }

    fun setCloudAllowed(allowed: Boolean) {
        interactionManager.setCloudAllowed(allowed)
    }

    fun startBackgroundWakeService() {
        assistantManager.startBackgroundWakeService()
    }

    fun stopBackgroundWakeService() {
        assistantManager.stopBackgroundWakeService()
    }

    fun startOverlayHud() {
        JarvisOverlayService.startOverlay(context)
    }

    fun stopOverlayHud() {
        JarvisOverlayService.stopOverlay(context)
    }

    fun getVoiceDiagnostics(): Map<String, String> {
        return assistantManager.getVoiceDiagnostics()
    }

    fun shutdown() {
        assistantManager.shutdown()
        interactionManager.shutdown()
    }
}
