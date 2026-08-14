package com.example.core.voice

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING_WAKE_WORD,
    WAKE_WORD_TRIGGERED,
    LISTENING_COMMAND,
    PROCESSING,
    SPEAKING,
    ERROR
}

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _audioWaveLevel = MutableStateFlow(0.1f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    var speechRate: Float = 1.05f
    var speechPitch: Float = 0.95f
    var currentLanguage: String = "EN" // "EN" or "BN"

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            applyLanguageSettings()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = VoiceState.SPEAKING
                    _audioWaveLevel.value = 0.8f
                }

                override fun onDone(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                    _audioWaveLevel.value = 0.05f
                }

                override fun onError(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                    _audioWaveLevel.value = 0.0f
                }
            })
        } else {
            Log.e("VoiceManager", "TextToSpeech init failed with status: $status")
        }
    }

    fun applyLanguageSettings() {
        if (!isTtsReady) return
        val locale = if (currentLanguage == "BN") Locale("bn", "BD") else Locale.US
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        if (!isTtsReady || text.isBlank()) return
        applyLanguageSettings()
        _voiceState.value = VoiceState.SPEAKING
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "JARVIS_REPLY_${System.currentTimeMillis()}")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "JARVIS_REPLY")
    }

    fun stopSpeaking() {
        tts?.stop()
        _voiceState.value = VoiceState.IDLE
        _audioWaveLevel.value = 0.0f
    }

    fun startListeningSimulation(onResult: (String) -> Unit) {
        _voiceState.value = VoiceState.LISTENING_COMMAND
        _audioWaveLevel.value = 0.65f
    }

    fun simulateVoiceInput(text: String) {
        _liveSpokenText.value = text
        _voiceState.value = VoiceState.PROCESSING
        _audioWaveLevel.value = 0.3f
    }

    fun setIdle() {
        _voiceState.value = VoiceState.IDLE
        _audioWaveLevel.value = 0.05f
    }

    fun setWaveform(level: Float) {
        _audioWaveLevel.value = level.coerceIn(0.0f, 1.0f)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
