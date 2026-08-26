package com.example.core.voice.wake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

enum class WakeSensitivity(val thresholdRatio: Float, val label: String) {
    LOW(1.4f, "Low (Fewer Triggers)"),
    MEDIUM(1.0f, "Medium (Balanced)"),
    HIGH(0.7f, "High (Sensitive)")
}

class WakeWordManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isWakeWordEnabled = MutableStateFlow(true)
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _sensitivity = MutableStateFlow(WakeSensitivity.MEDIUM)
    val sensitivity: StateFlow<WakeSensitivity> = _sensitivity.asStateFlow()

    private val _audioWaveLevel = MutableStateFlow(0.0f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    private val _isBatterySaver = MutableStateFlow(false)
    val isBatterySaver: StateFlow<Boolean> = _isBatterySaver.asStateFlow()

    private var onWakeWordTriggered: (() -> Unit)? = null
    private var lastTriggerTimestamp = 0L
    private val cooldownMillis = 1800L

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var keywordSpotter: SpeechRecognizer? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        val WAKE_KEYWORDS = listOf(
            "hey jarvis",
            "jarvis",
            "jarvis assistant",
            "ok jarvis",
            "okay jarvis",
            "এই জারভিস",
            "জারভিস"
        )
    }

    init {
        mainHandler.post {
            ensureKeywordSpotter()
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _isWakeWordEnabled.value = enabled
        if (!enabled) {
            stopMonitoring()
        }
    }

    fun setSensitivity(sens: WakeSensitivity) {
        _sensitivity.value = sens
    }

    fun setBatterySaver(enabled: Boolean) {
        _isBatterySaver.value = enabled
    }

    fun setOnWakeWordListener(listener: () -> Unit) {
        this.onWakeWordTriggered = listener
    }

    fun startMonitoring() {
        if (!_isWakeWordEnabled.value || _isMonitoring.value) return

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            Log.w("WakeWordManager", "Cannot monitor wake word: RECORD_AUDIO permission missing")
            return
        }

        _isMonitoring.value = true
        startAcousticEnergyDetector()
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w("WakeWordManager", "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
        _audioWaveLevel.value = 0.0f

        mainHandler.post {
            try {
                keywordSpotter?.cancel()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Efficient, lightweight on-device audio buffer energy and zero-crossing monitor.
     * When speech energy surpasses the acoustic threshold, it performs local keyword check.
     */
    private fun startAcousticEnergyDetector() {
        recordingJob?.cancel()
        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (minBufSize * 2).coerceAtLeast(4096)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("WakeWordManager", "AudioRecord initialization failed")
                    _isMonitoring.value = false
                    return@launch
                }

                audioRecord?.startRecording()
                val audioBuffer = ShortArray(1024)

                var baselineRms = 250.0
                var energyPeakCounter = 0

                while (isActive && _isMonitoring.value) {
                    val readSamples = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (readSamples > 0) {
                        var sumSquare = 0.0
                        var zeroCrossings = 0

                        for (i in 0 until readSamples) {
                            val sample = audioBuffer[i]
                            sumSquare += (sample * sample).toDouble()
                            if (i > 0 && ((audioBuffer[i - 1] > 0 && sample <= 0) || (audioBuffer[i - 1] < 0 && sample >= 0))) {
                                zeroCrossings++
                            }
                        }

                        val rms = sqrt(sumSquare / readSamples)
                        // Smooth baseline
                        baselineRms = baselineRms * 0.95 + rms * 0.05

                        val normalizedLevel = (rms / 3000.0).toFloat().coerceIn(0.02f, 0.95f)
                        _audioWaveLevel.value = normalizedLevel

                        val threshold = (baselineRms * 2.2 * _sensitivity.value.thresholdRatio).coerceAtLeast(800.0)

                        if (rms > threshold && zeroCrossings in 15..280) {
                            energyPeakCounter++
                            if (energyPeakCounter >= 2) {
                                energyPeakCounter = 0
                                checkWakeWordSpotter()
                            }
                        } else {
                            if (energyPeakCounter > 0) energyPeakCounter--
                        }
                    }

                    // Battery saver sleeps longer between acoustic checks
                    val sleepTime = if (_isBatterySaver.value) 90L else 40L
                    delay(sleepTime)
                }
            } catch (e: Exception) {
                Log.e("WakeWordManager", "Acoustic detector exception: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    // Clean
                }
                audioRecord = null
            }
        }
    }

    private fun ensureKeywordSpotter() {
        if (keywordSpotter == null && SpeechRecognizer.isRecognitionAvailable(context)) {
            keywordSpotter = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        // Spotter handles errors silently
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.forEach { candidate ->
                            val lower = candidate.lowercase().trim()
                            if (WAKE_KEYWORDS.any { lower.contains(it) }) {
                                triggerWakeWord()
                                return@forEach
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.forEach { candidate ->
                            val lower = candidate.lowercase().trim()
                            if (WAKE_KEYWORDS.any { lower.contains(it) }) {
                                triggerWakeWord()
                                return@forEach
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        }
    }

    private fun checkWakeWordSpotter() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < cooldownMillis) return

        mainHandler.post {
            if (keywordSpotter != null && _isMonitoring.value) {
                val spotterIntent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }
                try {
                    keywordSpotter?.startListening(spotterIntent)
                } catch (e: Exception) {
                    // Ignore transient spotter busy state
                }
            }
        }
    }

    fun triggerWakeWord() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < cooldownMillis) return
        lastTriggerTimestamp = now

        mainHandler.post {
            onWakeWordTriggered?.invoke()
        }
    }

    fun shutdown() {
        stopMonitoring()
        mainHandler.post {
            keywordSpotter?.destroy()
            keywordSpotter = null
        }
    }
}
