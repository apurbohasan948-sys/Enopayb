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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.agent.AgentState
import com.example.core.agent.PlanStep
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
fun TaskPlanViewerScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.jarvisAgentCore.agentState.collectAsState()
    val currentGoal by viewModel.jarvisAgentCore.currentGoal.collectAsState()
    val activePlan by viewModel.jarvisAgentCore.activePlan.collectAsState()
    val telemetry by viewModel.jarvisAgentCore.telemetryState.collectAsState()
    val logs by viewModel.jarvisAgentCore.executionLogs.collectAsState()
    val currentAction by viewModel.jarvisAgentCore.currentActionName.collectAsState()

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
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = "Plan Viewer",
                            tint = JarvisCyan,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Column {
                            Text(
                                text = "TASK PLAN & EXECUTION VIEWER",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = JarvisCyan
                            )
                            Text(
                                text = "Universal planner, dynamic steps, transition verification & recovery",
                                fontSize = 11.sp,
                                color = JarvisTextMuted
                            )
                        }
                    }
                }
            }

            // Quick Preset Tests
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Science, contentDescription = null, tint = JarvisAmber, modifier = Modifier.padding(end = 6.dp))
                            Text(
                                text = "PHASE 4 BENCHMARK TESTS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = JarvisAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        val benchmarks = listOf(
                            "Open YouTube",
                            "Open YouTube and search Tom and Jerry",
                            "Open Chrome and search HSC result",
                            "Open Settings and go back",
                            "Open Gallery and scroll",
                            "Open WhatsApp and find Hammad"
                        )

                        benchmarks.forEach { benchmark ->
                            Button(
                                onClick = { viewModel.executeAutonomousGoal(benchmark) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisDarkNavy),
                                border = BorderStroke(1.dp, JarvisBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = benchmark,
                                        fontSize = 11.sp,
                                        color = JarvisTextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisCyan)
                                }
                            }
                        }
                    }
                }
            }

            // Current Plan & Live Step Progress
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "DYNAMIC TASK PLAN",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = JarvisCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "GOAL: ${currentGoal.ifEmpty { "(None Active)" }}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val plan = activePlan
                        if (plan != null && plan.steps.isNotEmpty()) {
                            plan.steps.forEach { step ->
                                PlanStepItem(step = step, currentAction = currentAction)
                            }
                        } else {
                            Text(
                                text = "No active task plan in memory. Trigger a test goal above.",
                                fontSize = 11.sp,
                                color = JarvisTextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Live Execution Telemetry State
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "EXECUTION & RECOVERY STATE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = JarvisAmber
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        TelemetryLine("CURRENT APP", telemetry.currentApp, JarvisCyan)
                        TelemetryLine("CURRENT SCREEN", telemetry.currentScreen, JarvisBlue)
                        TelemetryLine("TARGET SELECTED", telemetry.targetSelected, JarvisAmber)
                        TelemetryLine("CONFIDENCE", "${(telemetry.targetConfidence * 100).toInt()}%", if (telemetry.targetConfidence >= 0.7f) JarvisEmerald else JarvisAmber)
                        TelemetryLine("ACTION", telemetry.action, JarvisTextPrimary)
                        TelemetryLine("ACTION RESULT", telemetry.actionResult, if (telemetry.actionResult.startsWith("SUCCESS")) JarvisEmerald else if (telemetry.actionResult.startsWith("FAILED")) JarvisRed else JarvisTextSecondary)
                        TelemetryLine("VERIFICATION", telemetry.verificationResult, if (telemetry.verificationResult.startsWith("VERIFIED")) JarvisEmerald else JarvisTextSecondary)
                        TelemetryLine("NEXT ACTION", telemetry.nextAction, JarvisCyan)
                    }
                }
            }

            // Live Execution Logs
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "EXECUTION LOG STREAM (${logs.size} entries)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = JarvisViolet
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val recentLogs = logs.takeLast(10).reversed()
                        if (recentLogs.isEmpty()) {
                            Text("Ready. Awaiting goal execution.", fontSize = 11.sp, color = JarvisTextMuted, fontFamily = FontFamily.Monospace)
                        } else {
                            recentLogs.forEach { log ->
                                Text(
                                    text = log,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (log.contains("❌") || log.contains("failed")) JarvisRed else if (log.contains("✅") || log.contains("🎉")) JarvisEmerald else if (log.contains("⚠️")) JarvisAmber else JarvisTextSecondary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStepItem(step: PlanStep, currentAction: String) {
    val isCurrent = currentAction.contains("Step ${step.stepNumber}")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) JarvisCyan.copy(alpha = 0.15f) else JarvisDarkVoid
        ),
        border = BorderStroke(1.dp, if (isCurrent) JarvisCyan else JarvisBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(22.dp)
                    .background(if (isCurrent) JarvisCyan else JarvisBorder, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${step.stepNumber}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) JarvisDarkVoid else JarvisTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.description,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) JarvisCyan else JarvisTextPrimary
                )
                Text(
                    text = "Tool: ${step.toolIntent.toolName} | Expected: ${step.expectedOutcome}",
                    fontSize = 9.sp,
                    color = JarvisTextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TelemetryLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = JarvisTextMuted
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = valueColor
        )
    }
}
