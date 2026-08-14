package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun AccessibilityDiagnosticsCard(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val diag by viewModel.accessibilityDiagnostics.collectAsState()

    HologramCard(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (diag.isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (diag.isConnected) JarvisEmerald else JarvisRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ACCESSIBILITY DIAGNOSTICS",
                    color = JarvisCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(
                onClick = { viewModel.refreshAccessibilityDiagnostics() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = JarvisCyan, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Key-Value Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            DiagnosticRow("Accessibility enabled", if (diag.isEnabled) "YES" else "NO", if (diag.isEnabled) JarvisEmerald else JarvisRed)
            DiagnosticRow("Service connected", if (diag.isConnected) "YES" else "NO", if (diag.isConnected) JarvisEmerald else JarvisRed)
            DiagnosticRow("Current package", diag.currentPackage, JarvisCyan)
            DiagnosticRow("rootInActiveWindow", if (diag.isRootAvailable) "AVAILABLE" else "NULL", if (diag.isRootAvailable) JarvisEmerald else JarvisAmber)
            DiagnosticRow("Node count", diag.totalNodes.toString(), JarvisTextPrimary)
            DiagnosticRow("Clickable node count", diag.clickableNodes.toString(), JarvisCyan)
            DiagnosticRow("Editable node count", diag.editableNodes.toString(), JarvisAmber)
            DiagnosticRow("Scrollable node count", diag.scrollableNodes.toString(), JarvisEmerald)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "DIRECT ACCESSIBILITY ACTION TESTS",
            color = JarvisTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Phase A Test Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TestButton(
                label = "READ SCREEN",
                icon = Icons.Default.Visibility,
                onClick = { viewModel.testReadScreen() },
                modifier = Modifier.weight(1f)
            )
            TestButton(
                label = "CLICK TEST",
                icon = Icons.Default.TouchApp,
                onClick = { viewModel.testClickElement("Search") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TestButton(
                label = "TYPE TEST",
                icon = Icons.Default.Edit,
                onClick = { viewModel.testTypeText("Tom and Jerry") },
                modifier = Modifier.weight(1f)
            )
            TestButton(
                label = "SCROLL TEST",
                icon = Icons.Default.UnfoldMore,
                onClick = { viewModel.testScrollScreen(true) },
                modifier = Modifier.weight(1f)
            )
            TestButton(
                label = "BACK TEST",
                icon = Icons.Default.ArrowBack,
                onClick = { viewModel.testPressBack() },
                modifier = Modifier.weight(1f)
            )
        }

        // Live Nodes Inspector if available
        if (diag.recentElements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "VISIBLE ELEMENTS (${diag.recentElements.size})",
                color = JarvisTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(diag.recentElements.take(10)) { el ->
                    Surface(
                        color = JarvisCardBg,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.6.dp, JarvisBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text(
                                text = el.text.ifEmpty { el.contentDescription.ifEmpty { el.viewId ?: el.className.substringAfterLast(".") } },
                                color = if (el.isClickable) JarvisCyan else if (el.isEditable) JarvisAmber else JarvisTextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Text(
                                text = "${if (el.isClickable) "Clickable " else ""}${if (el.isEditable) "Editable" else ""}",
                                color = JarvisTextMuted,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = JarvisTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TestButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.8.dp, JarvisCyan.copy(alpha = 0.5f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = JarvisCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
