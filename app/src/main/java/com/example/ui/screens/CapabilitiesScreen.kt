package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.capability.CapabilityItem
import com.example.core.capability.CapabilityStatus
import com.example.core.model.ToolIntent
import com.example.data.local.entity.AppRegistryEntity
import com.example.data.local.entity.DeviceActionHistoryEntity
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CapabilitiesScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val capabilities by viewModel.capabilitiesList.collectAsState()
    val registeredApps by viewModel.registeredAppsList.collectAsState()
    val actionHistory by viewModel.deviceActionHistory.collectAsState()
    var selectedSection by remember { mutableStateOf("CAPABILITIES") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = JarvisDarkVoid
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Capabilities",
                                tint = JarvisCyan,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column {
                                Text(
                                    text = "DEVICE CONTROL & CAPABILITIES",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = JarvisCyan
                                )
                                Text(
                                    text = "Hardware actuators, app bridges & system capability registry",
                                    fontSize = 11.sp,
                                    color = JarvisTextMuted
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.refreshCapabilities() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = JarvisCyan)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sections = listOf(
                        "CAPABILITIES" to Icons.Default.Security,
                        "DEVICE DIAGNOSTICS" to Icons.Default.Memory,
                        "APP REGISTRY" to Icons.Default.Apps,
                        "ACTION LOGS" to Icons.Default.History
                    )
                    sections.forEach { (secName, icon) ->
                        FilterChip(
                            selected = selectedSection == secName,
                            onClick = { selectedSection = secName },
                            label = { Text(secName, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                selectedLabelColor = JarvisCyan,
                                containerColor = JarvisDarkNavy,
                                labelColor = JarvisTextSecondary
                            ),
                            border = BorderStroke(1.dp, if (selectedSection == secName) JarvisCyan else JarvisBorder)
                        )
                    }
                }
            }

            when (selectedSection) {
                "CAPABILITIES" -> {
                    items(capabilities) { item ->
                        CapabilityCard(
                            item = item,
                            onOpenSetup = {
                                try {
                                    val intent = viewModel.capabilityManager.getSetupIntent(item.id)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open settings: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                "DEVICE DIAGNOSTICS" -> {
                    item {
                        DeviceDiagnosticsCard(
                            onRunBattery = { viewModel.executeDeviceTool(ToolIntent("get_battery", emptyMap(), "LOW")) },
                            onRunNetwork = { viewModel.executeDeviceTool(ToolIntent("get_network_status", emptyMap(), "LOW")) },
                            onToggleFlashlight = { viewModel.executeDeviceTool(ToolIntent("toggle_flashlight", emptyMap(), "LOW")) },
                            onVolumeUp = { viewModel.executeDeviceTool(ToolIntent("control_volume", mapOf("action" to "VOLUME_UP"), "LOW")) },
                            onVolumeDown = { viewModel.executeDeviceTool(ToolIntent("control_volume", mapOf("action" to "VOLUME_DOWN"), "LOW")) },
                            onMediaToggle = { viewModel.executeDeviceTool(ToolIntent("control_media", mapOf("action" to "PLAY_PAUSE"), "LOW")) },
                            onStorageReport = { viewModel.executeDeviceTool(ToolIntent("storage_report", emptyMap(), "LOW")) },
                            onSecurityAudit = { viewModel.executeDeviceTool(ToolIntent("run_security_audit", emptyMap(), "LOW")) },
                            onClearCache = { viewModel.executeDeviceTool(ToolIntent("clear_cache", emptyMap(), "LOW")) }
                        )
                    }
                }

                "APP REGISTRY" -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                            border = BorderStroke(1.dp, JarvisBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("REGISTERED PACKAGES: ${registeredApps.size}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = JarvisCyan)
                                    Text("Locally indexed for zero-latency launching", fontSize = 10.sp, color = JarvisTextMuted)
                                }
                                Button(
                                    onClick = {
                                        viewModel.scanInstalledApps { count ->
                                            Toast.makeText(context, "Indexed $count applications", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan.copy(alpha = 0.2f), contentColor = JarvisCyan),
                                    border = BorderStroke(1.dp, JarvisCyan)
                                ) {
                                    Text("SCAN NOW", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (registeredApps.isEmpty()) {
                        item {
                            Text("No apps indexed yet. Click 'SCAN NOW' to scan installed apps.", fontSize = 12.sp, color = JarvisTextMuted, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(registeredApps) { app ->
                            AppRegistryCard(app = app, onLaunch = {
                                viewModel.executeDeviceTool(ToolIntent("open_app", mapOf("appName" to app.applicationLabel), "LOW"))
                            })
                        }
                    }
                }

                "ACTION LOGS" -> {
                    if (actionHistory.isEmpty()) {
                        item {
                            Text("No device actions recorded yet. Trigger any tool to view execution traces.", fontSize = 12.sp, color = JarvisTextMuted, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(actionHistory) { action ->
                            ActionHistoryCard(action = action)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDiagnosticsCard(
    onRunBattery: () -> Unit,
    onRunNetwork: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onMediaToggle: () -> Unit,
    onStorageReport: () -> Unit,
    onSecurityAudit: () -> Unit,
    onClearCache: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
        border = BorderStroke(1.dp, JarvisBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DEVICE HARDWARE ACTUATION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = JarvisCyan)
            Text("Direct hardware execution without LLM reasoning latency", fontSize = 11.sp, color = JarvisTextMuted)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRunBattery,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("BATTERY", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                OutlinedButton(
                    onClick = onRunNetwork,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("NETWORK", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onToggleFlashlight,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisAmber),
                    border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.FlashlightOn, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("TORCH", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                OutlinedButton(
                    onClick = onMediaToggle,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisEmerald),
                    border = BorderStroke(1.dp, JarvisEmerald.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PLAY/PAUSE", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onVolumeUp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("VOL UP", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                OutlinedButton(
                    onClick = onVolumeDown,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("VOL DOWN", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onStorageReport,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("STORAGE", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                OutlinedButton(
                    onClick = onSecurityAudit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisEmerald),
                    border = BorderStroke(1.dp, JarvisEmerald.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AUDIT", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun AppRegistryCard(app: AppRegistryEntity, onLaunch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
        border = BorderStroke(1.dp, JarvisBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(app.applicationLabel, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = JarvisTextPrimary)
                Text(app.packageName, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = JarvisCyan)
                if (app.category.isNotEmpty() && app.category != "UNDEFINED") {
                    Text("Category: ${app.category}", fontSize = 10.sp, color = JarvisTextMuted)
                }
            }
            OutlinedButton(
                onClick = onLaunch,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                border = BorderStroke(1.dp, JarvisCyan)
            ) {
                Text("LAUNCH", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ActionHistoryCard(action: DeviceActionHistoryEntity) {
    val df = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(action.timestamp) { df.format(Date(action.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
        border = BorderStroke(1.dp, if (action.success) JarvisBorder else JarvisRed.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (action.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (action.success) JarvisEmerald else JarvisRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${action.toolName} -> ${action.action}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = JarvisCyan
                    )
                }
                Text(timeStr, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = JarvisTextMuted)
            }
            Spacer(Modifier.height(4.dp))
            val summaryText = action.failureReason ?: action.verificationProof ?: if (action.success) "Executed successfully in ${action.durationMs}ms" else "Failed"
            Text(summaryText, fontSize = 11.sp, color = JarvisTextSecondary)
            if (!action.verificationProof.isNullOrEmpty()) {
                Text("Proof: ${action.verificationProof}", fontSize = 10.sp, color = JarvisTextMuted)
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    item: CapabilityItem,
    onOpenSetup: () -> Unit
) {
    val statusColor = when (item.status) {
        CapabilityStatus.GRANTED -> JarvisEmerald
        CapabilityStatus.DENIED, CapabilityStatus.REQUIRES_SETUP -> JarvisAmber
        CapabilityStatus.UNSUPPORTED -> JarvisRed
    }

    val statusText = when (item.status) {
        CapabilityStatus.GRANTED -> "AVAILABLE"
        CapabilityStatus.DENIED -> "NOT AVAILABLE"
        CapabilityStatus.REQUIRES_SETUP -> "SETUP REQUIRED"
        CapabilityStatus.UNSUPPORTED -> "NOT PROVISIONED"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
        border = BorderStroke(1.dp, if (item.isCrucial && item.status != CapabilityStatus.GRANTED) JarvisAmber else JarvisBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = JarvisTextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.status == CapabilityStatus.GRANTED) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                fontSize = 12.sp,
                color = JarvisTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PERMISSION / ROLE:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = JarvisTextMuted
                    )
                    Text(
                        text = item.requiredPermission,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = JarvisCyan
                    )
                }

                if (item.status != CapabilityStatus.GRANTED && item.status != CapabilityStatus.UNSUPPORTED) {
                    OutlinedButton(
                        onClick = onOpenSetup,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                        border = BorderStroke(1.dp, JarvisCyan)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Setup", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SETUP", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
