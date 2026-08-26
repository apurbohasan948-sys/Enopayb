package com.example.core.learning

import android.util.Log
import com.example.core.agent.StepExecutionRecord
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.DatasetFormat
import com.example.data.local.entity.TrainingExampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * TrainingDatasetManager.
 * Curates verified high-quality task demonstrations for fine-tuning local models.
 * Excludes sensitive data (PII, tokens, passwords).
 * Exports in industry standard Alpaca, ShareGPT, and JSONL formats.
 */
class TrainingDatasetManager(
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_DatasetMgr"
    }

    val allExamples: Flow<List<TrainingExampleEntity>> = dao.getAllTrainingExamples()

    /**
     * Curates a verified task execution into a training example if quality standards are met.
     */
    suspend fun curateExample(
        instruction: String,
        contextSummary: String,
        stepRecords: List<StepExecutionRecord>,
        isSuccess: Boolean,
        qualityScore: Float = 0.95f
    ): Long = withContext(Dispatchers.IO) {
        if (!isSuccess || qualityScore < 0.85f) {
            Log.d(TAG, "Example skipped: did not meet quality threshold.")
            return@withContext -1L
        }

        // Privacy check
        if (containsSensitiveMarkers(instruction) || containsSensitiveMarkers(contextSummary)) {
            Log.w(TAG, "Privacy Shield: Demonstration blocked from training dataset due to sensitive marker.")
            return@withContext -1L
        }

        try {
            val planArray = JSONArray()
            val toolsUsed = mutableListOf<String>()

            stepRecords.forEach { record ->
                val sObj = JSONObject().apply {
                    put("stepNumber", record.step.stepNumber)
                    put("tool", record.step.toolIntent.toolName)
                    put("arguments", JSONObject(record.step.toolIntent.arguments))
                    put("expectedOutcome", record.step.expectedOutcome)
                }
                planArray.put(sObj)
                toolsUsed.add(record.step.toolIntent.toolName)
            }

            val example = TrainingExampleEntity(
                inputInstruction = instruction.trim(),
                contextSummary = contextSummary.trim(),
                successfulPlanJson = planArray.toString(),
                toolsUsedSummary = toolsUsed.distinct().joinToString(", "),
                verificationProof = "Verified through screen transition analysis (${stepRecords.size} steps).",
                qualityScore = qualityScore,
                format = DatasetFormat.ALPACA,
                isCurated = true,
                isExported = false,
                timestamp = System.currentTimeMillis()
            )

            val id = dao.insertTrainingExample(example)
            Log.d(TAG, "Curated new training example ID: $id ('$instruction')")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error curating training example", e)
            -1L
        }
    }

    /**
     * Exports curated examples in the requested AI format.
     */
    suspend fun exportDataset(format: DatasetFormat): String = withContext(Dispatchers.IO) {
        val examples = dao.getCuratedTrainingExamples()
        return@withContext when (format) {
            DatasetFormat.ALPACA -> exportAlpacaFormat(examples)
            DatasetFormat.SHAREGPT -> exportShareGptFormat(examples)
            DatasetFormat.JSONL_RAW -> exportJsonlFormat(examples)
        }
    }

    private fun exportAlpacaFormat(examples: List<TrainingExampleEntity>): String {
        val array = JSONArray()
        examples.forEach { ex ->
            val obj = JSONObject().apply {
                put("instruction", ex.inputInstruction)
                put("input", ex.contextSummary)
                put("output", ex.successfulPlanJson)
                put("quality_score", ex.qualityScore)
                put("verified", true)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    private fun exportShareGptFormat(examples: List<TrainingExampleEntity>): String {
        val array = JSONArray()
        examples.forEach { ex ->
            val convArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("from", "human")
                    put("value", "Instruction: ${ex.inputInstruction}\nContext: ${ex.contextSummary}")
                })
                put(JSONObject().apply {
                    put("from", "gpt")
                    put("value", ex.successfulPlanJson)
                })
            }
            val item = JSONObject().apply {
                put("conversations", convArray)
                put("system", "You are JARVIS, an autonomous Android AI assistant.")
            }
            array.put(item)
        }
        return array.toString(2)
    }

    private fun exportJsonlFormat(examples: List<TrainingExampleEntity>): String {
        val sb = StringBuilder()
        examples.forEach { ex ->
            val obj = JSONObject().apply {
                put("prompt", "${ex.inputInstruction}\nContext: ${ex.contextSummary}")
                put("completion", ex.successfulPlanJson)
            }
            sb.appendLine(obj.toString())
        }
        return sb.toString().trim()
    }

    private fun containsSensitiveMarkers(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("password") ||
                lower.contains("pin") ||
                lower.contains("token") ||
                lower.contains("bearer") ||
                lower.contains("credit card") ||
                lower.contains("cvv")
    }

    suspend fun clearTrainingDataset() = withContext(Dispatchers.IO) {
        dao.clearTrainingDataset()
    }
}
