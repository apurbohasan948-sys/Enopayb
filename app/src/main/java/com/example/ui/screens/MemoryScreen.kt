package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.GeminiTeacherSessionEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.TrainingExampleEntity
import com.example.data.local.entity.UserCorrectionEntity
import com.example.ui.JarvisViewModel
import com.example.ui.components.HologramCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDarkNavy
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

enum class MemorySubTab(val title: String) {
    FACTS("FACTS"),
    EXPERIENCES("EXPERIENCES"),
    CORRECTIONS("CORRECTIONS"),
    TEACHER("TEACHER"),
    TRAINING_DATA("TRAINING DATA & EXPORT")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.allMemories.collectAsState()
    val experiences by viewModel.allExperiences.collectAsState()
    val corrections by viewModel.allUserCorrections.collectAsState()
    val trainingExamples by viewModel.allTrainingExamples.collectAsState()
    val teacherSessions by viewModel.allTeacherSessions.collectAsState()
    val metrics by viewModel.learningMetrics.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshLearningMetrics()
    }

    var currentSubTab by remember { mutableStateOf(MemorySubTab.FACTS) }
    var selectedCategoryFilter by remember { mutableStateOf<MemoryCategory?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }
    var exportDialogTitle by remember { mutableStateOf("EXPORT BRAIN JSON") }

    val filteredMemories = if (selectedCategoryFilter == null) {
        memories
    } else {
        memories.filter { it.category == selectedCategoryFilter }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === Title & Main Actions Header ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JARVIS LEARNING & BRAIN",
                    color = JarvisCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${memories.size} Facts • ${experiences.size} Experiences • ${corrections.size} Corrections",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }

            Row {
                IconButton(
                    onClick = {
                        viewModel.exportFullBrainJson { json ->
                            exportDialogTitle = "JARVIS BRAIN JSON (v3.0.0)"
                            exportedJsonText = json
                            showExportDialog = true
                        }
                    },
                    modifier = Modifier.size(36.dp).testTag("btn_export_brain")
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Export Brain", tint = JarvisCyan)
                }
                IconButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.size(36.dp).testTag("btn_import_brain")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Import Brain", tint = JarvisBlue)
                }
                if (currentSubTab == MemorySubTab.FACTS) {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(36.dp).testTag("btn_add_memory")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = JarvisEmerald)
                    }
                }
            }
        }

        // === Sub-Tabs Selector ===
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(MemorySubTab.values()) { tab ->
                val selected = currentSubTab == tab
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) JarvisCyan.copy(alpha = 0.2f) else JarvisCardBg,
                    border = BorderStroke(1.dp, if (selected) JarvisCyan else JarvisBorder),
                    modifier = Modifier
                        .clickable { currentSubTab = tab }
                        .testTag("memory_subtab_${tab.name.lowercase()}")
                ) {
                    Text(
                        text = tab.title,
                        color = if (selected) JarvisCyan else JarvisTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // === Tab Content ===
        when (currentSubTab) {
            MemorySubTab.FACTS -> {
                FactsSubTabContent(
                    memories = filteredMemories,
                    allMemoriesCount = memories.size,
                    selectedCategory = selectedCategoryFilter,
                    onCategorySelect = { selectedCategoryFilter = it },
                    onDeleteMemory = { viewModel.deleteMemory(it) }
                )
            }
            MemorySubTab.EXPERIENCES -> {
                ExperiencesSubTabContent(
                    experiences = experiences,
                    onClearAll = { viewModel.clearAllExperiences() }
                )
            }
            MemorySubTab.CORRECTIONS -> {
                CorrectionsSubTabContent(
                    corrections = corrections,
                    onClearAll = { viewModel.clearAllUserCorrections() }
                )
            }
            MemorySubTab.TEACHER -> {
                TeacherSessionsSubTabContent(
                    sessions = teacherSessions,
                    onClearAll = { viewModel.clearTeacherSessions() }
                )
            }
            MemorySubTab.TRAINING_DATA -> {
                TrainingDataSubTabContent(
                    metrics = metrics,
                    trainingExamples = trainingExamples,
                    preferences = viewModel.preferences,
                    onExportDataset = {
                        viewModel.exportTrainingDatasetJson { json ->
                            exportDialogTitle = "DISTILLATION DATASET (JSONL)"
                            exportedJsonText = json
                            showExportDialog = true
                        }
                    },
                    onClearDataset = { viewModel.clearTrainingDataset() },
                    onToggleLearning = { viewModel.setLearningEnabled(it) },
                    onToggleExperiences = { viewModel.setStoreExperiencesEnabled(it) },
                    onToggleSkills = { viewModel.setAutoSkillCreationEnabled(it) },
                    onToggleTraining = { viewModel.setStoreTrainingDataEnabled(it) },
                    onTogglePrivacy = { viewModel.setPrivacyFilteringEnabled(it) },
                    onToggleTeacher = { viewModel.setGeminiTeacherAllowed(it) }
                )
            }
        }
    }

    // === Dialogs ===
    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { cat, key, value, conf ->
                viewModel.recordFactMemory(cat, key, value, conf)
                showAddDialog = false
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(exportDialogTitle, color = JarvisCyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            },
            text = {
                OutlinedTextField(
                    value = exportedJsonText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = JarvisDarkNavy
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text("IMPORT BRAIN DATA", color = JarvisCyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste a valid JARVIS Brain JSON export to restore memories, skills, and experiences:", color = JarvisTextSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("{\"version\": \"3.0.0\", ...}", color = JarvisTextMuted, fontSize = 10.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonInput.isNotBlank()) {
                            viewModel.importBrain(importJsonInput) { count ->
                                showImportDialog = false
                                importJsonInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Import", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showImportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = JarvisTextMuted)
                }
            },
            containerColor = JarvisDarkNavy
        )
    }
}

@Composable
private fun FactsSubTabContent(
    memories: List<MemoryEntity>,
    allMemoriesCount: Int,
    selectedCategory: MemoryCategory?,
    onCategorySelect: (MemoryCategory?) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                CategoryFilterChip(
                    label = "ALL ($allMemoriesCount)",
                    selected = selectedCategory == null,
                    onClick = { onCategorySelect(null) }
                )
            }
            items(MemoryCategory.values()) { cat ->
                CategoryFilterChip(
                    label = cat.name,
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelect(cat) }
                )
            }
        }

        if (memories.isEmpty()) {
            HologramCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "No memories stored in this category.", color = JarvisTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories) { memory ->
                    MemoryCardItem(memory = memory, onDelete = { onDeleteMemory(memory) })
                }
            }
        }
    }
}

