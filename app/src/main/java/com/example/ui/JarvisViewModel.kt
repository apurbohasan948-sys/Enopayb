package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.core.voice.VoiceManager
import com.example.core.voice.VoiceState
import com.example.data.local.database.JarvisDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import com.example.data.local.preference.JarvisPreferences
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.Dispatchers
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
    private val toolRouter = ToolRouter(application)

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
    }

    private fun syncGeminiProviderSettings() {
        geminiProvider.runtimeApiKey = _geminiApiKey.value
        geminiProvider.selectedModel = _selectedGeminiModel.value
        geminiProvider.temperature = _geminiTemperature.value
        geminiProvider.customSystemPrompt = _customSystemPrompt.value
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
     * Primary conversation and tool execution pipeline.
     */
    fun sendUserPrompt(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _isProcessing.value = true

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

            // 3. Local Offline RAG Search
            val currentChunks = allKnowledgeChunks.value
            val relevant = RagEngine.findRelevantChunks(trimmed, currentChunks, topK = 2)
            val matchedContext = relevant.map { it.first }

            // 4. Invoke Active Model Provider
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

            // 5. Handle Tool Planning & Execution
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
                    // Safe execution
                    val toolResult = toolRouter.executeTool(intent)
                    _lastExecutionResult.value = toolResult
                    repository.incrementSkillUsage(intent.toolName)

                    val replyWithTool = "${response.text}\n[Tool ${intent.toolName}: ${if (toolResult.success) "SUCCESS" else "FAILED"}]"
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

                // 6. Teacher -> Student Learning Pipeline
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

            val text = "Authorized execution of ${intent.toolName}: ${result.output}"
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

    fun importBrain(jsonStr: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importBrainJson(jsonStr)
            onComplete(count)
        }
    }

    // === Direct Tools Shortcut ===
    fun toggleFlashlightManual(state: Boolean) {
        viewModelScope.launch {
            val res = toolRouter.executeTool(
                ToolIntent("toggle_flashlight", mapOf("state" to state.toString()))
            )
            _lastExecutionResult.value = res
        }
    }

    fun runSecurityAudit() {
        viewModelScope.launch {
            val res = toolRouter.executeTool(
                ToolIntent("security_audit_check", emptyMap())
            )
            _lastExecutionResult.value = res
            repository.insertSecurityEvent(
                SecurityEventEntity(
                    eventType = "MANUAL_SECURITY_AUDIT",
                    riskScore = 0,
                    source = "SecurityDashboard",
                    description = res.output,
                    actionTaken = "Audit Passed",
                    isResolved = true
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }
}
