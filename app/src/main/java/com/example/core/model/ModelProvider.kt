package com.example.core.model

import com.example.BuildConfig
import com.example.core.tools.ToolDefinitions
import com.example.data.local.entity.KnowledgeChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolIntent(
    val toolName: String,
    val arguments: Map<String, String>,
    val riskLevel: String = "LOW",
    val rationale: String = ""
)

data class ModelResponse(
    val text: String,
    val latencyMs: Long,
    val providerType: String, // LOCAL, GEMINI, HYBRID
    val confidence: Float,
    val toolIntent: ToolIntent? = null,
    val usedContextChunks: List<KnowledgeChunkEntity> = emptyList(),
    val isTeacherTrained: Boolean = false
)

enum class ActiveModelType {
    LOCAL_GGUF_CPU,
    GEMINI_CLOUD_TEACHER,
    HYBRID_SUPERVISED
}

interface ModelProvider {
    suspend fun generateResponse(
        prompt: String,
        contextChunks: List<KnowledgeChunkEntity> = emptyList(),
        language: String = "EN"
    ): ModelResponse
}

/**
 * Local Model Provider: Fast, offline, privacy-first intent reasoning engine.
 * Understands English, Bengali, and Banglish natural language patterns.
 */
class LocalModelProvider : ModelProvider {
    var modelName: String = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
    var contextLength: Int = 2048
    var temperature: Float = 0.4f
    var quantizedType: String = "Q4_K_M (4-bit Mobile)"

    override suspend fun generateResponse(
        prompt: String,
        contextChunks: List<KnowledgeChunkEntity>,
        language: String
    ): ModelResponse = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val raw = prompt.trim()
        val lower = raw.lowercase()

