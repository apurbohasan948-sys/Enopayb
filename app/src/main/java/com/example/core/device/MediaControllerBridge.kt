package com.example.core.device

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

data class MediaControlResult(
    val success: Boolean,
    val action: String,
    val details: String,
    val currentVolume: Int? = null,
    val maxVolume: Int? = null,
    val error: String? = null
)

class MediaControllerBridge(private val context: Context) {
    private val TAG = "JARVIS_MediaController"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun executeAction(action: String, value: Int? = null): MediaControlResult {
        val upperAction = action.trim().uppercase()
        val am = audioManager ?: return MediaControlResult(
            success = false,
            action = upperAction,
            details = "AudioManager system service is not available.",
            error = "SERVICE_UNAVAILABLE"
        )

        return try {
            when (upperAction) {
                "PLAY" -> {
                    dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY)
                    MediaControlResult(true, "PLAY", "Dispatched MEDIA_PLAY key event to active audio session.")
                }
                "PAUSE" -> {
                    dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_PAUSE)
                    MediaControlResult(true, "PAUSE", "Dispatched MEDIA_PAUSE key event to active audio session.")
                }
                "PLAY_PAUSE", "TOGGLE" -> {
                    dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    MediaControlResult(true, "PLAY_PAUSE", "Dispatched MEDIA_PLAY_PAUSE toggle key event.")
                }
                "NEXT", "SKIP_FORWARD" -> {
                    dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_NEXT)
                    MediaControlResult(true, "NEXT", "Dispatched MEDIA_NEXT track key event.")
                }
                "PREVIOUS", "SKIP_BACKWARD" -> {
                    dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    MediaControlResult(true, "PREVIOUS", "Dispatched MEDIA_PREVIOUS track key event.")
                }
                "STOP" -> {
                    dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_STOP)
                    MediaControlResult(true, "STOP", "Dispatched MEDIA_STOP playback key event.")
                }
                "VOLUME_UP", "VOL_UP" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    val curr = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    MediaControlResult(true, "VOLUME_UP", "Raised media volume to $curr/$max.", curr, max)
                }
                "VOLUME_DOWN", "VOL_DOWN" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    val curr = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    MediaControlResult(true, "VOLUME_DOWN", "Lowered media volume to $curr/$max.", curr, max)
                }
                "MUTE" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    val curr = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    MediaControlResult(true, "MUTE", "Muted media volume.", curr, max)
                }
                "UNMUTE" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    val curr = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    MediaControlResult(true, "UNMUTE", "Unmuted media volume ($curr/$max).", curr, max)
                }
                "SET_VOLUME" -> {
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val target = (value ?: 5).coerceIn(0, max)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                    MediaControlResult(true, "SET_VOLUME", "Set media volume to $target/$max.", target, max)
                }
                else -> {
                    MediaControlResult(false, upperAction, "Unrecognized media action '$action'.", error = "INVALID_ACTION")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media control failed for action $action", e)
            MediaControlResult(false, upperAction, "Failed to execute media action '$action': ${e.message}", error = e.localizedMessage)
        }
    }

    private fun dispatchMediaKey(am: AudioManager, keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        val eventDown = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val eventUp = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)

        am.dispatchMediaKeyEvent(eventDown)
        am.dispatchMediaKeyEvent(eventUp)
    }
}
