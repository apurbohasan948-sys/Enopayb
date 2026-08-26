package com.example.core.voice.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

class JarvisVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i("JarvisVoiceInteractionService", "JARVIS VoiceInteractionService is READY and active.")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i("JarvisVoiceInteractionService", "JARVIS VoiceInteractionService shutting down.")
    }
}

class JarvisVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisVoiceInteractionSession(this)
    }
}

class JarvisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        Log.d("JarvisSession", "JarvisVoiceInteractionSession created")
    }

    override fun onShow(args: Bundle?, flags: Int) {
        super.onShow(args, flags)
        Log.d("JarvisSession", "JarvisVoiceInteractionSession onShow: flags=$flags")
    }

    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        Log.d("JarvisSession", "Received AssistStructure from foreground application")
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null) {
            Log.d("JarvisSession", "Received assistant screenshot (${screenshot.width}x${screenshot.height})")
        }
    }

    override fun onHide() {
        super.onHide()
        Log.d("JarvisSession", "JarvisVoiceInteractionSession onHide")
    }
}
