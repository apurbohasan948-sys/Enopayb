package com.example.core.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisVoiceForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_voice_assistant_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.example.action.START_VOICE_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_VOICE_SERVICE"
        const val ACTION_TRIGGER_WAKE = "com.example.action.TRIGGER_WAKE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, JarvisVoiceForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, JarvisVoiceForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                _isRunning.value = false
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_WAKE -> {
                // Trigger wake from notification action
            }
            else -> {
                _isRunning.value = true
                val notification = buildForegroundNotification("Active — Listening for \"Hey JARVIS\"")
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JARVIS available in background for voice commands"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisVoiceForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Voice Assistant")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateStatus(state: VoiceState, detailText: String? = null) {
        val text = detailText ?: when (state) {
            VoiceState.SLEEPING -> "Standby — Say \"Hey JARVIS\""
            VoiceState.WAKE_DETECTED -> "Wake word detected"
            VoiceState.LISTENING -> "Listening for command..."
            VoiceState.PROCESSING -> "Processing request..."
            VoiceState.ACTING -> "Executing task on device..."
            VoiceState.SPEAKING -> "Speaking response..."
            VoiceState.WAITING_FOR_CONFIRMATION -> "Waiting for confirmation..."
            VoiceState.CANCELLED -> "Operation cancelled"
        }

        val notification = buildForegroundNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        Log.d("JarvisVoiceService", "Voice Foreground Service destroyed")
    }
}
