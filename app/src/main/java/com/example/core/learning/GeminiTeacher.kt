package com.example.core.learning

import android.util.Log
import com.example.BuildConfig
import com.example.core.agent.PlanStep
import com.example.core.agent.TaskPlan
import com.example.core.model.ToolIntent
import com.example.core.vision.UnifiedScreen
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.GeminiTeacherSessionEntity
import com.example.data.local.preference.JarvisPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TeacherPlanResult(
    val success: Boolean,
    val plan: TaskPlan?,
    val confidence: Float,
    val thoughtProcess: String,
    val rawJson: String,
    val latencyMs: Long,
    val errorMessage: String? = null
)

/**
 * GeminiTeacher.
 * Structured Cloud AI Teacher and Supervisor.
 * Invoked during low-confidence scenarios, novel apps, or complex multi-step reasoning.
 * Outputs strictly validated JSON task plans for local execution and distillation into skills.
 */
class GeminiTeacher(
    private val dao: JarvisDao,
    private val preferences: JarvisPreferences
) {
    companion object {
        private const val TAG = "JARVIS_GeminiTeacher"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Solicits a structured multi-step execution plan from the Gemini Teacher.
     */
    suspend fun requestStructuredTeachingPlan(
        goal: String,
        currentScreen: UnifiedScreen?,
        lowConfidenceReason: String,
        relevantMemoryContext: String = ""
    ): TeacherPlanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = getApiKey()
        val model = preferences.geminiModel.ifBlank { "gemini-3.5-flash" }

        if (apiKey.isBlank()) {
            return@withContext TeacherPlanResult(
                success = false,
                plan = null,
                confidence = 0.0f,
                thoughtProcess = "No API key configured for Gemini Teacher.",
                rawJson = "",
                latencyMs = 0L,
                errorMessage = "Gemini API key is empty in Settings."
            )
        }

        try {
            val systemPrompt = """
You are the JARVIS Autonomous Android AI Teacher & Plan Generator.
Given a user goal, active screen elements, and background context, produce a STRICT JSON EXECUTION PLAN.

AVAILABLE TOOLS:
- open_app(app_name: String)
- tap(target_text: String)
- type_text(text: String)
- scroll(direction: "DOWN" | "UP")
- press_back()
- press_home()
- send_whatsapp_message(contact_name: String, message: String)
- make_phone_call(contact_name: String)
- send_sms(recipient: String, message: String)
- toggle_flashlight(state: Boolean)
- get_device_status()
- get_screen_elements()

STRICT JSON OUTPUT FORMAT ONLY (NO PROSE OUTSIDE JSON):
{
  "thoughtProcess": "Short explanation of approach",
  "confidence": 0.95,
  "appPackage": "com.target.app",
  "steps": [
    {
      "stepNumber": 1,
      "description": "Step action description",
      "tool": "open_app",
      "arguments": {
        "app_name": "Target App"
      },
      "expectedOutcome": "Target App launched in foreground"
    }
  ]
}
            """.trimIndent()

            val screenSummary = currentScreen?.getSummary() ?: "No active screen snapshot available"
            val userPayload = """
USER GOAL: "$goal"
ESCALATION REASON: $lowConfidenceReason

CURRENT SCREEN STATE:
$screenSummary

RETRIEVED MEMORY CONTEXT:
$relevantMemoryContext

Output the strict JSON task plan now:
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemPrompt\n\n$userPayload")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 1200)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val latency = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                val err = "HTTP ${response.code}: $responseBody"
                Log.e(TAG, "Gemini Teacher API error: $err")
                return@withContext TeacherPlanResult(
                    success = false,
                    plan = null,
                    confidence = 0.0f,
                    thoughtProcess = "Teacher API returned error",
                    rawJson = responseBody,
                    latencyMs = latency,
                    errorMessage = err
                )
            }

            val responseJson = JSONObject(responseBody)
            val text = responseJson.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: ""

            val parsedPlan = parseStructuredPlan(text, goal)
            val session = GeminiTeacherSessionEntity(
                userGoal = goal,
                lowConfidenceReason = lowConfidenceReason,
                teacherModel = model,
                structuredPlanJson = text,
                wasExecuted = false,
                executionSuccessful = false,
                latencyMs = latency
            )
            dao.insertTeacherSession(session)

            if (parsedPlan != null && parsedPlan.steps.isNotEmpty()) {
                TeacherPlanResult(
                    success = true,
                    plan = parsedPlan,
                    confidence = 0.95f,
                    thoughtProcess = "Gemini Teacher formulated ${parsedPlan.steps.size} step plan.",
                    rawJson = text,
                    latencyMs = latency
                )
            } else {
                TeacherPlanResult(
                    success = false,
                    plan = null,
                    confidence = 0.3f,
                    thoughtProcess = "Failed to parse structured steps from Teacher output",
                    rawJson = text,
                    latencyMs = latency,
                    errorMessage = "JSON plan had empty steps"
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Gemini Teacher Exception", e)
            TeacherPlanResult(
                success = false,
                plan = null,
                confidence = 0.0f,
                thoughtProcess = "Exception: ${e.localizedMessage}",
                rawJson = "",
                latencyMs = latency,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun parseStructuredPlan(rawJson: String, goal: String): TaskPlan? {
        return try {
            val cleanJson = when {
                rawJson.contains("```json") -> rawJson.substringAfter("```json").substringBefore("```").trim()
                rawJson.contains("```") -> rawJson.substringAfter("```").substringBefore("```").trim()
                rawJson.contains("{") && rawJson.contains("}") -> {
                    val s = rawJson.indexOf("{")
                    val e = rawJson.lastIndexOf("}") + 1
                    rawJson.substring(s, e)
                }
                else -> rawJson
            }

            val root = JSONObject(cleanJson)
            val stepsArray = root.optJSONArray("steps") ?: JSONArray()
            val planSteps = mutableListOf<PlanStep>()

            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val stepNum = stepObj.optInt("stepNumber", i + 1)
                val desc = stepObj.optString("description", "Execute teacher step $stepNum")
                val tool = stepObj.optString("tool", "tap")
                val exp = stepObj.optString("expectedOutcome", "Step verified")

                val argsObj = stepObj.optJSONObject("arguments") ?: JSONObject()
                val argsMap = mutableMapOf<String, String>()
                val keys = argsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    argsMap[k] = argsObj.optString(k)
                }

                val risk = stepObj.optString("riskLevel", "LOW")
                planSteps.add(PlanStep(stepNum, desc, ToolIntent(tool, argsMap, risk), exp))
            }

            TaskPlan(goal = goal, steps = planSteps)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing teacher structured plan", e)
            null
        }
    }

    private fun getApiKey(): String {
        val prefKey = preferences.geminiApiKey
        if (prefKey.isNotBlank() && prefKey != "MY_GEMINI_API_KEY") return prefKey
        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        } catch (e: Exception) {
            ""
        }
    }
}
