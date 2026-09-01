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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.agent.AgentState
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDarkNavy
import com.example.ui.theme.JarvisDarkVoid
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

@Composable
fun DebugConsoleScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.jarvisAgentCore.agentState.collectAsState()
    val currentGoal by viewModel.jarvisAgentCore.currentGoal.collectAsState()
    val activePlan by viewModel.jarvisAgentCore.activePlan.collectAsState()
    val currentAction by viewModel.jarvisAgentCore.currentActionName.collectAsState()
    val lastResult by viewModel.jarvisAgentCore.lastActionResult.collectAsState()
    val logs by viewModel.jarvisAgentCore.executionLogs.collectAsState()
    val worldSnapshot = viewModel.jarvisAgentCore.worldModel.latestSnapshot
    val latestScreen by viewModel.latestUnifiedScreen.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val telemetry by viewModel.agentTelemetryState.collectAsState()

    var testGoalInput by remember { mutableStateOf("Play Tom and Jerry on YouTube") }
    var showTraceDialog by remember { mutableStateOf(false) }
    var traceDialogContent by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = JarvisDarkVoid
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Debug",
                                tint = JarvisCyan,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column {
                                Text(
                                    text = "AUTONOMOUS AGENT DEBUG CONSOLE",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = JarvisCyan
                                )
                                Text(
                                    text = "Live telemetry, multimodal observation & state inspector",
                                    fontSize = 11.sp,
                                    color = JarvisTextMuted
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                traceDialogContent = viewModel.exportDebugTraceJson()
                                showTraceDialog = true
                            },
                            border = BorderStroke(1.dp, JarvisCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "Export Trace", tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TRACE JSON", color = JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Quick Execution Controls
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "EXECUTE AUTONOMOUS GOAL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = JarvisTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testGoalInput,
                            onValueChange = { testGoalInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter task goal...", color = JarvisTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary,
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisBorder,
                                focusedContainerColor = JarvisDarkNavy,
                                unfocusedContainerColor = JarvisDarkNavy
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.executeGoal(testGoalInput) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RUN GOAL", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { viewModel.jarvisAgentCore.cancelActiveExecution() },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CANCEL", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // Multi-Turn Persistent Conversation Context
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PERSISTENT CONVERSATION STATE & CONTEXT",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = JarvisCyan
                        )

                        DebugTelemetryRow("SESSION ID", conversationState.sessionId.take(16) + "...", JarvisTextPrimary)
                        DebugTelemetryRow("TURN COUNT", "${conversationState.turnHistory.size} recorded turns", JarvisTextSecondary)
                        DebugTelemetryRow("ACTIVE GOAL", conversationState.activeTaskGoal ?: "(None)", JarvisAmber)
                        DebugTelemetryRow("CURRENT APP", conversationState.currentAppPackage ?: "(Unknown)", JarvisCyan)
                        DebugTelemetryRow("SCREEN SUMMARY", conversationState.currentScreenSummary ?: "(Not Captured)", JarvisBlue)
                        DebugTelemetryRow("LAST REFERENCED", conversationState.lastReferencedItem ?: "(None)", JarvisEmerald)
                        DebugTelemetryRow("AWAITING CONFIRM", if (conversationState.pendingConfirmationIntent != null) conversationState.pendingConfirmationIntent!!.toolName else "False", if (conversationState.pendingConfirmationIntent != null) JarvisRed else JarvisEmerald)
                        DebugTelemetryRow("AWAITING FOLLOWUP", conversationState.awaitingFollowUp.toString(), if (conversationState.awaitingFollowUp) JarvisAmber else JarvisTextMuted)
                        DebugTelemetryRow("LAST ACTION", conversationState.lastActionName ?: "(None)", JarvisTextSecondary)
                        DebugTelemetryRow("LAST VERIFY STATUS", if (conversationState.lastActionVerified) "VERIFIED" else "UNVERIFIED", if (conversationState.lastActionVerified) JarvisEmerald else JarvisAmber)
                    }
                }
            }

            // Live State Grid
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "LIVE AGENT TELEMETRY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = JarvisAmber
                        )

                        DebugTelemetryRow("CURRENT GOAL", if (telemetry.currentGoal != "(None)") telemetry.currentGoal else currentGoal.ifEmpty { "(None Active)" }, JarvisTextPrimary)
                        DebugTelemetryRow("CURRENT APP", if (telemetry.currentApp.isNotEmpty()) telemetry.currentApp else worldSnapshot.foregroundPackage, JarvisCyan)
                        DebugTelemetryRow("CURRENT SCREEN", telemetry.currentScreen, JarvisBlue)
                        DebugTelemetryRow("ACCESSIBILITY ELEMS", "${telemetry.accessibilityElementsCount} nodes", JarvisTextSecondary)
                        DebugTelemetryRow("OCR ELEMENTS", "${telemetry.ocrElementsCount} text regions", JarvisViolet)
                        DebugTelemetryRow("VISION ELEMENTS", "${telemetry.visionElementsCount} visual targets", JarvisCyan)
                        DebugTelemetryRow("TARGET SELECTED", telemetry.targetSelected, JarvisAmber)
                        DebugTelemetryRow("TARGET CONFIDENCE", "${(telemetry.targetConfidence * 100).toInt()}%", if (telemetry.targetConfidence >= 0.7f) JarvisEmerald else JarvisAmber)
                        DebugTelemetryRow("ACTION", telemetry.action, JarvisAmber)
                        DebugTelemetryRow("ACTION RESULT", telemetry.actionResult, if (telemetry.actionResult.startsWith("SUCCESS")) JarvisEmerald else if (telemetry.actionResult.startsWith("FAILED")) JarvisRed else JarvisTextSecondary)
                        DebugTelemetryRow("VERIFICATION RESULT", telemetry.verificationResult, if (telemetry.verificationResult.startsWith("VERIFIED")) JarvisEmerald else JarvisTextSecondary)
                        DebugTelemetryRow("NEXT ACTION", telemetry.nextAction, JarvisCyan)
                    }
                }
            }

            // Real-time Agent Logs
            item {
                Text(
                    text = "REAL-TIME EXECUTION LOG STREAM",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = JarvisTextSecondary
                )
            }

            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "No active task execution logs. Trigger a goal above to observe live state transitions.",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisTextMuted
                    )
                }
            } else {
                items(logs.reversed()) { log ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(JarvisCardBg, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (log.contains("❌") || log.contains("failed")) JarvisRed else if (log.contains("✅") || log.contains("🎉")) JarvisEmerald else JarvisCyan
                        )
                    }
                }
            }
        }
    }

    if (showTraceDialog) {
        AlertDialog(
            onDismissRequest = { showTraceDialog = false },
            title = {
                Text(
                    "AGENT DEBUG TRACE (JSON)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = JarvisCyan
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .background(JarvisDarkNavy, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = traceDialogContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = JarvisTextPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(traceDialogContent))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COPY TRACE", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTraceDialog = false }) {
                    Text("CLOSE", color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = JarvisCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun DebugTelemetryRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisTextMuted,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun stateColor(state: AgentState): Color {
    return when (state) {
        AgentState.IDLE -> JarvisTextMuted
        AgentState.LISTENING, AgentState.UNDERSTANDING -> JarvisBlue
        AgentState.PLANNING -> JarvisViolet
        AgentState.OBSERVING -> JarvisCyan
        AgentState.ACTING, AgentState.WAITING -> JarvisAmber
        AgentState.VERIFYING, AgentState.LEARNING -> JarvisCyan
        AgentState.COMPLETED -> JarvisEmerald
        AgentState.FAILED -> JarvisRed
        AgentState.WAITING_FOR_USER, AgentState.CONFIRMATION_REQUIRED -> JarvisAmber
        AgentState.CANCELLED -> JarvisTextMuted
    }
}
