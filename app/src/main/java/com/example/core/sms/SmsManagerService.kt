package com.example.core.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SmsDeliveryStatus {
    object Idle : SmsDeliveryStatus()
    data class Prepared(val recipient: String, val message: String) : SmsDeliveryStatus()
    data class Sending(val recipient: String) : SmsDeliveryStatus()
    data class Sent(val recipient: String, val timestamp: Long) : SmsDeliveryStatus()
    data class Delivered(val recipient: String, val timestamp: Long) : SmsDeliveryStatus()
    data class Failed(val recipient: String, val error: String) : SmsDeliveryStatus()
}

data class SmsExecutionResult(
    val success: Boolean,
    val status: String,
    val message: String,
    val evidence: String? = null,
    val requiresPermission: Boolean = false,
    val missingPermission: String? = null
)

class SmsManagerService(private val context: Context) {

    private val ACTION_SMS_SENT = "com.example.jarvis.SMS_SENT"
    private val ACTION_SMS_DELIVERED = "com.example.jarvis.SMS_DELIVERED"

    private val _deliveryStatus = MutableStateFlow<SmsDeliveryStatus>(SmsDeliveryStatus.Idle)
    val deliveryStatus: StateFlow<SmsDeliveryStatus> = _deliveryStatus.asStateFlow()

    private var isReceiverRegistered = false

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SMS_SENT -> {
                    val recipient = intent.getStringExtra("recipient") ?: "Unknown"
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Sent(recipient, System.currentTimeMillis())
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Failed(recipient, "Generic SMS dispatch failure")
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Failed(recipient, "No cellular network service")
                        }
                        SmsManager.RESULT_ERROR_NULL_PDU -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Failed(recipient, "Null PDU error")
                        }
                        SmsManager.RESULT_ERROR_RADIO_OFF -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Failed(recipient, "Radio / Airplane mode is turned on")
                        }
                        else -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Failed(recipient, "SMS dispatch failed with code: $resultCode")
                        }
                    }
                }
                ACTION_SMS_DELIVERED -> {
                    val recipient = intent.getStringExtra("recipient") ?: "Unknown"
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Delivered(recipient, System.currentTimeMillis())
                        }
                        Activity.RESULT_CANCELED -> {
                            _deliveryStatus.value = SmsDeliveryStatus.Failed(recipient, "SMS delivery was not confirmed by carrier")
                        }
                    }
                }
            }
        }
    }

    init {
        registerSmsReceivers()
    }

    fun hasSendSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerSmsReceivers() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_SMS_SENT)
                addAction(ACTION_SMS_DELIVERED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(smsReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(smsReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            // Receiver registration failure fallback
        }
    }

    /**
     * Prepares an SMS for user review before confirmation.
     */
    fun prepareSms(recipientNumber: String, messageText: String, contactName: String? = null): SmsExecutionResult {
        val cleanNumber = recipientNumber.replace("[^0-9+]".toRegex(), "")
        if (cleanNumber.isBlank()) {
            return SmsExecutionResult(
                success = false,
                status = "INVALID_NUMBER",
                message = "Cannot prepare SMS: invalid recipient number \"$recipientNumber\""
            )
        }

        if (messageText.isBlank()) {
            return SmsExecutionResult(
                success = false,
                status = "EMPTY_BODY",
                message = "Cannot prepare SMS: message body is empty."
            )
        }

        val displayName = contactName ?: cleanNumber
        _deliveryStatus.value = SmsDeliveryStatus.Prepared(cleanNumber, messageText)

        return SmsExecutionResult(
            success = true,
            status = "PREPARED",
            message = "Prepared SMS to $displayName ($cleanNumber): \"$messageText\". Awaiting confirmation to send.",
            evidence = "SMS payload buffered for $cleanNumber with length ${messageText.length} chars"
        )
    }

    /**
     * Sends the SMS using Android SmsManager if permission is granted,
     * or opens the system SMS composer intent as a reliable fallback.
     */
    fun sendSms(recipientNumber: String, messageText: String, contactName: String? = null): SmsExecutionResult {
        val cleanNumber = recipientNumber.replace("[^0-9+]".toRegex(), "")
        if (cleanNumber.isBlank()) {
            return SmsExecutionResult(
                success = false,
                status = "INVALID_NUMBER",
                message = "Invalid phone number for SMS: \"$recipientNumber\""
            )
        }

        val displayName = contactName ?: cleanNumber

        if (hasSendSmsPermission()) {
            return try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_SMS_SENT).putExtra("recipient", cleanNumber),
                    flags
                )

                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent(ACTION_SMS_DELIVERED).putExtra("recipient", cleanNumber),
                    flags
                )

                _deliveryStatus.value = SmsDeliveryStatus.Sending(cleanNumber)

                val parts = smsManager.divideMessage(messageText)
                if (parts.size > 1) {
                    val sentIntents = ArrayList<PendingIntent>().apply { repeat(parts.size) { add(sentIntent) } }
                    val deliveredIntents = ArrayList<PendingIntent>().apply { repeat(parts.size) { add(deliveredIntent) } }
                    smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentIntents, deliveredIntents)
                } else {
                    smsManager.sendTextMessage(cleanNumber, null, messageText, sentIntent, deliveredIntent)
                }

                SmsExecutionResult(
                    success = true,
                    status = "SENT_TO_RADIO",
                    message = "SMS dispatched to carrier for $displayName ($cleanNumber).",
                    evidence = "SmsManager.sendTextMessage invoked for $cleanNumber (length: ${messageText.length})"
                )
            } catch (e: Exception) {
                _deliveryStatus.value = SmsDeliveryStatus.Failed(cleanNumber, e.localizedMessage ?: "Unknown error")
                SmsExecutionResult(
                    success = false,
                    status = "DISPATCH_FAILED",
                    message = "Failed to send SMS via SmsManager: ${e.localizedMessage}"
                )
            }
        } else {
            // Fallback: Launch system SMS app with pre-filled recipient and body
            return try {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$cleanNumber")
                    putExtra("sms_body", messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(smsIntent)

                _deliveryStatus.value = SmsDeliveryStatus.Prepared(cleanNumber, messageText)

                SmsExecutionResult(
                    success = true,
                    status = "COMPOSER_OPENED",
                    message = "SMS Composer opened for $displayName ($cleanNumber) with message. Direct SEND_SMS permission is not granted.",
                    evidence = "ACTION_SENDTO intent dispatched with smsto:$cleanNumber",
                    requiresPermission = true,
                    missingPermission = Manifest.permission.SEND_SMS
                )
            } catch (e: Exception) {
                SmsExecutionResult(
                    success = false,
                    status = "INTENT_FAILED",
                    message = "Failed to launch SMS composer: ${e.localizedMessage}"
                )
            }
        }
    }
}
