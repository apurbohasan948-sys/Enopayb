package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AutonomousDashboardScreen
import com.example.ui.screens.CapabilitiesScreen
import com.example.ui.screens.DebugConsoleScreen
import com.example.ui.screens.HudConsoleScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.RagKnowledgeScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SkillsScreen
import com.example.ui.screens.TaskPlanViewerScreen
import com.example.ui.screens.VisionDebugScreen
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDarkNavy
import com.example.ui.theme.JarvisDarkVoid
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.MyApplicationTheme

enum class JarvisTab(val title: String, val icon: ImageVector) {
    CONSOLE("HUD", Icons.Default.Terminal),
    AUTONOMOUS("AUTO", Icons.Default.AutoAwesome),
    PLAN("PLAN", Icons.Default.AltRoute),
    DEBUG("DEBUG", Icons.Default.BugReport),
    ROLES("ROLES", Icons.Default.Security),
    VISION("VISION", Icons.Default.Visibility),
    MODELS("MODELS", Icons.Default.Memory),
    MEMORY("BRAIN", Icons.Default.Bookmark),
    SKILLS("SKILLS", Icons.Default.Build),
    KNOWLEDGE("RAG", Icons.Default.MenuBook)
}

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestInitialPermissions()

        setContent {
            MyApplicationTheme {
                JarvisApp(viewModel = viewModel)
            }
        }
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp(viewModel: JarvisViewModel) {
    var currentTab by remember { mutableStateOf(JarvisTab.CONSOLE) }
    val isShieldActive by viewModel.isSecurityShieldActive.collectAsState()
    val hardwareMetrics by viewModel.hardwareMetrics.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisDarkVoid,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isShieldActive) JarvisCyan else JarvisAmber, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "J.A.R.V.I.S.",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = "PERSONAL AI ASSISTANT // REDMI NOTE 12",
                                    color = JarvisTextMuted,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val geminiKey by viewModel.geminiApiKey.collectAsState()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (geminiKey.isNotBlank()) JarvisViolet.copy(alpha = 0.15f) else JarvisCardBg,
                                border = BorderStroke(0.8.dp, if (geminiKey.isNotBlank()) JarvisViolet else JarvisBorder),
                                modifier = Modifier
                                    .clickable { currentTab = JarvisTab.MODELS }
                                    .padding(end = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Cloud,
                                        contentDescription = "Gemini Key Config",
                                        tint = if (geminiKey.isNotBlank()) JarvisViolet else JarvisTextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (geminiKey.isNotBlank()) "API READY" else "OFFLINE",
                                        color = if (geminiKey.isNotBlank()) JarvisViolet else JarvisTextMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Compact System Health Chip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = JarvisCardBg,
                                border = BorderStroke(0.8.dp, JarvisBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = JarvisCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${hardwareMetrics.ramAllocatedMb}MB",
                                        color = JarvisTextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisDarkVoid
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = JarvisDarkNavy,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            ) {
                JarvisTab.values().forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JarvisCyan,
                            selectedTextColor = JarvisCyan,
                            unselectedIconColor = JarvisTextMuted,
                            unselectedTextColor = JarvisTextMuted,
                            indicatorColor = JarvisCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JarvisDarkVoid,
                            JarvisDarkNavy.copy(alpha = 0.6f),
                            JarvisDarkVoid
                        )
                    )
                )
        ) {
            when (currentTab) {
                JarvisTab.CONSOLE -> HudConsoleScreen(viewModel = viewModel)
                JarvisTab.AUTONOMOUS -> AutonomousDashboardScreen(viewModel = viewModel)
                JarvisTab.PLAN -> TaskPlanViewerScreen(viewModel = viewModel)
                JarvisTab.DEBUG -> DebugConsoleScreen(viewModel = viewModel)
                JarvisTab.ROLES -> CapabilitiesScreen(viewModel = viewModel)
                JarvisTab.VISION -> VisionDebugScreen(viewModel = viewModel)
                JarvisTab.MODELS -> ModelsScreen(viewModel = viewModel)
                JarvisTab.MEMORY -> MemoryScreen(viewModel = viewModel)
                JarvisTab.SKILLS -> SkillsScreen(viewModel = viewModel)
                JarvisTab.KNOWLEDGE -> RagKnowledgeScreen(viewModel = viewModel)
            }
        }
    }
}