@Composable
private fun ExperiencesSubTabContent(
    experiences: List<ExperienceEntity>,
    onClearAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val successCount = experiences.count { it.isSuccess }
            val rate = if (experiences.isNotEmpty()) (successCount * 100) / experiences.size else 100
            Text(
                text = "${experiences.size} Recorded Experiences • $rate% Success Rate",
                color = JarvisTextSecondary,
                fontSize = 11.sp
            )
            if (experiences.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = JarvisRed.copy(alpha = 0.1f),
                    border = BorderStroke(0.8.dp, JarvisRed.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onClearAll() }
                ) {
                    Text("Clear All", color = JarvisRed, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        if (experiences.isEmpty()) {
            HologramCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No task experiences recorded yet.", color = JarvisTextSecondary, fontSize = 12.sp)
                    Text("Execute voice or agent goals to automatically capture task experiences.", color = JarvisTextMuted, fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(experiences) { exp ->
                    ExperienceCardItem(experience = exp)
                }
            }
        }
    }
}

@Composable
private fun CorrectionsSubTabContent(
    corrections: List<UserCorrectionEntity>,
    onClearAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${corrections.size} User Intent & Workflow Corrections",
                color = JarvisTextSecondary,
                fontSize = 11.sp
            )
            if (corrections.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = JarvisRed.copy(alpha = 0.1f),
                    border = BorderStroke(0.8.dp, JarvisRed.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onClearAll() }
                ) {
                    Text("Clear All", color = JarvisRed, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        if (corrections.isEmpty()) {
            HologramCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No user corrections recorded yet.", color = JarvisTextSecondary, fontSize = 12.sp)
                    Text("When you correct JARVIS during an action, it records the exact intent to prevent repeating mistakes.", color = JarvisTextMuted, fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(corrections) { corr ->
                    CorrectionCardItem(correction = corr)
                }
            }
        }
    }
}

@Composable
private fun TeacherSessionsSubTabContent(
    sessions: List<GeminiTeacherSessionEntity>,
    onClearAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${sessions.size} Gemini Teacher Structured Sessions",
                color = JarvisTextSecondary,
                fontSize = 11.sp
            )
            if (sessions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = JarvisRed.copy(alpha = 0.1f),
                    border = BorderStroke(0.8.dp, JarvisRed.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onClearAll() }
                ) {
                    Text("Clear All", color = JarvisRed, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        if (sessions.isEmpty()) {
            HologramCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Gemini Teacher sessions recorded.", color = JarvisTextSecondary, fontSize = 12.sp)
                    Text("Teacher sessions occur when local models encounter low-confidence or novel multi-step tasks.", color = JarvisTextMuted, fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    TeacherSessionCardItem(session = session)
                }
            }
        }
    }
}

