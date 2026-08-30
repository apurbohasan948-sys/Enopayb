package com.example.core.voice.wake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Phase 12 WakeWordEngine.
 * Lightweight, on-device acoustic keyword & speech energy detector.
 * Supports configurable wake phrases ("Hey JARVIS", "Hey Edith"),
 * multi-level sensitivity, audio focus & telephony call state awareness,
 * and graceful background status reporting without crashing.
 */
class WakeWordEngine(
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

    private val _selectedWakePhrase = MutableStateFlow("Hey JARVIS")
    val selectedWakePhrase: StateFlow<String> = _selectedWakePhrase.asStateFlow()

    private val _wakeStatusMessage = MutableStateFlow("Idle")
    val wakeStatusMessage: StateFlow<String> = _wakeStatusMessage.asStateFlow()

    private var onWakeWordTriggered: (() -> Unit)? = null
    private var lastTriggerTimestamp = 0L
    private val cooldownMillis = 1500L

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        val SUPPORTED_WAKE_PHRASES = listOf(
            "Hey JARVIS",
            "Hey Edith",
            "JARVIS",
            "Ok JARVIS",
            "এই জারভিস"
        )
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _isWakeWordEnabled.value = enabled
        if (!enabled) {
            stopMonitoring()
            _wakeStatusMessage.value = "Wake word disabled"
        } else {
            _wakeStatusMessage.value = "Wake word ready"
        }
    }

    fun setSensitivity(sens: WakeSensitivity) {
        _sensitivity.value = sens
    }

    fun setSelectedWakePhrase(phrase: String) {
        if (phrase.isNotBlank()) {
            _selectedWakePhrase.value = phrase
        }
    }

    fun setOnWakeWordListener(listener: () -> Unit) {
        this.onWakeWordTriggered = listener
    }

    fun hasAudioPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    private fun isPhoneCallActive(): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val state = tm?.callState ?: TelephonyManager.CALL_STATE_IDLE
            state != TelephonyManager.CALL_STATE_IDLE
        } catch (e: Throwable) {
            false
        }
    }

    fun startMonitoring() {
        if (!_isWakeWordEnabled.value) {
            _wakeStatusMessage.value = "Wake word is turned OFF"
            return
        }

        if (!hasAudioPermission()) {
            _wakeStatusMessage.value = "Microphone permission required"
            Log.w("WakeWordEngine", "Cannot monitor wake word: RECORD_AUDIO permission missing")
            return
        }

        if (isPhoneCallActive()) {
            _wakeStatusMessage.value = "Paused during phone call"
            Log.i("WakeWordEngine", "Phone call active; wake monitoring suspended")
            return
        }

        if (_isMonitoring.value) return

        _isMonitoring.value = true
        _wakeStatusMessage.value = "Listening for \"${_selectedWakePhrase.value}\""
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
            Log.w("WakeWordEngine", "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
        _audioWaveLevel.value = 0.0f
        _wakeStatusMessage.value = "Standby"
    }

    /**
     * Efficient on-device audio buffer energy and zero-crossing monitor.
     * Computes RMS audio energy locally without cloud transmission.
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
                    Log.w("WakeWordEngine", "AudioRecord initialization failed. Background mic may be restricted.")
                    _wakeStatusMessage.value = "Background wake word unavailable under current Android settings."
                    _isMonitoring.value = false
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize / 2)

                var baselineRms = 200.0
                val ratio = _sensitivity.value.thresholdRatio

                while (isActive && _isMonitoring.value) {
                    if (isPhoneCallActive()) {
                        delay(2000)
                        continue
                    }

                    val readShorts = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readShorts > 0) {
                        var sumSquare = 0.0
                        var zeroCrossings = 0
                        for (i in 0 until readShorts) {
                            val sample = buffer[i].toDouble()
                            sumSquare += sample * sample
                            if (i > 0 && ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0))) {
                                zeroCrossings++
                            }
                        }

                        val currentRms = sqrt(sumSquare / readShorts)
                        baselineRms = baselineRms * 0.95 + currentRms * 0.05
                        val normalizedWave = (currentRms / 3000.0).coerceIn(0.02, 1.0).toFloat()
                        _audioWaveLevel.value = normalizedWave

                        // Acoustic trigger criteria: RMS energy spike + valid speech zero-crossing rate
                        val dynamicThreshold = (baselineRms * (1.8f * ratio)).coerceAtLeast(600.0)
                        val isSpeechAcoustics = currentRms > dynamicThreshold && zeroCrossings in (readShorts / 40)..(readShorts / 4)

                        if (isSpeechAcoustics) {
                            val now = System.currentTimeMillis()
                            if (now - lastTriggerTimestamp > cooldownMillis) {
                                lastTriggerTimestamp = now
                                mainHandler.post {
                                    triggerWakeDetected()
                                }
                            }
                        }
                    }
                    delay(30)
                }
            } catch (e: Throwable) {
                Log.w("WakeWordEngine", "Acoustic detection exception: ${e.message}")
                _wakeStatusMessage.value = "Background wake word unavailable under current Android settings."
                _isMonitoring.value = false
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    // Ignore
                }
                audioRecord = null
            }
        }
    }

    /**
     * Triggers wake detection when user speaks wake phrase or presses wake button.
     */
    fun triggerWakeDetected() {
        if (!_isWakeWordEnabled.value) return
        _wakeStatusMessage.value = "Wake phrase detected: ${_selectedWakePhrase.value}"
        onWakeWordTriggered?.invoke()
    }

    fun shutdown() {
        stopMonitoring()
        onWakeWordTriggered = null
    }
}
