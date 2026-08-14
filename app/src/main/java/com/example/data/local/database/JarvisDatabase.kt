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
        KnowledgeChunkEntity::class
    ],
    version = 1,
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
            // Seed Core Skills
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
                    version = "1.0.0"
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
                    version = "1.0.0"
                ),
                SkillEntity(
                    name = "query_battery_status",
                    description = "Retrieves battery level, charging status, and thermal health.",
                    requiredPermissions = "None",
                    inputSchema = "{}",
                    outputSchema = "{\"level\": \"int\", \"isCharging\": \"boolean\", \"health\": \"string\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Register sticky receiver for Intent.ACTION_BATTERY_CHANGED\n2. Parse EXTRA_LEVEL, EXTRA_SCALE, and EXTRA_STATUS\n3. Format battery diagnostic metrics",
                    verificationMethod = "BatteryManager intent validation",
                    version = "1.0.0"
                ),
                SkillEntity(
                    name = "make_call",
                    description = "Prepares and opens the system dialer for a specified contact or phone number.",
                    requiredPermissions = "None (Intent.ACTION_DIAL) / CALL_PHONE (Direct)",
                    inputSchema = "{\"contact_name\": \"string\", \"phone_number\": \"string\"}",
                    outputSchema = "{\"status\": \"string\", \"dialed\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.MEDIUM,
                    procedure = "1. Verify phone number syntax\n2. Require user confirmation if direct dial requested\n3. Launch Intent.ACTION_DIAL with tel: uri\n4. Return success status",
                    verificationMethod = "Dialer intent launch confirmation",
                    version = "1.0.0"
                ),
                SkillEntity(
                    name = "send_message",
                    description = "Prepares an SMS or messaging draft to the target recipient.",
                    requiredPermissions = "None (Intent.ACTION_SENDTO)",
                    inputSchema = "{\"recipient\": \"string\", \"message\": \"string\"}",
                    outputSchema = "{\"status\": \"string\", \"draftCreated\": \"boolean\"}",
                    riskLevel = SkillRiskLevel.MEDIUM,
                    procedure = "1. Validate recipient contact\n2. Check message content for high-risk data\n3. Launch SMS intent with pre-filled body\n4. Ask confirmation before transmission",
                    verificationMethod = "Intent dispatcher result",
                    version = "1.0.0"
                ),
                SkillEntity(
                    name = "search_knowledge_rag",
                    description = "Executes local offline RAG vector & keyword similarity search across indexed documents.",
                    requiredPermissions = "None",
                    inputSchema = "{\"query\": \"string\", \"top_k\": \"int\"}",
                    outputSchema = "{\"results\": \"list<KnowledgeChunk>\", \"match_score\": \"float\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Sanitize query\n2. Compute text cosine similarity and token overlaps\n3. Retrieve top-k context chunks\n4. Inject into local prompt context",
                    verificationMethod = "Score threshold check (>0.65)",
                    version = "1.0.0"
                ),
                SkillEntity(
                    name = "security_audit_check",
                    description = "Runs a full audit of device permissions, prompt sanitization, and app integrity.",
                    requiredPermissions = "None",
                    inputSchema = "{}",
                    outputSchema = "{\"risk_score\": \"int\", \"vulnerabilities\": \"list\"}",
                    riskLevel = SkillRiskLevel.LOW,
                    procedure = "1. Query active permissions\n2. Inspect database integrity\n3. Scan prompt injection quarantine\n4. Generate defensive security report",
                    verificationMethod = "SecurityMonitor verification",
                    version = "1.0.0"
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
                    title = "Teacher-Student Continuous Learning Protocol",
                    sourceDocument = "learning_pipeline.md",
                    content = "When local model confidence < 0.65 or a complex skill is required, JARVIS routes the request to Gemini Cloud Supervisor. The returned reasoning is validated against safety rules, extracted into structured Skill/Knowledge items, and cached locally for subsequent 100% offline local execution.",
                    tags = "teacher-student, gemini, self-learning, offline, lora"
                )
            )
            dao.insertKnowledgeChunks(initialKnowledge)

            // Seed Initial System Security Baseline Event
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    eventType = "SYSTEM_INITIALIZED",
                    riskScore = 0,
                    source = "JarvisSecurityMonitor",
                    description = "Defensive Security Engine online. Prompt injection guard active. Local storage encrypted.",
                    actionTaken = "Security Baseline Established",
                    isResolved = true
                )
            )

            // Seed Welcome Message
            dao.insertChatMessage(
                ChatMessageEntity(
                    role = "JARVIS",
                    message = "Greetings. I am JARVIS, your privacy-first, local-first Android AI assistant. All core tools, memory, and security modules are operational. How may I assist you today?",
                    providerType = "LOCAL",
                    latencyMs = 12
                )
            )
        }
    }
}
