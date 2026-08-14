package com.example.data.repository

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class JarvisRepository(private val dao: JarvisDao) {

    // === MEMORIES ===
    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()
    fun getMemoriesByCategory(category: MemoryCategory): Flow<List<MemoryEntity>> = dao.getMemoriesByCategory(category)
    suspend fun searchMemories(query: String): List<MemoryEntity> = dao.searchMemories(query)
    suspend fun insertMemory(memory: MemoryEntity): Long = dao.insertMemory(memory)
    suspend fun updateMemory(memory: MemoryEntity) = dao.updateMemory(memory)
    suspend fun deleteMemory(memory: MemoryEntity) = dao.deleteMemory(memory)
    suspend fun clearAllMemories() = dao.clearAllMemories()

    // === SKILLS ===
    val allSkills: Flow<List<SkillEntity>> = dao.getAllSkills()
    suspend fun getSkillByName(name: String): SkillEntity? = dao.getSkillByName(name)
    suspend fun insertSkill(skill: SkillEntity): Long = dao.insertSkill(skill)
    suspend fun updateSkill(skill: SkillEntity) = dao.updateSkill(skill)
    suspend fun deleteSkill(skill: SkillEntity) = dao.deleteSkill(skill)
    suspend fun incrementSkillUsage(name: String) = dao.incrementSkillUsage(name)

    // === SECURITY EVENTS ===
    val allSecurityEvents: Flow<List<SecurityEventEntity>> = dao.getAllSecurityEvents()
    suspend fun insertSecurityEvent(event: SecurityEventEntity): Long = dao.insertSecurityEvent(event)
    suspend fun clearSecurityEvents() = dao.clearSecurityEvents()

    // === CHAT MESSAGES ===
    val chatMessages: Flow<List<ChatMessageEntity>> = dao.getRecentChatMessages()
    suspend fun insertChatMessage(message: ChatMessageEntity): Long = dao.insertChatMessage(message)
    suspend fun clearChatMessages() = dao.clearChatMessages()

    // === KNOWLEDGE CHUNKS ===
    val allKnowledgeChunks: Flow<List<KnowledgeChunkEntity>> = dao.getAllKnowledgeChunks()
    suspend fun searchKnowledge(query: String): List<KnowledgeChunkEntity> = dao.searchKnowledgeChunks(query)
    suspend fun insertKnowledgeChunk(chunk: KnowledgeChunkEntity): Long = dao.insertKnowledgeChunk(chunk)
    suspend fun deleteKnowledgeChunk(chunk: KnowledgeChunkEntity) = dao.deleteKnowledgeChunk(chunk)
    suspend fun clearKnowledgeChunks() = dao.clearKnowledgeChunks()

    // === BRAIN BACKUP / EXPORT / IMPORT ===
    suspend fun exportBrainJson(memories: List<MemoryEntity>, skills: List<SkillEntity>, knowledge: List<KnowledgeChunkEntity>): String {
        val root = JSONObject()
        root.put("version", "1.0.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("assistant", "JARVIS-Android")

        val memArray = JSONArray()
        memories.forEach { m ->
            val obj = JSONObject()
            obj.put("category", m.category.name)
            obj.put("key", m.key)
            obj.put("value", m.value)
            obj.put("confidence", m.confidence.toDouble())
            obj.put("source", m.source)
            memArray.put(obj)
        }
        root.put("memories", memArray)

        val skillArray = JSONArray()
        skills.forEach { s ->
            val obj = JSONObject()
            obj.put("name", s.name)
            obj.put("description", s.description)
            obj.put("requiredPermissions", s.requiredPermissions)
            obj.put("riskLevel", s.riskLevel.name)
            obj.put("procedure", s.procedure)
            obj.put("version", s.version)
            skillArray.put(obj)
        }
        root.put("skills", skillArray)

        val knowArray = JSONArray()
        knowledge.forEach { k ->
            val obj = JSONObject()
            obj.put("title", k.title)
            obj.put("sourceDocument", k.sourceDocument)
            obj.put("content", k.content)
            obj.put("tags", k.tags)
            knowArray.put(obj)
        }
        root.put("knowledge", knowArray)

        return root.toString(2)
    }

    suspend fun importBrainJson(jsonStr: String): Int {
        var importedCount = 0
        try {
            val root = JSONObject(jsonStr)
            if (root.has("memories")) {
                val memArray = root.getJSONArray("memories")
                for (i in 0 until memArray.length()) {
                    val obj = memArray.getJSONObject(i)
                    val catStr = obj.optString("category", MemoryCategory.IMPORTANT_FACT.name)
                    val cat = try { MemoryCategory.valueOf(catStr) } catch (e: Exception) { MemoryCategory.IMPORTANT_FACT }
                    dao.insertMemory(
                        MemoryEntity(
                            category = cat,
                            key = obj.optString("key", "imported_fact"),
                            value = obj.optString("value", ""),
                            confidence = obj.optDouble("confidence", 1.0).toFloat(),
                            source = "Brain Import"
                        )
                    )
                    importedCount++
                }
            }
            if (root.has("knowledge")) {
                val knowArray = root.getJSONArray("knowledge")
                for (i in 0 until knowArray.length()) {
                    val obj = knowArray.getJSONObject(i)
                    dao.insertKnowledgeChunk(
                        KnowledgeChunkEntity(
                            title = obj.optString("title", "Imported Document"),
                            sourceDocument = obj.optString("sourceDocument", "backup.json"),
                            content = obj.optString("content", ""),
                            tags = obj.optString("tags", "backup, imported")
                        )
                    )
                    importedCount++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return importedCount
    }
}
