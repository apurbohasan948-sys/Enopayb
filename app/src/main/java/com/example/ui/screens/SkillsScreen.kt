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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.SkillEntity
import com.example.ui.JarvisViewModel
import com.example.ui.components.AccessibilityDiagnosticsCard
import com.example.ui.components.HologramCard
import com.example.ui.components.RiskBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

enum class SkillFilter {
    ALL,
    BUILTIN,
    LEARNED
}

@Composable
fun SkillsScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val skills by viewModel.allSkills.collectAsState()
    var currentFilter by remember { mutableStateOf(SkillFilter.ALL) }

    val filteredSkills = when (currentFilter) {
        SkillFilter.ALL -> skills
        SkillFilter.BUILTIN -> skills.filter { it.isBuiltIn }
        SkillFilter.LEARNED -> skills.filter { !it.isBuiltIn }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === Title Header ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SKILLS & TOOL REGISTRY",
                    color = JarvisCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${skills.size} Total (${skills.count { !it.isBuiltIn }} Synthesized from Experience)",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }
            StatusPill(label = "POLICY ENFORCED", icon = Icons.Default.Security, color = JarvisEmerald)
        }

        // === Filter Selector ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SkillFilter.values()) { filter ->
                    val selected = currentFilter == filter
                    val count = when (filter) {
                        SkillFilter.ALL -> skills.size
                        SkillFilter.BUILTIN -> skills.count { it.isBuiltIn }
                        SkillFilter.LEARNED -> skills.count { !it.isBuiltIn }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) JarvisCyan.copy(alpha = 0.2f) else JarvisCardBg,
                        border = BorderStroke(1.dp, if (selected) JarvisCyan else JarvisBorder),
                        modifier = Modifier
                            .clickable { currentFilter = filter }
                            .testTag("skill_filter_${filter.name.lowercase()}")
                    ) {
                        Text(
                            text = "${filter.name} ($count)",
                            color = if (selected) JarvisCyan else JarvisTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (skills.any { !it.isBuiltIn }) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = JarvisRed.copy(alpha = 0.1f),
                    border = BorderStroke(0.8.dp, JarvisRed.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { viewModel.clearLearnedSkills() }
                ) {
                    Text("Clear Learned", color = JarvisRed, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AccessibilityDiagnosticsCard(viewModel = viewModel)
            }

            if (filteredSkills.isEmpty()) {
                item {
                    HologramCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No skills in this category.", color = JarvisTextSecondary, fontSize = 12.sp)
                            Text("JARVIS automatically synthesizes new skills after verified multi-step task completions.", color = JarvisTextMuted, fontSize = 10.sp)
                        }
                    }
                }
            } else {
                items(filteredSkills) { skill ->
                    SkillCardItem(
                        skill = skill,
                        onExecute = {
                            when (skill.name) {
                                "toggle_flashlight" -> viewModel.sendUserPrompt("Turn on flashlight")
                                "query_battery_status" -> viewModel.sendUserPrompt("Check battery status")
                                "open_app" -> viewModel.sendUserPrompt("Open Settings")
                                "security_audit_check" -> viewModel.runSecurityAudit()
                                "make_call" -> viewModel.sendUserPrompt("Call 911")
                                "send_message" -> viewModel.sendUserPrompt("Send message to Emergency: Need assistance")
                                "search_knowledge_rag" -> viewModel.sendUserPrompt("What is the architecture of Redmi Note 12?")
                                else -> viewModel.sendUserPrompt("Execute skill ${skill.name}")
                            }
                        },
                        onToggle = { viewModel.toggleSkill(skill) },
                        onRollback = { viewModel.rollbackSkill(skill) },
                        onDelete = { viewModel.deleteSkill(skill) }
                    )
                }
            }
        }
    }
}

@Composable
fun SkillCardItem(
    skill: SkillEntity,
    onExecute: () -> Unit,
    onToggle: () -> Unit = {},
    onRollback: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val borderColor = when {
        !skill.isEnabled -> JarvisTextMuted.copy(alpha = 0.3f)
        !skill.isBuiltIn -> JarvisViolet.copy(alpha = 0.6f)
        else -> JarvisBorder
    }

    val (statusLabel, statusColor) = when {
        !skill.isEnabled -> "DISABLED" to JarvisTextMuted
        skill.isBuiltIn -> "CORE BUILTIN" to JarvisCyan
        skill.procedure.contains("CANDIDATE") -> "CANDIDATE" to JarvisViolet
        skill.procedure.contains("VALIDATING") -> "VALIDATING" to JarvisAmber
        skill.procedure.contains("VERIFIED") -> "VERIFIED" to JarvisCyan
        skill.procedure.contains("DEPRECATED") || skill.failureCount >= 3 -> "DEPRECATED" to JarvisRed
        skill.procedure.contains("STALE") -> "STALE" to JarvisAmber
        else -> "ACTIVE" to JarvisEmerald
    }

    HologramCard(borderColor = borderColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (skill.isBuiltIn) Icons.Default.Build else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (skill.isBuiltIn) JarvisCyan else JarvisViolet,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = skill.name,
                    color = if (skill.isBuiltIn) JarvisCyan else JarvisViolet,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, statusColor)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            RiskBadge(riskLevel = skill.riskLevel)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = skill.description,
            color = JarvisTextPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Procedure Execution Steps:",
                    color = JarvisTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = skill.procedure,
                    color = JarvisTextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val totalExec = skill.successCount + skill.failureCount
                val rate = if (totalExec > 0) (skill.successCount * 100 / totalExec) else 100
                Text(
                    text = "Success Rate: $rate% (${skill.successCount}/$totalExec) | v${skill.version}",
                    color = if (rate >= 80) JarvisCyan else JarvisAmber,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Perms: ${skill.requiredPermissions}",
                    color = JarvisTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!skill.isBuiltIn && skill.previousVersionProcedure != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = JarvisAmber.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, JarvisAmber.copy(alpha = 0.6f)),
                        modifier = Modifier.clickable { onRollback() }
                    ) {
                        Text(
                            text = "Rollback",
                            color = JarvisAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }

                if (!skill.isBuiltIn) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (skill.isEnabled) JarvisEmerald.copy(alpha = 0.15f) else JarvisTextMuted.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, if (skill.isEnabled) JarvisEmerald else JarvisTextMuted),
                        modifier = Modifier.clickable { onToggle() }
                    ) {
                        Text(
                            text = if (skill.isEnabled) "Enabled" else "Disabled",
                            color = if (skill.isEnabled) JarvisEmerald else JarvisTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }

                Button(
                    onClick = onExecute,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Run", color = JarvisCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
