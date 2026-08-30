package com.example.core.voice

import com.example.core.voice.service.JarvisOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 12 VoiceStateController.
 * Controls transitions through the formal Voice Assistant state machine:
 * IDLE -> LISTENING_FOR_WAKEWORD -> WAKE_DETECTED -> LISTENING_FOR_COMMAND ->
 * TRANSCRIBING -> UNDERSTANDING -> EXECUTING -> RESPONDING -> LISTENING_FOR_FOLLOWUP -> IDLE.
 *
 * Keeps Floating HUD overlay state synchronized.
 */
class VoiceStateController {

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun transitionTo(newState: VoiceState, message: String? = null) {
        _state.value = newState
        _statusMessage.value = message ?: newState.toDisplayLabel()

        // Sync with HUD
        JarvisOverlayService.currentOverlayState.value = newState
    }

    fun isListening(): Boolean {
        return _state.value == VoiceState.LISTENING_FOR_COMMAND ||
                _state.value == VoiceState.LISTENING_FOR_FOLLOWUP ||
                _state.value == VoiceState.LISTENING
    }

    fun isExecuting(): Boolean {
        return _state.value == VoiceState.EXECUTING || _state.value == VoiceState.ACTING
    }

    fun isSpeaking(): Boolean {
        return _state.value == VoiceState.RESPONDING || _state.value == VoiceState.SPEAKING
    }

    fun reset() {
        transitionTo(VoiceState.IDLE, "Standby")
    }
}
