package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.health.CrashReportData
import com.example.core.health.CrashReporter
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDarkNavy
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

@Composable
fun DiagnosticScreen(viewModel: JarvisViewModel) {
    val context = LocalContext.current
    val prefs = viewModel.preferences

    var isSafeMode by remember { mutableStateOf(prefs.isSafeModeEnabled) }
    var isVisionEnabled by remember { mutableStateOf(prefs.isVisionEnabled) }
    var isLocalModelEnabled by remember { mutableStateOf(prefs.isLocalModelEnabled) }
    var isGeminiEnabled by remember { mutableStateOf(prefs.isGeminiServiceEnabled) }
    var isAccessibilityEnabled by remember { mutableStateOf(prefs.isAccessibilityServiceEnabled) }
    var isAutonomousEnabled by remember { mutableStateOf(prefs.isAutonomousWorkersEnabled) }

    var lastCrash by remember { mutableStateOf(CrashReporter.getLastCrash(context)) }
    var crashCount by remember { mutableStateOf(CrashReporter.getCrashCount(context)) }
    var refreshKey by remember { mutableStateOf(0) }

    val hardwareMetrics by viewModel.hardwareMetrics.collectAsState()
    val modelStatus by viewModel.modelLifecycleManager.status.collectAsState()
    val accessDiag by viewModel.accessibilityDiagnostics.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. SAFE MODE MASTER CARD
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSafeMode) JarvisAmber.copy(alpha = 0.12f) else JarvisCardBg
                ),
                border = BorderStroke(1.2.dp, if (isSafeMode) JarvisAmber else JarvisCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("diagnostic_safe_mode_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSafeMode) Icons.Default.Warning else Icons.Default.Security,
                                contentDescription = "Safe Mode",
                                tint = if (isSafeMode) JarvisAmber else JarvisCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isSafeMode) "JARVIS SAFE MODE [ACTIVE]" else "JARVIS STANDARD MODE",
                                    color = if (isSafeMode) JarvisAmber else JarvisCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isSafeMode) "Heavy AI models disabled for crash prevention" else "All engine systems operating normally",
                                    color = JarvisTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = isSafeMode,
                            onCheckedChange = { active ->
                                isSafeMode = active
                                prefs.isSafeModeEnabled = active
                                CrashReporter.setSafeMode(context, active)
                                if (active) {
                                    viewModel.unloadModelMemory()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisAmber,
                                checkedTrackColor = JarvisAmber.copy(alpha = 0.3f),
                                uncheckedThumbColor = JarvisTextMuted,
                                uncheckedTrackColor = JarvisDarkNavy
                            ),
                            modifier = Modifier.testTag("safe_mode_master_switch")
                        )
                    }

                    if (isSafeMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = JarvisAmber.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, JarvisAmber.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Safe Mode keeps the UI, Room database, system logs, and basic deterministic commands active while completely unloading heavy local neural weights and vision parsers.",
                                color = JarvisAmber,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. HARDWARE & MEMORY HEALTH (Redmi Note 12 Protection)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                border = BorderStroke(0.8.dp, JarvisBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SYSTEM & MEMORY INTEGRITY",
                                color = JarvisTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = {
                            viewModel.refreshAccessibilityDiagnostics()
                            lastCrash = CrashReporter.getLastCrash(context)
                            crashCount = CrashReporter.getCrashCount(context)
                            refreshKey++
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = JarvisCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricItem("JVM MEMORY", "${hardwareMetrics.ramAllocatedMb} MB", JarvisCyan)
                        MetricItem("CPU TEMP", "${hardwareMetrics.cpuTempCelsius}°C", JarvisEmerald)
                        MetricItem("TOTAL CRASHES", "$crashCount", if (crashCount > 0) JarvisRed else JarvisEmerald)
                    }

                    if (modelStatus.errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = JarvisRed.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, JarvisRed.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = JarvisRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = modelStatus.errorMessage ?: "",
                                    color = JarvisRed,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. COMPONENT HEALTH & MANUAL ISOLATION TOGGLES
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                border = BorderStroke(0.8.dp, JarvisBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "COMPONENT STATUS & TOGGLES",
                        color = JarvisTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Database
                    ComponentStatusRow(
                        title = "Room Database",
                        subtitle = "Local SQLite jarvis_brain.db (Non-destructive)",
                        icon = Icons.Default.Storage,
                        statusText = "CONNECTED",
                        statusColor = JarvisEmerald,
                        isEnabled = true,
                        canToggle = false
                    )

                    Divider(color = JarvisBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Vision Engine (Default OFF after Phase 10)
                    ComponentStatusRow(
                        title = "Vision & Screenshot Perception",
                        subtitle = if (isVisionEnabled) "Active (Screen capture & OCR)" else "Disabled (OFF by default for stability)",
                        icon = if (isVisionEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        statusText = if (isVisionEnabled) "ENABLED" else "DISABLED",
                        statusColor = if (isVisionEnabled) JarvisViolet else JarvisTextMuted,
                        isEnabled = isVisionEnabled && !isSafeMode,
                        canToggle = !isSafeMode,
                        onToggle = { enabled ->
                            isVisionEnabled = enabled
                            prefs.isVisionEnabled = enabled
                        }
                    )

                    Divider(color = JarvisBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Local Neural Model
                    ComponentStatusRow(
                        title = "Local SLM Engine",
                        subtitle = "Qwen2.5-1.5B (Lazy / Memory-Gated)",
                        icon = Icons.Default.Memory,
                        statusText = if (isSafeMode) "SAFE GATED" else if (modelStatus.state.name == "READY") "READY" else "UNLOADED (LAZY)",
                        statusColor = if (isSafeMode) JarvisAmber else if (modelStatus.state.name == "READY") JarvisEmerald else JarvisCyan,
                        isEnabled = isLocalModelEnabled && !isSafeMode,
                        canToggle = !isSafeMode,
                        onToggle = { enabled ->
                            isLocalModelEnabled = enabled
                            prefs.isLocalModelEnabled = enabled
                            if (!enabled) viewModel.unloadModelMemory()
                        }
                    )

                    Divider(color = JarvisBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Gemini Cloud Model
                    ComponentStatusRow(
                        title = "Gemini Cloud Teacher",
                        subtitle = "Google AI Studio API / Multimodal fallback",
                        icon = Icons.Default.Cloud,
                        statusText = if (prefs.geminiApiKey.isNotBlank()) "CONFIGURED" else "NO API KEY",
                        statusColor = if (prefs.geminiApiKey.isNotBlank()) JarvisEmerald else JarvisTextMuted,
                        isEnabled = isGeminiEnabled && !isSafeMode,
                        canToggle = !isSafeMode,
                        onToggle = { enabled ->
                            isGeminiEnabled = enabled
                            prefs.isGeminiServiceEnabled = enabled
                        }
                    )

                    Divider(color = JarvisBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Accessibility Engine
                    ComponentStatusRow(
                        title = "Accessibility Engine",
                        subtitle = if (accessDiag.isConnected) "Bound to system service" else "Permission pending / Disabled",
                        icon = Icons.Default.Security,
                        statusText = if (accessDiag.isConnected) "RUNNING" else "STANDBY",
                        statusColor = if (accessDiag.isConnected) JarvisEmerald else JarvisAmber,
                        isEnabled = isAccessibilityEnabled && !isSafeMode,
                        canToggle = !isSafeMode,
                        onToggle = { enabled ->
                            isAccessibilityEnabled = enabled
                            prefs.isAccessibilityServiceEnabled = enabled
                        }
                    )

                    Divider(color = JarvisBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Autonomous Workers
                    ComponentStatusRow(
                        title = "Autonomous Task Agents",
                        subtitle = "Multi-step planner & automated executors",
                        icon = Icons.Default.AutoAwesome,
                        statusText = if (isSafeMode) "DISABLED" else "READY",
                        statusColor = if (isSafeMode) JarvisAmber else JarvisCyan,
                        isEnabled = isAutonomousEnabled && !isSafeMode,
                        canToggle = !isSafeMode,
                        onToggle = { enabled ->
                            isAutonomousEnabled = enabled
                            prefs.isAutonomousWorkersEnabled = enabled
                        }
                    )
                }
            }
        }

        // 4. CRASH DIAGNOSTIC LOG & RECOVERY CONTROLS
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                border = BorderStroke(0.8.dp, JarvisBorder),
                modifier = Modifier.fillMaxWidth().testTag("diagnostic_crash_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = if (lastCrash != null) JarvisRed else JarvisEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CRASH TELEMETRY & LOGS",
                                color = JarvisTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (lastCrash != null || crashCount > 0) {
                            IconButton(onClick = {
                                CrashReporter.clearCrashReports(context)
                                CrashReporter.resetCrashCount(context)
                                prefs.resetAllServiceCrashCounts()
                                lastCrash = null
                                crashCount = 0
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = JarvisTextMuted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (lastCrash == null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = JarvisEmerald.copy(alpha = 0.1f),
                            border = BorderStroke(0.8.dp, JarvisEmerald.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "No fatal crashes recorded. All components running stable.",
                                    color = JarvisEmerald,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        val crash = lastCrash!!
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = JarvisRed.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, JarvisRed.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "LAST INCIDENT // ${crash.timestampFormatted}",
                                    color = JarvisRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${crash.exceptionType}: ${crash.message}",
                                    color = JarvisTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Service: ${crash.currentService}", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    Text("Screen: ${crash.lastScreen}", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Avail RAM: ${crash.availableRAM}", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    Text("Action: ${crash.lastAction}", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }

                                if (crash.stackTrace.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = crash.stackTrace.take(400) + if (crash.stackTrace.length > 400) "..." else "",
                                            color = JarvisTextMuted,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    CrashReporter.resetCrashCount(context)
                                    CrashReporter.clearCrashReports(context)
                                    prefs.resetAllServiceCrashCounts()
                                    lastCrash = null
                                    crashCount = 0
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, JarvisBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset Crash Count", color = JarvisTextPrimary, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    isSafeMode = false
                                    prefs.isSafeModeEnabled = false
                                    CrashReporter.setSafeMode(context, false)
                                    CrashReporter.resetCrashCount(context)
                                    CrashReporter.clearCrashReports(context)
                                    lastCrash = null
                                    crashCount = 0
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, JarvisCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Recover Full Mode", color = JarvisCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column {
        Text(text = label, color = JarvisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ComponentStatusRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusText: String,
    statusColor: Color,
    isEnabled: Boolean,
    canToggle: Boolean,
    onToggle: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, color = JarvisTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = statusText, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Text(text = subtitle, color = JarvisTextMuted, fontSize = 10.sp)
            }
        }

        if (canToggle && onToggle != null) {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisCyan,
                    checkedTrackColor = JarvisCyan.copy(alpha = 0.3f),
                    uncheckedThumbColor = JarvisTextMuted,
                    uncheckedTrackColor = JarvisDarkNavy
                )
            )
        }
    }
}