@Composable
private fun TrainingDataSubTabContent(
    metrics: com.example.core.learning.LearningMetrics,
    trainingExamples: List<TrainingExampleEntity>,
    preferences: com.example.data.local.preference.JarvisPreferences,
    onExportDataset: () -> Unit,
    onClearDataset: () -> Unit,
    onToggleLearning: (Boolean) -> Unit,
    onToggleExperiences: (Boolean) -> Unit,
    onToggleSkills: (Boolean) -> Unit,
    onToggleTraining: (Boolean) -> Unit,
    onTogglePrivacy: (Boolean) -> Unit,
    onToggleTeacher: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Readiness Banner
        item {
            HologramCard(borderColor = if (metrics.isReadyForTraining) JarvisEmerald else JarvisCyan) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("LOCAL BRAIN TRAINING READINESS", color = JarvisCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (metrics.isReadyForTraining) "READY FOR DISTILLATION" else "ACCUMULATING EXPERIENCES",
                                color = if (metrics.isReadyForTraining) JarvisEmerald else JarvisAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisDarkNavy,
                            border = BorderStroke(1.dp, if (metrics.isReadyForTraining) JarvisEmerald else JarvisBorder)
                        ) {
                            Text(
                                text = "${metrics.trainingReadinessScore}/100",
                                color = if (metrics.isReadyForTraining) JarvisEmerald else JarvisCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "JARVIS uses verified local experiences to curate datasets for offline SLM fine-tuning. Actual model training occurs via external fine-tuning scripts or cloud distillation.",
                        color = JarvisTextSecondary,
                        fontSize = 10.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportDataset,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export JSONL Dataset", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        if (trainingExamples.isNotEmpty()) {
                            Button(
                                onClick = onClearDataset,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisRed.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, JarvisRed)
                            ) {
                                Text("Clear", color = JarvisRed, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Learning Policy Controls
        item {
            HologramCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LEARNING & PRIVACY POLICIES", color = JarvisCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                    PolicySwitchRow("Enable Autonomous Learning", preferences.isLearningEnabled) { onToggleLearning(it) }
                    PolicySwitchRow("Store Verified Task Experiences", preferences.isStoreExperiencesEnabled) { onToggleExperiences(it) }
                    PolicySwitchRow("Auto-Synthesize Skills from Successes", preferences.isAutoSkillCreationEnabled) { onToggleSkills(it) }
                    PolicySwitchRow("Curate SLM Training Dataset", preferences.isStoreTrainingDataEnabled) { onToggleTraining(it) }
                    PolicySwitchRow("Strict Privacy Shield (Filter Passwords/Tokens)", preferences.isPrivacyFilteringEnabled) { onTogglePrivacy(it) }
                    PolicySwitchRow("Allow Gemini Teacher Escalation", preferences.isGeminiTeacherAllowed) { onToggleTeacher(it) }
                }
            }
        }

        // Curated Training Examples
        item {
            Text(
                text = "CURATED EXAMPLES (${trainingExamples.size})",
                color = JarvisTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        items(trainingExamples) { example ->
            HologramCard {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = example.inputInstruction,
                            color = JarvisCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Score: ${(example.qualityScore * 100).toInt()}%",
                            color = JarvisEmerald,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(text = "Context: ${example.contextSummary}", color = JarvisTextMuted, fontSize = 9.sp)
                    Text(text = "Output: ${example.successfulPlanJson.take(120)}...", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun PolicySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var state by remember(checked) { mutableStateOf(checked) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = JarvisTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = state,
            onCheckedChange = {
                state = it
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisCyan,
                checkedTrackColor = JarvisCyan.copy(alpha = 0.3f),
                uncheckedThumbColor = JarvisTextMuted,
                uncheckedTrackColor = JarvisCardBg
            )
        )
    }
}

@Composable
private fun MemoryCardItem(memory: MemoryEntity, onDelete: () -> Unit) {
    HologramCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category = memory.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = memory.key,
                        color = JarvisTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JarvisRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }

            Text(text = memory.value, color = Color.White, fontSize = 12.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Source: ${memory.source}", color = JarvisTextMuted, fontSize = 9.sp)
                Text(text = "Conf: ${(memory.confidence * 100).toInt()}% • Uses: ${memory.usageCount}", color = JarvisCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ExperienceCardItem(experience: ExperienceEntity) {
    HologramCard(borderColor = if (experience.isSuccess) JarvisBorder else JarvisRed.copy(alpha = 0.4f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = experience.goal,
                    color = JarvisCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                StatusPill(
                    label = if (experience.isSuccess) "VERIFIED" else "FAILED",
                    icon = if (experience.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    color = if (experience.isSuccess) JarvisEmerald else JarvisRed
                )
            }

            Text(text = "App: ${experience.appPackage}", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(text = "Actions: ${experience.actionsTakenJson.take(140)}...", color = JarvisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

            if (experience.failedStrategy != null) {
                Text(text = "Failure Reason: ${experience.failedStrategy}", color = JarvisRed, fontSize = 10.sp)
            }
            if (experience.recoveryStrategy != null) {
                Text(text = "Recovery: ${experience.recoveryStrategy}", color = JarvisEmerald, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CorrectionCardItem(correction: UserCorrectionEntity) {
    HologramCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "Goal: ${correction.userGoal}", color = JarvisCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = "Previous Assumption: ${correction.previousAssumption}", color = JarvisRed.copy(alpha = 0.8f), fontSize = 10.sp)
            Text(text = "User Correction: ${correction.userCorrection}", color = JarvisEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "Target Resolved: ${correction.actualTarget} in ${correction.appPackage}", color = JarvisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun TeacherSessionCardItem(session: GeminiTeacherSessionEntity) {
    HologramCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = session.userGoal, color = JarvisViolet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = if (session.executionSuccessful) "COMPLETED" else "FAILED", color = if (session.executionSuccessful) JarvisEmerald else JarvisRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Text(text = "Escalation Reason: ${session.lowConfidenceReason}", color = JarvisTextSecondary, fontSize = 10.sp)
            Text(text = "Generated Plan: ${session.structuredPlanJson.take(120)}...", color = JarvisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun CategoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) JarvisCyan.copy(alpha = 0.2f) else JarvisCardBg,
        border = BorderStroke(1.dp, if (selected) JarvisCyan else JarvisBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) JarvisCyan else JarvisTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun CategoryBadge(category: MemoryCategory) {
    val (color, label) = when (category) {
        MemoryCategory.USER_PROFILE -> JarvisCyan to "USER"
        MemoryCategory.USER_PREFERENCE -> JarvisEmerald to "PREF"
        MemoryCategory.PERSONAL_CONTEXT -> JarvisCyan to "CONTEXT"
        MemoryCategory.TASK_HISTORY -> JarvisTextSecondary to "HISTORY"
        MemoryCategory.EXPERIENCE -> JarvisViolet to "EXP"
        MemoryCategory.SKILL -> JarvisEmerald to "SKILL"
        MemoryCategory.KNOWLEDGE -> JarvisBlue to "RAG"
        MemoryCategory.APP_PATTERN -> JarvisViolet to "APP"
        MemoryCategory.SCREEN_PATTERN -> JarvisBlue to "SCREEN"
        MemoryCategory.USER_CORRECTION -> JarvisAmber to "CORRECT"
        MemoryCategory.IMPORTANT_FACT -> JarvisCyan to "FACT"
        MemoryCategory.CONVERSATION_SUMMARY -> JarvisTextMuted to "CONV"
        MemoryCategory.SECURITY_EVENT -> JarvisRed to "SECURITY"
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onAdd: (MemoryCategory, String, String, Float) -> Unit
) {
    var category by remember { mutableStateOf(MemoryCategory.IMPORTANT_FACT) }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("STORE LONG-TERM MEMORY", color = JarvisCyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = JarvisTextSecondary, fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        MemoryCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Memory Key", color = JarvisTextSecondary, fontSize = 11.sp) },
                    placeholder = { Text("e.g. user_favorite_music", color = JarvisTextMuted, fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder
                    )
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Memory Value", color = JarvisTextSecondary, fontSize = 11.sp) },
                    placeholder = { Text("e.g. Instrumental Lo-Fi", color = JarvisTextMuted, fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (key.isNotBlank() && value.isNotBlank()) {
                        onAdd(category, key, value, 1.0f)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
            ) {
                Text("Save Memory", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Cancel", color = JarvisTextMuted)
            }
        },
        containerColor = JarvisDarkNavy
    )
}
