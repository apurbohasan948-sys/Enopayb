package com.example.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Gemini Multimodal Vision Provider.
 * Sends screenshot image payload to Gemini 1.5/2.5/Flash multimodal endpoint
 * and parses structured UI visual elements (icons, buttons, input fields, thumbnails, bounds).
 */
class GeminiVisionProvider : VisionProvider {

    override val providerName: String = "Gemini Cloud Multimodal Vision (Flash)"
    override val isMultimodalSupported: Boolean = true

    var runtimeApiKey: String = ""
    var selectedModel: String = "gemini-2.5-flash"

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

    override suspend fun analyzeScreenshot(
        bitmap: Bitmap?,
        prompt: String,
        semanticGoal: String?,
        appPackage: String?,
        screenWidth: Int,
        screenHeight: Int
    ): VisualAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = getEffectiveApiKey()

        if (apiKey.isBlank()) {
            return@withContext VisualAnalysisResult(
                success = false,
                elements = emptyList(),
                description = "Gemini Vision fallback unavailable: API Key not set.",
                providerName = providerName,
                error = "API_KEY_MISSING",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        if (bitmap == null) {
            return@withContext VisualAnalysisResult(
                success = false,
                elements = emptyList(),
                description = "No screenshot bitmap provided for visual analysis.",
                providerName = providerName,
                error = "BITMAP_NULL",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            // Downscale bitmap if larger than 1024px for quick latency and low token usage
            val maxDim = 1024
            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val newW = (bitmap.width * ratio).toInt()
                val newH = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val imageBytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val targetGoal = semanticGoal?.let { SemanticTarget.normalizeIntent(it) } ?: "INTERACTIVE_ELEMENTS"

            val visionPrompt = """
                You are JARVIS's Android Multimodal Vision Engine.
                Analyze this Android mobile screen for the target: '$targetGoal' (Application: '${appPackage ?: "Unknown"}').
                Identify visible icons, buttons, search bars, play buttons, more options, back buttons, and input fields.
                Screen resolution is: width=${bitmap.width}, height=${bitmap.height}.

                Output strictly a JSON object formatted as:
                {
                  "elements": [
                    {
                      "semanticRole": "SEARCH"|"PLAY"|"PAUSE"|"MORE_OPTIONS"|"BACK"|"HOME"|"SETTINGS"|"SHARE"|"DOWNLOAD"|"ADD"|"INPUT_FIELD"|"VIDEO_ITEM"|"SEND_BUTTON",
                      "visualDescription": "magnifying glass icon in top right bar",
                      "confidence": 0.95,
                      "bounds": {
                        "left": 900,
                        "top": 100,
                        "right": 980,
                        "bottom": 180
                      }
                    }
                  ],
                  "summary": "Screen summary"
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", visionPrompt)
                            })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("response_mime_type", "application/json")
                })
            }

            val modelName = selectedModel.ifEmpty { "gemini-2.5-flash" }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext VisualAnalysisResult(
                    success = false,
                    elements = emptyList(),
                    description = "Gemini Vision request failed with HTTP ${response.code}",
                    providerName = providerName,
                    error = "HTTP_${response.code}",
                    rawJson = responseBody,
                    latencyMs = latency
                )
            }

            val parsedElements = mutableListOf<VisualElement>()
            var description = "Detected visual elements via Gemini Vision"

            try {
                val respJson = JSONObject(responseBody)
                val candidates = respJson.optJSONArray("candidates")
                val textResponse = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "{}"

                val parsedContent = JSONObject(textResponse)
                description = parsedContent.optString("summary", description)
                val elementsArray = parsedContent.optJSONArray("elements")

                if (elementsArray != null) {
                    for (i in 0 until elementsArray.length()) {
                        val item = elementsArray.getJSONObject(i)
                        val role = item.optString("semanticRole", SemanticTarget.UNKNOWN)
                        val desc = item.optString("visualDescription", "Visual Icon")
                        val conf = item.optDouble("confidence", 0.9).toFloat()
                        val boundsObj = item.optJSONObject("bounds")
                        val rect = if (boundsObj != null) {
                            Rect(
                                boundsObj.optInt("left", 0),
                                boundsObj.optInt("top", 0),
                                boundsObj.optInt("right", 100),
                                boundsObj.optInt("bottom", 100)
                            )
                        } else {
                            Rect(0, 0, 100, 100)
                        }

                        parsedElements.add(
                            VisualElement(
                                semanticRole = role,
                                visualDescription = desc,
                                bounds = rect,
                                confidence = conf,
                                source = "GEMINI_VISION"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            VisualAnalysisResult(
                success = parsedElements.isNotEmpty(),
                elements = parsedElements,
                description = "$description (${parsedElements.size} elements found)",
                providerName = providerName,
                rawJson = responseBody,
                latencyMs = latency
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            VisualAnalysisResult(
                success = false,
                elements = emptyList(),
                description = "Gemini Vision error: ${e.localizedMessage}",
                providerName = providerName,
                error = e.message,
                latencyMs = latency
            )
        }
    }
}
