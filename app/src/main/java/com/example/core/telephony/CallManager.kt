package com.example.core.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallState {
    IDLE,
    DIALING,
    RINGING,
    ACTIVE,
    DISCONNECTED
}

data class CallInitiationResult(
    val success: Boolean,
    val callState: CallState,
    val message: String,
    val evidence: String? = null,
    val requiresPermission: Boolean = false,
    val missingPermission: String? = null
)

class CallManager(private val context: Context) {

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val telecomManager: TelecomManager? =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var telephonyCallback: Any? = null
    private var legacyPhoneStateListener: PhoneStateListener? = null

    init {
        registerCallStateListener()
    }

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerCallStateListener() {
        if (!hasReadPhoneStatePermission()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        updateInternalCallState(state)
                    }
                }
                telephonyCallback = callback
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        updateInternalCallState(state)
                    }
                }
                legacyPhoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            // Silently handle if security policy denies background listener
        }
    }

    private fun updateInternalCallState(telephonyState: Int) {
        val newState = when (telephonyState) {
            TelephonyManager.CALL_STATE_IDLE -> CallState.IDLE
            TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.ACTIVE
            else -> CallState.IDLE
        }
        _callState.value = newState
    }

    /**
     * Initiates a legitimate phone call to the target phone number.
     * Uses CALL_PHONE for direct call if granted, otherwise opens DIAL intent.
     */
    fun initiateCall(phoneNumber: String, contactName: String? = null): CallInitiationResult {
        val cleanNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")
        if (cleanNumber.isBlank()) {
            return CallInitiationResult(
                success = false,
                callState = CallState.IDLE,
                message = "Invalid phone number provided: \"$phoneNumber\""
            )
        }

        val displayName = contactName ?: cleanNumber

        if (hasCallPhonePermission()) {
            return try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$cleanNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                _callState.value = CallState.DIALING

                CallInitiationResult(
                    success = true,
                    callState = CallState.DIALING,
                    message = "Direct call initiated to $displayName ($cleanNumber).",
                    evidence = "ACTION_CALL intent dispatched to Android Telephony subsystem for $cleanNumber"
                )
            } catch (e: Exception) {
                CallInitiationResult(
                    success = false,
                    callState = CallState.IDLE,
                    message = "Failed to launch direct call: ${e.localizedMessage}"
                )
            }
        } else {
            // Fallback to DIAL intent without requiring dangerous CALL_PHONE permission
            return try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$cleanNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                _callState.value = CallState.DIALING

                CallInitiationResult(
                    success = true,
                    callState = CallState.DIALING,
                    message = "Dialer opened with $displayName ($cleanNumber). CALL_PHONE permission not granted for direct dial.",
                    evidence = "ACTION_DIAL intent opened dialer keypad with tel:$cleanNumber",
                    requiresPermission = true,
                    missingPermission = Manifest.permission.CALL_PHONE
                )
            } catch (e: Exception) {
                CallInitiationResult(
                    success = false,
                    callState = CallState.IDLE,
                    message = "Failed to open phone dialer: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Checks whether telephony capability exists on this hardware.
     */
    fun isTelephonySupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    fun getTelephonyDiagnostics(): Map<String, String> {
        return mapOf(
            "Telephony Supported" to isTelephonySupported().toString(),
            "CALL_PHONE Permission" to hasCallPhonePermission().toString(),
            "READ_PHONE_STATE Permission" to hasReadPhoneStatePermission().toString(),
            "Current Call State" to _callState.value.name,
            "Network Operator" to (telephonyManager?.networkOperatorName ?: "Unknown / Simulator")
        )
    }
}
