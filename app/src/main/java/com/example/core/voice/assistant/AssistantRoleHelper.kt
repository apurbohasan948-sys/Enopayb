package com.example.core.voice.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object AssistantRoleHelper {

    /**
     * Checks if JARVIS is currently configured as the system default digital assistant.
     * Uses official Android RoleManager and Secure Settings checks without faking status.
     */
    fun isDefaultAssistant(context: Context): Boolean {
        try {
            // 1. Android Q+ (API 29+) RoleManager check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    val isHeld = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
                    if (isHeld) return true
                }
            }

            // 2. Settings.Secure assistant inspection
            val currentAssistant = Settings.Secure.getString(context.contentResolver, "assistant")
            if (currentAssistant != null && currentAssistant.contains(context.packageName)) {
                return true
            }

            // 3. Settings.Secure voice interaction service check
            val voiceService = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            if (voiceService != null && voiceService.contains(context.packageName)) {
                return true
            }
        } catch (e: Exception) {
            Log.w("AssistantRoleHelper", "Error checking assistant status: ${e.message}")
        }
        return false
    }

    /**
     * Launches the official Android settings flow for the user to select JARVIS as Default Assistant.
     */
    fun openDefaultAssistantSettings(context: Context) {
        val intents = mutableListOf<Intent>()

        // Option A: RoleManager request intent on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                try {
                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(roleIntent)
                    return
                } catch (e: Exception) {
                    Log.w("AssistantRoleHelper", "RoleManager request failed: ${e.message}")
                }
            }
        }

        // Option B: Direct Voice Input / Assistant Settings
        intents.add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        // Option C: Manage Default Apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intents.add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // Option D: Application Details
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w("AssistantRoleHelper", "Failed intent ${intent.action}: ${e.message}")
            }
        }
    }
}