        // 1. Tool Intent Heuristics (Local fast-path: Bengali, English, Banglish)
        val toolIntent: ToolIntent? = when {
            // WhatsApp message
            lower.contains("whatsapp") && (lower.contains("message") || lower.contains("send") || lower.contains("বলো") || lower.contains("মেসেজ") || lower.contains("পাঠাও")) -> {
                val (contact, msg) = extractWhatsAppParameters(raw)
                ToolIntent(
                    toolName = "send_whatsapp_message",
                    arguments = mapOf("contact_name" to contact, "message" to msg),
                    riskLevel = "MEDIUM",
                    rationale = "User requested sending a WhatsApp message"
                )
            }
            // WhatsApp chat open
            lower.contains("whatsapp") && (lower.contains("open") || lower.contains("খোল") || lower.contains("চ্যাট")) -> {
                val contact = extractContactFromQuery(raw)
                if (contact.isNotEmpty()) {
                    ToolIntent(
                        toolName = "open_whatsapp_chat",
                        arguments = mapOf("contact_name" to contact),
                        riskLevel = "LOW",
                        rationale = "User requested opening a WhatsApp chat"
                    )
                } else {
                    ToolIntent(
                        toolName = "open_app",
                        arguments = mapOf("app_name" to "WhatsApp"),
                        riskLevel = "LOW",
                        rationale = "Open WhatsApp application"
                    )
                }
            }
            // Phone call
            lower.startsWith("call ") || lower.startsWith("কল ") || lower.startsWith("ফোন ") || lower.contains("call to ") || lower.contains("কে কল করো") || lower.contains("কে ফোন দাও") -> {
                val target = extractContactFromCallQuery(raw)
                ToolIntent(
                    toolName = "make_phone_call",
                    arguments = mapOf("contact_name" to target),
                    riskLevel = "MEDIUM",
                    rationale = "Telephony call requires confirmation"
                )
            }
            // SMS message
            lower.contains("sms") || (lower.contains("মেসেজ") && !lower.contains("whatsapp")) -> {
                val (contact, msg) = extractSmsParameters(raw)
                ToolIntent(
                    toolName = "send_sms",
                    arguments = mapOf("recipient" to contact, "message" to msg),
                    riskLevel = "MEDIUM",
                    rationale = "SMS draft/sending intent"
                )
            }
            // Contacts lookup
            lower.contains("contact") || lower.contains("নাম্বার") || lower.contains("number of") -> {
                val name = extractContactFromQuery(raw)
                ToolIntent(
                    toolName = "get_contacts",
                    arguments = mapOf("name_query" to name),
                    riskLevel = "LOW",
                    rationale = "Lookup contact details"
                )
            }
            // Flashlight / Torch
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশলাইট") || lower.contains("আলো") -> {
                val state = !lower.contains("off") && !lower.contains("বন্ধ")
                ToolIntent(
                    toolName = "toggle_flashlight",
                    arguments = mapOf("state" to state.toString()),
                    riskLevel = "LOW",
                    rationale = "Hardware camera flashlight toggle"
                )
            }
            // Open App
            lower.startsWith("open ") || lower.startsWith("খোল ") || lower.startsWith("চালু করো ") || lower.startsWith("launch ") -> {
                val appName = extractAppNameFromOpenQuery(raw)
                ToolIntent(
                    toolName = "open_app",
                    arguments = mapOf("app_name" to appName),
                    riskLevel = "LOW",
                    rationale = "Launch installed application"
                )
            }
            // Navigation: Back / Home
            lower == "go back" || lower == "back" || lower == "পিছনে যাও" -> {
                ToolIntent("press_back", emptyMap(), "LOW", "Back navigation")
            }
            lower == "go home" || lower == "home" || lower == "হোমে যাও" -> {
                ToolIntent("press_home", emptyMap(), "LOW", "Home navigation")
            }
            // Screen Reading
            lower.contains("read screen") || lower.contains("স্ক্রিন পড়") || lower.contains("what is on screen") -> {
                ToolIntent("read_screen", emptyMap(), "LOW", "Screen inspection")
            }
            // Battery & Device Diagnostics
            lower.contains("battery") || lower.contains("চার্জ") || lower.contains("ব্যাটারি") || lower.contains("device status") -> {
                ToolIntent("get_device_status", emptyMap(), "LOW", "Device diagnostics")
            }
            // Web Search
            lower.startsWith("search ") || lower.startsWith("গুগল করো ") || lower.startsWith("খোঁজ ") -> {
                val query = raw.substringAfter(" ").trim()
                ToolIntent("search_web", mapOf("query" to query), "LOW", "Web search")
            }
            // Security Audit
            lower.contains("security") || lower.contains("audit") || lower.contains("সিকিউরিটি") -> {
                ToolIntent("security_audit_check", emptyMap(), "LOW", "Security audit")
            }
            else -> null
        }

        val latency = System.currentTimeMillis() - startTime

        if (toolIntent != null) {
            val responseText = generateLocalIntentResponseText(toolIntent)
            return@withContext ModelResponse(
                text = responseText,
                latencyMs = latency,
                providerType = "LOCAL_REASONER",
                confidence = 0.95f,
                toolIntent = toolIntent,
                usedContextChunks = contextChunks
            )
        }

        // Conversational / RAG response
        val conversationalText = if (contextChunks.isNotEmpty()) {
            "JARVIS Local Knowledge: " + contextChunks.first().content
        } else {
            "JARVIS Standby: Command analyzed. Ready for phone tasks or knowledge queries."
        }

        ModelResponse(
            text = conversationalText,
            latencyMs = latency,
            providerType = "LOCAL_REASONER",
            confidence = 0.70f,
            toolIntent = null,
            usedContextChunks = contextChunks
        )
    }

    private fun extractWhatsAppParameters(raw: String): Pair<String, String> {
        val lower = raw.lowercase()
        var contact = "Contact"
        var message = "I will call you later."

        if (raw.contains("-কে বলো") || raw.contains("-কে বল")) {
            val beforeKe = raw.substringBefore("-কে").substringAfter("খুলে ").substringAfter("WhatsApp ").trim()
            if (beforeKe.isNotEmpty()) contact = beforeKe
            val afterBolo = raw.substringAfter("বলো ").substringAfter("বল ").trim()
            if (afterBolo.isNotEmpty()) message = afterBolo
        } else if (raw.contains(" to ") && raw.contains(":")) {
            contact = raw.substringAfter(" to ").substringBefore(":").trim()
            message = raw.substringAfter(":").trim()
        } else if (raw.contains(" message ") && raw.contains(" that ")) {
            contact = raw.substringAfter(" message ").substringBefore(" that ").trim()
            message = raw.substringAfter(" that ").trim()
        } else {
            val parts = raw.split(" ")
            if (parts.size >= 3) {
                contact = parts[1]
                message = parts.drop(2).joinToString(" ")
            }
        }
        return Pair(contact, message)
    }

    private fun extractContactFromCallQuery(raw: String): String {
        return raw.replace("(?i)call to |call |ফোন করো |কল করো |ফোন দাও |কে কল করো".toRegex(), "").trim()
    }

    private fun extractContactFromQuery(raw: String): String {
        return raw.replace("(?i)open |খোল |whatsapp |হোয়াটসঅ্যাপ |chat with |contact of |নাম্বার |number ".toRegex(), "").trim()
    }

    private fun extractSmsParameters(raw: String): Pair<String, String> {
        val contact = if (raw.contains(" to ")) raw.substringAfter(" to ").substringBefore(":").trim() else "Contact"
        val message = if (raw.contains(":")) raw.substringAfter(":").trim() else raw
        return Pair(contact, message)
    }

    private fun extractAppNameFromOpenQuery(raw: String): String {
        return raw.replace("(?i)open |খোল |চালু করো |launch ".toRegex(), "").trim()
    }

    private fun generateLocalIntentResponseText(intent: ToolIntent): String {
        return when (intent.toolName) {
            "open_app" -> "Opening ${intent.arguments["app_name"]}."
            "send_whatsapp_message" -> "Preparing WhatsApp message for ${intent.arguments["contact_name"]}."
            "open_whatsapp_chat" -> "Opening WhatsApp chat with ${intent.arguments["contact_name"]}."
            "make_phone_call" -> "Initiating call to ${intent.arguments["contact_name"]}."
            "send_sms" -> "Drafting SMS to ${intent.arguments["recipient"]}."
            "toggle_flashlight" -> "Adjusting device flashlight."
            "get_device_status" -> "Checking device telemetry."
            "press_back" -> "Going back."
            "press_home" -> "Going home."
            "read_screen" -> "Inspecting screen elements."
            "search_web" -> "Searching web."
            "security_audit_check" -> "Running security scan."
            else -> "Executing ${intent.toolName}."
        }
    }
}

