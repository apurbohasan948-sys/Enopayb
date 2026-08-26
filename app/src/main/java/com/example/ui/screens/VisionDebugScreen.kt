package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.vision.ScreenDiffResult
import com.example.core.vision.SemanticScreenModel
import com.example.core.vision.SemanticTarget
import com.example.core.vision.SemanticUIElement
import com.example.core.vision.VisualElement
import com.example.data.local.entity.VisualExperienceEntity
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisBorderGlow
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionDebugScreen(viewModel: JarvisViewModel) {
    val unifiedScreen by viewModel.latestUnifiedScreen.collectAsState()
    val semanticScreen by viewModel.latestSemanticScreen.collectAsState()
    val screenDiff by viewModel.latestScreenDiff.collectAsState()
    val matchedTarget by viewModel.latestMatchedTarget.collectAsState()
    val semanticStatus by viewModel.semanticActionStatus.collectAsState()
    val visualExperiences by viewModel.visualExperiences.collectAsState()
    val isCloudVisionEnabled by viewModel.isCloudVisionEnabled.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val diagnostics by viewModel.accessibilityDiagnostics.collectAsState()
    val lastDetectedElements by viewModel.lastDetectedElements.collectAsState()

    var testGoalInput by remember { mutableStateOf("Search for Tom and Jerry") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDarkVoid)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Engine Status Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vision_engine_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(JarvisCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Vision Engine",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "UNIVERSAL SCREEN UNDERSTANDING",
                                    color = JarvisCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Phase 10: Semantic UI + Visual Symbols + Screen Diff",
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Cloud / Local toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isCloudVisionEnabled) "CLOUD" else "LOCAL",
                                color = if (isCloudVisionEnabled) JarvisEmerald else JarvisAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = isCloudVisionEnabled,
                                onCheckedChange = { viewModel.toggleCloudVision(it) },
                                modifier = Modifier.testTag("cloud_vision_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisEmerald,
                                    checkedTrackColor = JarvisEmerald.copy(alpha = 0.3f),
                                    uncheckedThumbColor = JarvisAmber,
                                    uncheckedTrackColor = JarvisDarkVoid
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Telemetry Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VisionMetricBadge(
                            label = "ACTIVE APP",
                            value = semanticScreen?.packageName?.substringAfterLast(".") ?: unifiedScreen?.packageName?.substringAfterLast(".") ?: diagnostics.currentPackage.substringAfterLast("."),
                            color = JarvisCyan,
                            modifier = Modifier.weight(1f)
                        )
                        VisionMetricBadge(
                            label = "UI ELEMENTS",
                            value = "${semanticScreen?.elements?.size ?: unifiedScreen?.elements?.size ?: diagnostics.totalNodes}",
                            color = JarvisViolet,
                            modifier = Modifier.weight(1f)
                        )
                        VisionMetricBadge(
                            label = "SCREEN TYPE",
                            value = semanticScreen?.screenType?.name ?: "GENERAL",
                            color = JarvisEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (semanticScreen?.isDialogActive == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = JarvisAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = JarvisAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "POPUP DIALOG DETECTED (${semanticScreen?.dialogType ?: "Generic Alert"}). Automatic Protection Active.",
                                    color = JarvisAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Semantic Target Matcher & Interactive Action Sandbox
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("semantic_matcher_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                border = BorderStroke(1.dp, JarvisBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SEMANTIC TARGET MATCHER SANDBOX",
                        color = JarvisCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = testGoalInput,
                        onValueChange = { testGoalInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_semantic_goal"),
                        label = { Text("Task Goal or Icon Description", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Search for Tom and Jerry, Find Play button, Back arrow", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.observeSemanticScreen(testGoalInput, forceVisualScan = true) },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_observe_semantic")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Observe Semantic Screen", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testSemanticTargetMatch(testGoalInput) },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisViolet),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_match_target")
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Match Visual Target", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testSemanticActionExecution(testGoalInput, "CLICK") },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_execute_semantic_action")
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute Click & Verify", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testIconRecognitionOnScreen() },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisAmber),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_test_icons")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detect UI Icons 🔍 ▶ ←", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (matchedTarget != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val match = matchedTarget!!
                        val selected = match.selectedElement
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = JarvisDarkVoid,
                            border = BorderStroke(1.dp, if (selected != null) JarvisEmerald else JarvisRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TARGET RESOLUTION: ${match.targetRole}",
                                        color = if (selected != null) JarvisEmerald else JarvisRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${(match.confidence * 100).toInt()}% Conf",
                                        color = JarvisTextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (selected != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Label: '${selected.label ?: selected.iconMeaning ?: selected.description ?: "Unlabeled"}' • Bounds: ${selected.bounds}",
                                        color = JarvisTextSecondary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Reason: ${match.reason}",
                                        color = JarvisTextMuted,
                                        fontSize = 10.sp
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Failure Reason: ${match.reason}",
                                        color = JarvisRed,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (semanticStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Status: $semanticStatus",
                            color = JarvisTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Section 3: Screen Diff & State Verification Inspector
        if (screenDiff != null) {
            item {
                val diff = screenDiff!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("screen_diff_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                    border = BorderStroke(1.dp, if (diff.transitionOccurred) JarvisEmerald else JarvisAmber)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = JarvisEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SCREEN TRANSITION VERIFICATION",
                                    color = JarvisEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "${(diff.confidence * 100).toInt()}% Conf",
                                color = if (diff.transitionOccurred) JarvisEmerald else JarvisAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Transition Type: ${diff.transitionType} (Transition: ${diff.transitionOccurred})",
                            color = JarvisTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Summary: ${diff.summary}",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = JarvisEmerald.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "+${diff.newElements.size} Added",
                                    color = JarvisEmerald,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = JarvisRed.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "-${diff.removedElements.size} Removed",
                                    color = JarvisRed,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = JarvisAmber.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${diff.changedRegions.size} Changed Regions",
                                    color = JarvisAmber,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Visual Spatial Geometry Radar
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vision_radar_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
                border = BorderStroke(1.dp, JarvisBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VISUAL GEOMETRY RADAR",
                            color = JarvisCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${semanticScreen?.elements?.size ?: lastDetectedElements.size} targets mapped",
                            color = JarvisTextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Radar Box Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(JarvisDarkVoid)
                            .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Draw reference grid
                            drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = 1f)
                            drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

                            val elementsToDraw = semanticScreen?.elements?.map {
                                VisualElement(it.role, it.label ?: it.iconMeaning ?: "", it.bounds, it.confidence, it.source)
                            } ?: lastDetectedElements

                            elementsToDraw.forEach { elem ->
                                val normLeft = (elem.bounds.left.toFloat() / 1080f).coerceIn(0f, 1f) * w
                                val normTop = (elem.bounds.top.toFloat() / 2400f).coerceIn(0f, 1f) * h
                                val normWidth = ((elem.bounds.width().toFloat() / 1080f) * w).coerceAtLeast(16f)
                                val normHeight = ((elem.bounds.height().toFloat() / 2400f) * h).coerceAtLeast(12f)

                                val boxColor = when (elem.semanticRole) {
                                    SemanticTarget.SEARCH -> JarvisCyan
                                    SemanticTarget.PLAY -> JarvisEmerald
                                    SemanticTarget.VIDEO_ITEM -> JarvisAmber
                                    SemanticTarget.INPUT_FIELD -> JarvisViolet
                                    SemanticTarget.BACK -> JarvisRed
                                    else -> JarvisEmerald
                                }

                                drawRect(
                                    color = boxColor.copy(alpha = 0.25f),
                                    topLeft = Offset(normLeft, normTop),
                                    size = Size(normWidth, normHeight)
                                )
                                drawRect(
                                    color = boxColor,
                                    topLeft = Offset(normLeft, normTop),
                                    size = Size(normWidth, normHeight),
                                    style = Stroke(width = 2f)
                                )
                            }
                        }

                        if ((semanticScreen?.elements?.isEmpty() ?: true) && lastDetectedElements.isEmpty()) {
                            Text(
                                text = "Tap 'Observe Semantic Screen' to map controls and symbols",
                                color = JarvisTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Semantic Elements List
        item {
            val totalElems = semanticScreen?.elements?.size ?: lastDetectedElements.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEMANTIC UI ELEMENTS ($totalElems)",
                    color = JarvisTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (semanticScreen != null && semanticScreen!!.elements.isNotEmpty()) {
            items(semanticScreen!!.elements) { elem ->
                SemanticElementCard(
                    element = elem,
                    onTap = {
                        viewModel.testSemanticActionExecution(elem.role, "CLICK")
                    }
                )
            }
        } else if (lastDetectedElements.isNotEmpty()) {
            items(lastDetectedElements) { elem ->
                VisualElementCard(
                    element = elem,
                    onTap = {
                        viewModel.testClickElement(elem.semanticRole)
                    }
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No semantic elements scanned yet. Tap 'Observe Semantic Screen'.",
                            color = JarvisTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Section 6: Learned Visual Experience Database
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LEARNED VISUAL EXPERIENCES (${visualExperiences.size})",
                        color = JarvisEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (visualExperiences.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearVisualExperiences() },
                        modifier = Modifier.size(28.dp).testTag("btn_clear_visual_exp")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = JarvisRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (visualExperiences.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No learned visual experiences recorded yet. Successful interactions will be automatically saved here for instant offline recognition.",
                            color = JarvisTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(visualExperiences) { exp ->
                VisualExperienceItemCard(experience = exp)
            }
        }
    }
}

@Composable
fun SemanticElementCard(
    element: SemanticUIElement,
    onTap: () -> Unit
) {
    val roleColor = when (element.role) {
        SemanticTarget.SEARCH -> JarvisCyan
        SemanticTarget.PLAY -> JarvisEmerald
        SemanticTarget.PAUSE -> JarvisAmber
        SemanticTarget.MORE_OPTIONS -> JarvisViolet
        SemanticTarget.VIDEO_ITEM -> JarvisAmber
        SemanticTarget.INPUT_FIELD -> JarvisCyan
        SemanticTarget.SEND_BUTTON -> JarvisEmerald
        SemanticTarget.BACK -> JarvisRed
        else -> JarvisTextPrimary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("semantic_elem_${element.role}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
        border = BorderStroke(1.dp, roleColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = roleColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, roleColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = element.role,
                            color = roleColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = JarvisDarkVoid
                    ) {
                        Text(
                            text = element.source,
                            color = JarvisTextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onTap,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = roleColor),
                    border = BorderStroke(1.dp, roleColor.copy(alpha = 0.5f)),
                    modifier = Modifier.height(30.dp).testTag("btn_tap_semantic_${element.id}")
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tap Target", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val desc = element.iconMeaning?.let { "Icon: $it" }
                ?: element.label?.let { "Text: $it" }
                ?: element.description
                ?: "Unlabeled Visual Control"

            Text(
                text = desc,
                color = JarvisTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Bounds & Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bounds: [${element.bounds.left}, ${element.bounds.top}, ${element.bounds.right}, ${element.bounds.bottom}]",
                    color = JarvisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "${(element.confidence * 100).toInt()}% Conf",
                    color = if (element.confidence >= 0.90f) JarvisEmerald else JarvisAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { element.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = roleColor,
                trackColor = JarvisDarkVoid
            )
        }
    }
}

@Composable
fun VisionMetricBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = JarvisDarkVoid,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                color = JarvisTextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun VisualElementCard(
    element: VisualElement,
    onTap: () -> Unit
) {
    val roleColor = when (element.semanticRole) {
        SemanticTarget.SEARCH -> JarvisCyan
        SemanticTarget.PLAY -> JarvisEmerald
        SemanticTarget.PAUSE -> JarvisAmber
        SemanticTarget.MORE_OPTIONS -> JarvisViolet
        SemanticTarget.VIDEO_ITEM -> JarvisAmber
        SemanticTarget.INPUT_FIELD -> JarvisCyan
        SemanticTarget.SEND_BUTTON -> JarvisEmerald
        else -> JarvisTextPrimary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("visual_elem_${element.semanticRole}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
        border = BorderStroke(1.dp, roleColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = roleColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, roleColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = element.semanticRole,
                            color = roleColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = JarvisDarkVoid
                    ) {
                        Text(
                            text = element.source,
                            color = JarvisTextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onTap,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = roleColor),
                    border = BorderStroke(1.dp, roleColor.copy(alpha = 0.5f)),
                    modifier = Modifier.height(30.dp).testTag("btn_tap_${element.semanticRole}")
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tap Target", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = element.visualDescription,
                color = JarvisTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bounds & Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bounds: [${element.bounds.left}, ${element.bounds.top}, ${element.bounds.right}, ${element.bounds.bottom}] Center: (${element.bounds.centerX()}, ${element.bounds.centerY()})",
                    color = JarvisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "${(element.confidence * 100).toInt()}% Conf",
                    color = if (element.confidence >= 0.90f) JarvisEmerald else JarvisAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { element.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = roleColor,
                trackColor = JarvisDarkVoid
            )
        }
    }
}

@Composable
fun VisualExperienceItemCard(experience: VisualExperienceEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exp_item_${experience.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisDarkNavy),
        border = BorderStroke(1.dp, JarvisBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = experience.semanticRole,
                        color = JarvisEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = experience.appPackage.substringAfterLast("."),
                        color = JarvisTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = experience.visualDescription,
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Bounds: [${experience.boundsLeft}, ${experience.boundsTop}, ${experience.boundsRight}, ${experience.boundsBottom}] • Action: ${experience.actionTaken}",
                    color = JarvisTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = JarvisEmerald.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "SAVED",
                    color = JarvisEmerald,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

