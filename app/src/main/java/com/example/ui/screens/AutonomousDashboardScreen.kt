package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.autonomy.AutonomyMode
import com.example.core.autonomy.AutonomyPolicyConfig
import com.example.core.health.NetworkStatus
import com.example.core.health.ResourceMode
import com.example.core.health.ResourceSnapshot
import com.example.core.health.SystemHealthReport
import com.example.data.local.entity.AutonomousTaskEntity
import com.example.data.local.entity.AutonomousTaskPriority
import com.example.data.local.entity.AutonomousTaskStatus
import com.example.data.local.entity.AutonomousTaskType
import com.example.data.local.entity.HealthEventEntity
import com.example.data.local.entity.HealthSeverity
import com.example.data.local.entity.KnowledgeStatus
import com.example.data.local.entity.KnowledgeVersionEntity
import com.example.data.local.entity.ScheduleTriggerType
import com.example.data.local.entity.ScheduledTaskEntity
import com.example.data.local.entity.WebResearchRecordEntity
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCardBgHover
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDarkNavy
import com.example.ui.theme.JarvisDarkVoid
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DashboardSection(val title: String, val icon: ImageVector) {
    TASKS("TASKS", Icons.Default.Terminal),
    SCHEDULER("SCHEDULER", Icons.Default.Alarm),
    RESEARCH("RESEARCH", Icons.Default.Search),
    KNOWLEDGE("KNOWLEDGE", Icons.Default.MenuBook),
    HEALTH("HEALTH", Icons.Default.HealthAndSafety),
    POLICY("POLICY", Icons.Default.Security)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutonomousDashboardScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val autonomyMode by viewModel.autonomyMode.collectAsState()
    val policyConfig by viewModel.autonomyPolicyConfig.collectAsState()
    val isEmergencyStopActive by viewModel.isEmergencyStopActive.collectAsState()
    val lastStopReason by viewModel.lastEmergencyStopReason.collectAsState()

    val tasks by viewModel.allAutonomousTasks.collectAsState()
    val runningTask by viewModel.activeRunningAutonomousTask.collectAsState()
    val scheduledTasks by viewModel.allScheduledTasks.collectAsState()
    val knowledgeVersions by viewModel.allKnowledgeVersions.collectAsState()
    val researchRecords by viewModel.allWebResearchRecords.collectAsState()
    val healthEvents by viewModel.allHealthEvents.collectAsState()
    val healthReport by viewModel.systemHealthReport.collectAsState()
    val resourceSnapshot by viewModel.resourceSnapshot.collectAsState()

    var selectedSection by remember { mutableStateOf(DashboardSection.TASKS) }
    var newTaskGoal by remember { mutableStateOf("") }
    var newResearchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisDarkVoid)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("autonomous_dashboard_screen")
    ) {
        // 1. MASTER EMERGENCY STOP BANNER
        MasterStopHeader(
            isStopActive = isEmergencyStopActive,
            stopReason = lastStopReason,
            onEmergencyStop = { viewModel.triggerEmergencyStop("Manual UI Emergency Button") },
            onResetStop = { viewModel.resetEmergencyStop() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. AUTONOMY MODE SELECTOR
        AutonomyModeSelector(
            currentMode = autonomyMode,
            onSelectMode = { viewModel.setAutonomyMode(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. SECTION SUB-TABS
        ScrollableTabRow(
            selectedTabIndex = selectedSection.ordinal,
            containerColor = JarvisDarkNavy,
            contentColor = JarvisCyan,
            edgePadding = 4.dp,
            divider = {},
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp))
        ) {
            DashboardSection.values().forEach { section ->
                val selected = selectedSection == section
                Tab(
                    selected = selected,
                    onClick = { selectedSection = section },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.title,
                                modifier = Modifier.size(16.dp),
                                tint = if (selected) JarvisCyan else JarvisTextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = section.title,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) JarvisTextPrimary else JarvisTextSecondary
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. SECTION CONTENT
        when (selectedSection) {
            DashboardSection.TASKS -> {
                TasksSection(
                    tasks = tasks,
                    runningTask = runningTask,
                    newGoal = newTaskGoal,
                    onGoalChange = { newTaskGoal = it },
                    onSubmitGoal = {
                        if (newTaskGoal.isNotBlank()) {
                            viewModel.submitAutonomousGoal(newTaskGoal)
                            newTaskGoal = ""
                        }
                    },
                    onCancelTask = { viewModel.cancelAutonomousTask(it) },
                    onDeleteTask = { viewModel.deleteAutonomousTask(it) },
                    onClearAll = { viewModel.clearAllAutonomousTasks() }
                )
            }
            DashboardSection.SCHEDULER -> {
                SchedulerSection(
                    scheduledTasks = scheduledTasks,
                    onToggleEnabled = { id, enabled -> viewModel.toggleScheduledTask(id, enabled) },
                    onDelete = { viewModel.deleteScheduledTask(it) },
                    onAddScheduledTask = { title, instruction, triggerType, time, interval ->
                        viewModel.scheduleTask(title, instruction, triggerType, time, interval)
                    }
                )
            }
            DashboardSection.RESEARCH -> {
                ResearchSection(
                    researchRecords = researchRecords,
                    query = newResearchQuery,
                    onQueryChange = { newResearchQuery = it },
                    onTriggerResearch = {
                        if (newResearchQuery.isNotBlank()) {
                            viewModel.triggerWebResearch(newResearchQuery)
                            newResearchQuery = ""
                        }
                    }
                )
            }
            DashboardSection.KNOWLEDGE -> {
                KnowledgeVersioningSection(
                    versions = knowledgeVersions,
                    onApprove = { viewModel.approveKnowledgeUpdate(it) },
                    onRollback = { key, version -> viewModel.rollbackKnowledge(key, version) },
                    onTriggerMaintenance = { viewModel.triggerMemoryMaintenance() }
                )
            }
            DashboardSection.HEALTH -> {
                HealthSection(
                    healthReport = healthReport,
                    resourceSnapshot = resourceSnapshot,
                    healthEvents = healthEvents,
                    onPerformCheck = { viewModel.performSystemHealthCheck() },
                    onAttemptRecovery = { viewModel.attemptSelfRecovery(it) },
                    onSetResourceMode = { viewModel.setResourceMode(it) }
                )
            }
            DashboardSection.POLICY -> {
                PolicySection(
                    config = policyConfig,
                    onUpdateConfig = { viewModel.updateAutonomyPolicy(it) }
                )
            }
        }
    }
}

@Composable
private fun MasterStopHeader(
    isStopActive: Boolean,
    stopReason: String?,
    onEmergencyStop: () -> Unit,
    onResetStop: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    2.dp,
                    if (isStopActive) JarvisRed.copy(alpha = glowAlpha) else JarvisBorder
                ),
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isStopActive) JarvisRed.copy(alpha = 0.15f) else JarvisCardBg
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isStopActive) JarvisRed else JarvisEmerald)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isStopActive) "⚠️ MASTER STOP ACTIVE" else "AUTONOMOUS SYSTEM ONLINE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStopActive) JarvisRed else JarvisEmerald
                    )
                    Text(
                        text = if (isStopActive) (stopReason ?: "All autonomous tasks halted") else "Active policy boundaries strictly enforced",
                        fontSize = 11.sp,
                        color = JarvisTextSecondary
                    )
                }
            }

            if (isStopActive) {
                Button(
                    onClick = onResetStop,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("reset_stop_button")
                ) {
                    Text("RESUME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisDarkVoid)
                }
            } else {
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("emergency_stop_button")
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("MASTER STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AutonomyModeSelector(
    currentMode: AutonomyMode,
    onSelectMode: (AutonomyMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AutonomyMode.values().forEach { mode ->
            val isSelected = currentMode == mode
            val color = when (mode) {
                AutonomyMode.MANUAL -> JarvisAmber
                AutonomyMode.ASSISTED -> JarvisCyan
                AutonomyMode.AUTONOMOUS -> JarvisViolet
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMode(mode) }
                    .border(
                        BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) color else JarvisBorder),
                        RoundedCornerShape(10.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) color.copy(alpha = 0.15f) else JarvisCardBg
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) color else JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (mode) {
                            AutonomyMode.MANUAL -> "Direct Only"
                            AutonomyMode.ASSISTED -> "Confirm Sensitive"
                            AutonomyMode.AUTONOMOUS -> "Self-Governed"
                        },
                        fontSize = 9.sp,
                        color = JarvisTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun TasksSection(
    tasks: List<AutonomousTaskEntity>,
    runningTask: AutonomousTaskEntity?,
    newGoal: String,
    onGoalChange: (String) -> Unit,
    onSubmitGoal: () -> Unit,
    onCancelTask: (Long) -> Unit,
    onDeleteTask: (AutonomousTaskEntity) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Goal submission bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newGoal,
                onValueChange = onGoalChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("autonomous_goal_input"),
                placeholder = { Text("Submit autonomous task (e.g., 'Summarize notes')", fontSize = 12.sp, color = JarvisTextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisBorder,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSubmitGoal,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_goal_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisDarkVoid)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Running Task Live Status
        if (runningTask != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, JarvisCyan), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBgHover),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RUNNING TASK #${runningTask.id}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisCyan)
                        }
                        IconButton(
                            onClick = { onCancelTask(runningTask.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = "Cancel", tint = JarvisRed, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(runningTask.goal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JarvisTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = JarvisCyan,
                        trackColor = JarvisBorder
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Header with Clear Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TASK QUEUE (${tasks.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
            if (tasks.isNotEmpty()) {
                Text(
                    text = "Clear History",
                    fontSize = 11.sp,
                    color = JarvisTextMuted,
                    modifier = Modifier.clickable { onClearAll() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No autonomous tasks in queue.", fontSize = 12.sp, color = JarvisTextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onCancel = { onCancelTask(task.id) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: AutonomousTaskEntity,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (task.status) {
        AutonomousTaskStatus.COMPLETED -> JarvisEmerald
        AutonomousTaskStatus.RUNNING -> JarvisCyan
        AutonomousTaskStatus.QUEUED -> JarvisBlue
        AutonomousTaskStatus.WAITING, AutonomousTaskStatus.WAITING_FOR_USER -> JarvisAmber
        AutonomousTaskStatus.FAILED, AutonomousTaskStatus.BLOCKED, AutonomousTaskStatus.CANCELLED -> JarvisRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = task.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[${task.priority.name}]",
                        fontSize = 9.sp,
                        color = JarvisTextMuted
                    )
                }

                Row {
                    if (task.status == AutonomousTaskStatus.RUNNING || task.status == AutonomousTaskStatus.QUEUED) {
                        IconButton(onClick = onCancel, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "Cancel", tint = JarvisAmber, modifier = Modifier.size(14.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JarvisTextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(task.goal, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = JarvisTextPrimary)

            if (!task.resultSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.resultSummary,
                    fontSize = 11.sp,
                    color = JarvisTextSecondary
                )
            }

            if (!task.failureReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${task.failureReason}",
                    fontSize = 10.sp,
                    color = JarvisRed
                )
            }

            if (!task.blockingReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Blocked: ${task.blockingReason}",
                    fontSize = 10.sp,
                    color = JarvisAmber
                )
            }
        }
    }
}

@Composable
private fun SchedulerSection(
    scheduledTasks: List<ScheduledTaskEntity>,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onDelete: (ScheduledTaskEntity) -> Unit,
    onAddScheduledTask: (String, String, ScheduleTriggerType, Long, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf(ScheduleTriggerType.RECURRING) }
    var interval by remember { mutableStateOf("DAILY") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ADD SCHEDULED TASK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Task Title (e.g., Morning Health Check)", fontSize = 11.sp, color = JarvisTextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        placeholder = { Text("Task Instruction for Agent", fontSize = 11.sp, color = JarvisTextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("HOURLY", "DAILY", "WEEKLY").forEach { opt ->
                                val sel = interval == opt
                                Text(
                                    text = opt,
                                    fontSize = 10.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) JarvisCyan else JarvisTextMuted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) JarvisCyan.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clickable { interval = opt }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank() && instruction.isNotBlank()) {
                                    onAddScheduledTask(
                                        title,
                                        instruction,
                                        triggerType,
                                        System.currentTimeMillis() + 60_000L,
                                        interval
                                    )
                                    title = ""
                                    instruction = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisDarkVoid)
                        }
                    }
                }
            }
        }

        item {
            Text("ACTIVE SCHEDULES (${scheduledTasks.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
        }

        items(scheduledTasks, key = { it.id }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JarvisTextPrimary)
                        Text(item.instruction, fontSize = 11.sp, color = JarvisTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Trigger: ${item.triggerType.name} (${item.cronOrInterval})",
                            fontSize = 10.sp,
                            color = JarvisCyan
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = item.isEnabled,
                            onCheckedChange = { onToggleEnabled(item.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisCyan,
                                checkedTrackColor = JarvisDarkNavy
                            )
                        )
                        IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JarvisTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchSection(
    researchRecords: List<WebResearchRecordEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onTriggerResearch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Research topic (e.g. 'Quantum Computing milestones')", fontSize = 12.sp, color = JarvisTextMuted) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("research_topic_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisBorder,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onTriggerResearch,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("trigger_research_button")
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = JarvisDarkVoid)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("VERIFIED RESEARCH REPORTS (${researchRecords.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(researchRecords, key = { it.id }) { report ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(report.query, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = JarvisCyan)
                            Text(
                                text = "Confidence: ${(report.confidence * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = JarvisEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(report.synthesizedSummary, fontSize = 11.sp, color = JarvisTextPrimary)

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sources checked: ${report.sourcesCount}", fontSize = 10.sp, color = JarvisTextSecondary)
                            if (report.storedAsKnowledge) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("• Added to RAG Knowledge", fontSize = 10.sp, color = JarvisViolet)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeVersioningSection(
    versions: List<KnowledgeVersionEntity>,
    onApprove: (Long) -> Unit,
    onRollback: (String, Int) -> Unit,
    onTriggerMaintenance: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("KNOWLEDGE VERSIONS (${versions.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
            Button(
                onClick = onTriggerMaintenance,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisDarkNavy),
                border = BorderStroke(1.dp, JarvisCyan),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("OPTIMIZE DB", fontSize = 10.sp, color = JarvisCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(versions, key = { it.id }) { ver ->
                val isPending = ver.status == KnowledgeStatus.PENDING_APPROVAL
                val statusColor = when (ver.status) {
                    KnowledgeStatus.ACTIVE -> JarvisEmerald
                    KnowledgeStatus.PENDING_APPROVAL -> JarvisAmber
                    KnowledgeStatus.ARCHIVED, KnowledgeStatus.SUPERSEDED -> JarvisTextMuted
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            BorderStroke(1.dp, if (isPending) JarvisAmber else JarvisBorder),
                            RoundedCornerShape(10.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${ver.topic} (v${ver.version})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisTextPrimary)
                            Text(ver.status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(ver.summary, fontSize = 11.sp, color = JarvisTextSecondary)

                        if (isPending) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { onApprove(ver.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("APPROVE UPDATE", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthSection(
    healthReport: SystemHealthReport,
    resourceSnapshot: ResourceSnapshot,
    healthEvents: List<HealthEventEntity>,
    onPerformCheck: () -> Unit,
    onAttemptRecovery: (String) -> Unit,
    onSetResourceMode: (ResourceMode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SYSTEM DIAGNOSTICS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisCyan)
                        IconButton(onClick = onPerformCheck, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = JarvisCyan, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiagnosticMetricItem("RAM", "${resourceSnapshot.ramUsedMb}MB / ${resourceSnapshot.ramAvailableMb + resourceSnapshot.ramUsedMb}MB", modifier = Modifier.weight(1f))
                        DiagnosticMetricItem("BATTERY", "${resourceSnapshot.batteryPercent}% (${if (resourceSnapshot.isCharging) "Charging" else "Discharging"})", modifier = Modifier.weight(1f))
                        DiagnosticMetricItem("NETWORK", healthReport.networkStatus.name, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiagnosticMetricItem("GEMINI", if (healthReport.isGeminiConfigured) "ONLINE" else "OFFLINE", modifier = Modifier.weight(1f))
                        DiagnosticMetricItem("LOCAL SLM", if (healthReport.isLocalModelLoaded) "READY" else "UNLOADED", modifier = Modifier.weight(1f))
                        DiagnosticMetricItem("ACCESSIBILITY", if (healthReport.isAccessibilityActive) "ACTIVE" else "DISABLED", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("RESOURCE POWER MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResourceMode.values().forEach { mode ->
                            val sel = resourceSnapshot.mode == mode
                            Text(
                                text = mode.label,
                                fontSize = 10.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) JarvisCyan else JarvisTextMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) JarvisCyan.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(BorderStroke(1.dp, if (sel) JarvisCyan else JarvisBorder), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .clickable { onSetResourceMode(mode) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("SAFE SELF-RECOVERY ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("GEMINI", "LOCAL_SLM", "ACCESSIBILITY", "DATABASE").forEach { comp ->
                            OutlinedButton(
                                onClick = { onAttemptRecovery(comp) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, JarvisBorder)
                            ) {
                                Text("RECOVER $comp", fontSize = 9.sp, color = JarvisTextPrimary)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("HEALTH & RECOVERY LOG (${healthEvents.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
        }

        items(healthEvents, key = { it.id }) { event ->
            val sevColor = when (event.severity) {
                HealthSeverity.INFO -> JarvisCyan
                HealthSeverity.WARNING -> JarvisAmber
                HealthSeverity.ERROR, HealthSeverity.CRITICAL -> JarvisRed
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(event.component, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = sevColor)
                        Text(event.severity.name, fontSize = 9.sp, color = sevColor)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(event.description, fontSize = 10.sp, color = JarvisTextPrimary)
                    if (event.recoveryAttempted) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Recovery: ${event.recoveryActionTaken ?: (if (event.recoverySuccessful) "Success" else "Failed")}",
                            fontSize = 9.sp,
                            color = if (event.recoverySuccessful) JarvisEmerald else JarvisAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(6.dp)),
        colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(title, fontSize = 9.sp, color = JarvisTextMuted)
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = JarvisTextPrimary)
        }
    }
}

@Composable
private fun PolicySection(
    config: AutonomyPolicyConfig,
    onUpdateConfig: (AutonomyPolicyConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AUTONOMY BOUNDARIES & LIMITS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisCyan)
                    Spacer(modifier = Modifier.height(8.dp))

                    PolicySwitchRow(
                        title = "Auto-Approve High-Confidence Knowledge",
                        subtitle = "Automatically update memory if web source credibility > 85%",
                        checked = config.autoUpdateKnowledgeOnHighConfidence,
                        onCheckedChange = { onUpdateConfig(config.copy(autoUpdateKnowledgeOnHighConfidence = it)) }
                    )

                    Divider(color = JarvisBorder, modifier = Modifier.padding(vertical = 8.dp))

                    Text("Limits", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Max Task Duration: ${config.maxTaskDurationSec}s", fontSize = 10.sp, color = JarvisTextPrimary)
                    Text("• Max Actions Per Task: ${config.maxActionsPerTask}", fontSize = 10.sp, color = JarvisTextPrimary)
                    Text("• Max Retries: ${config.maxRetries}", fontSize = 10.sp, color = JarvisTextPrimary)
                    Text("• Min Battery for Autonomy: ${config.minBatteryLevelForAutonomy}%", fontSize = 10.sp, color = JarvisTextPrimary)
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, JarvisBorder), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("APPROVED AUTONOMOUS TASKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisEmerald)
                    Spacer(modifier = Modifier.height(6.dp))
                    config.allowedTasks.forEach { task ->
                        Text("✓ $task", fontSize = 10.sp, color = JarvisTextSecondary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("STRICTLY BLOCKED ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JarvisRed)
                    Spacer(modifier = Modifier.height(6.dp))
                    config.blockedTasks.forEach { task ->
                        Text("✗ $task", fontSize = 10.sp, color = JarvisRed.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = JarvisTextPrimary)
            Text(subtitle, fontSize = 9.sp, color = JarvisTextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisCyan,
                checkedTrackColor = JarvisDarkNavy
            )
        )
    }
}
