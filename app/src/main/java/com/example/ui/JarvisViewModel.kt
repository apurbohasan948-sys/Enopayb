package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.accessibility.AccessibilityDiagnostics
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.agent.AgentController
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
import com.example.data.local.database.JarvisDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.VisualExperienceEntity
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

    val voiceManager = VoiceManager(application)

    val localVisionProvider = LocalVisionProvider()
    val geminiVisionProvider = GeminiVisionProvider()
    val hybridVisionProvider = HybridVisionProvider(localVisionProvider, geminiVisionProvider, repository)
    val screenEngine = ScreenUnderstandingEngine(application, hybridVisionProvider, repository)

    private val toolRouter = ToolRouter(application, screenEngine)
    val agentController = AgentController(application, toolRouter, screenEngine)

    private val localProvider = LocalModelProvider()
    private val geminiProvider = GeminiModelProvider()
    private val hybridProvider = HybridModelProvider(localProvider, geminiProvider)

    // Data Streams from Room
    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMemories: StateFlow<List<MemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSkills: StateFlow<List<SkillEntity>> = repository.allSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val securityEvents: StateFlow<List<SecurityEventEntity>> = repository.allSecurityEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allKnowledgeChunks: StateFlow<List<KnowledgeChunkEntity>> = repository.allKnowledgeChunks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visualExperiences: StateFlow<List<VisualExperienceEntity>> = repository.allVisualExperiences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Vision Streams
    val latestUnifiedScreen: StateFlow<UnifiedScreen?> = screenEngine.latestUnifiedScreen
    val latestScreenshotBitmap: StateFlow<Bitmap?> = screenEngine.latestScreenshotBitmap
    val lastDetectedElements: StateFlow<List<VisualElement>> = screenEngine.lastDetectedElements

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

    private val preferences = JarvisPreferences(application)

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
        voiceManager.currentLanguage = _currentLanguage.value
        syncGeminiProviderSettings()
        refreshAccessibilityDiagnostics()
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
                ActiveModelType.LOCAL_GGUF_CPU -> localProvider
                ActiveModelType.GEMINI_CLOUD_TEACHER -> geminiProvider
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

    // === Export / Import Brain ===
    fun exportBrain(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportBrainJson(
                allMemories.value,
                allSkills.value,
                allKnowledgeChunks.value
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

    fun testPlayTomAndJerry() {
        viewModelScope.launch {
            val plan = AgentController.planTaskForQuery("Play Tom and Jerry") ?: return@launch
            agentController.executeTaskPlan(plan) { status ->
                // on step update
            }
        }
    }

    fun clearVisualExperiences() {
        viewModelScope.launch {
            repository.clearVisualExperiences()
        }
    }
}
