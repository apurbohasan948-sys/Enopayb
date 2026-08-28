package com.example.core.voice

import android.content.Context
import com.example.core.agent.JarvisAgentCore
import com.example.core.model.ToolIntent
import com.example.core.tools.ToolRouter
import com.example.core.voice.context.VoiceConversationContext
import com.example.core.voice.service.JarvisOverlayService
import com.example.core.voice.service.JarvisVoiceForegroundService
import com.example.core.voice.wake.WakeSensitivity
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

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

    val interactionManager = VoiceInteractionManager(
        context = context,
        coroutineScope = scope,
        agentCoreProvider = { agentCore ?: agentCoreProvider?.invoke() },
        toolRouterProvider = { toolRouter ?: toolRouterProvider?.invoke() },
        repositoryProvider = { repository ?: repositoryProvider?.invoke() }
    )

    val voiceState: StateFlow<VoiceState> = interactionManager.voiceState
    val liveSpokenText: StateFlow<String> = interactionManager.liveSpokenText
    val audioWaveLevel: StateFlow<Float> = interactionManager.audioWaveLevel
    val conversationContext: StateFlow<VoiceConversationContext> = interactionManager.conversationContext
    val pendingConfirmationIntent: StateFlow<ToolIntent?> = interactionManager.pendingConfirmationIntent
    val isMicrophoneMuted: StateFlow<Boolean> = interactionManager.isMicrophoneMuted
    val isCloudAllowed: StateFlow<Boolean> = interactionManager.isCloudAllowed

    var speechRate: Float = 1.05f
        set(value) {
            field = value
            interactionManager.setSpeechRate(value)
        }

    var speechPitch: Float = 0.95f
        set(value) {
            field = value
            interactionManager.setSpeechPitch(value)
        }

    var currentLanguage: String = "EN"

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        interactionManager.speakDirect(text, onFinished)
    }

    fun stopSpeaking() {
        interactionManager.stopSpeaking()
    }

    fun startListeningForCommand() {
        interactionManager.startListeningForCommand()
    }

    fun startListeningSimulation(onResult: (String) -> Unit) {
        interactionManager.startListeningForCommand()
    }

    fun simulateVoiceInput(text: String) {
        interactionManager.processSpokenCommand(text)
    }

    fun setIdle() {
        // Standby
    }

    fun setWaveform(level: Float) {
        // Handled dynamically by audio visualizer
    }

    fun setWakeSensitivity(sens: WakeSensitivity) {
        interactionManager.setWakeSensitivity(sens)
    }

    fun onAudioPermissionGranted() {
        interactionManager.onAudioPermissionGranted()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        interactionManager.setWakeWordEnabled(enabled)
    }

    fun toggleMicrophone(muted: Boolean) {
        interactionManager.toggleMicrophone(muted)
    }

    fun setCloudAllowed(allowed: Boolean) {
        interactionManager.setCloudAllowed(allowed)
    }

    fun startBackgroundWakeService() {
        interactionManager.startBackgroundWakeMonitoring()
    }

    fun stopBackgroundWakeService() {
        interactionManager.stopBackgroundWakeMonitoring()
    }

    fun startOverlayHud() {
        JarvisOverlayService.startOverlay(context)
    }

    fun stopOverlayHud() {
        JarvisOverlayService.stopOverlay(context)
    }

    fun shutdown() {
        interactionManager.shutdown()
    }
}
