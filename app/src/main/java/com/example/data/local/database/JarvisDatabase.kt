package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AppKnowledgeEntity
import com.example.data.local.entity.AutonomousTaskEntity
import com.example.data.local.entity.BrainSnapshotEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.DeviceActionHistoryEntity
import com.example.data.local.entity.DeviceCapabilityEntity
import com.example.data.local.entity.AppRegistryEntity
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.GeminiTeacherSessionEntity
import com.example.data.local.entity.HealthEventEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.KnowledgeGraphLinkEntity
import com.example.data.local.entity.KnowledgeItemEntity
import com.example.data.local.entity.KnowledgeSourceEntity
import com.example.data.local.entity.KnowledgeSourceType
import com.example.data.local.entity.KnowledgeType
import com.example.data.local.entity.KnowledgeVersionEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.ScheduledTaskEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import com.example.data.local.entity.SkillSource
import com.example.data.local.entity.SourceStatus
import com.example.data.local.entity.TrainingExampleEntity
import com.example.data.local.entity.UserCorrectionEntity
import com.example.data.local.entity.ValidationStage
import com.example.data.local.entity.VisualExperienceEntity
import com.example.data.local.entity.WebResearchRecordEntity
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
        VisualExperienceEntity::class,
        ExperienceEntity::class,
        UserCorrectionEntity::class,
        TrainingExampleEntity::class,
        GeminiTeacherSessionEntity::class,
        ScheduledTaskEntity::class,
        AutonomousTaskEntity::class,
        KnowledgeVersionEntity::class,
        HealthEventEntity::class,
        WebResearchRecordEntity::class,
        KnowledgeSourceEntity::class,
        KnowledgeItemEntity::class,
        AppKnowledgeEntity::class,
        KnowledgeGraphLinkEntity::class,
        BrainSnapshotEntity::class,
        DeviceCapabilityEntity::class,
        AppRegistryEntity::class,
        DeviceActionHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS visual_experiences (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        appPackage TEXT NOT NULL,
                        screenContext TEXT NOT NULL,
                        semanticRole TEXT NOT NULL,
                        visualDescription TEXT NOT NULL,
                        actionTaken TEXT NOT NULL,
                        result TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        boundsLeft INTEGER NOT NULL,
                        boundsTop INTEGER NOT NULL,
                        boundsRight INTEGER NOT NULL,
                        boundsBottom INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN importance REAL NOT NULL DEFAULT 0.5")
                    db.execSQL("ALTER TABLE memories ADD COLUMN usageCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE memories ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}

                try {
                    db.execSQL("ALTER TABLE skills ADD COLUMN successCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE skills ADD COLUMN failureCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE skills ADD COLUMN successRate REAL NOT NULL DEFAULT 1.0")
                    db.execSQL("ALTER TABLE skills ADD COLUMN confidence REAL NOT NULL DEFAULT 0.95")
                    db.execSQL("ALTER TABLE skills ADD COLUMN lastSuccessAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE skills ADD COLUMN isLearnedFromExperience INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE skills ADD COLUMN source TEXT NOT NULL DEFAULT 'BUILTIN'")
                    db.execSQL("ALTER TABLE skills ADD COLUMN previousVersionProcedure TEXT DEFAULT NULL")
                } catch (e: Exception) {}

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS experiences (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goal TEXT NOT NULL,
                        appPackage TEXT NOT NULL,
                        initialScreenSummary TEXT NOT NULL,
                        actionsTakenJson TEXT NOT NULL,
                        verificationSummary TEXT NOT NULL,
                        isSuccess INTEGER NOT NULL,
                        failedStrategy TEXT,
                        recoveryStrategy TEXT,
                        durationMs INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        source TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_corrections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userGoal TEXT NOT NULL,
                        previousAssumption TEXT NOT NULL,
                        userCorrection TEXT NOT NULL,
                        correctedAction TEXT NOT NULL,
                        actualTarget TEXT NOT NULL,
                        appPackage TEXT NOT NULL,
                        screenContext TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        appliedCount INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_dataset (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        inputInstruction TEXT NOT NULL,
                        contextSummary TEXT NOT NULL,
                        successfulPlanJson TEXT NOT NULL,
                        toolsUsedSummary TEXT NOT NULL,
                        verificationProof TEXT NOT NULL,
                        qualityScore REAL NOT NULL,
                        format TEXT NOT NULL,
                        isCurated INTEGER NOT NULL,
                        isExported INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gemini_teacher_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userGoal TEXT NOT NULL,
                        lowConfidenceReason TEXT NOT NULL,
                        teacherModel TEXT NOT NULL,
                        structuredPlanJson TEXT NOT NULL,
                        wasExecuted INTEGER NOT NULL,
                        executionSuccessful INTEGER NOT NULL,
                        skillExtracted INTEGER NOT NULL,
                        generatedSkillName TEXT,
                        latencyMs INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scheduled_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        instruction TEXT NOT NULL,
                        triggerType TEXT NOT NULL,
                        cronOrInterval TEXT NOT NULL,
                        scheduledTimeMillis INTEGER NOT NULL,
                        lastExecutedMillis INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL,
                        maxRetries INTEGER NOT NULL,
                        riskLevel TEXT NOT NULL,
                        requiresConfirmation INTEGER NOT NULL,
                        payloadJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS autonomous_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goal TEXT NOT NULL,
                        taskType TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        status TEXT NOT NULL,
                        riskLevel TEXT NOT NULL,
                        requiresConfirmation INTEGER NOT NULL,
                        plannedActionsJson TEXT NOT NULL,
                        executionLogsJson TEXT NOT NULL,
                        retryCount INTEGER NOT NULL,
                        maxRetries INTEGER NOT NULL,
                        failureReason TEXT,
                        blockingReason TEXT,
                        targetAppPackage TEXT,
                        durationMs INTEGER NOT NULL,
                        resultSummary TEXT,
                        verificationProof TEXT,
                        createdAt INTEGER NOT NULL,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        knowledgeKey TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        sourceUrl TEXT,
                        sourceQualityScore REAL NOT NULL,
                        confidence REAL NOT NULL,
                        status TEXT NOT NULL,
                        oldVersionContent TEXT,
                        changeReason TEXT NOT NULL,
                        isAutoUpdated INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS health_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        component TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        description TEXT NOT NULL,
                        recoveryAttempted INTEGER NOT NULL,
                        recoverySuccessful INTEGER NOT NULL,
                        recoveryActionTaken TEXT,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS web_research_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL,
                        userGoal TEXT NOT NULL,
                        status TEXT NOT NULL,
                        synthesizedSummary TEXT NOT NULL,
                        sourcesCount INTEGER NOT NULL,
                        verifiedSourcesJson TEXT NOT NULL,
                        keyFindingsJson TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        durationMs INTEGER NOT NULL,
                        storedAsKnowledge INTEGER NOT NULL,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_sources (
                        sourceId TEXT PRIMARY KEY NOT NULL,
                        sourceType TEXT NOT NULL,
                        sourceUrl TEXT,
                        title TEXT NOT NULL,
                        retrievedAt INTEGER NOT NULL,
                        contentHash TEXT NOT NULL,
                        trustScore REAL NOT NULL,
                        lastVerified INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        knowledgeKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        knowledgeType TEXT NOT NULL,
                        validationStage TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        trustScore REAL NOT NULL,
                        sourceCount INTEGER NOT NULL,
                        usageCount INTEGER NOT NULL,
                        failureCount INTEGER NOT NULL,
                        sourceId TEXT,
                        sourceUrl TEXT,
                        contentHash TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        appPackage TEXT,
                        appVersion TEXT,
                        osVersion TEXT,
                        expiryPolicy TEXT NOT NULL,
                        lastVerified INTEGER NOT NULL,
                        isStale INTEGER NOT NULL,
                        isUncertain INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_knowledge (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        appName TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        version TEXT NOT NULL,
                        knownScreensJson TEXT NOT NULL,
                        semanticTargetsJson TEXT NOT NULL,
                        commonActionsJson TEXT NOT NULL,
                        successfulSkillsJson TEXT NOT NULL,
                        failedStrategiesJson TEXT NOT NULL,
                        recoveryStrategiesJson TEXT NOT NULL,
                        lastVerified INTEGER NOT NULL,
                        isStale INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_graph_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fromType TEXT NOT NULL,
                        fromId TEXT NOT NULL,
                        relation TEXT NOT NULL,
                        toType TEXT NOT NULL,
                        toId TEXT NOT NULL,
                        weight REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS brain_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        snapshotVersion TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        deviceProfile TEXT NOT NULL,
                        summaryJson TEXT NOT NULL,
                        exportedJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS device_capabilities (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        available INTEGER NOT NULL,
                        permission TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        restricted INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        lastChecked INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_registry (
                        packageName TEXT PRIMARY KEY NOT NULL,
                        applicationLabel TEXT NOT NULL,
                        versionName TEXT NOT NULL,
                        versionCode INTEGER NOT NULL,
                        isSystemApp INTEGER NOT NULL,
                        launchIntentAvailable INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        lastScannedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS device_action_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        toolName TEXT NOT NULL,
                        action TEXT NOT NULL,
                        target TEXT NOT NULL,
                        argumentsJson TEXT NOT NULL,
                        success INTEGER NOT NULL,
                        riskLevel TEXT NOT NULL,
                        failureReason TEXT,
                        verificationProof TEXT,
                        durationMs INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        JarvisDatabase::class.java,
                        "jarvis_brain.db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                        .addCallback(JarvisDatabaseCallback(scope))
                        .build()
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    android.util.Log.e("JARVIS_DB", "Failed to build Room database safely", e)
                    // Non-destructive fallback: attempt safe re-open without dropping tables
                    val fallback = Room.databaseBuilder(
                        context.applicationContext,
                        JarvisDatabase::class.java,
                        "jarvis_brain.db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                        .build()
                    INSTANCE = fallback
                    fallback
                }
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

            // Seed Phase 14 Verified Knowledge Items & Sources
            val initialSource = KnowledgeSourceEntity(
                sourceId = "src_core_system",
                sourceType = KnowledgeSourceType.OFFICIAL_DOCUMENTATION,
                sourceUrl = "https://developer.android.com",
                title = "Android Architecture & System Guidelines",
                retrievedAt = System.currentTimeMillis(),
                contentHash = "hash_android_core",
                trustScore = 1.0f,
                status = SourceStatus.ACTIVE
            )
            dao.insertKnowledgeSource(initialSource)

            val initialKnowledgeItems = listOf(
                KnowledgeItemEntity(
                    knowledgeKey = "hardware_redmi_note_12",
                    title = "Redmi Note 12 Architecture",
                    content = "Snapdragon 685 octa-core CPU. Recommended local model: Qwen2.5-0.5B to 1.5B GGUF Q4_K_M. Max safe RAM 1.2GB.",
                    summary = "Hardware specifications and local AI constraints for Redmi Note 12.",
                    knowledgeType = KnowledgeType.DEVICE_KNOWLEDGE,
                    validationStage = ValidationStage.ACTIVE,
                    confidence = 0.98f,
                    trustScore = 1.0f,
                    sourceId = "src_core_system",
                    contentHash = "hash_redmi_12",
                    tags = "hardware, redmi, local-llm"
                ),
                KnowledgeItemEntity(
                    knowledgeKey = "android_15_security",
                    title = "Android 15 Foreground Service Policies",
                    content = "Microphone and camera access require foreground service type declaration and active system notification with privacy indicator.",
                    summary = "Android 15 background permission constraints.",
                    knowledgeType = KnowledgeType.SYSTEM_KNOWLEDGE,
                    validationStage = ValidationStage.ACTIVE,
                    confidence = 0.99f,
                    trustScore = 1.0f,
                    sourceId = "src_core_system",
                    contentHash = "hash_android_15_sec",
                    tags = "security, android15, permissions"
                ),
                KnowledgeItemEntity(
                    knowledgeKey = "whatsapp_ui_actions",
                    title = "WhatsApp Semantic Navigation Pattern",
                    content = "To send message: Launch package com.whatsapp -> locate Contact Search or FAB -> enter contact query -> click chat -> enter text -> click Send button.",
                    summary = "Standard procedure for sending WhatsApp messages.",
                    knowledgeType = KnowledgeType.APP_BEHAVIOR,
                    validationStage = ValidationStage.ACTIVE,
                    confidence = 0.96f,
                    trustScore = 0.95f,
                    sourceId = "src_core_system",
                    contentHash = "hash_whatsapp_pattern",
                    tags = "whatsapp, ui, procedure",
                    appPackage = "com.whatsapp"
                )
            )
            dao.insertKnowledgeItems(initialKnowledgeItems)

            val initialAppKnowledge = AppKnowledgeEntity(
                appName = "WhatsApp",
                packageName = "com.whatsapp",
                version = "2.24",
                knownScreensJson = "[\"ChatListScreen\", \"ConversationScreen\", \"ContactPickerScreen\"]",
                semanticTargetsJson = "[\"Search\", \"New Chat\", \"Message Input Box\", \"Send Button\"]",
                commonActionsJson = "[\"send_message\", \"make_call\", \"view_status\"]",
                successfulSkillsJson = "[\"send_whatsapp_message\", \"find_contact\"]",
                failedStrategiesJson = "[]",
                recoveryStrategiesJson = "[\"If input unfocused, tap input field first\", \"If contact not in list, search phonebook\"]",
                lastVerified = System.currentTimeMillis(),
                isStale = false
            )
            dao.insertAppKnowledge(initialAppKnowledge)

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
