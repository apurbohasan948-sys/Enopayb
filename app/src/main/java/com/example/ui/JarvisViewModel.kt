package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.accessibility.AccessibilityDiagnostics
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.agent.AgentController
import com.example.core.agent.UniversalTask
import com.example.core.communication.CommunicationHistoryTracker
import com.example.core.communication.CommunicationIntent
import com.example.core.communication.CommunicationIntentParser
import com.example.core.communication.CommunicationTelemetry
import com.example.core.contacts.ContactResolutionResult
import com.example.core.contacts.ContactResolver
import com.example.core.learning.LearningMetrics
import com.example.core.learning.TeacherStudentPipeline
import com.example.core.model.ActiveModelType
import com.example.core.model.GeminiModelProvider
import com.example.core.model.HybridModelProvider
import com.example.core.model.LocalModelProvider
import com.example.core.model.ModelProvider
import com.example.core.model.ModelResponse
import com.example.core.model.ToolIntent
import com.example.core.rag.RagEngine
import com.example.core.security.SecurityPolicyEngine
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolRouter
import com.example.core.vision.GeminiVisionProvider
import com.example.core.vision.HybridVisionProvider
import com.example.core.vision.LocalVisionProvider
import com.example.core.vision.ScreenUnderstandingEngine
import com.example.core.vision.SemanticTarget
import com.example.core.vision.UnifiedScreen
import com.example.core.vision.VisualElement
import com.example.core.voice.VoiceManager
import com.example.core.voice.VoiceState
import com.example.core.voice.assistant.AssistantRoleHelper
import com.example.core.voice.context.VoiceConversationContext
import com.example.core.voice.service.JarvisOverlayService
import com.example.core.voice.service.JarvisVoiceForegroundService
import com.example.core.voice.wake.WakeSensitivity
import com.example.data.local.database.JarvisDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.VisualExperienceEntity
import com.example.core.autonomy.AutonomyMode
import com.example.core.autonomy.AutonomyPolicyConfig
import com.example.core.autonomy.AutonomousAgentManager
import com.example.core.autonomy.MasterStopManager
import com.example.core.health.ResourceMode
import com.example.core.health.ResourceSnapshot
import com.example.core.health.SystemHealthReport
import com.example.core.model.LocalSLMModelProvider
import com.example.core.research.KnowledgeUpdateProposal
import com.example.data.local.entity.AutonomousTaskEntity
import com.example.data.local.entity.AutonomousTaskPriority
import com.example.data.local.entity.AutonomousTaskType
import com.example.data.local.entity.HealthEventEntity
import com.example.data.local.entity.HealthSeverity
import com.example.data.local.entity.KnowledgeVersionEntity
import com.example.data.local.entity.ScheduleTriggerType
import com.example.data.local.entity.ScheduledTaskEntity
import com.example.data.local.entity.WebResearchRecordEntity
import com.example.data.local.preference.JarvisPreferences
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ApiTestStatus {
    object Idle : ApiTestStatus()
    object Testing : ApiTestStatus()
    data class Success(val message: String) : ApiTestStatus()
    data class Error(val message: String) : ApiTestStatus()
}

