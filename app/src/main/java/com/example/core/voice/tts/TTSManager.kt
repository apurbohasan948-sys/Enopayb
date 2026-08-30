package com.example.core.voice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ResponseMode(val label: String) {
    BRIEF("Brief (Concise)"),
    NORMAL("Normal (Conversational)"),
    DETAILED("Detailed (Comprehensive)")
}

/**
 * Phase 12 TTSManager.
 * Manages Text-To-Speech generation, response verbosity modes, audio focus ducking,
 * and multilingual language switching for English and Bangla.
 */
class TTSManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentLanguage = MutableStateFlow("EN")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _responseMode = MutableStateFlow(ResponseMode.BRIEF)
    val responseMode: StateFlow<ResponseMode> = _responseMode.asStateFlow()

    var speechRate: Float = 1.05f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setSpeechRate(field)
        }

    var speechPitch: Float = 0.95f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setPitch(field)
        }

    private var onSpeakingFinished: (() -> Unit)? = null
    private var onSpeakingStarted: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            Log.w("TTSManager", "Failed to construct TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            setLanguage("EN")
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)
        } else {
            Log.e("TTSManager", "TTS initialization failed with code: $status")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                onSpeakingStarted?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                abandonAudioFocus()
                onSpeakingFinished?.invoke()
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                abandonAudioFocus()
                onSpeakingFinished?.invoke()
            }
        })
    }

    fun setResponseMode(mode: ResponseMode) {
        _responseMode.value = mode
    }

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        if (!isInitialized) return

        try {
            val locale = when (lang.uppercase()) {
                "BN", "BANGLA", "BANGLISH" -> Locale("bn", "BD")
                else -> Locale.US
            }
            val res = tts?.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        } catch (e: Throwable) {
            Log.w("TTSManager", "setLanguage exception: ${e.message}")
        }
    }

    fun detectLanguage(text: String): String {
        val containsBangla = text.any { it.code in 0x0980..0x09FF }
        return if (containsBangla) "BN" else "EN"
    }

    /**
     * Speaks formatted text respecting ResponseMode (BRIEF, NORMAL, DETAILED).
     */
    fun speak(
        text: String,
        isVerifiedSuccess: Boolean = true,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            onDone?.invoke()
            return
        }

        val formattedText = formatForResponseMode(trimmed, isVerifiedSuccess)
        val lang = detectLanguage(formattedText)
        setLanguage(lang)

        onSpeakingStarted = onStart
        onSpeakingFinished = onDone

        requestAudioFocus()

        try {
            val utteranceId = "JARVIS_TTS_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts?.speak(formattedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Throwable) {
            Log.w("TTSManager", "speak error: ${e.message}")
            _isSpeaking.value = false
            abandonAudioFocus()
            onDone?.invoke()
        }
    }

    private fun formatForResponseMode(rawText: String, isVerifiedSuccess: Boolean): String {
        val clean = rawText.replace(Regex("(?m)^#+.*"), "").trim()
        val sentences = clean.split(Regex("[.!?\n]")).map { it.trim() }.filter { it.isNotBlank() }

        return when (_responseMode.value) {
            ResponseMode.BRIEF -> {
                if (sentences.isNotEmpty()) {
                    val first = sentences.first()
                    if (first.length < 80) first else first.substring(0, 77) + "..."
                } else if (isVerifiedSuccess) {
                    "Done."
                } else {
                    "Could not complete action."
                }
            }
            ResponseMode.NORMAL -> {
                if (sentences.size <= 2) clean else sentences.take(2).joinToString(". ") + "."
            }
            ResponseMode.DETAILED -> clean
        }
    }

    /**
     * Immediate Barge-in cancellation.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        _isSpeaking.value = false
        abandonAudioFocus()
        onSpeakingFinished?.invoke()
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setOnAudioFocusChangeListener { /* handle focus changes */ }
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Throwable) {
            Log.w("TTSManager", "Error requesting audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Throwable) {
            Log.w("TTSManager", "Error abandoning audio focus: ${e.message}")
        }
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
        tts = null
        isInitialized = false
    }
}
