package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@Composable
fun CapabilitiesScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val capabilities by viewModel.capabilitiesList.collectAsState()

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
                                    text = "ANDROID CAPABILITIES & ROLES",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = JarvisCyan
                                )
                                Text(
                                    text = "Live inspection of OS permissions, roles, and administrative access",
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
