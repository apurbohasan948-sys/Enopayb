package com.example

import android.app.Application
import android.util.Log
import com.example.core.health.CrashReporter
import com.example.data.local.preference.JarvisPreferences

class JarvisApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Install CrashReporter immediately to catch any subsequent startup crash
        CrashReporter.install(this)
        CrashReporter.currentScreen = "Application Startup"
        CrashReporter.currentService = "JarvisApplication"
        CrashReporter.lastAction = "Initializing Application"

        try {
            val prefs = JarvisPreferences(this)
            
            // If Safe Mode was auto-triggered due to 2+ consecutive startup crashes, sync preference
            if (CrashReporter.isSafeModeTriggered(this)) {
                prefs.isSafeModeEnabled = true
                Log.w("JARVIS_APP", "SAFE MODE active on launch due to previous crash count.")
            }

            Log.i("JARVIS_APP", "JarvisApplication initialized. Safe Mode: ${prefs.isSafeModeEnabled}, Vision Enabled: ${prefs.isVisionEnabled}")
        } catch (e: Exception) {
            Log.e("JARVIS_APP", "Startup initialization warning", e)
            CrashReporter.recordCrash(this, e, overrideService = "JarvisApplication", overrideAction = "onCreate")
        }
    }
}
