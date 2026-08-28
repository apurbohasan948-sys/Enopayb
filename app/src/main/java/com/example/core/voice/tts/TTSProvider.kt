package com.example.core.voice.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

interface TTSProvider {
    fun initialize(onReady: () -> Unit)
    fun speak(text: String, utteranceId: String, onStart: () -> Unit, onDone: () -> Unit, onError: () -> Unit)
    fun stop()
    fun setSpeechRate(rate: Float)
    fun setPitch(pitch: Float)
    fun setLanguage(language: String)
    fun shutdown()
    val isReady: Boolean
}

class AndroidTTSProvider(private val context: Context) : TTSProvider, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var _isReady = false
    override val isReady: Boolean get() = _isReady

    private var onReadyCallback: (() -> Unit)? = null
    private var currentLangCode: String = "EN"
    private var rate: Float = 1.05f
    private var pitchVal: Float = 0.95f

    private var currentOnStart: (() -> Unit)? = null
    private var currentOnDone: (() -> Unit)? = null
    private var currentOnError: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            Log.w("AndroidTTSProvider", "Failed to initialize TextToSpeech: ${e.message}")
            tts = null
        }
    }

    override fun initialize(onReady: () -> Unit) {
        if (_isReady) {
            onReady()
        } else {
            onReadyCallback = onReady
        }
    }

    override fun onInit(status: Int) {
        try {
            if (status == TextToSpeech.SUCCESS) {
                _isReady = true
                setupProgressListener()
                applyLanguageInternal()
                onReadyCallback?.invoke()
                onReadyCallback = null
            } else {
                Log.e("AndroidTTSProvider", "TTS Initialization failed: status $status")
            }
        } catch (e: Throwable) {
            Log.w("AndroidTTSProvider", "onInit exception: ${e.message}")
        }
    }

    private fun setupProgressListener() {
        try {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    try { currentOnStart?.invoke() } catch (e: Throwable) { Log.w("AndroidTTSProvider", "onStart error: ${e.message}") }
                }

                override fun onDone(utteranceId: String?) {
                    try { currentOnDone?.invoke() } catch (e: Throwable) { Log.w("AndroidTTSProvider", "onDone error: ${e.message}") }
                }

                override fun onError(utteranceId: String?) {
                    try { currentOnError?.invoke() } catch (e: Throwable) { Log.w("AndroidTTSProvider", "onError error: ${e.message}") }
                }
            })
        } catch (e: Throwable) {
            Log.w("AndroidTTSProvider", "setupProgressListener error: ${e.message}")
        }
    }

    private fun applyLanguageInternal() {
        if (!_isReady) return
        try {
            val locale = when (currentLangCode.uppercase()) {
                "BN", "BANGLA", "BANGLISH" -> Locale("bn", "BD")
                else -> Locale.US
            }
            val res = tts?.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(rate)
            tts?.setPitch(pitchVal)
        } catch (e: Throwable) {
            Log.w("AndroidTTSProvider", "applyLanguageInternal error: ${e.message}")
        }
    }

    override fun speak(
        text: String,
        utteranceId: String,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        if (!_isReady || text.isBlank()) {
            onDone()
            return
        }

        currentOnStart = onStart
        currentOnDone = onDone
        currentOnError = onError

        try {
            applyLanguageInternal()

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Throwable) {
            Log.w("AndroidTTSProvider", "speak error: ${e.message}")
            onError()
        }
    }

    override fun stop() {
        tts?.stop()
        currentOnDone?.invoke()
    }

    override fun setSpeechRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        if (_isReady) tts?.setSpeechRate(this.rate)
    }

    override fun setPitch(pitch: Float) {
        this.pitchVal = pitch.coerceIn(0.5f, 2.0f)
        if (_isReady) tts?.setPitch(this.pitchVal)
    }

    override fun setLanguage(language: String) {
        this.currentLangCode = language
        applyLanguageInternal()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady = false
    }
}

class TextToSpeechManager(private val context: Context) {

    private val provider: TTSProvider = AndroidTTSProvider(context)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _spokenLanguage = MutableStateFlow("EN")
    val spokenLanguage: StateFlow<String> = _spokenLanguage.asStateFlow()

    var speechRate: Float = 1.05f
        set(value) {
            field = value
            provider.setSpeechRate(value)
        }

    var speechPitch: Float = 0.95f
        set(value) {
            field = value
            provider.setPitch(value)
        }

    init {
        provider.initialize {
            provider.setSpeechRate(speechRate)
            provider.setPitch(speechPitch)
        }
    }

    /**
     * Detects if the prompt contains Bangla characters to switch TTS language dynamically.
     */
    fun detectLanguage(text: String): String {
        val containsBangla = text.any { it.code in 0x0980..0x09FF }
        return if (containsBangla) "BN" else "EN"
    }

    fun speak(
        text: String,
        languageOverride: String? = null,
        onStart: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) {
        val lang = languageOverride ?: detectLanguage(text)
        _spokenLanguage.value = lang
        provider.setLanguage(lang)

        val utteranceId = "JARVIS_TTS_${System.currentTimeMillis()}"

        provider.speak(
            text = text,
            utteranceId = utteranceId,
            onStart = {
                _isSpeaking.value = true
                onStart?.invoke()
            },
            onDone = {
                _isSpeaking.value = false
                onFinished?.invoke()
            },
            onError = {
                _isSpeaking.value = false
                onFinished?.invoke()
            }
        )
    }

    /**
     * Immediately stops TTS for Barge-in / interruption.
     */
    fun stop() {
        if (_isSpeaking.value) {
            provider.stop()
            _isSpeaking.value = false
        }
    }

    fun shutdown() {
        provider.shutdown()
    }
}
