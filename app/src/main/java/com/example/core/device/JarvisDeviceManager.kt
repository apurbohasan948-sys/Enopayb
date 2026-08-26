package com.example.core.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class JarvisDeviceManager(private val context: Context) {

    val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    val adminComponent: ComponentName by lazy {
        ComponentName(context, JarvisDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(): Boolean {
        return try {
            devicePolicyManager.isAdminActive(adminComponent)
        } catch (e: Exception) {
            false
        }
    }

    fun isDeviceOwner(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun isProfileOwner(): Boolean {
        return try {
            devicePolicyManager.isProfileOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun getAdminActivationIntent(): Intent {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "JARVIS Device Management requires administrative privilege to lock the screen and maintain system security upon request."
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    fun lockScreen(): Boolean {
        return if (isDeviceAdminActive()) {
            try {
                devicePolicyManager.lockNow()
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
}
