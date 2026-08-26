package com.example.core.voice.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.core.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    companion object {
        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        val currentOverlayState = MutableStateFlow(VoiceState.SLEEPING)
        val overlayWaveLevel = MutableStateFlow(0.05f)

        fun canShowOverlay(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun startOverlay(context: Context) {
            if (!canShowOverlay(context)) return
            val intent = Intent(context, JarvisOverlayService::class.java)
            context.startService(intent)
        }

        fun stopOverlay(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingHud()
        _isOverlayActive.value = true
    }

    private fun setupFloatingHud() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }
        layoutParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@JarvisOverlayService)
            setViewTreeSavedStateRegistryOwner(this@JarvisOverlayService)

            setContent {
                val state by currentOverlayState.collectAsState()
                val wave by overlayWaveLevel.collectAsState()

                FloatingHudOrb(
                    state = state,
                    waveLevel = wave,
                    onTap = {
                        val appIntent = Intent(this@JarvisOverlayService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(appIntent)
                    },
                    onLongPress = {
                        currentOverlayState.value = VoiceState.CANCELLED
                    }
                )
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        composeView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        overlayView = composeView
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("JarvisOverlayService", "Failed to add floating HUD view: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isOverlayActive.value = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View might already be detached
            }
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingHudOrb(
    state: VoiceState,
    waveLevel: Float,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val orbColor = when (state) {
        VoiceState.SLEEPING -> Color(0xFF00E5FF)
        VoiceState.WAKE_DETECTED, VoiceState.LISTENING -> Color(0xFF00B0FF)
        VoiceState.PROCESSING -> Color(0xFFFFB300)
        VoiceState.ACTING -> Color(0xFF7C4DFF)
        VoiceState.SPEAKING -> Color(0xFF00E676)
        VoiceState.WAITING_FOR_CONFIRMATION -> Color(0xFFFF9100)
        VoiceState.CANCELLED -> Color(0xFFFF1744)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xDD080E1A),
        shadowElevation = 8.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xDD080E1A))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Arc Reactor Core Circle
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(orbColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((8.dp * (1f + waveLevel)).coerceIn(4.dp, 14.dp))
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = when (state) {
                    VoiceState.SLEEPING -> "JARVIS"
                    VoiceState.WAKE_DETECTED -> "WAKE"
                    VoiceState.LISTENING -> "LISTENING"
                    VoiceState.PROCESSING -> "THINKING"
                    VoiceState.ACTING -> "ACTING"
                    VoiceState.SPEAKING -> "SPEAKING"
                    VoiceState.WAITING_FOR_CONFIRMATION -> "WAITING"
                    VoiceState.CANCELLED -> "CANCELLED"
                },
                color = orbColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
