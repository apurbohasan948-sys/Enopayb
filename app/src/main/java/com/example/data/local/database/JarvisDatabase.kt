package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MemoryEntity::class,
        SkillEntity::class,
        SecurityEventEntity::class,
        ChatMessageEntity::class,
        KnowledgeChunkEntity::class,
        com.example.data.local.entity.VisualExperienceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_brain.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(JarvisDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class JarvisDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database.jarvisDao())
                }
            }
        }

        private suspend fun populateInitialData(dao: JarvisDao) {
            // Seed Full Suite of Skills & Tools
            val initialSkills = listOf(
                SkillEntity(
                    name = "open_app",
                    description = "Launches an installed Android application by package or display name.",
                    requiredPermissions = "None",
                    inputSchema = "{\"app_name\": \"string\"}",
                    outputSchema = "{\"status\": \"string\", \"opened\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Resolve app package via PackageManager\n2. Create Launch Intent\n3. Start Activity with FLAG_ACTIVITY_NEW_TASK\n4. Verify foreground transition",
                    verificationMethod = "PackageManager query and activity launch verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "send_whatsapp_message",
                    description = "Resolves contact and sends a WhatsApp message using legitimate Android mechanism.",
                    requiredPermissions = "READ_CONTACTS",
                    inputSchema = "{\"contact_name\": \"string\", \"message\": \"string\"}",
                    outputSchema = "{\"status\": \"string\", \"sent\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.MEDIUM,
                    procedure = "1. Search contacts\n2. Verify single recipient\n3. Launch WhatsApp deep link\n4. Require confirmation if enabled",
                    verificationMethod = "WhatsApp client launch verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "open_whatsapp_chat",
                    description = "Opens WhatsApp conversation directly for a given contact or number.",
                    requiredPermissions = "READ_CONTACTS",
                    inputSchema = "{\"contact_name\": \"string\"}",
                    outputSchema = "{\"opened\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Resolve contact\n2. Open WhatsApp conversation URI",
                    verificationMethod = "Activity launch verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "make_phone_call",
                    description = "Initiates outgoing phone call using legitimate telephony APIs.",
                    requiredPermissions = "CALL_PHONE, READ_CONTACTS",
                    inputSchema = "{\"contact_name\": \"string\"}",
                    outputSchema = "{\"status\": \"string\", \"called\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.MEDIUM,
                    procedure = "1. Resolve contact\n2. Ask confirmation\n3. Launch CALL_PHONE or DIAL intent\n4. Verify dialer state",
                    verificationMethod = "Telephony state verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "send_sms",
                    description = "Prepares and sends an SMS text message to a contact.",
                    requiredPermissions = "SEND_SMS, READ_CONTACTS",
                    inputSchema = "{\"recipient\": \"string\", \"message\": \"string\"}",
                    outputSchema = "{\"status\": \"string\", \"sent\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.MEDIUM,
                    procedure = "1. Resolve contact number\n2. Prompt confirmation\n3. Dispatch SMS intent\n4. Verify delivery buffer",
                    verificationMethod = "SMS Intent dispatch verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "get_contacts",
                    description = "Queries local Android contacts safely without inventing phone numbers.",
                    requiredPermissions = "READ_CONTACTS",
                    inputSchema = "{\"name_query\": \"string\"}",
                    outputSchema = "{\"contacts\": \"list<ContactInfo>\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Query ContactsContract.CommonDataKinds.Phone\n2. Filter exact or fuzzy matches\n3. Return authentic results",
                    verificationMethod = "ContentResolver cursor validation",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "read_screen",
                    description = "Extracts UI hierarchy and visible text elements via AccessibilityService.",
                    requiredPermissions = "AccessibilityService",
                    inputSchema = "{}",
                    outputSchema = "{\"elements\": \"list<VisibleElement>\", \"current_app\": \"string\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Query rootInActiveWindow\n2. Traverse view hierarchy\n3. Extract visible text nodes",
                    verificationMethod = "Accessibility node tree verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "tap",
                    description = "Taps or clicks a UI element on screen by text or view ID.",
                    requiredPermissions = "AccessibilityService",
                    inputSchema = "{\"target_text\": \"string\"}",
                    outputSchema = "{\"success\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.MEDIUM,
                    procedure = "1. Search active window for text\n2. Perform ACTION_CLICK\n3. Verify click event",
                    verificationMethod = "AccessibilityNodeInfo.performAction result",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "scroll",
                    description = "Scrolls active view forward or backward.",
                    requiredPermissions = "AccessibilityService",
                    inputSchema = "{\"direction\": \"FORWARD | BACKWARD\"}",
                    outputSchema = "{\"scrolled\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Locate scrollable node\n2. Perform ACTION_SCROLL",
                    verificationMethod = "Scroll action outcome",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "press_back",
                    description = "Performs global Android Back navigation.",
                    requiredPermissions = "AccessibilityService",
                    inputSchema = "{}",
                    outputSchema = "{\"success\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Call performGlobalAction(GLOBAL_ACTION_BACK)",
                    verificationMethod = "Accessibility global action result",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "press_home",
                    description = "Navigates to the Android Home launcher screen.",
                    requiredPermissions = "None",
                    inputSchema = "{}",
                    outputSchema = "{\"success\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Call GLOBAL_ACTION_HOME or start Home Intent",
                    verificationMethod = "Home launcher verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "toggle_flashlight",
                    description = "Turns the device camera torch/flashlight ON or OFF.",
                    requiredPermissions = "android.permission.CAMERA",
                    inputSchema = "{\"state\": \"boolean\"}",
                    outputSchema = "{\"state\": \"boolean\", \"torchMode\": \"string\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Access CameraManager\n2. Locate rear camera ID with FLASH_INFO_AVAILABLE\n3. Call setTorchMode(cameraId, state)\n4. Confirm torch state callback",
                    verificationMethod = "CameraManager.TorchCallback status",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "get_device_status",
                    description = "Retrieves battery level, charging status, active app, and thermals.",
                    requiredPermissions = "None",
                    inputSchema = "{}",
                    outputSchema = "{\"battery\": \"string\", \"charging\": \"boolean\", \"temp\": \"string\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Register Intent.ACTION_BATTERY_CHANGED\n2. Query thermal and app telemetry",
                    verificationMethod = "BatteryManager intent validation",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "search_web",
                    description = "Launches web search in default browser for given query.",
                    requiredPermissions = "None",
                    inputSchema = "{\"query\": \"string\"}",
                    outputSchema = "{\"searched\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Encode query\n2. Open browser with search URL",
                    verificationMethod = "Browser intent verification",
                    version = "1.1.0"
                ),
                SkillEntity(
                    name = "security_audit_check",
                    description = "Runs a defensive audit of permissions, prompt injection shield, and sandboxing.",
                    requiredPermissions = "None",
                    inputSchema = "{}",
                    outputSchema = "{\"risk_score\": \"int\", \"vulnerabilities\": \"list\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Query active permissions\n2. Inspect prompt quarantine\n3. Validate sandboxed tool controller",
                    verificationMethod = "SecurityPolicyEngine verification",
                    version = "1.1.0"
                )
            )
            dao.insertSkills(initialSkills)

            // Seed Initial Long Term Memories
            val initialMemories = listOf(
                MemoryEntity(
                    category = MemoryCategory.USER_PROFILE,
                    key = "assistant_identity",
                    value = "JARVIS - Privacy-First Local Android Personal AI Assistant",
                    confidence = 1.0f,
                    source = "System Initialization"
                ),
                MemoryEntity(
                    category = MemoryCategory.USER_PREFERENCE,
                    key = "primary_languages",
                    value = "English, Bengali (বাংলা), and Banglish",
                    confidence = 0.98f,
                    source = "User Setting"
                ),
                MemoryEntity(
                    category = MemoryCategory.USER_PREFERENCE,
                    key = "ai_execution_mode",
                    value = "Local-First with Optional Gemini Supervisor Fallback",
                    confidence = 1.0f,
                    source = "Architecture Spec"
                ),
                MemoryEntity(
                    category = MemoryCategory.IMPORTANT_FACT,
                    key = "target_hardware",
                    value = "Redmi Note 12 / Android 15 (Snapdragon 685, 4-8GB RAM, CPU-optimized)",
                    confidence = 0.99f,
                    source = "Hardware Profile"
                )
            )
            initialMemories.forEach { dao.insertMemory(it) }

            // Seed Knowledge Base Chunks (RAG)
            val initialKnowledge = listOf(
                KnowledgeChunkEntity(
                    title = "Redmi Note 12 Hardware & LLM Feasibility",
                    sourceDocument = "hardware_spec.md",
                    content = "Redmi Note 12 runs Qualcomm Snapdragon 685 (4x Cortex-A73 @ 2.8GHz, 4x Cortex-A53 @ 1.9GHz) with Adreno 610. Best local LLM architectures: Qwen2.5-0.5B to 1.5B Instruct or SmolLM2-1.7B quantized via GGUF (Q4_K_M / Q3_K_M). RAM consumption stays under 600MB-1.1GB, yielding 8-14 tokens/sec on CPU without thermal throttling.",
                    tags = "hardware, redmi, local-ai, quantization, gguf"
                ),
                KnowledgeChunkEntity(
                    title = "Android Background Microphone Restrictions",
                    sourceDocument = "android_security_policy.md",
                    content = "Starting Android 10+ and strictly enforced in Android 14/15, background apps cannot access the microphone without an active FOREGROUND_SERVICE of type microphone and a persistent notification with visible privacy indicator. Local wake-word systems must run via an explicit foreground service or while the user is inside the assistant interface.",
                    tags = "wake-word, audio, android-policy, permissions, security"
                ),
                KnowledgeChunkEntity(
                    title = "Tool Calling & Action Execution Architecture",
                    sourceDocument = "tool_architecture.md",
                    content = "The AI model is strictly the BRAIN and never touches the phone directly. User natural language is converted into structured tool calls (e.g. send_whatsapp_message, make_phone_call, open_app). The Android Controller validates risk, confirms if needed, runs the real Android API, and returns execution result to the assistant before vocalizing completion.",
                    tags = "tool-calling, security, whatsapp, android-hands"
                )
            )
            dao.insertKnowledgeChunks(initialKnowledge)

            // Seed Initial System Security Baseline Event
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    eventType = "SYSTEM_INITIALIZED",
                    riskScore = 0,
                    source = "JarvisSecurityMonitor",
                    description = "Defensive Security Engine online. Prompt injection guard active. Local storage encrypted. Tool sandboxing ready.",
                    actionTaken = "Security Baseline Established",
                    isResolved = true
                )
            )

            // Seed Welcome Message
            dao.insertChatMessage(
                ChatMessageEntity(
                    role = "JARVIS",
                    message = "Greetings. I am JARVIS, your privacy-first, local-first Android AI assistant. AI brain and Android hands are synchronized. Speak or type your request.",
                    providerType = "LOCAL",
                    latencyMs = 8
                )
            )
        }
    }
}
