package com.example.core.brain

import com.example.core.knowledge.KnowledgeIngestionEngine
import com.example.core.security.PrivacyFilter
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.BrainSnapshotEntity
import com.example.data.local.entity.KnowledgeItemEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import com.example.data.local.entity.SkillSource
import com.example.data.local.entity.ValidationStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupExportResult(
    val success: Boolean,
    val exportedJson: String,
    val knowledgeCount: Int,
    val skillCount: Int,
    val memoryCount: Int,
    val sanitizedItemsCount: Int
)

data class BackupImportResult(
    val success: Boolean,
    val restoredKnowledgeCount: Int,
    val restoredSkillCount: Int,
    val restoredMemoryCount: Int,
    val rejectedCount: Int,
    val message: String
)

class BrainBackupManager(
    private val dao: JarvisDao,
    private val ingestionEngine: KnowledgeIngestionEngine
) {
    suspend fun createBrainExport(): BackupExportResult = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("schemaVersion", "1.0")
        root.put("createdAt", System.currentTimeMillis())
        root.put("deviceProfile", "Redmi Note 12 / Android 15")

        var sanitizedCount = 0

        // 1. Export Verified Knowledge Items
        val knowledgeList = dao.getAllKnowledgeItemsSync().filter {
            it.validationStage == ValidationStage.ACTIVE || it.validationStage == ValidationStage.VERIFIED
        }
        val knowledgeArray = JSONArray()
        for (item in knowledgeList) {
            val sanitized = PrivacyFilter.sanitizeForCloud(item.content)
            if (sanitized.hadSensitiveData) sanitizedCount++

            val obj = JSONObject()
            obj.put("key", item.knowledgeKey)
            obj.put("title", item.title)
            obj.put("content", sanitized.sanitizedText)
            obj.put("summary", item.summary)
            obj.put("type", item.knowledgeType.name)
            obj.put("confidence", item.confidence.toDouble())
            obj.put("tags", item.tags)
            knowledgeArray.put(obj)
        }
        root.put("knowledgeItems", knowledgeArray)

        // 2. Export Skills
        val skills = dao.getAllSkillsSync().filter { it.isEnabled }
        val skillArray = JSONArray()
        for (s in skills) {
            val sanitized = PrivacyFilter.sanitizeForCloud(s.procedure)
            if (sanitized.hadSensitiveData) sanitizedCount++

            val obj = JSONObject()
            obj.put("name", s.name)
            obj.put("description", s.description)
            obj.put("procedure", sanitized.sanitizedText)
            obj.put("requiredPermissions", s.requiredPermissions)
            obj.put("inputSchema", s.inputSchema)
            obj.put("outputSchema", s.outputSchema)
            obj.put("verificationMethod", s.verificationMethod)
            obj.put("confidence", s.confidence.toDouble())
            obj.put("successCount", s.successCount)
            skillArray.put(obj)
        }
        root.put("skills", skillArray)

        // 3. Export Memories (exclude raw credentials)
        val memories = dao.getAllMemoriesSync().filter { it.category != MemoryCategory.USER_PREFERENCE || !it.key.contains("key") }
        val memoryArray = JSONArray()
        for (m in memories) {
            val sanitized = PrivacyFilter.sanitizeForCloud(m.value)
            if (sanitized.hadSensitiveData) sanitizedCount++

            val obj = JSONObject()
            obj.put("category", m.category.name)
            obj.put("key", m.key)
            obj.put("value", sanitized.sanitizedText)
            obj.put("confidence", m.confidence.toDouble())
            memoryArray.put(obj)
        }
        root.put("memories", memoryArray)

        val exportedJson = root.toString(2)

        // Record Brain Snapshot
        val summaryObj = JSONObject().apply {
            put("knowledgeCount", knowledgeList.size)
            put("skillCount", skills.size)
            put("memoryCount", memories.size)
        }
        dao.insertBrainSnapshot(
            BrainSnapshotEntity(
                snapshotVersion = "1.0",
                createdAt = System.currentTimeMillis(),
                deviceProfile = "Redmi Note 12 / Android 15",
                summaryJson = summaryObj.toString(),
                exportedJson = exportedJson
            )
        )

        BackupExportResult(
            success = true,
            exportedJson = exportedJson,
            knowledgeCount = knowledgeList.size,
            skillCount = skills.size,
            memoryCount = memories.size,
            sanitizedItemsCount = sanitizedCount
        )
    }

    suspend fun importBrain(jsonString: String): BackupImportResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            var restoredK = 0
            var restoredS = 0
            var restoredM = 0
            var rejected = 0

            // 1. Import Knowledge
            if (root.has("knowledgeItems")) {
                val array = root.getJSONArray("knowledgeItems")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val title = obj.optString("title", "Imported Knowledge")
                    val content = obj.optString("content", "")
                    val tags = obj.optString("tags", "backup_import")

                    val result = ingestionEngine.ingest(
                        com.example.core.knowledge.IngestionCandidate(
                            title = title,
                            content = content,
                            tags = tags,
                            sourceType = com.example.data.local.entity.KnowledgeSourceType.USER_PROVIDED
                        )
                    )
                    if (result.success) restoredK++ else rejected++
                }
            }

            // 2. Import Skills
            if (root.has("skills")) {
                val array = root.getJSONArray("skills")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val desc = obj.optString("description", "")
                    val proc = obj.optString("procedure", "")
                    val reqPerms = obj.optString("requiredPermissions", "[]")
                    val inSchema = obj.optString("inputSchema", "{}")
                    val outSchema = obj.optString("outputSchema", "{}")
                    val verMethod = obj.optString("verificationMethod", "SCREEN_INSPECTION")

                    if (name.isNotBlank() && proc.isNotBlank()) {
                        val existing = dao.getSkillByName(name)
                        if (existing == null) {
                            dao.insertSkill(
                                SkillEntity(
                                    name = name,
                                    description = desc,
                                    requiredPermissions = reqPerms,
                                    inputSchema = inSchema,
                                    outputSchema = outSchema,
                                    procedure = proc,
                                    verificationMethod = verMethod,
                                    source = SkillSource.TEACHER,
                                    isEnabled = true
                                )
                            )
                            restoredS++
                        }
                    }
                }
            }

            // 3. Import Memories
            if (root.has("memories")) {
                val array = root.getJSONArray("memories")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val key = obj.optString("key", "")
                    val value = obj.optString("value", "")
                    val catStr = obj.optString("category", "IMPORTANT_FACT")
                    val category = try { MemoryCategory.valueOf(catStr) } catch (e: Exception) { MemoryCategory.IMPORTANT_FACT }

                    if (key.isNotBlank() && value.isNotBlank()) {
                        val existing = dao.getMemoryByKey(key)
                        if (existing == null) {
                            dao.insertMemory(
                                MemoryEntity(
                                    category = category,
                                    key = key,
                                    value = value,
                                    confidence = 0.95f,
                                    source = "Backup Import"
                                )
                            )
                            restoredM++
                        }
                    }
                }
            }

            BackupImportResult(
                success = true,
                restoredKnowledgeCount = restoredK,
                restoredSkillCount = restoredS,
                restoredMemoryCount = restoredM,
                rejectedCount = rejected,
                message = "Import complete: $restoredK knowledge, $restoredS skills, $restoredM memories restored."
            )
        } catch (e: Exception) {
            BackupImportResult(
                success = false,
                restoredKnowledgeCount = 0,
                restoredSkillCount = 0,
                restoredMemoryCount = 0,
                rejectedCount = 0,
                message = "Failed to parse brain backup: ${e.localizedMessage}"
            )
        }
    }
}