data class HardwareMetrics(
    val deviceModel: String = "Redmi Note 12 (Android 15)",
    val cpuArchitecture: String = "Snapdragon 685 (8-Core Kryo)",
    val ramAllocatedMb: Int = 840,
    val ramTotalMb: Int = 4096,
    val averageInferenceLatencyMs: Long = 52,
    val offlineReadinessScore: Int = 100,
    val cpuTempCelsius: Float = 33.5f,
    val activeGgufModel: String = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getDatabase(application, viewModelScope)
    private val repository = JarvisRepository(database.jarvisDao())

    val preferences = JarvisPreferences(application)
    val memoryManager = com.example.core.memory.MemoryManager(database.jarvisDao())
    val memoryRetriever = com.example.core.memory.MemoryRetriever(database.jarvisDao())
    val experienceManager = com.example.core.learning.ExperienceManager(database.jarvisDao())
    val skillManager = com.example.core.learning.SkillManager(database.jarvisDao())
    val userCorrectionLearner = com.example.core.learning.UserCorrectionLearner(database.jarvisDao())
    val geminiTeacher = com.example.core.learning.GeminiTeacher(database.jarvisDao(), preferences)
    val trainingDatasetManager = com.example.core.learning.TrainingDatasetManager(database.jarvisDao())
    val localModelTrainer = com.example.core.learning.LocalModelTrainer(database.jarvisDao())

    // Phase 9: Engines and Managers
    val cloudUsagePolicy = com.example.core.model.CloudUsagePolicy(application)
    val performanceMonitor = com.example.core.model.PerformanceMonitor(application)
    val modelProfileManager = com.example.core.model.ModelProfileManager(application)
    val modelLifecycleManager = com.example.core.model.ModelLifecycleManager(application, viewModelScope)
    val screenStateCache = com.example.core.vision.ScreenStateCache()
    val actionCache = com.example.core.agent.ActionCache()
    val memoryMaintenanceEngine = com.example.core.memory.MemoryMaintenanceEngine(database.jarvisDao())
    val skillOptimizer = com.example.core.learning.SkillOptimizer(database.jarvisDao())
    val brainBackupManager = com.example.core.memory.BrainBackupManager(application, database.jarvisDao())
    val modelUpdateManager = com.example.core.model.ModelUpdateManager(application)
    val crashRecoveryManager = com.example.core.health.CrashRecoveryManager(application, database.jarvisDao())
    val modelProviderManager = com.example.core.model.ModelProviderManager(application, preferences)
    val localKnowledgeRetriever = com.example.core.rag.LocalKnowledgeRetriever(database.jarvisDao())

    // Phase 14: Long-Term Brain / Knowledge Growth Engine
    val knowledgeSourceManager = com.example.core.knowledge.KnowledgeSourceManager(database.jarvisDao())
    val knowledgeValidator = com.example.core.knowledge.KnowledgeValidator(SecurityPolicyEngine)
    val knowledgeIngestionEngine = com.example.core.knowledge.KnowledgeIngestionEngine(
        database.jarvisDao(),
        knowledgeSourceManager,
        knowledgeValidator
    )
    val appKnowledgeManager = com.example.core.knowledge.AppKnowledgeManager(database.jarvisDao())
    val knowledgeGraph = com.example.core.knowledge.KnowledgeGraph(database.jarvisDao())
    val selfImprovementGuard = com.example.core.knowledge.SelfImprovementGuard(SecurityPolicyEngine)
    val researchPolicy = com.example.core.research.ResearchPolicy()
    val brainStorageManager = com.example.core.brain.BrainStorageManager(database.jarvisDao())
    val phase14BackupManager = com.example.core.brain.BrainBackupManager(
        database.jarvisDao(),
        knowledgeIngestionEngine
    )
    val knowledgeTeachingLoop = com.example.core.knowledge.KnowledgeTeachingLoop(
        database.jarvisDao(),
        localKnowledgeRetriever,
        knowledgeIngestionEngine,
        researchPolicy,
        cloudUsagePolicy,
        SecurityPolicyEngine
    )

    val voiceManager = VoiceManager(application)
    val capabilityManager = com.example.core.capability.CapabilityManager(application)

    val localProvider = LocalModelProvider()
    val geminiProvider = GeminiModelProvider()
    val hybridProvider = HybridModelProvider(localProvider, geminiProvider)

    val localVisionProvider = LocalVisionProvider()
    val geminiVisionProvider = GeminiVisionProvider()
    val hybridVisionProvider = HybridVisionProvider(localVisionProvider, geminiVisionProvider, repository)
    val visionRouter = com.example.core.vision.VisionRouter(localVisionProvider, geminiVisionProvider, cloudUsagePolicy)
    val screenEngine = ScreenUnderstandingEngine(application, hybridVisionProvider, repository)

    private val toolRouter = ToolRouter(application, screenEngine, database.jarvisDao())
    val agentController = AgentController(application, toolRouter, screenEngine)

    // Phase 15: Device Managers & Providers
    val deviceCapabilityManager = toolRouter.deviceCapabilityManager
    val appManager = toolRouter.appManager
    val deviceStatusProvider = toolRouter.deviceStatusProvider
    val mediaControllerBridge = toolRouter.mediaControllerBridge
    val flashlightController = toolRouter.flashlightController
    val settingsNavigator = toolRouter.settingsNavigator
    val fileAccessManager = toolRouter.fileAccessManager
    val deviceSecurityAudit = toolRouter.deviceSecurityAudit

    val registeredAppsList: StateFlow<List<com.example.data.local.entity.AppRegistryEntity>> = repository.allRegisteredApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceActionHistory: StateFlow<List<com.example.data.local.entity.DeviceActionHistoryEntity>> = repository.recentDeviceActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 8 & 9 autonomous dependencies
    val localSLMProvider = LocalSLMModelProvider(application)
    val offlineManager = com.example.core.model.OfflineManager(
        application,
        com.example.core.health.NetworkStateMonitor(application)
    )

    val modelRouter = com.example.core.model.ModelRouter(
        memoryRetriever,
        skillManager,
        preferences,
        cloudUsagePolicy,
        offlineManager
    )

    val worldModel = com.example.core.agent.DeviceWorldModel(application, capabilityManager)
    val jarvisAgentCore = com.example.core.agent.JarvisAgentCore(
        context = application,
        toolRouter = toolRouter,
        screenEngine = screenEngine,
        worldModel = worldModel,
        repository = repository,
        voiceManager = voiceManager,
        preferences = preferences,
        memoryManager = memoryManager,
        memoryRetriever = memoryRetriever,
        experienceManager = experienceManager,
        skillManager = skillManager,
        userCorrectionLearner = userCorrectionLearner,
        geminiTeacher = geminiTeacher,
        trainingDatasetManager = trainingDatasetManager,
        modelRouter = modelRouter
    )

    val autonomousAgentManager = AutonomousAgentManager(
        context = application,
        dao = database.jarvisDao(),
        capabilityManager = capabilityManager,
        securityPolicyEngine = SecurityPolicyEngine,
        geminiProvider = geminiProvider,
        localSLMProvider = localSLMProvider,
        agentCoreProvider = { jarvisAgentCore },
        coroutineScope = viewModelScope
    )

    private val _capabilitiesList = MutableStateFlow(capabilityManager.getAllCapabilities())
    val capabilitiesList: StateFlow<List<com.example.core.capability.CapabilityItem>> = _capabilitiesList.asStateFlow()

    // Data Streams from Room
    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMemories: StateFlow<List<MemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSkills: StateFlow<List<SkillEntity>> = repository.allSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExperiences: StateFlow<List<com.example.data.local.entity.ExperienceEntity>> = repository.allExperiences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUserCorrections: StateFlow<List<com.example.data.local.entity.UserCorrectionEntity>> = repository.allUserCorrections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTrainingExamples: StateFlow<List<com.example.data.local.entity.TrainingExampleEntity>> = repository.allTrainingExamples
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTeacherSessions: StateFlow<List<com.example.data.local.entity.GeminiTeacherSessionEntity>> = repository.allTeacherSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _learningMetrics = MutableStateFlow(com.example.core.learning.LearningMetrics())
    val learningMetrics: StateFlow<com.example.core.learning.LearningMetrics> = _learningMetrics.asStateFlow()

    val securityEvents: StateFlow<List<SecurityEventEntity>> = repository.allSecurityEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allKnowledgeChunks: StateFlow<List<KnowledgeChunkEntity>> = repository.allKnowledgeChunks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visualExperiences: StateFlow<List<VisualExperienceEntity>> = repository.allVisualExperiences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 8: Autonomous Streams
    val autonomyMode: StateFlow<AutonomyMode> = autonomousAgentManager.autonomyMode
    val autonomyPolicyConfig: StateFlow<AutonomyPolicyConfig> = autonomousAgentManager.policyConfig
    val isEmergencyStopActive: StateFlow<Boolean> = MasterStopManager.isEmergencyStopActive
    val lastEmergencyStopReason: StateFlow<String?> = MasterStopManager.lastStopReason

    val allAutonomousTasks: StateFlow<List<AutonomousTaskEntity>> = database.jarvisDao().getAllAutonomousTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRunningAutonomousTask: StateFlow<AutonomousTaskEntity?> = autonomousAgentManager.taskQueue.activeRunningTask

    val allScheduledTasks: StateFlow<List<ScheduledTaskEntity>> = database.jarvisDao().getAllScheduledTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allKnowledgeVersions: StateFlow<List<KnowledgeVersionEntity>> = database.jarvisDao().getAllKnowledgeVersions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWebResearchRecords: StateFlow<List<WebResearchRecordEntity>> = database.jarvisDao().getAllWebResearchRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHealthEvents: StateFlow<List<HealthEventEntity>> = database.jarvisDao().getAllHealthEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 14: Long-Term Brain State Streams
    val allKnowledgeSources: StateFlow<List<com.example.data.local.entity.KnowledgeSourceEntity>> = knowledgeSourceManager.allSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allKnowledgeItems: StateFlow<List<com.example.data.local.entity.KnowledgeItemEntity>> = database.jarvisDao().getAllKnowledgeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppKnowledge: StateFlow<List<com.example.data.local.entity.AppKnowledgeEntity>> = appKnowledgeManager.allAppKnowledge
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBrainSnapshots: StateFlow<List<com.example.data.local.entity.BrainSnapshotEntity>> = database.jarvisDao().getAllBrainSnapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _brainStorageStats = MutableStateFlow<com.example.core.brain.BrainStorageStats?>(null)
    val brainStorageStats: StateFlow<com.example.core.brain.BrainStorageStats?> = _brainStorageStats.asStateFlow()

    val systemHealthReport: StateFlow<SystemHealthReport> = autonomousAgentManager.healthMonitor.healthReport
    val resourceSnapshot: StateFlow<ResourceSnapshot> = autonomousAgentManager.resourceManager.currentSnapshot

    // Vision Streams (Phase 10: Semantic UI Understanding)
    val iconRecognizer = screenEngine.iconRecognizer
    val universalEngine = screenEngine.universalEngine
    val actionExecutor = screenEngine.actionExecutor
    val diffEngine = com.example.core.vision.ScreenDiffEngine()
    val targetMatcher = screenEngine.targetMatcher
    val latestUnifiedScreen: StateFlow<UnifiedScreen?> = screenEngine.latestUnifiedScreen
    val latestSemanticScreen: StateFlow<com.example.core.vision.SemanticScreenModel?> = universalEngine.latestSemanticScreen
    val latestScreenshotBitmap: StateFlow<Bitmap?> = screenEngine.latestScreenshotBitmap
    val lastDetectedElements: StateFlow<List<VisualElement>> = screenEngine.lastDetectedElements

    private val _latestScreenDiff = MutableStateFlow<com.example.core.vision.ScreenDiffResult?>(null)
    val latestScreenDiff: StateFlow<com.example.core.vision.ScreenDiffResult?> = _latestScreenDiff.asStateFlow()

    private val _latestMatchedTarget = MutableStateFlow<com.example.core.vision.MatchedTargetResult?>(null)
    val latestMatchedTarget: StateFlow<com.example.core.vision.MatchedTargetResult?> = _latestMatchedTarget.asStateFlow()

    private val _semanticActionStatus = MutableStateFlow<String?>(null)
    val semanticActionStatus: StateFlow<String?> = _semanticActionStatus.asStateFlow()

    private val _isCloudVisionEnabled = MutableStateFlow(true)
    val isCloudVisionEnabled: StateFlow<Boolean> = _isCloudVisionEnabled.asStateFlow()

    // UI States
    private val _activeModelType = MutableStateFlow(ActiveModelType.HYBRID_SUPERVISED)
    val activeModelType: StateFlow<ActiveModelType> = _activeModelType.asStateFlow()

    private val _currentLanguage = MutableStateFlow("EN")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _isSecurityShieldActive = MutableStateFlow(true)
    val isSecurityShieldActive: StateFlow<Boolean> = _isSecurityShieldActive.asStateFlow()

    private val _pendingConfirmationIntent = MutableStateFlow<ToolIntent?>(null)
    val pendingConfirmationIntent: StateFlow<ToolIntent?> = _pendingConfirmationIntent.asStateFlow()

    private val _lastExecutionResult = MutableStateFlow<ToolExecutionResult?>(null)
    val lastExecutionResult: StateFlow<ToolExecutionResult?> = _lastExecutionResult.asStateFlow()

    private val _hardwareMetrics = MutableStateFlow(HardwareMetrics())
    val hardwareMetrics: StateFlow<HardwareMetrics> = _hardwareMetrics.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _ragTestResults = MutableStateFlow<List<Pair<KnowledgeChunkEntity, Float>>>(emptyList())
    val ragTestResults: StateFlow<List<Pair<KnowledgeChunkEntity, Float>>> = _ragTestResults.asStateFlow()

    private val _accessibilityDiagnostics = MutableStateFlow(
        JarvisAccessibilityService.getDiagnostics(application)
    )
    val accessibilityDiagnostics: StateFlow<AccessibilityDiagnostics> = _accessibilityDiagnostics.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceManager.voiceState
    val audioWaveLevel: StateFlow<Float> = voiceManager.audioWaveLevel
    val liveSpokenText: StateFlow<String> = voiceManager.liveSpokenText
    val conversationContext: StateFlow<VoiceConversationContext> = voiceManager.conversationContext
    val isMicrophoneMuted: StateFlow<Boolean> = voiceManager.isMicrophoneMuted
    val isCloudAllowed: StateFlow<Boolean> = voiceManager.isCloudAllowed
    val isOverlayActive: StateFlow<Boolean> = JarvisOverlayService.isOverlayActive
    val isVoiceServiceRunning: StateFlow<Boolean> = JarvisVoiceForegroundService.isRunning

    private val _isDefaultAssistant = MutableStateFlow(false)
    val isDefaultAssistant: StateFlow<Boolean> = _isDefaultAssistant.asStateFlow()

    val communicationTelemetry: StateFlow<CommunicationTelemetry> = CommunicationHistoryTracker.telemetry
    val communicationHistory: StateFlow<List<CommunicationTelemetry>> = CommunicationHistoryTracker.history

    private val _geminiApiKey = MutableStateFlow(preferences.geminiApiKey)
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _selectedGeminiModel = MutableStateFlow(preferences.geminiModel)
    val selectedGeminiModel: StateFlow<String> = _selectedGeminiModel.asStateFlow()

    private val _geminiTemperature = MutableStateFlow(preferences.temperature)
    val geminiTemperature: StateFlow<Float> = _geminiTemperature.asStateFlow()

    private val _customSystemPrompt = MutableStateFlow(preferences.systemPrompt)
    val customSystemPrompt: StateFlow<String> = _customSystemPrompt.asStateFlow()

    private val _apiTestStatus = MutableStateFlow<ApiTestStatus>(ApiTestStatus.Idle)
    val apiTestStatus: StateFlow<ApiTestStatus> = _apiTestStatus.asStateFlow()

    init {
        try {
            com.example.core.health.CrashReporter.currentScreen = "Main Console"
            com.example.core.health.CrashReporter.currentService = "JarvisViewModel"
            com.example.core.health.CrashReporter.lastAction = "Initializing ViewModel"

            voiceManager.agentCore = jarvisAgentCore
            voiceManager.toolRouter = toolRouter
            voiceManager.repository = repository
            voiceManager.currentLanguage = _currentLanguage.value

            syncGeminiProviderSettings()

            try {
                refreshAccessibilityDiagnostics()
            } catch (e: Exception) {
                android.util.Log.w("JarvisViewModel", "Accessibility diag init warning", e)
            }

            // Asynchronous, non-blocking setup on IO dispatcher
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    setupPersistentBrainDirectories()
                    performStartupCrashRecovery()
                    performanceMonitor.captureMemorySnapshot()
                } catch (e: Exception) {
                    android.util.Log.w("JarvisViewModel", "Startup IO background task warning: ${e.message}")
                }
            }

            // Mark successful UI launch
            com.example.core.health.CrashReporter.markSuccessfulStartup(application)
            android.util.Log.i("JarvisViewModel", "JarvisViewModel initialized successfully.")
        } catch (e: Throwable) {
            android.util.Log.e("JarvisViewModel", "Startup initialization caught fatal error", e)
            com.example.core.health.CrashReporter.recordCrash(application, e, overrideService = "JarvisViewModel", overrideAction = "init")
        }
    }

    private fun setupPersistentBrainDirectories() {
        val dirs = listOf("brain", "memory", "skills", "experiences", "knowledge", "models", "logs", "training_dataset")
        for (dirName in dirs) {
            val dir = java.io.File(getApplication<Application>().filesDir, dirName)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    private fun performStartupCrashRecovery() {
        viewModelScope.launch {
            crashRecoveryManager.performStartupCrashRecoveryCheck()
        }
    }

    fun exportBrainSnapshot(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val result = brainBackupManager.exportBrain()
            if (result.success && result.exportedJson != null) {
                onExported(result.exportedJson)
            }
        }
    }

    fun importBrainSnapshot(json: String, onResult: (com.example.core.memory.BrainBackupResult) -> Unit) {
        viewModelScope.launch {
            val result = brainBackupManager.importBrain(json)
            onResult(result)
        }
    }

    fun runMemoryCompaction(onResult: (com.example.core.memory.MaintenanceReport) -> Unit) {
        viewModelScope.launch {
            val report = memoryMaintenanceEngine.runMaintenance()
            onResult(report)
        }
    }

    fun runSkillOptimization(onResult: (com.example.core.learning.SkillOptimizationReport) -> Unit) {
        viewModelScope.launch {
            val report = skillOptimizer.optimizeSkills()
            onResult(report)
        }
    }

    fun setCloudUsagePolicy(
        enabled: Boolean,
        wifiOnly: Boolean,
        limit: Int,
        visionAllowed: Boolean
    ) {
        cloudUsagePolicy.updatePolicy(
            isGeminiEnabled = enabled,
            isWifiOnly = wifiOnly,
            dailyRequestLimit = limit,
            isVisionAllowed = visionAllowed
        )
    }

    fun setModelProfile(profile: com.example.core.model.ModelSizeProfile) {
        modelProfileManager.setProfile(profile)
        viewModelScope.launch {
            modelLifecycleManager.ensureModelReady(profile.modelName)
        }
    }

    fun unloadModelMemory() {
        modelLifecycleManager.unloadModel()
    }

    fun reloadModelMemory() {
        viewModelScope.launch {
            modelLifecycleManager.ensureModelReady(modelProfileManager.currentProfile.value.modelName)
        }
    }

    fun onAudioPermissionGranted() {
        try {
            voiceManager.onAudioPermissionGranted()
        } catch (e: Exception) {
            android.util.Log.w("JarvisViewModel", "Error handling audio permission: ${e.message}")
        }
    }

    fun refreshAccessibilityDiagnostics() {
        _accessibilityDiagnostics.value = JarvisAccessibilityService.getDiagnostics(getApplication())
    }

    private fun syncGeminiProviderSettings() {
        geminiProvider.runtimeApiKey = _geminiApiKey.value
        geminiProvider.selectedModel = _selectedGeminiModel.value
        geminiProvider.temperature = _geminiTemperature.value
        geminiProvider.customSystemPrompt = _customSystemPrompt.value

        geminiVisionProvider.runtimeApiKey = _geminiApiKey.value
        geminiVisionProvider.selectedModel = if (_selectedGeminiModel.value.contains("gemini")) _selectedGeminiModel.value else "gemini-2.5-flash"
        hybridVisionProvider.isCloudVisionEnabled = _isCloudVisionEnabled.value
    }

    fun saveGeminiConfig(apiKey: String, model: String, temperature: Float, systemPrompt: String) {
        val trimmedKey = apiKey.trim()
        val trimmedModel = model.trim().ifEmpty { JarvisPreferences.DEFAULT_MODEL }
        val trimmedPrompt = systemPrompt.trim().ifEmpty { JarvisPreferences.DEFAULT_SYSTEM_PROMPT }

        preferences.geminiApiKey = trimmedKey
        preferences.geminiModel = trimmedModel
        preferences.temperature = temperature
        preferences.systemPrompt = trimmedPrompt

        _geminiApiKey.value = trimmedKey
        _selectedGeminiModel.value = trimmedModel
        _geminiTemperature.value = temperature
        _customSystemPrompt.value = trimmedPrompt

        syncGeminiProviderSettings()
    }

    fun testGeminiConnection(keyToTest: String? = null, modelToTest: String? = null) {
        viewModelScope.launch {
            _apiTestStatus.value = ApiTestStatus.Testing
            val key = keyToTest?.trim() ?: _geminiApiKey.value
            val model = modelToTest?.trim() ?: _selectedGeminiModel.value

            val (success, message) = geminiProvider.testConnection(key, model)
            if (success) {
                _apiTestStatus.value = ApiTestStatus.Success(message)
            } else {
                _apiTestStatus.value = ApiTestStatus.Error(message)
            }
        }
    }

    fun resetApiTestStatus() {
        _apiTestStatus.value = ApiTestStatus.Idle
    }

    fun clearGeminiApiKey() {
        preferences.clearApiKey()
        _geminiApiKey.value = ""
        syncGeminiProviderSettings()
        _apiTestStatus.value = ApiTestStatus.Idle
    }

    fun setModelType(type: ActiveModelType) {
        _activeModelType.value = type
    }

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        voiceManager.currentLanguage = lang
    }

    fun toggleSecurityShield(active: Boolean) {
        _isSecurityShieldActive.value = active
        viewModelScope.launch {
            repository.insertSecurityEvent(
                SecurityEventEntity(
                    eventType = if (active) "SHIELD_ENABLED" else "SHIELD_PAUSED",
                    riskScore = if (active) 0 else 40,
                    source = "User Action",
                    description = "Defensive prompt injection & privilege shield ${if (active) "Activated" else "Suspended"}.",
                    actionTaken = "Policy Updated",
                    isResolved = true
                )
            )
        }
    }

    /**
     * Primary conversation and multi-step agent execution pipeline.
     */
    fun sendUserPrompt(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _isProcessing.value = true
            refreshAccessibilityDiagnostics()

            // 1. Record User Message
            val userMsg = ChatMessageEntity(
                role = "USER",
                message = trimmed,
                language = _currentLanguage.value
            )
            repository.insertChatMessage(userMsg)

            // 2. Defensive Security Scan
            if (_isSecurityShieldActive.value) {
                val scan = SecurityPolicyEngine.scanPrompt(trimmed)
                if (!scan.isSafe) {
                    repository.insertSecurityEvent(
                        SecurityEventEntity(
                            eventType = "INJECTION_ATTEMPT_BLOCKED",
                            riskScore = scan.riskScore,
                            source = "PromptQuarantine",
                            description = "Blocked malicious input: ${scan.flaggedPatterns.joinToString()}",
                            actionTaken = "Execution Aborted",
                            isResolved = true
                        )
                    )

                    val blockedReply = if (_currentLanguage.value == "BN") {
                        "নিরাপত্তা সতর্কতা: প্রম্পট ইনজেকশন বা ক্ষতিকর কমান্ড ডিটেক্ট হওয়ায় অনুরোধটি বাতিল করা হয়েছে।"
                    } else {
                        "Security Alert: Malicious prompt injection pattern detected and neutralized by JARVIS Defensive Shield."
                    }

                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = blockedReply,
                            providerType = "DEFENSE_SHIELD",
                            latencyMs = 4
                        )
                    )
                    voiceManager.speak(blockedReply)
                    _isProcessing.value = false
                    return@launch
                }
            }

            // 2.3 Deterministic No-LLM Command Fast Path (Zero token, sub-10ms)
            val deterministicMatch = com.example.core.model.IntentRouter.matchCommand(trimmed)
            if (deterministicMatch.isMatched) {
                val startNs = System.currentTimeMillis()
                if (deterministicMatch.toolIntent != null) {
                    val toolResult = toolRouter.executeTool(deterministicMatch.toolIntent)
                    _lastExecutionResult.value = toolResult
                    val latency = System.currentTimeMillis() - startNs
                    performanceMonitor.recordNoAiTask(latency)
                    val reply = deterministicMatch.directOutput ?: toolResult.output
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = reply,
                            providerType = "NO_AI_DETERMINISTIC",
                            toolCallInfo = deterministicMatch.toolIntent.toolName,
                            latencyMs = latency
                        )
                    )
                    voiceManager.speak(reply)
                } else {
                    val latency = 2L
                    performanceMonitor.recordNoAiTask(latency)
                    val reply = deterministicMatch.directOutput ?: "Processed."
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = reply,
                            providerType = "NO_AI_DETERMINISTIC",
                            latencyMs = latency
                        )
                    )
                    voiceManager.speak(reply)
                }
                _isProcessing.value = false
                return@launch
            }

            // 2.5 Communication & Confirmation Intent Handling
            val commIntent = CommunicationIntentParser.parse(trimmed)

            // Handle direct user confirmation if an action is pending
            if (_pendingConfirmationIntent.value != null) {
                if (commIntent is CommunicationIntent.UserConfirmation) {
                    if (commIntent.confirmed) {
                        approvePendingTool()
                    } else {
                        cancelPendingTool()
                    }
                    _isProcessing.value = false
                    return@launch
                }
            }

            // Handle dedicated Communication Intents
            when (commIntent) {
                is CommunicationIntent.FindContact -> {
                    val result = toolRouter.executeTool(
                        ToolIntent("find_contact", mapOf("query" to commIntent.contactQuery), "LOW")
                    )
                    _lastExecutionResult.value = result
                    val reply = result.output
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = reply,
                            providerType = "COMMUNICATION_ENGINE",
                            toolCallInfo = "find_contact"
                        )
                    )
                    voiceManager.speak(reply)
                    _isProcessing.value = false
                    return@launch
                }

                is CommunicationIntent.MakeCall -> {
                    val app = getApplication<Application>()
                    val contactRes = ContactResolver.searchContacts(app, commIntent.contactQuery)
                    var targetNumber = commIntent.directNumber
                    var targetName = commIntent.contactQuery

                    when (contactRes) {
                        is ContactResolutionResult.SingleMatch -> {
                            targetNumber = contactRes.contact.phoneNumber
                            targetName = contactRes.contact.name
                        }
                        is ContactResolutionResult.MultipleMatches -> {
                            val list = contactRes.matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                            val msg = "Found multiple contacts matching '${commIntent.contactQuery}': $list. Which one would you like to call?"
                            repository.insertChatMessage(ChatMessageEntity(role = "JARVIS", message = msg, providerType = "COMMUNICATION_ENGINE"))
                            voiceManager.speak(msg)
                            _isProcessing.value = false
                            return@launch
                        }
                        is ContactResolutionResult.NoMatch -> {
                            if (targetNumber == null) {
                                val msg = "No contact found with name '${commIntent.contactQuery}' in your address book."
                                repository.insertChatMessage(ChatMessageEntity(role = "JARVIS", message = msg, providerType = "COMMUNICATION_ENGINE"))
                                voiceManager.speak(msg)
                                _isProcessing.value = false
                                return@launch
                            }
                        }
                        else -> {}
                    }

                    val finalNumber = targetNumber ?: commIntent.contactQuery
                    val toolIntent = ToolIntent("make_phone_call", mapOf("contact_name" to targetName, "number" to finalNumber), "HIGH")
                    _pendingConfirmationIntent.value = toolIntent

                    val confirmMsg = "Ready to call $targetName ($finalNumber). Should I proceed?"
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = confirmMsg,
                            providerType = "COMMUNICATION_ENGINE",
                            toolCallInfo = "make_phone_call"
                        )
                    )
                    voiceManager.speak(confirmMsg)
                    _isProcessing.value = false
                    return@launch
                }

                is CommunicationIntent.SendSms -> {
                    val app = getApplication<Application>()
                    val contactRes = ContactResolver.searchContacts(app, commIntent.contactQuery)
                    var targetNumber = commIntent.directNumber
                    var targetName = commIntent.contactQuery

                    when (contactRes) {
                        is ContactResolutionResult.SingleMatch -> {
                            targetNumber = contactRes.contact.phoneNumber
                            targetName = contactRes.contact.name
                        }
                        is ContactResolutionResult.MultipleMatches -> {
                            val list = contactRes.matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                            val msg = "Found multiple contacts matching '${commIntent.contactQuery}': $list. Which one should I text?"
                            repository.insertChatMessage(ChatMessageEntity(role = "JARVIS", message = msg, providerType = "COMMUNICATION_ENGINE"))
                            voiceManager.speak(msg)
                            _isProcessing.value = false
                            return@launch
                        }
                        is ContactResolutionResult.NoMatch -> {
                            if (targetNumber == null) {
                                val msg = "Cannot find contact '${commIntent.contactQuery}' to send SMS."
                                repository.insertChatMessage(ChatMessageEntity(role = "JARVIS", message = msg, providerType = "COMMUNICATION_ENGINE"))
                                voiceManager.speak(msg)
                                _isProcessing.value = false
                                return@launch
                            }
                        }
                        else -> {}
                    }

                    val finalNumber = targetNumber ?: commIntent.contactQuery
                    val toolIntent = ToolIntent(
                        "send_sms",
                        mapOf("recipient" to targetName, "number" to finalNumber, "message" to commIntent.messageText),
                        "HIGH"
                    )
                    _pendingConfirmationIntent.value = toolIntent

                    val confirmMsg = "Ready to send SMS to $targetName ($finalNumber):\n\"${commIntent.messageText}\"\nShould I send it?"
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = confirmMsg,
                            providerType = "COMMUNICATION_ENGINE",
                            toolCallInfo = "send_sms"
                        )
                    )
                    voiceManager.speak("Ready to send SMS to $targetName. Should I send it?")
                    _isProcessing.value = false
                    return@launch
                }

                is CommunicationIntent.SendWhatsAppMessage -> {
                    val app = getApplication<Application>()
                    val contactRes = ContactResolver.searchContacts(app, commIntent.contactQuery)
                    var targetName = commIntent.contactQuery

                    if (contactRes is ContactResolutionResult.SingleMatch) {
                        targetName = contactRes.contact.name
                    } else if (contactRes is ContactResolutionResult.MultipleMatches) {
                        val list = contactRes.matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                        val msg = "Found multiple contacts matching '${commIntent.contactQuery}': $list. Which one should I message on WhatsApp?"
                        repository.insertChatMessage(ChatMessageEntity(role = "JARVIS", message = msg, providerType = "COMMUNICATION_ENGINE"))
                        voiceManager.speak(msg)
                        _isProcessing.value = false
                        return@launch
                    }

                    val toolIntent = ToolIntent(
                        "send_whatsapp_message",
                        mapOf("contact_name" to targetName, "message" to commIntent.messageText),
                        "HIGH"
                    )
                    _pendingConfirmationIntent.value = toolIntent

                    val confirmMsg = "Ready to send WhatsApp message to $targetName:\n\"${commIntent.messageText}\"\nShould I proceed?"
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = confirmMsg,
                            providerType = "COMMUNICATION_ENGINE",
                            toolCallInfo = "send_whatsapp_message"
                        )
                    )
                    voiceManager.speak("Ready to send WhatsApp message to $targetName. Should I proceed?")
                    _isProcessing.value = false
                    return@launch
                }

                is CommunicationIntent.OpenWhatsApp -> {
                    val target = commIntent.contactQuery
                    val toolResult = if (target != null) {
                        toolRouter.executeTool(ToolIntent("open_whatsapp_chat", mapOf("contact_name" to target), "LOW"))
                    } else {
                        toolRouter.executeTool(ToolIntent("open_app", mapOf("app_name" to "WhatsApp"), "LOW"))
                    }
                    _lastExecutionResult.value = toolResult
                    val reply = toolResult.output
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = reply,
                            providerType = "COMMUNICATION_ENGINE",
                            toolCallInfo = "open_whatsapp"
                        )
                    )
                    voiceManager.speak(reply)
                    _isProcessing.value = false
                    return@launch
                }

                else -> {
                    // Proceed to Universal Task Planner / Model
                }
            }

            // 3. Multi-Step Task Planner Check (PHASE H)
            val multiStepPlan = AgentController.planTaskForQuery(trimmed)
            if (multiStepPlan != null) {
                val planNotice = "Executing Plan: ${multiStepPlan.goal} (${multiStepPlan.steps.size} steps)"
                repository.insertChatMessage(
                    ChatMessageEntity(
                        role = "JARVIS",
                        message = planNotice,
                        providerType = "AGENT_PLANNER",
                        toolCallInfo = "multi_step_executor"
                    )
                )
                voiceManager.speak(planNotice)

                val summary = agentController.executeTaskPlan(multiStepPlan) { stepUpdate ->
                    viewModelScope.launch {
                        repository.insertChatMessage(
                            ChatMessageEntity(
                                role = "AGENT",
                                message = stepUpdate,
                                providerType = "AGENT_CONTROLLER"
                            )
                        )
                    }
                }

                refreshAccessibilityDiagnostics()
                val finishMsg = if (summary.success) {
                    "Plan Completed Successfully: ${summary.goal} (${summary.completedSteps}/${summary.totalSteps} steps verified)."
                } else {
                    "Plan Execution Halted: ${summary.finalOutput}"
                }

                repository.insertChatMessage(
                    ChatMessageEntity(
                        role = "JARVIS",
                        message = finishMsg,
                        providerType = "AGENT_CONTROLLER"
                    )
                )
                voiceManager.speak(finishMsg)
                _isProcessing.value = false
                return@launch
            }

            // 4. Local Offline RAG Search
            val currentChunks = allKnowledgeChunks.value
            val relevant = RagEngine.findRelevantChunks(trimmed, currentChunks, topK = 2)
            val matchedContext = relevant.map { it.first }

            // 5. Invoke Active Model Provider
            val provider: ModelProvider = when (_activeModelType.value) {
                ActiveModelType.LOCAL_GGUF_CPU, ActiveModelType.LOCAL_SLM -> localProvider
                ActiveModelType.GEMINI_CLOUD_TEACHER, ActiveModelType.GEMINI_FLASH -> geminiProvider
                ActiveModelType.HYBRID_SUPERVISED -> hybridProvider
            }

            val response: ModelResponse = provider.generateResponse(
                prompt = trimmed,
                contextChunks = matchedContext,
                language = _currentLanguage.value
            )

            // 6. Handle Tool Planning & Execution with Screen Verification (PHASE G & I)
            if (response.toolIntent != null) {
                val intent = response.toolIntent
                val risk = SecurityPolicyEngine.evaluateToolRisk(intent.toolName, intent.arguments)

                if (SecurityPolicyEngine.requiresUserConfirmation(risk)) {
                    _pendingConfirmationIntent.value = intent
                    val confirmMsg = "Confirmation Required: Tool '${intent.toolName}' has risk level $risk. Please approve to execute on device."
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = confirmMsg,
                            providerType = response.providerType,
                            toolCallInfo = intent.toolName,
                            latencyMs = response.latencyMs
                        )
                    )
                    voiceManager.speak(confirmMsg)
                    _isProcessing.value = false
                    return@launch
                } else {
                    // Safe execution with verification
                    val toolResult = toolRouter.executeTool(intent)
                    _lastExecutionResult.value = toolResult
                    repository.incrementSkillUsage(intent.toolName)
                    refreshAccessibilityDiagnostics()

                    val replyWithTool = "${response.text}\n[Tool ${intent.toolName}: ${if (toolResult.success) "SUCCESS" else "FAILED"}]\nEvidence: ${toolResult.evidence ?: toolResult.output}"
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "JARVIS",
                            message = replyWithTool,
                            providerType = response.providerType,
                            toolCallInfo = intent.toolName,
                            latencyMs = response.latencyMs
                        )
                    )
                    voiceManager.speak(response.text)
                }
            } else {
                // Conversational Reply
                repository.insertChatMessage(
                    ChatMessageEntity(
                        role = "JARVIS",
                        message = response.text,
                        providerType = response.providerType,
                        latencyMs = response.latencyMs
                    )
                )
                voiceManager.speak(response.text)

                // 7. Teacher -> Student Learning Pipeline
                if (response.isTeacherTrained && response.confidence >= 0.90f) {
                    val candidate = TeacherStudentPipeline.processTeacherResponse(trimmed, response.text)
                    if (candidate != null) {
                        repository.insertKnowledgeChunk(TeacherStudentPipeline.convertToKnowledgeChunk(candidate))
                        repository.insertMemory(TeacherStudentPipeline.convertToMemoryEntity(candidate))
                        repository.insertSecurityEvent(
                            SecurityEventEntity(
                                eventType = "KNOWLEDGE_LEARNED",
                                riskScore = 0,
                                source = "TeacherStudentPipeline",
                                description = "Validated new fact from Gemini Teacher and cached in local offline storage.",
                                actionTaken = "Local Knowledge Base Updated",
                                isResolved = true
                            )
                        )
                    }
                }
            }

            _isProcessing.value = false
        }
    }

    fun approvePendingTool() {
        val intent = _pendingConfirmationIntent.value ?: return
        _pendingConfirmationIntent.value = null
        viewModelScope.launch {
            val result = toolRouter.executeTool(intent)
            _lastExecutionResult.value = result
            repository.incrementSkillUsage(intent.toolName)
            refreshAccessibilityDiagnostics()

            val text = "Authorized execution of ${intent.toolName}: ${result.output}\nEvidence: ${result.evidence ?: "Completed"}"
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "TOOL",
                    message = text,
                    providerType = "ANDROID_CONTROLLER"
                )
            )
            voiceManager.speak(result.output)
        }
    }

    fun cancelPendingTool() {
        _pendingConfirmationIntent.value = null
        viewModelScope.launch {
            val text = "Tool execution cancelled by user."
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "JARVIS",
                    message = text,
                    providerType = "LOCAL"
                )
            )
            voiceManager.speak(text)
        }
    }

    // === Direct Accessibility Diagnostics & Diagnostic Tests (PHASE A) ===

    fun testReadScreen() {
        viewModelScope.launch {
            val result = toolRouter.executeTool(ToolIntent("read_screen", emptyMap(), "LOW"))
            _lastExecutionResult.value = result
            refreshAccessibilityDiagnostics()
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "DIAGNOSTIC",
                    message = "[TEST: READ SCREEN]\n${result.output}",
                    providerType = "ACCESSIBILITY_SERVICE"
                )
            )
            voiceManager.speak("Screen inspected. Found ${_accessibilityDiagnostics.value.totalNodes} elements.")
        }
    }

    fun testClickElement(target: String = "Search") {
        viewModelScope.launch {
            val result = toolRouter.executeTool(ToolIntent("tap", mapOf("target_text" to target), "LOW"))
            _lastExecutionResult.value = result
            refreshAccessibilityDiagnostics()
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "DIAGNOSTIC",
                    message = "[TEST: CLICK TARGET '$target']\nSuccess: ${result.success}\nEvidence: ${result.evidence ?: result.output}",
                    providerType = "ACCESSIBILITY_SERVICE"
                )
            )
        }
    }

    fun testTypeText(text: String = "Tom and Jerry") {
        viewModelScope.launch {
            val result = toolRouter.executeTool(ToolIntent("type_text", mapOf("text" to text), "LOW"))
            _lastExecutionResult.value = result
            refreshAccessibilityDiagnostics()
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "DIAGNOSTIC",
                    message = "[TEST: TYPE TEXT \"$text\"]\nSuccess: ${result.success}\nDetails: ${result.evidence ?: result.output}",
                    providerType = "ACCESSIBILITY_SERVICE"
                )
            )
        }
    }

    fun testScrollScreen(forward: Boolean = true) {
        viewModelScope.launch {
            val result = toolRouter.executeTool(ToolIntent("scroll", mapOf("direction" to if (forward) "DOWN" else "UP"), "LOW"))
            _lastExecutionResult.value = result
            refreshAccessibilityDiagnostics()
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "DIAGNOSTIC",
                    message = "[TEST: SCROLL ${if (forward) "DOWN" else "UP"}]\nSuccess: ${result.success}\nDetails: ${result.evidence ?: result.output}",
                    providerType = "ACCESSIBILITY_SERVICE"
                )
            )
        }
    }

    fun testPressBack() {
        viewModelScope.launch {
            val result = toolRouter.executeTool(ToolIntent("press_back", emptyMap(), "LOW"))
            _lastExecutionResult.value = result
            refreshAccessibilityDiagnostics()
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "DIAGNOSTIC",
                    message = "[TEST: BACK NAVIGATION]\nSuccess: ${result.success}",
                    providerType = "ACCESSIBILITY_SERVICE"
                )
            )
        }
    }

    fun runSecurityAudit() {
        viewModelScope.launch {
            val result = toolRouter.executeTool(ToolIntent("security_audit_check", emptyMap(), "LOW"))
            _lastExecutionResult.value = result
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "SECURITY",
                    message = result.output,
                    providerType = "DEFENSE_SHIELD"
                )
            )
        }
    }

    // === Memory Management ===
    fun addMemory(category: MemoryCategory, key: String, value: String) {
        viewModelScope.launch {
            repository.insertMemory(
                MemoryEntity(
                    category = category,
                    key = key,
                    value = value,
                    confidence = 1.0f,
                    source = "User Entry"
                )
            )
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    // === RAG / Knowledge Base ===
    fun addKnowledgeChunk(title: String, sourceDoc: String, content: String, tags: String) {
        viewModelScope.launch {
            val chunks = RagEngine.chunkDocument(title, sourceDoc, content, tags)
            chunks.forEach { repository.insertKnowledgeChunk(it) }
        }
    }

    fun deleteKnowledgeChunk(chunk: KnowledgeChunkEntity) {
        viewModelScope.launch {
            repository.deleteKnowledgeChunk(chunk)
        }
    }

    fun testRagQuery(query: String) {
        viewModelScope.launch {
            val chunks = allKnowledgeChunks.value
            val results = RagEngine.findRelevantChunks(query, chunks, topK = 5, minScoreThreshold = 0.05f)
            _ragTestResults.value = results
        }
    }

    fun executeGoal(goal: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            refreshAccessibilityDiagnostics()
            refreshCapabilities()
            val universalTask = jarvisAgentCore.universalPlanner.planUniversalTask(goal)
            jarvisAgentCore.executeUniversalTask(universalTask, viewModelScope)
            _isProcessing.value = false
            refreshAccessibilityDiagnostics()
        }
    }

    fun executeAutonomousGoal(goal: String) {
        executeGoal(goal)
    }

    fun executeUniversalTask(task: UniversalTask) {
        viewModelScope.launch {
            _isProcessing.value = true
            refreshAccessibilityDiagnostics()
            refreshCapabilities()
            jarvisAgentCore.executeUniversalTask(task, viewModelScope)
            _isProcessing.value = false
            refreshAccessibilityDiagnostics()
        }
    }

    // === Export / Import Brain ===
    fun exportBrain(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportBrainJson(
                memories = allMemories.value,
                skills = allSkills.value,
                knowledge = allKnowledgeChunks.value,
                experiences = allExperiences.value,
                corrections = allUserCorrections.value
            )
            onExported(json)
        }
    }

    fun importBrain(jsonStr: String, onImported: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importBrainJson(jsonStr)
            onImported(count)
        }
    }

    // === Multimodal Screen Understanding & Visual Diagnostics ===

    fun toggleCloudVision(enabled: Boolean) {
        _isCloudVisionEnabled.value = enabled
        hybridVisionProvider.isCloudVisionEnabled = enabled
    }

    fun triggerVisualScreenAnalysis(semanticGoal: String? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            val screen = screenEngine.observeScreen(semanticGoal = semanticGoal, forceVisualScan = true)
            _isProcessing.value = false
            refreshAccessibilityDiagnostics()

            val text = "[MULTIMODAL SCAN]\nApp: ${screen.packageName}\nNodes: ${screen.totalNodes} | Visual Elements: ${screen.visualElements.size}\nSummary: ${screen.getSummary()}"
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "VISION",
                    message = text,
                    providerType = "HYBRID_VISION"
                )
            )
            voiceManager.speak("Screen visual scan complete. Detected ${screen.visualElements.size} visual elements.")
        }
    }

    fun testIntentDetection(targetRole: String = "SEARCH") {
        viewModelScope.launch {
            _isProcessing.value = true
            val (element, node) = screenEngine.findElementByIntent(targetRole)
            _isProcessing.value = false

            val msg = if (element != null) {
                "[INTENT DETECTED: $targetRole]\nSource: ${element.source}\nBounds: [${element.bounds.left}, ${element.bounds.top}, ${element.bounds.right}, ${element.bounds.bottom}]\nConfidence: ${(element.confidence * 100).toInt()}%\nDesc: ${element.visualDescription ?: element.text ?: element.contentDescription}"
            } else {
                "[INTENT FAILED: $targetRole]\nNo matching element found via Accessibility, Heuristics, or Gemini Vision."
            }

            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "VISION",
                    message = msg,
                    providerType = "SCREEN_UNDERSTANDING"
                )
            )
            voiceManager.speak(if (element != null) "Found target $targetRole with ${(element.confidence * 100).toInt()}% confidence" else "Target $targetRole not found.")
        }
    }

    // === Phase 10: Semantic UI Understanding, Icon Recognition & Screen Control ===

    fun observeSemanticScreen(taskGoal: String? = null, forceVisualScan: Boolean = true) {
        viewModelScope.launch {
            _isProcessing.value = true
            val semanticModel = universalEngine.observeScreen(
                taskGoal = taskGoal,
                forceVisualScan = forceVisualScan
            )
            _isProcessing.value = false
            refreshAccessibilityDiagnostics()

            val msg = "[SEMANTIC SCREEN MODEL]\nPackage: ${semanticModel.packageName}\nType: ${semanticModel.screenType} | Dialog: ${semanticModel.isDialogActive}\nTotal Elements: ${semanticModel.elements.size}\nIcons Identified: ${semanticModel.elements.count { it.source == "ICON_RECOGNIZER" || it.source == "HYBRID_ICON" }}\nConfidence: ${(semanticModel.screenConfidence * 100).toInt()}%\nTitle: ${semanticModel.screenTitle}"
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "VISION",
                    message = msg,
                    providerType = "UNIVERSAL_VISION"
                )
            )
            voiceManager.speak("Semantic screen model updated. ${semanticModel.elements.size} elements identified.")
        }
    }

    fun testSemanticTargetMatch(goal: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            val semanticModel = universalEngine.observeScreen(taskGoal = goal, forceVisualScan = false)
            val match = targetMatcher.matchTarget(goal, semanticModel)
            _latestMatchedTarget.value = match
            _isProcessing.value = false

            val selected = match.selectedElement
            val msg = if (selected != null) {
                "[TARGET MATCHED: '$goal']\nRole: ${match.targetRole}\nLabel/Meaning: ${selected.label ?: selected.iconMeaning ?: selected.description}\nConfidence: ${(match.confidence * 100).toInt()}%\nBounds: ${selected.bounds}\nReason: ${match.reason}"
            } else {
                "[TARGET NOT FOUND: '$goal']\nReason: ${match.reason}"
            }

            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "VISION",
                    message = msg,
                    providerType = "SEMANTIC_MATCHER"
                )
            )
            voiceManager.speak(if (selected != null) "Matched target for $goal" else "Could not match target for $goal")
        }
    }

    fun testSemanticActionExecution(targetGoal: String, actionType: String = "CLICK", textPayload: String? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            _semanticActionStatus.value = "Executing $actionType on '$targetGoal'..."
            val beforeScreen = universalEngine.latestSemanticScreen.value
            val result = actionExecutor.executeAction(
                targetGoalOrRole = targetGoal,
                actionType = actionType,
                textToType = textPayload,
                expectedOutcome = "Target responds to $actionType"
            )
            val afterScreen = universalEngine.observeScreen(taskGoal = targetGoal)
            val diff = diffEngine.computeDiff(beforeScreen, afterScreen, expectedOutcome = "Target responds to $actionType")
            _latestScreenDiff.value = diff
            _semanticActionStatus.value = if (result.success) "SUCCESS: ${result.actionMethod}" else "FAILED: ${result.evidence}"
            _isProcessing.value = false
            refreshAccessibilityDiagnostics()

            val msg = "[SEMANTIC ACTION: $actionType on '$targetGoal']\nStatus: ${if (result.success) "SUCCESS" else "FAILED"}\nMethod: ${result.actionMethod}\nEvidence: ${result.evidence}\nScreen Transition: ${diff.transitionType} (${diff.summary})"
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "ACTION",
                    message = msg,
                    providerType = "SEMANTIC_EXECUTOR"
                )
            )
            voiceManager.speak(if (result.success) "Action succeeded on $targetGoal" else "Action failed on $targetGoal")
        }
    }

    fun testIconRecognitionOnScreen() {
        viewModelScope.launch {
            _isProcessing.value = true
            val semanticModel = universalEngine.observeScreen(forceVisualScan = true)
            val iconElements = semanticModel.elements.filter { it.iconMeaning != null || it.source == "ICON_RECOGNIZER" || it.source == "HYBRID_ICON" }
            _isProcessing.value = false

            val listSummary = iconElements.joinToString("\n") { elem ->
                "• [${elem.role}] Meaning: '${elem.iconMeaning ?: elem.label}' (Conf: ${(elem.confidence * 100).toInt()}%, Bounds: ${elem.bounds})"
            }

            val msg = "[ICON RECOGNITION RESULTS]\nDetected ${iconElements.size} visual icons:\n${if (listSummary.isNotEmpty()) listSummary else "No standalone icons detected."}"
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "VISION",
                    message = msg,
                    providerType = "ICON_RECOGNIZER"
                )
            )
            voiceManager.speak("Identified ${iconElements.size} visual UI icons.")
        }
    }

    fun testPlayTomAndJerry() {
        viewModelScope.launch {
            val plan = AgentController.planTaskForQuery("Play Tom and Jerry") ?: return@launch
            agentController.executeTaskPlan(plan) { status ->
                // on step update
            }
        }
    }

    fun testContactResolution(query: String = "Hammad") {
        viewModelScope.launch {
            val result = toolRouter.executeTool(ToolIntent("find_contact", mapOf("query" to query), "LOW"))
            _lastExecutionResult.value = result
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "COMMUNICATION",
                    message = "[CONTACT SEARCH: $query]\n${result.output}\nEvidence: ${result.evidence ?: "Verified"}",
                    providerType = "CONTACT_RESOLVER"
                )
            )
            voiceManager.speak(result.output)
        }
    }

    fun testMakeCall(contact: String = "Hammad") {
        sendUserPrompt("Call $contact")
    }

    fun testSendSms(contact: String = "Hammad", message: String = "I will call later") {
        sendUserPrompt("Send $contact an SMS saying $message")
    }

    fun testSendWhatsApp(contact: String = "Hammad", message: String = "I'm on my way") {
        sendUserPrompt("Send $contact a WhatsApp message saying $message")
    }

    fun testOpenWhatsApp(contact: String = "Hammad") {
        sendUserPrompt("Open WhatsApp and find $contact")
    }

    fun clearVisualExperiences() {
        viewModelScope.launch {
            repository.clearVisualExperiences()
        }
    }

    fun refreshDefaultAssistantStatus() {
        _isDefaultAssistant.value = AssistantRoleHelper.isDefaultAssistant(getApplication())
    }

    fun openDefaultAssistantSettings() {
        AssistantRoleHelper.openDefaultAssistantSettings(getApplication())
    }

    fun startVoiceListening() {
        voiceManager.startListeningForCommand()
    }

    fun stopVoiceSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun handleBargeInStop() {
        voiceManager.interactionManager.handleBargeInStop()
    }

    fun toggleMicrophone(muted: Boolean) {
        voiceManager.toggleMicrophone(muted)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        voiceManager.setWakeWordEnabled(enabled)
    }

    fun setWakeSensitivity(sens: WakeSensitivity) {
        voiceManager.setWakeSensitivity(sens)
    }

    fun setCloudAllowed(allowed: Boolean) {
        voiceManager.setCloudAllowed(allowed)
    }

    fun toggleOverlayHud(context: android.content.Context) {
        if (JarvisOverlayService.isOverlayActive.value) {
            JarvisOverlayService.stopOverlay(context)
        } else {
            JarvisOverlayService.startOverlay(context)
        }
    }

    fun startBackgroundVoiceService(context: android.content.Context) {
        voiceManager.startBackgroundWakeService()
    }

    fun stopBackgroundVoiceService(context: android.content.Context) {
        voiceManager.stopBackgroundWakeService()
    }

    fun testWakeWordTrigger() {
        voiceManager.interactionManager.processSpokenCommand("Hey JARVIS")
    }

    fun testSpokenUtterance(text: String) {
        voiceManager.simulateVoiceInput(text)
    }

    // === PHASE 7 LEARNING, SKILLS & MEMORY MANAGEMENT ===

    fun refreshLearningMetrics() {
        viewModelScope.launch {
            val memories = allMemories.value.size
            val experiences = allExperiences.value.size
            val successes = allExperiences.value.count { it.isSuccess }
            val failures = experiences - successes
            val skills = allSkills.value.size
            val learnedSkills = allSkills.value.count { it.isLearnedFromExperience }
            val corrections = allUserCorrections.value.size
            val teacherSessions = allTeacherSessions.value.size
            val trainingExamples = allTrainingExamples.value.size

            _learningMetrics.value = LearningMetrics(
                totalMemories = memories,
                totalExperiences = experiences,
                successfulExperiences = successes,
                failedExperiences = failures,
                totalSkills = skills,
                learnedSkillsCount = learnedSkills,
                userCorrectionsCount = corrections,
                geminiTeachingSessions = teacherSessions,
                trainingExamplesCount = trainingExamples,
                localExecutionCount = experiences,
                geminiAssistedCount = teacherSessions,
                localAutonomyPercentage = if (experiences + teacherSessions > 0) ((experiences.toFloat() / (experiences + teacherSessions)) * 100).toInt() else 100
            )
        }
    }

    fun recordFactMemory(category: MemoryCategory, key: String, value: String, confidence: Float = 1.0f) {
        viewModelScope.launch {
            memoryManager.recordMemory(
                category = category,
                key = key,
                value = value,
                confidence = confidence,
                importance = 0.8f,
                source = "Manual Entry"
            )
            refreshLearningMetrics()
        }
    }

    fun recordUserCorrection(
        userGoal: String,
        previousAssumption: String,
        userCorrection: String,
        correctedAction: String,
        actualTarget: String,
        appPackage: String = "com.example"
    ) {
        viewModelScope.launch {
            userCorrectionLearner.recordCorrection(
                userGoal = userGoal,
                previousAssumption = previousAssumption,
                userCorrection = userCorrection,
                correctedAction = correctedAction,
                actualTarget = actualTarget,
                appPackage = appPackage,
                screenContext = "manual_correction"
            )
            refreshLearningMetrics()
        }
    }

    fun exportTrainingDatasetJson(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val json = trainingDatasetManager.exportDataset(com.example.data.local.entity.DatasetFormat.ALPACA)
            onExported(json)
        }
    }

    fun exportFullBrainJson(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val memories = repository.searchMemories("")
            val skills = repository.allSkills.stateIn(this).value
            val knowledge = repository.searchKnowledge("")
            val experiences = repository.allExperiences.stateIn(this).value
            val corrections = repository.allUserCorrections.stateIn(this).value

            val json = repository.exportBrainJson(
                memories = memories,
                skills = skills,
                knowledge = knowledge,
                experiences = experiences,
                corrections = corrections
            )
            onExported(json)
        }
    }

    fun clearAllExperiences() {
        viewModelScope.launch {
            repository.clearAllExperiences()
            refreshLearningMetrics()
        }
    }

    fun clearAllUserCorrections() {
        viewModelScope.launch {
            repository.clearAllUserCorrections()
            refreshLearningMetrics()
        }
    }

    fun clearTrainingDataset() {
        viewModelScope.launch {
            repository.clearTrainingDataset()
            refreshLearningMetrics()
        }
    }

    fun clearTeacherSessions() {
        viewModelScope.launch {
            repository.clearTeacherSessions()
            refreshLearningMetrics()
        }
    }

    fun clearLearnedSkills() {
        viewModelScope.launch {
            repository.clearLearnedSkills()
            refreshLearningMetrics()
        }
    }

    fun toggleSkill(skill: SkillEntity) {
        viewModelScope.launch {
            skillManager.toggleSkill(skill)
            refreshLearningMetrics()
        }
    }

    fun rollbackSkill(skill: SkillEntity) {
        viewModelScope.launch {
            skillManager.rollbackSkill(skill)
            refreshLearningMetrics()
        }
    }

    fun deleteSkill(skill: SkillEntity) {
        viewModelScope.launch {
            skillManager.deleteSkill(skill)
            refreshLearningMetrics()
        }
    }

    fun setLearningEnabled(enabled: Boolean) {
        preferences.isLearningEnabled = enabled
    }

    fun setStoreExperiencesEnabled(enabled: Boolean) {
        preferences.isStoreExperiencesEnabled = enabled
    }

    fun setAutoSkillCreationEnabled(enabled: Boolean) {
        preferences.isAutoSkillCreationEnabled = enabled
    }

    fun setStoreTrainingDataEnabled(enabled: Boolean) {
        preferences.isStoreTrainingDataEnabled = enabled
    }

    fun setPrivacyFilteringEnabled(enabled: Boolean) {
        preferences.isPrivacyFilteringEnabled = enabled
    }

    fun setGeminiTeacherAllowed(allowed: Boolean) {
        preferences.isGeminiTeacherAllowed = allowed
    }

    // === PHASE 8 AUTONOMOUS ACTIONS ===
    fun setAutonomyMode(mode: AutonomyMode) {
        autonomousAgentManager.setAutonomyMode(mode)
    }

    fun updateAutonomyPolicy(config: AutonomyPolicyConfig) {
        autonomousAgentManager.updatePolicyConfig(config)
    }

    fun triggerEmergencyStop(reason: String = "UI Emergency Button") {
        autonomousAgentManager.triggerEmergencyStop(reason)
    }

    fun resetEmergencyStop() {
        autonomousAgentManager.resetEmergencyStop()
    }

    fun submitAutonomousGoal(goal: String, priority: AutonomousTaskPriority = AutonomousTaskPriority.MEDIUM) {
        autonomousAgentManager.submitGoal(goal = goal, priority = priority)
    }

    fun triggerWebResearch(query: String) {
        autonomousAgentManager.triggerResearch(query)
    }

    fun triggerMemoryMaintenance() {
        autonomousAgentManager.triggerMaintenance()
    }

    fun scheduleTask(
        title: String,
        instruction: String,
        triggerType: ScheduleTriggerType,
        timeMillis: Long,
        cronOrInterval: String = ""
    ) {
        viewModelScope.launch {
            autonomousAgentManager.scheduler.scheduleTask(
                title = title,
                instruction = instruction,
                triggerType = triggerType,
                scheduledTimeMillis = timeMillis,
                cronOrInterval = cronOrInterval
            )
        }
    }

    fun toggleScheduledTask(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            autonomousAgentManager.scheduler.toggleTaskEnabled(id, enabled)
        }
    }

    fun deleteScheduledTask(task: ScheduledTaskEntity) {
        viewModelScope.launch {
            autonomousAgentManager.scheduler.deleteScheduledTask(task)
        }
    }

    fun cancelAutonomousTask(taskId: Long) {
        viewModelScope.launch {
            autonomousAgentManager.taskQueue.cancelTask(taskId)
        }
    }

    fun deleteAutonomousTask(task: AutonomousTaskEntity) {
        viewModelScope.launch {
            autonomousAgentManager.taskQueue.deleteTask(task)
        }
    }

    fun clearAllAutonomousTasks() {
        viewModelScope.launch {
            autonomousAgentManager.taskQueue.clearAllTasks()
        }
    }

    fun approveKnowledgeUpdate(versionId: Long) {
        viewModelScope.launch {
            autonomousAgentManager.knowledgeUpdateManager.approvePendingUpdate(versionId)
        }
    }

    fun rollbackKnowledge(key: String, targetVersion: Int) {
        viewModelScope.launch {
            autonomousAgentManager.knowledgeUpdateManager.rollbackToVersion(key, targetVersion)
        }
    }

    fun attemptSelfRecovery(component: String) {
        viewModelScope.launch {
            autonomousAgentManager.healthMonitor.recoveryManager.attemptRecovery(component, "Manual diagnostic recovery triggered")
            autonomousAgentManager.healthMonitor.performDiagnosticCheck(agentState = "RECOVERY_TEST")
        }
    }

    fun performSystemHealthCheck() {
        viewModelScope.launch {
            autonomousAgentManager.healthMonitor.performDiagnosticCheck()
        }
    }

    fun setResourceMode(mode: ResourceMode) {
        autonomousAgentManager.resourceManager.setResourceMode(mode)
    }

    // Phase 14: Long-Term Brain Actions
    fun refreshBrainStorageStats() {
        viewModelScope.launch {
            _brainStorageStats.value = brainStorageManager.getStorageStats()
        }
    }

    fun ingestNewKnowledge(
        candidate: com.example.core.knowledge.IngestionCandidate,
        onResult: (com.example.core.knowledge.IngestionResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = knowledgeIngestionEngine.ingest(candidate)
            onResult(result)
            refreshBrainStorageStats()
        }
    }

    fun exportPhase14BrainSnapshot(onResult: (com.example.core.brain.BackupExportResult) -> Unit) {
        viewModelScope.launch {
            val result = phase14BackupManager.createBrainExport()
            onResult(result)
        }
    }

    fun importPhase14BrainSnapshot(json: String, onResult: (com.example.core.brain.BackupImportResult) -> Unit) {
        viewModelScope.launch {
            val result = phase14BackupManager.importBrain(json)
            onResult(result)
            refreshBrainStorageStats()
        }
    }

    fun compactBrainStorage() {
        viewModelScope.launch {
            val stats = brainStorageManager.compactBrain()
            _brainStorageStats.value = stats
        }
    }

    fun flagKnowledgeSource(sourceId: String) {
        viewModelScope.launch {
            knowledgeSourceManager.flagSource(sourceId)
        }
    }

    fun deleteKnowledgeItem(item: com.example.data.local.entity.KnowledgeItemEntity) {
        viewModelScope.launch {
            database.jarvisDao().deleteKnowledgeItem(item)
            refreshBrainStorageStats()
        }
    }

    // === PHASE 15: DEVICE CAPABILITY & CONTROL ACTIONS ===

    fun refreshCapabilities() {
        _capabilitiesList.value = capabilityManager.getAllCapabilities()
        viewModelScope.launch(Dispatchers.IO) {
            deviceCapabilityManager.detectCapabilities()
        }
    }

    fun scanInstalledApps(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = appManager.scanInstalledApps()
            withContext(Dispatchers.Main) {
                onDone(apps.size)
            }
        }
    }

    fun executeDeviceTool(intent: ToolIntent) {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = toolRouter.executeTool(intent)
            _lastExecutionResult.value = result
            repository.insertChatMessage(
                ChatMessageEntity(
                    role = "ACTION",
                    message = "[DEVICE TOOL: ${intent.toolName}]\n${result.output}",
                    providerType = "DEVICE_CONTROLLER",
                    toolCallInfo = intent.toolName
                )
            )
            voiceManager.speak(result.output)
            _isProcessing.value = false
        }
    }
}
