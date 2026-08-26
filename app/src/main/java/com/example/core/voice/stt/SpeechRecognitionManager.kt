package com.example.core.voice.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class STTState {
    IDLE,
    PREPARING,
    LISTENING,
    PROCESSING,
    ERROR
}

class SpeechRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _sttState = MutableStateFlow(STTState.IDLE)
    val sttState: StateFlow<STTState> = _sttState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _finalText = MutableStateFlow("")
    val finalText: StateFlow<String> = _finalText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((Int, String) -> Unit)? = null
    private var onPartialCallback: ((String) -> Unit)? = null

    var currentLanguageCode: String = "en-US" // "en-US", "bn-BD", or "auto"
    var isContinuous: Boolean = false

    private var isListeningActive = false

    init {
        mainHandler.post {
            ensureRecognizer()
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _sttState.value = STTState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                _sttState.value = STTState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Normalize rmsdB (-2 to ~10) to 0.0f..1.0f
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                _rmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _sttState.value = STTState.PROCESSING
                _rmsLevel.value = 0.05f
            }

            override fun onError(error: Int) {
                _sttState.value = STTState.ERROR
                _rmsLevel.value = 0.0f
                isListeningActive = false
                val errorMsg = getErrorMessage(error)
                Log.w("SpeechRecognitionManager", "STT Error ($error): $errorMsg")
                onErrorCallback?.invoke(error, errorMsg)

                if (isContinuous) {
                    mainHandler.postDelayed({
                        if (isContinuous && !isListeningActive) {
                            startListeningInternal()
                        }
                    }, 1000)
                }
            }

            override fun onResults(results: Bundle?) {
                _sttState.value = STTState.IDLE
                _rmsLevel.value = 0.0f
                isListeningActive = false

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognized = matches?.firstOrNull()?.trim() ?: ""

                if (recognized.isNotBlank()) {
                    _finalText.value = recognized
                    onResultCallback?.invoke(recognized)
                }

                if (isContinuous) {
                    mainHandler.postDelayed({
                        if (isContinuous && !isListeningActive) {
                            startListeningInternal()
                        }
                    }, 600)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    _partialText.value = text
                    onPartialCallback?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening(
        language: String = currentLanguageCode,
        continuous: Boolean = false,
        onPartial: ((String) -> Unit)? = null,
        onResult: (String) -> Unit,
        onError: ((Int, String) -> Unit)? = null
    ) {
        this.currentLanguageCode = language
        this.isContinuous = continuous
        this.onPartialCallback = onPartial
        this.onResultCallback = onResult
        this.onErrorCallback = onError

        mainHandler.post {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _sttState.value = STTState.ERROR
            onErrorCallback?.invoke(-1, "Speech recognition not available on device")
            return
        }

        ensureRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            val langTag = when (currentLanguageCode.lowercase()) {
                "bn", "bn-bd", "bangla" -> "bn-BD"
                "banglish" -> "bn-BD"
                else -> Locale.getDefault().toLanguageTag()
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langTag)

            // Suggest offline on-device speech recognition when available
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(intent)
            isListeningActive = true
            _sttState.value = STTState.PREPARING
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Failed to start speech recognizer", e)
            _sttState.value = STTState.ERROR
            onErrorCallback?.invoke(-2, e.localizedMessage ?: "Unknown error")
        }
    }

    fun stopListening() {
        isContinuous = false
        isListeningActive = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("SpeechRecognitionManager", "Error stopping recognizer: ${e.message}")
            }
            _sttState.value = STTState.IDLE
            _rmsLevel.value = 0.0f
        }
    }

    fun cancel() {
        isContinuous = false
        isListeningActive = false
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("SpeechRecognitionManager", "Error cancelling recognizer: ${e.message}")
            }
            _sttState.value = STTState.IDLE
            _rmsLevel.value = 0.0f
        }
    }

    fun shutdown() {
        cancel()
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side recognition error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "RECORD_AUDIO permission missing"
            SpeechRecognizer.ERROR_NETWORK -> "Network communication error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected (timeout)"
            else -> "Speech error code $errorCode"
        }
    }
}
