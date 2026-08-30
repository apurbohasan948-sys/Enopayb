package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.knowledge.IngestionCandidate
import com.example.data.local.entity.AppKnowledgeEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.KnowledgeItemEntity
import com.example.data.local.entity.KnowledgeSourceEntity
import com.example.data.local.entity.KnowledgeSourceType
import com.example.data.local.entity.SourceStatus
import com.example.data.local.entity.ValidationStage
import com.example.ui.JarvisViewModel
import com.example.ui.components.HologramCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JarvisAmber
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

enum class BrainKnowledgeTab(val title: String) {
    VERIFIED_ITEMS("Knowledge"),
    SOURCES("Sources"),
    APP_BRAIN("App Brain"),
    RAG_SEARCH("Vector RAG"),
    STORAGE_BACKUP("Storage & Sync")
}

@Composable
fun RagKnowledgeScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(BrainKnowledgeTab.VERIFIED_ITEMS) }
    val knowledgeItems by viewModel.allKnowledgeItems.collectAsState()
    val knowledgeSources by viewModel.allKnowledgeSources.collectAsState()
    val appKnowledgeList by viewModel.allAppKnowledge.collectAsState()
    val knowledgeChunks by viewModel.allKnowledgeChunks.collectAsState()
    val brainStats by viewModel.brainStorageStats.collectAsState()
    val testResults by viewModel.ragTestResults.collectAsState()

    var showIngestDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var snapshotNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshBrainStorageStats()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // === Top Header ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LONG-TERM BRAIN ENGINE",
                        color = JarvisCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "${knowledgeItems.size} Verified Items | ${knowledgeSources.size} Sources | Multi-Tier Local RAG",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }

            Row {
                IconButton(
                    onClick = { showIngestDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ingest Knowledge", tint = JarvisCyan)
                }
            }
        }

        // === Navigation Tabs ===
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = JarvisCardBg,
            contentColor = JarvisCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                    color = JarvisCyan
                )
            }
        ) {
            BrainKnowledgeTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            color = if (selectedTab == tab) JarvisCyan else JarvisTextSecondary
                        )
                    }
                )
            }
        }

        // === Tab Body ===
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                BrainKnowledgeTab.VERIFIED_ITEMS -> VerifiedKnowledgeItemsView(
                    items = knowledgeItems,
                    onDelete = { viewModel.deleteKnowledgeItem(it) }
                )
                BrainKnowledgeTab.SOURCES -> KnowledgeSourcesView(
                    sources = knowledgeSources,
                    onFlag = { viewModel.flagKnowledgeSource(it) }
                )
                BrainKnowledgeTab.APP_BRAIN -> AppBrainKnowledgeView(
                    appKnowledgeList = appKnowledgeList
                )
                BrainKnowledgeTab.RAG_SEARCH -> RagVectorSearchView(
                    chunks = knowledgeChunks,
                    testResults = testResults,
                    onSearch = { viewModel.testRagQuery(it) },
                    onDeleteChunk = { viewModel.deleteKnowledgeChunk(it) }
                )
                BrainKnowledgeTab.STORAGE_BACKUP -> BrainStorageBackupView(
                    stats = brainStats,
                    onCompact = { viewModel.compactBrainStorage() },
                    onExport = {
                        viewModel.exportPhase14BrainSnapshot { res ->
                            snapshotNotice = "Exported: ${res.knowledgeCount} facts, ${res.skillCount} skills, ${res.memoryCount} memories (${res.sanitizedItemsCount} sanitized)"
                        }
                    },
                    onImportClick = { showImportDialog = true }
                )
            }
        }

        snapshotNotice?.let { notice ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = JarvisDarkNavy,
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = notice, color = JarvisTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(
                        text = "DISMISS",
                        color = JarvisCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { snapshotNotice = null }
                    )
                }
            }
        }
    }

    // === Ingest Knowledge Candidate Dialog ===
    if (showIngestDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        var sourceUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showIngestDialog = false },
            containerColor = JarvisCardBg,
            title = {
                Text(
                    text = "INGEST KNOWLEDGE CANDIDATE",
                    color = JarvisCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title / Topic", color = JarvisTextSecondary, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Verified Knowledge Content", color = JarvisTextSecondary, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        label = { Text("Source URL or Doc (optional)", color = JarvisTextSecondary, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma separated)", color = JarvisTextSecondary, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            viewModel.ingestNewKnowledge(
                                IngestionCandidate(
                                    title = title,
                                    content = content,
                                    sourceUrl = if (sourceUrl.isNotBlank()) sourceUrl else null,
                                    tags = tags,
                                    sourceType = KnowledgeSourceType.USER_PROVIDED
                                )
                            )
                            showIngestDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Validate & Ingest", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showIngestDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }

    // === Import Brain JSON Dialog ===
    if (showImportDialog) {
        var importJson by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = JarvisCardBg,
            title = {
                Text(
                    text = "IMPORT BRAIN SNAPSHOT",
                    color = JarvisCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste sanitized Brain JSON payload. Entities will pass through Security & Ingestion validation.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        label = { Text("Brain JSON", color = JarvisTextSecondary, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJson.isNotBlank()) {
                            viewModel.importPhase14BrainSnapshot(importJson) { res ->
                                snapshotNotice = res.message
                            }
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Import & Merge", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showImportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }
}

@Composable
fun VerifiedKnowledgeItemsView(
    items: List<KnowledgeItemEntity>,
    onDelete: (KnowledgeItemEntity) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No verified knowledge items yet.", color = JarvisTextMuted, fontSize = 12.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                HologramCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = JarvisCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Type: ${item.knowledgeType.name} | Used: ${item.usageCount} times",
                                color = JarvisTextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val stageColor = when (item.validationStage) {
                                ValidationStage.ACTIVE -> JarvisEmerald
                                ValidationStage.VERIFIED -> JarvisCyan
                                ValidationStage.UNCERTAIN -> JarvisAmber
                                else -> JarvisTextMuted
                            }
                            StatusPill(
                                label = "${item.validationStage} (${(item.confidence * 100).toInt()}%)",
                                color = stageColor
                            )
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JarvisRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.content, color = JarvisTextPrimary, fontSize = 11.sp, lineHeight = 15.sp)

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Trust: ${(item.trustScore * 100).toInt()}% | Sources: ${item.sourceCount}",
                            color = JarvisViolet,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Tags: ${item.tags}",
                            color = JarvisTextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KnowledgeSourcesView(
    sources: List<KnowledgeSourceEntity>,
    onFlag: (String) -> Unit
) {
    if (sources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No recorded knowledge sources.", color = JarvisTextMuted, fontSize = 12.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(sources) { src ->
                HologramCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Source, contentDescription = null, tint = JarvisViolet, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = src.title,
                                    color = JarvisTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Type: ${src.sourceType.name}",
                                    color = JarvisTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                label = "${(src.trustScore * 100).toInt()}% Trust",
                                color = if (src.trustScore >= 0.85f) JarvisEmerald else JarvisAmber
                            )
                            if (src.status == SourceStatus.ACTIVE) {
                                IconButton(
                                    onClick = { onFlag(src.sourceId) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Flag, contentDescription = "Flag Source", tint = JarvisAmber, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    src.sourceUrl?.let { url ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "URL: $url", color = JarvisCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hash: ${src.contentHash} | Status: ${src.status}",
                        color = JarvisTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AppBrainKnowledgeView(
    appKnowledgeList: List<AppKnowledgeEntity>
) {
    if (appKnowledgeList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No app knowledge recorded yet.", color = JarvisTextMuted, fontSize = 12.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(appKnowledgeList) { app ->
                HologramCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = app.appName,
                                color = JarvisCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "v${app.version}",
                            color = JarvisTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(text = app.packageName, color = JarvisTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(text = "Screens: ${app.knownScreensJson}", color = JarvisTextPrimary, fontSize = 10.sp)
                    Text(text = "Targets: ${app.semanticTargetsJson}", color = JarvisTextSecondary, fontSize = 10.sp)

                    if (app.recoveryStrategiesJson.length > 5) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recovery Strategies: ${app.recoveryStrategiesJson}",
                            color = JarvisAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RagVectorSearchView(
    chunks: List<KnowledgeChunkEntity>,
    testResults: List<Pair<KnowledgeChunkEntity, Float>>,
    onSearch: (String) -> Unit,
    onDeleteChunk: (KnowledgeChunkEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        HologramCard {
            Text(
                text = "ON-DEVICE VECTOR SIMILARITY QUERY",
                color = JarvisTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearch(it)
                    },
                    placeholder = { Text("Search offline indexed knowledge...", color = JarvisTextMuted, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { onSearch(searchQuery) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = JarvisCyan)
                }
            }

            if (testResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Top Vector Matches:",
                    color = JarvisEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    testResults.take(3).forEach { (chunk, score) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = JarvisDarkNavy,
                            border = BorderStroke(0.8.dp, JarvisEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = chunk.title, color = JarvisTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(text = chunk.content.take(70) + "...", color = JarvisTextSecondary, fontSize = 10.sp)
                                }
                                StatusPill(
                                    label = "${(score * 100).toInt()}% Match",
                                    color = JarvisEmerald
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(chunks) { chunk ->
                HologramCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = chunk.title, color = JarvisCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { onDeleteChunk(chunk) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JarvisRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(text = chunk.content, color = JarvisTextPrimary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun BrainStorageBackupView(
    stats: com.example.core.brain.BrainStorageStats?,
    onCompact: () -> Unit,
    onExport: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        HologramCard {
            Text(
                text = "BRAIN STORAGE METRICS",
                color = JarvisCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (stats != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Knowledge Items:", color = JarvisTextSecondary, fontSize = 11.sp)
                    Text(text = "${stats.knowledgeItemCount} (${stats.verifiedKnowledgeCount} verified)", color = JarvisTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Uncertain / Stale:", color = JarvisTextSecondary, fontSize = 11.sp)
                    Text(text = "${stats.uncertainKnowledgeCount} uncertain, ${stats.staleKnowledgeCount} stale", color = JarvisAmber, fontSize = 11.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Active Skills:", color = JarvisTextSecondary, fontSize = 11.sp)
                    Text(text = "${stats.activeSkillCount}", color = JarvisEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Memories:", color = JarvisTextSecondary, fontSize = 11.sp)
                    Text(text = "${stats.memoryCount}", color = JarvisTextPrimary, fontSize = 11.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Experiences:", color = JarvisTextSecondary, fontSize = 11.sp)
                    Text(text = "${stats.experienceCount}", color = JarvisTextPrimary, fontSize = 11.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Estimated Footprint:", color = JarvisTextSecondary, fontSize = 11.sp)
                    Text(text = "${stats.estimatedSizeBytes / 1024} KB", color = JarvisViolet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(text = "Loading metrics...", color = JarvisTextMuted, fontSize = 11.sp)
            }
        }

        HologramCard {
            Text(
                text = "BRAIN ACTIONS & BACKUP",
                color = JarvisTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onCompact,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisDarkNavy),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Compact Brain & Remove Duplicates", color = JarvisCyan, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, JarvisEmerald.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = JarvisEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Export Brain", color = JarvisEmerald, fontSize = 10.sp)
                }

                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, JarvisViolet.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = JarvisViolet, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Import Brain", color = JarvisViolet, fontSize = 10.sp)
                }
            }
        }
    }
}
