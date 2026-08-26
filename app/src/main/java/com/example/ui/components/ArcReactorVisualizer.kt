package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.voice.VoiceState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisViolet
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorVisualizer(
    voiceState: VoiceState,
    audioWaveLevel: Float,
    isSecurityShieldActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")
    
    // Rotating outer rings
    val rotationOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_outer"
    )

    val rotationInner by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_inner"
    )

    // Pulsating glow
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    val activeColor = when (voiceState) {
        VoiceState.SLEEPING -> if (isSecurityShieldActive) JarvisCyan else JarvisAmber
        VoiceState.WAKE_DETECTED, VoiceState.LISTENING -> JarvisCyan
        VoiceState.PROCESSING -> JarvisViolet
        VoiceState.ACTING -> JarvisAmber
        VoiceState.SPEAKING -> JarvisEmerald
        VoiceState.WAITING_FOR_CONFIRMATION -> JarvisAmber
        VoiceState.CANCELLED -> JarvisRed
    }

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f - 12.dp.toPx()

            // 1. Radial Background Hologram Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.35f * pulseGlow),
                        activeColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.3f
                ),
                radius = baseRadius * 1.3f,
                center = center
            )

            // 2. Outer Segmented Ring
            val segments = 12
            val segmentSweep = 360f / segments
            for (i in 0 until segments) {
                val startAngle = (rotationOuter + (i * segmentSweep)) % 360f
                drawArc(
                    color = activeColor.copy(alpha = 0.4f),
                    startAngle = startAngle,
                    sweepAngle = segmentSweep * 0.65f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2, baseRadius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Middle Rotating Arc Reactor Teeth
            val innerRadius = baseRadius * 0.72f
            val teethCount = 8
            for (i in 0 until teethCount) {
                val angleDeg = rotationInner + (i * (360f / teethCount))
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val p1 = Offset(
                    center.x + (innerRadius * 0.85f * cos(angleRad)).toFloat(),
                    center.y + (innerRadius * 0.85f * sin(angleRad)).toFloat()
                )
                val p2 = Offset(
                    center.x + (innerRadius * 1.05f * cos(angleRad)).toFloat(),
                    center.y + (innerRadius * 1.05f * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = JarvisCyan.copy(alpha = 0.7f * pulseGlow),
                    start = p1,
                    end = p2,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Audio Level Wave Ring Spikes
            val dynamicWaveRadius = innerRadius * (0.8f + (audioWaveLevel * 0.35f))
            drawCircle(
                color = activeColor.copy(alpha = 0.5f + (audioWaveLevel * 0.4f)),
                radius = dynamicWaveRadius,
                center = center,
                style = Stroke(width = (2f + (audioWaveLevel * 4f)).dp.toPx())
            )

            // 5. Central AI Core Orb
            val coreRadius = baseRadius * 0.42f * pulseGlow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        activeColor,
                        activeColor.copy(alpha = 0.2f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
        }

        // Center HUD Glyphs / Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "JARVIS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = when (voiceState) {
                    VoiceState.SLEEPING -> "STANDBY"
                    VoiceState.WAKE_DETECTED -> "ONLINE"
                    VoiceState.LISTENING -> "LISTENING"
                    VoiceState.PROCESSING -> "REASONING"
                    VoiceState.ACTING -> "ACTING"
                    VoiceState.SPEAKING -> "SPEAKING"
                    VoiceState.WAITING_FOR_CONFIRMATION -> "WAITING"
                    VoiceState.CANCELLED -> "CANCELLED"
                },
                color = activeColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}