/**
 * Gemini Model Provider: The Cloud AI Brain.
 * Produces structured tool calls for phone operations.
 */
class GeminiModelProvider : ModelProvider {
    var runtimeApiKey: String = ""
    var selectedModel: String = "gemini-3.5-flash"
    var temperature: Float = 0.4f
    var customSystemPrompt: String = ToolDefinitions.generateSystemPromptToolDescriptions()

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun getEffectiveApiKey(): String {
        if (runtimeApiKey.isNotBlank() && runtimeApiKey != "MY_GEMINI_API_KEY") {
            return runtimeApiKey
        }
        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun testConnection(apiKeyToTest: String, modelToTest: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = apiKeyToTest.trim().ifEmpty { getEffectiveApiKey() }
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            return@withContext Pair(false, "API key is empty. Enter a valid Google AI Studio Gemini API key.")
        }

        val model = modelToTest.trim().ifEmpty { selectedModel }
        val startTime = System.currentTimeMillis()
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "JARVIS connection diagnostic test. Respond with 'Online'.")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                val replyText = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Connected"
                Pair(true, "Success ($latency ms): Connected to $model\nResponse: ${replyText.trim()}")
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                Pair(false, "Authentication or Model Error (HTTP ${response.code}): $errorMsg")
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Pair(false, "Network failure (${latency}ms): ${e.localizedMessage ?: "Unable to reach Google Gemini endpoint"}")
        }
    }

    override suspend fun generateResponse(
        prompt: String,
        contextChunks: List<KnowledgeChunkEntity>,
        language: String
    ): ModelResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = getEffectiveApiKey()
        val model = selectedModel.ifEmpty { "gemini-3.5-flash" }

        if (apiKey.isBlank()) {
            delay(150)
            val fallbackLatency = System.currentTimeMillis() - startTime
            return@withContext ModelResponse(
                text = "Gemini Cloud Teacher: No active API key entered in settings. Enter your key in the Models tab for live Cloud reasoning.",
                latencyMs = fallbackLatency,
                providerType = "GEMINI (Simulation)",
                confidence = 0.90f,
                isTeacherTrained = false
            )
        }

        try {
            val systemInstruction = ToolDefinitions.generateSystemPromptToolDescriptions()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemInstruction\n\nUser Request: $prompt")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature)
                    put("maxOutputTokens", 1024)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""

                // Extract structured tool intent if present in JSON format
                val toolIntent = parseToolIntentFromJson(text)

                val displayableText = if (toolIntent != null) {
                    when (toolIntent.toolName) {
                        "open_app" -> "Opening ${toolIntent.arguments["app_name"]}."
                        "send_whatsapp_message" -> "Preparing WhatsApp message for ${toolIntent.arguments["contact_name"]}."
                        "make_phone_call" -> "Initiating call to ${toolIntent.arguments["contact_name"]}."
                        "send_sms" -> "Drafting SMS to ${toolIntent.arguments["recipient"]}."
                        else -> "Executing action: ${toolIntent.toolName}"
                    }
                } else {
                    text.trim()
                }

                ModelResponse(
                    text = displayableText,
                    latencyMs = latency,
                    providerType = "GEMINI (${model})",
                    confidence = 0.98f,
                    toolIntent = toolIntent,
                    isTeacherTrained = true
                )
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                ModelResponse(
                    text = "Gemini Cloud Error ($model): $errorMsg. Falling back to local brain.",
                    latencyMs = latency,
                    providerType = "GEMINI (Fallback)",
                    confidence = 0.40f
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ModelResponse(
                text = "Network unreachable (${e.localizedMessage}). Local offline brain active.",
                latencyMs = latency,
                providerType = "LOCAL (Offline Fallback)",
                confidence = 0.70f
            )
        }
    }

    private fun parseToolIntentFromJson(rawText: String): ToolIntent? {
        try {
            val jsonCandidate = when {
                rawText.contains("```json") -> {
                    rawText.substringAfter("```json").substringBefore("```").trim()
                }
                rawText.contains("```") -> {
                    rawText.substringAfter("```").substringBefore("```").trim()
                }
                rawText.contains("{") && rawText.contains("}") -> {
                    val start = rawText.indexOf("{")
                    val end = rawText.lastIndexOf("}") + 1
                    rawText.substring(start, end)
                }
                else -> null
            } ?: return null

            val json = JSONObject(jsonCandidate)
            val toolName = json.optString("tool").ifEmpty { json.optString("tool_name") }
            if (toolName.isBlank()) return null

            val argsObj = json.optJSONObject("arguments") ?: JSONObject()
            val argsMap = mutableMapOf<String, String>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                argsMap[key] = argsObj.optString(key)
            }

            val risk = json.optString("risk_level", "MEDIUM")
            return ToolIntent(
                toolName = toolName,
                arguments = argsMap,
                riskLevel = risk,
                rationale = "Generated by Gemini Reasoning Brain"
            )
        } catch (e: Exception) {
            return null
        }
    }
}

