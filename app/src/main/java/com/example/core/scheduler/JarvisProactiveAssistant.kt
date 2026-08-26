package com.example.core.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class JarvisProactiveAssistant(
    private val context: Context,
    private val dao: JarvisDao
) {
    companion object {
        const val CHANNEL_ID = "jarvis_proactive_notifications"
        const val CHANNEL_NAME = "JARVIS Proactive Assistant"
        private const val MIN_NOTIFICATION_INTERVAL_MS = 15_000L // 15s rate limiter
    }

    private val lastNotificationTimes = ConcurrentHashMap<String, Long>()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Proactive updates, scheduled task outcomes, and research summaries from JARVIS"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Sends a proactive update to the user (both via system notification and chat message history).
     * Enforces rate limiting to prevent notification spam.
     */
    suspend fun notifyUser(
        category: String,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastTime = lastNotificationTimes[category] ?: 0L
        if (now - lastTime < MIN_NOTIFICATION_INTERVAL_MS) {
            // Drop to avoid spamming
            return@withContext
        }
        lastNotificationTimes[category] = now

        // 1. Insert into Chat message log as JARVIS system message
        dao.insertChatMessage(
            ChatMessageEntity(
                role = "JARVIS",
                message = "📢 **$title**\n$message",
                providerType = "PROACTIVE_ASSISTANT"
            )
        )

        // 2. Dispatch Android System Notification if permission allows
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val nm = NotificationManagerCompat.from(context)
            nm.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Notification permission not yet granted by user
        } catch (e: Exception) {}
    }
}
