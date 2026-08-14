package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun SkillsScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val skills by viewModel.allSkills.collectAsState()

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
                    text = "${skills.size} Structured Android Action Capabilities",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }
            StatusPill(label = "POLICY ENFORCED", icon = Icons.Default.Security, color = JarvisEmerald)
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
            items(skills) { skill ->
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
                    }
                )
            }
        }
    }
}

@Composable
fun SkillCardItem(
    skill: SkillEntity,
    onExecute: () -> Unit
) {
    HologramCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = skill.name,
                    color = JarvisCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
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
                Text(
                    text = "Perms: ${skill.requiredPermissions}",
                    color = JarvisTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Uses: ${skill.executionCount} | Ver: ${skill.version}",
                    color = JarvisTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onExecute,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Run", color = JarvisCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
