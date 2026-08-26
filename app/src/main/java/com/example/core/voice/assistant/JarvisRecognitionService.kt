package com.example.core.voice.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.util.Log

class JarvisRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.d("JarvisRecognition", "RecognitionService onStartListening")
        listener?.readyForSpeech(Bundle())
    }

    override fun onCancel(listener: Callback?) {
        Log.d("JarvisRecognition", "RecognitionService onCancel")
    }

    override fun onStopListening(listener: Callback?) {
        Log.d("JarvisRecognition", "RecognitionService onStopListening")
    }
}