/**
 * Hybrid Provider: Intelligent Local-First Router with Supervisor Fallback
 */
class HybridModelProvider(
    private val localProvider: LocalModelProvider = LocalModelProvider(),
    private val geminiProvider: GeminiModelProvider = GeminiModelProvider()
) : ModelProvider {

    override suspend fun generateResponse(
        prompt: String,
        contextChunks: List<KnowledgeChunkEntity>,
        language: String
    ): ModelResponse {
        val lower = prompt.lowercase()

        // Explicit local operations that don't need cloud latency
        val isExplicitLocal = lower.contains("flashlight") ||
                lower.startsWith("open ") ||
                lower.startsWith("খোল ") ||
                lower.contains("battery") ||
                lower.contains("চার্জ") ||
                lower.startsWith("call ") ||
                lower.startsWith("কল ") ||
                lower.startsWith("phone ") ||
                lower.contains("whatsapp") ||
                lower.contains("security") ||
                lower == "back" || lower == "home"

        val isExplicitCloud = lower.contains("search web") ||
                lower.contains("today's news") ||
                lower.contains("ask gemini") ||
                lower.contains("explain ") ||
                lower.contains("why ") ||
                lower.contains("how ")

        if (isExplicitLocal && !isExplicitCloud) {
            val local = localProvider.generateResponse(prompt, contextChunks, language)
            if (local.toolIntent != null) return local
        }

        val localResult = localProvider.generateResponse(prompt, contextChunks, language)
        if (localResult.confidence >= 0.85f && !isExplicitCloud) {
            return localResult
        }

        // Escalate to Gemini Brain
        val cloudResult = geminiProvider.generateResponse(prompt, contextChunks, language)
        return if (cloudResult.confidence > 0.50f) cloudResult else localResult
    }
}
