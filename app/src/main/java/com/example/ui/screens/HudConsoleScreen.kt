package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ActiveModelType
import com.example.core.voice.VoiceState
import com.example.data.local.entity.ChatMessageEntity
import com.example.ui.JarvisViewModel
import com.example.ui.components.ArcReactorVisualizer
import com.example.ui.components.HologramCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HudConsoleScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val audioWaveLevel by viewModel.audioWaveLevel.collectAsState()
    val isShieldActive by viewModel.isSecurityShieldActive.collectAsState()
    val activeModelType by viewModel.activeModelType.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val pendingIntent by viewModel.pendingConfirmationIntent.collectAsState()
    val metrics by viewModel.hardwareMetrics.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // === Top HUD Status Bar ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                label = when (activeModelType) {
                    ActiveModelType.LOCAL_GGUF_CPU -> "LOCAL QWEN-1.5B"
                    ActiveModelType.GEMINI_CLOUD_TEACHER -> "GEMINI TEACHER"
                    ActiveModelType.HYBRID_SUPERVISED -> "HYBRID SUPERVISED"
                },
                icon = Icons.Default.Bolt,
                color = JarvisCyan
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    label = if (isShieldActive) "DEFENSE: ACTIVE" else "DEFENSE: OFF",
                    icon = Icons.Default.Security,
                    color = if (isShieldActive) JarvisEmerald else JarvisAmber
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = JarvisBorder.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        viewModel.setLanguage(if (currentLang == "EN") "BN" else "EN")
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentLang == "EN") "EN" else "বাংলা",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // === Arc Reactor Core Hologram ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            ArcReactorVisualizer(
                voiceState = voiceState,
                audioWaveLevel = audioWaveLevel,
                isSecurityShieldActive = isShieldActive
            )
        }

        // === High-Risk Tool Confirmation Banner ===
        AnimatedVisibility(
            visible = pendingIntent != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            pendingIntent?.let { intent ->
                HologramCard(
                    borderColor = JarvisRed,
                    glowColor = JarvisRed.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "SECURITY CONFIRMATION REQUIRED",
                        color = JarvisRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tool: ${intent.toolName}\nArgs: ${intent.arguments}\nRationale: ${intent.rationale}",
                        color = JarvisTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { viewModel.cancelPendingTool() },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCardBg),
                            border = BorderStroke(1.dp, JarvisBorder),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = JarvisTextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject", fontSize = 11.sp, color = JarvisTextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.approvePendingTool() },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Authorize", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // === Quick Action Prompt Chips ===
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chips = listOf(
                "WhatsApp Hammad" to "whatsapp_msg",
                "Call Hammad" to "call_hammad",
                "Open YouTube" to "open_yt",
                "Read Screen" to "read_screen",
                "Device Status" to "query_battery_status",
                "Turn on Flashlight" to "toggle_flashlight",
                "Security Audit" to "security_audit_check",
                "হোয়াটসঅ্যাপ মেসেজ" to "bn_wa",
                "বাংলা মোড" to "lang_bn"
            )
            items(chips) { (label, tag) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = JarvisCardBg,
                    border = BorderStroke(0.8.dp, JarvisCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable {
                        when (tag) {
                            "whatsapp_msg" -> viewModel.sendUserPrompt("WhatsApp খুলে Hammad-কে বলো আমি পরে আসছি")
                            "call_hammad" -> viewModel.sendUserPrompt("Call Hammad")
                            "open_yt" -> viewModel.sendUserPrompt("Open YouTube")
                            "read_screen" -> viewModel.sendUserPrompt("Read screen")
                            "bn_wa" -> {
                                viewModel.setLanguage("BN")
                                viewModel.sendUserPrompt("হোয়াটসঅ্যাপ এ Hammad কে মেসেজ দাও আমি ১০ মিনিট পরে আসব")
                            }
                            "lang_bn" -> {
                                viewModel.setLanguage("BN")
                                viewModel.sendUserPrompt("হ্যালো জারভিস, তুমি কি করতে পারো?")
                            }
                            else -> viewModel.sendUserPrompt(label)
                        }
                    }
                ) {
                    Text(
                        text = label,
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // === Conversation Message Stream ===
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                ChatMessageItem(
                    message = msg,
                    onSpeak = { viewModel.voiceManager.speak(msg.message) }
                )
            }
            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = JarvisCyan,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "JARVIS Neural Engine processing...",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // === Voice & Text Command Input Bar ===
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = JarvisCardBg,
            border = BorderStroke(1.dp, JarvisBorderGlow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic / Wake-Word trigger button
                IconButton(
                    onClick = {
                        if (voiceState == VoiceState.SPEAKING) {
                            viewModel.voiceManager.stopSpeaking()
                        } else {
                            viewModel.sendUserPrompt("Hey JARVIS")
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.LISTENING_COMMAND) JarvisCyan.copy(alpha = 0.25f)
                            else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (voiceState == VoiceState.SPEAKING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (voiceState == VoiceState.SPEAKING) JarvisAmber else JarvisCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = if (currentLang == "BN") "কমান্ড বা প্রশ্ন লিখুন..." else "Enter command (e.g. Open YouTube, Battery, Torch)...",
                            fontSize = 12.sp,
                            color = JarvisTextMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendUserPrompt(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(JarvisCyan.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    onSpeak: () -> Unit
) {
    val isUser = message.role == "USER"
    val isTool = message.role == "TOOL"
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = when {
                isUser -> JarvisCyan.copy(alpha = 0.15f)
                isTool -> JarvisViolet.copy(alpha = 0.15f)
                else -> JarvisCardBg
            },
            border = BorderStroke(
                1.dp,
                when {
                    isUser -> JarvisCyan.copy(alpha = 0.4f)
                    isTool -> JarvisViolet.copy(alpha = 0.4f)
                    else -> JarvisBorder
                }
            ),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (message.role) {
                            "USER" -> "USER"
                            "TOOL" -> "ANDROID TOOL"
                            "DEFENSE_SHIELD" -> "DEFENSE SHIELD"
                            else -> "JARVIS [${message.providerType}]"
                        },
                        color = when (message.role) {
                            "USER" -> JarvisCyan
                            "TOOL" -> JarvisViolet
                            "DEFENSE_SHIELD" -> JarvisRed
                            else -> JarvisBlue
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedTime,
                            color = JarvisTextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (!isUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Play Audio",
                                tint = JarvisCyan.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onSpeak() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.message,
                    color = JarvisTextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (message.latencyMs > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Latency: ${message.latencyMs}ms | Local Execution",
                        color = JarvisTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
