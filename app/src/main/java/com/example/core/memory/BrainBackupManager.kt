package com.example.core.memory

import android.content.Context
import android.util.Log
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BrainBackupResult(
    val success: Boolean,
    val exportedJson: String? = null,
    val memoriesCount: Int = 0,
    val skillsCount: Int = 0,
    val knowledgeCount: Int = 0,
    val message: String
)

/**
 * BrainBackupManager.
 * Exports and imports JARVIS Brain snapshots (Memories, Skills, Experiences, Knowledge).
 * Strictly omits private credentials, API keys, and sensitive tokens from backup exports.
 */
class BrainBackupManager(
    private val context: Context,
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_BrainBackup"
    }

    /**
     * Exports brain data to a structured JSON string.
     */
    suspend fun exportBrain(): BrainBackupResult = withContext(Dispatchers.IO) {
        try {
            val memories = dao.getAllMemoriesSync()
            val skills = dao.getAllSkillsSync()
            val knowledge = dao.getAllKnowledgeChunksSync()

            val rootJson = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

                // Memories array
                put("memories", JSONArray().apply {
                    memories.forEach { mem ->
                        put(JSONObject().apply {
                            put("category", mem.category.name)
                            put("key", mem.key)
                            put("value", mem.value)
                            put("confidence", mem.confidence.toDouble())
                            put("importance", mem.importance.toDouble())
                        })
                    }
                })

                // Skills array
                put("skills", JSONArray().apply {
                    skills.forEach { skill ->
                        put(JSONObject().apply {
                            put("name", skill.name)
                            put("description", skill.description)
                            put("procedure", skill.procedure)
                            put("verificationMethod", skill.verificationMethod)
                            put("confidence", skill.confidence.toDouble())
                            put("successRate", skill.successRate.toDouble())
                            put("isLearned", skill.isLearnedFromExperience)
                        })
                    }
                })

                // Knowledge chunks
                put("knowledge", JSONArray().apply {
                    knowledge.forEach { chunk ->
                        put(JSONObject().apply {
                            put("title", chunk.title)
                            put("content", chunk.content)
                            put("tags", chunk.tags)
                            put("sourceDocument", chunk.sourceDocument)
                        })
                    }
                })
            }

            val jsonString = rootJson.toString(2)
            BrainBackupResult(
                success = true,
                exportedJson = jsonString,
                memoriesCount = memories.size,
                skillsCount = skills.size,
                knowledgeCount = knowledge.size,
                message = "Brain exported successfully (${memories.size} memories, ${skills.size} skills, ${knowledge.size} knowledge items)."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Brain export failed", e)
            BrainBackupResult(success = false, message = "Export error: ${e.localizedMessage}")
        }
    }

    /**
     * Imports brain snapshot from JSON string into Room database.
     */
    suspend fun importBrain(jsonString: String): BrainBackupResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            var memCount = 0
            var skillCount = 0
            var knowCount = 0

            // Import memories
            val memArray = root.optJSONArray("memories") ?: JSONArray()
            for (i in 0 until memArray.length()) {
                val obj = memArray.getJSONObject(i)
                val catStr = obj.optString("category", "USER_PREFERENCE")
                val cat = try { MemoryCategory.valueOf(catStr) } catch (e: Exception) { MemoryCategory.USER_PREFERENCE }
                val key = obj.optString("key", "")
                val value = obj.optString("value", "")
                if (key.isNotBlank() && value.isNotBlank()) {
                    dao.insertMemory(
                        MemoryEntity(
                            category = cat,
                            key = key,
                            value = value,
                            confidence = obj.optDouble("confidence", 0.95).toFloat(),
                            importance = obj.optDouble("importance", 0.5).toFloat()
                        )
                    )
                    memCount++
                }
            }

            // Import skills
            val skillArray = root.optJSONArray("skills") ?: JSONArray()
            for (i in 0 until skillArray.length()) {
                val obj = skillArray.getJSONObject(i)
                val name = obj.optString("name", "")
                if (name.isNotBlank()) {
                    val existing = dao.getSkillByName(name)
                    val skill = SkillEntity(
                        id = existing?.id ?: 0L,
                        name = name,
                        description = obj.optString("description", "Imported skill"),
                        requiredPermissions = "AccessibilityService",
                        inputSchema = "{\"query\": \"string\"}",
                        outputSchema = "{\"status\": \"string\"}",
                        procedure = obj.optString("procedure", obj.optString("procedureJson", "[]")),
                        verificationMethod = obj.optString("verificationMethod", "accessibility_check"),
                        confidence = obj.optDouble("confidence", 0.90).toFloat(),
                        successRate = obj.optDouble("successRate", 1.0).toFloat(),
                        isLearnedFromExperience = obj.optBoolean("isLearned", false),
                        isEnabled = true
                    )
                    dao.insertSkill(skill)
                    skillCount++
                }
            }

            // Import knowledge
            val knowArray = root.optJSONArray("knowledge") ?: JSONArray()
            for (i in 0 until knowArray.length()) {
                val obj = knowArray.getJSONObject(i)
                val title = obj.optString("title", "")
                val content = obj.optString("content", "")
                if (title.isNotBlank() && content.isNotBlank()) {
                    dao.insertKnowledgeChunk(
                        KnowledgeChunkEntity(
                            title = title,
                            sourceDocument = obj.optString("sourceDocument", "IMPORTED_BACKUP"),
                            content = content,
                            tags = obj.optString("tags", "IMPORTED"),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    knowCount++
                }
            }

            BrainBackupResult(
                success = true,
                memoriesCount = memCount,
                skillsCount = skillCount,
                knowledgeCount = knowCount,
                message = "Brain restored successfully: $memCount memories, $skillCount skills, $knowCount knowledge items."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Brain import failed", e)
            BrainBackupResult(success = false, message = "Import error: ${e.localizedMessage}")
        }
    }
}
