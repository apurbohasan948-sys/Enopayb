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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.core.vision.SemanticTarget
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
    val visualExperiences by viewModel.visualExperiences.collectAsState()
    val isCloudVisionEnabled by viewModel.isCloudVisionEnabled.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val diagnostics by viewModel.accessibilityDiagnostics.collectAsState()
    val lastDetectedElements by viewModel.lastDetectedElements.collectAsState()

    var testRoleInput by remember { mutableStateOf("SEARCH") }

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
                                    text = "MULTIMODAL VISION ENGINE",
                                    color = JarvisCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Text + Icon + Layout Perception",
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Cloud Vision Switch
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
                            value = unifiedScreen?.packageName?.substringAfterLast(".") ?: diagnostics.currentPackage.substringAfterLast("."),
                            color = JarvisCyan,
                            modifier = Modifier.weight(1f)
                        )
                        VisionMetricBadge(
                            label = "UI NODES",
                            value = "${unifiedScreen?.elements?.size ?: diagnostics.totalNodes}",
                            color = JarvisViolet,
                            modifier = Modifier.weight(1f)
                        )
                        VisionMetricBadge(
                            label = "VISUAL ICONS",
                            value = "${lastDetectedElements.size}",
                            color = JarvisEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 2: Interactive Test & Diagnostics Actions
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vision_actions_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                border = BorderStroke(1.dp, JarvisBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "LIVE PERCEPTION CONTROLS",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerVisualScreenAnalysis() },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_scan_screen")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Active Screen", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testIntentDetection("SEARCH") },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisViolet),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_find_search")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Find 🔍 (Search)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testIntentDetection("PLAY") },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_find_play")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Find ▶ (Play)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testPlayTomAndJerry() },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisAmber),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_play_tom_jerry")
                        ) {
                            Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Tom & Jerry Flow", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 3: Visual Spatial Bounding Box Radar
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
                            text = "${lastDetectedElements.size} targets mapped",
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

                            // Draw detected element bounding boxes normalized
                            lastDetectedElements.forEach { elem ->
                                val normLeft = (elem.bounds.left.toFloat() / 1080f).coerceIn(0f, 1f) * w
                                val normTop = (elem.bounds.top.toFloat() / 2400f).coerceIn(0f, 1f) * h
                                val normWidth = ((elem.bounds.width().toFloat() / 1080f) * w).coerceAtLeast(16f)
                                val normHeight = ((elem.bounds.height().toFloat() / 2400f) * h).coerceAtLeast(12f)

                                val boxColor = when (elem.semanticRole) {
                                    SemanticTarget.SEARCH -> JarvisCyan
                                    SemanticTarget.PLAY -> JarvisEmerald
                                    SemanticTarget.VIDEO_ITEM -> JarvisAmber
                                    SemanticTarget.INPUT_FIELD -> JarvisViolet
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

                        if (lastDetectedElements.isEmpty()) {
                            Text(
                                text = "Tap 'Scan Active Screen' to detect icons and visual controls",
                                color = JarvisTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Detected Visual Elements List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DETECTED VISUAL TARGETS (${lastDetectedElements.size})",
                    color = JarvisTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (lastDetectedElements.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                    border = BorderStroke(1.dp, JarvisBorder)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No visual elements scanned yet. Open an app (e.g. YouTube) and tap 'Scan Active Screen'.",
                            color = JarvisTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(lastDetectedElements) { elem ->
                VisualElementCard(
                    element = elem,
                    onTap = {
                        viewModel.testClickElement(elem.semanticRole)
                    }
                )
            }
        }

        // Section 5: Learned Visual Experience Database
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
