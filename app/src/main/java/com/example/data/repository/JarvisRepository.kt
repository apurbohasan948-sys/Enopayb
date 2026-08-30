package com.example.data.repository

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.ExperienceSource
import com.example.data.local.entity.GeminiTeacherSessionEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import com.example.data.local.entity.SkillSource
import com.example.data.local.entity.TrainingExampleEntity
import com.example.data.local.entity.UserCorrectionEntity
import com.example.data.local.entity.VisualExperienceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
    suspend fun clearMemoriesByCategory(category: MemoryCategory) = dao.clearMemoriesByCategory(category)

    // === EXPERIENCES ===
    val allExperiences: Flow<List<ExperienceEntity>> = dao.getAllExperiences()
    val successfulExperiences: Flow<List<ExperienceEntity>> = dao.getSuccessfulExperiences()
    val failedExperiences: Flow<List<ExperienceEntity>> = dao.getFailedExperiences()
    suspend fun insertExperience(experience: ExperienceEntity): Long = dao.insertExperience(experience)
    suspend fun deleteExperience(experience: ExperienceEntity) = dao.deleteExperience(experience)
    suspend fun clearAllExperiences() = dao.clearAllExperiences()

    // === SKILLS ===
    val allSkills: Flow<List<SkillEntity>> = dao.getAllSkills()
    val enabledSkills: Flow<List<SkillEntity>> = dao.getEnabledSkills()
    val learnedSkills: Flow<List<SkillEntity>> = dao.getLearnedSkills()
    suspend fun getSkillByName(name: String): SkillEntity? = dao.getSkillByName(name)
    suspend fun insertSkill(skill: SkillEntity): Long = dao.insertSkill(skill)
    suspend fun updateSkill(skill: SkillEntity) = dao.updateSkill(skill)
    suspend fun deleteSkill(skill: SkillEntity) = dao.deleteSkill(skill)
    suspend fun incrementSkillUsage(name: String) = dao.incrementSkillUsage(name)
    suspend fun clearLearnedSkills() = dao.clearLearnedSkills()

    // === USER CORRECTIONS ===
    val allUserCorrections: Flow<List<UserCorrectionEntity>> = dao.getAllUserCorrections()
    suspend fun insertUserCorrection(correction: UserCorrectionEntity): Long = dao.insertUserCorrection(correction)
    suspend fun deleteUserCorrection(correction: UserCorrectionEntity) = dao.deleteUserCorrection(correction)
    suspend fun clearAllUserCorrections() = dao.clearAllUserCorrections()

    // === TRAINING DATASET ===
    val allTrainingExamples: Flow<List<TrainingExampleEntity>> = dao.getAllTrainingExamples()
    suspend fun getCuratedTrainingExamples(): List<TrainingExampleEntity> = dao.getCuratedTrainingExamples()
    suspend fun insertTrainingExample(example: TrainingExampleEntity): Long = dao.insertTrainingExample(example)
    suspend fun deleteTrainingExample(example: TrainingExampleEntity) = dao.deleteTrainingExample(example)
    suspend fun clearTrainingDataset() = dao.clearTrainingDataset()

    // === GEMINI TEACHER SESSIONS ===
    val allTeacherSessions: Flow<List<GeminiTeacherSessionEntity>> = dao.getAllTeacherSessions()
    suspend fun clearTeacherSessions() = dao.clearTeacherSessions()

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
    val allVisualExperiences: Flow<List<VisualExperienceEntity>> = dao.getAllVisualExperiences()

    suspend fun getVisualExperiencesForRole(pkg: String, role: String): List<VisualExperienceEntity> = withContext(Dispatchers.IO) {
        dao.getExperiencesForRole(pkg, role)
    }

    suspend fun insertVisualExperience(experience: VisualExperienceEntity): Long = withContext(Dispatchers.IO) {
        dao.insertVisualExperience(experience)
    }

    suspend fun clearVisualExperiences() = withContext(Dispatchers.IO) {
        dao.clearVisualExperiences()
    }
    suspend fun searchKnowledge(query: String): List<KnowledgeChunkEntity> = dao.searchKnowledgeChunks(query)
    suspend fun insertKnowledgeChunk(chunk: KnowledgeChunkEntity): Long = dao.insertKnowledgeChunk(chunk)
    suspend fun deleteKnowledgeChunk(chunk: KnowledgeChunkEntity) = dao.deleteKnowledgeChunk(chunk)
    suspend fun clearKnowledgeChunks() = dao.clearKnowledgeChunks()

    // === BRAIN BACKUP / EXPORT / IMPORT ===
    suspend fun exportBrainJson(
        memories: List<MemoryEntity>,
        skills: List<SkillEntity>,
        knowledge: List<KnowledgeChunkEntity>,
        experiences: List<ExperienceEntity> = emptyList(),
        corrections: List<UserCorrectionEntity> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", "3.0.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("assistant", "JARVIS-Android-Autonomous")

        val memArray = JSONArray()
        memories.forEach { m ->
            val obj = JSONObject()
            obj.put("category", m.category.name)
            obj.put("key", m.key)
            obj.put("value", m.value)
            obj.put("confidence", m.confidence.toDouble())
            obj.put("importance", m.importance.toDouble())
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
            obj.put("inputSchema", s.inputSchema)
            obj.put("outputSchema", s.outputSchema)
            obj.put("procedure", s.procedure)
            obj.put("verificationMethod", s.verificationMethod)
            obj.put("version", s.version)
            obj.put("isLearnedFromExperience", s.isLearnedFromExperience)
            obj.put("source", s.source.name)
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

        val expArray = JSONArray()
        experiences.forEach { e ->
            val obj = JSONObject()
            obj.put("goal", e.goal)
            obj.put("appPackage", e.appPackage)
            obj.put("actionsTakenJson", e.actionsTakenJson)
            obj.put("isSuccess", e.isSuccess)
            obj.put("confidence", e.confidence.toDouble())
            obj.put("source", e.source.name)
            expArray.put(obj)
        }
        root.put("experiences", expArray)

        val corrArray = JSONArray()
        corrections.forEach { c ->
            val obj = JSONObject()
            obj.put("userGoal", c.userGoal)
            obj.put("previousAssumption", c.previousAssumption)
            obj.put("userCorrection", c.userCorrection)
            obj.put("correctedAction", c.correctedAction)
            obj.put("actualTarget", c.actualTarget)
            obj.put("appPackage", c.appPackage)
            corrArray.put(obj)
        }
        root.put("corrections", corrArray)

        return@withContext root.toString(2)
    }

    suspend fun importBrainJson(jsonStr: String): Int = withContext(Dispatchers.IO) {
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
                            importance = obj.optDouble("importance", 0.6).toFloat(),
                            source = "Brain Import"
                        )
                    )
                    importedCount++
                }
            }
            if (root.has("skills")) {
                val skillArray = root.getJSONArray("skills")
                for (i in 0 until skillArray.length()) {
                    val obj = skillArray.getJSONObject(i)
                    val riskStr = obj.optString("riskLevel", SkillRiskLevel.LOW.name)
                    val risk = try { SkillRiskLevel.valueOf(riskStr) } catch (e: Exception) { SkillRiskLevel.LOW }
                    val srcStr = obj.optString("source", SkillSource.BUILTIN.name)
                    val src = try { SkillSource.valueOf(srcStr) } catch (e: Exception) { SkillSource.BUILTIN }
                    dao.insertSkill(
                        SkillEntity(
                            name = obj.optString("name", "imported_skill"),
                            description = obj.optString("description", ""),
                            requiredPermissions = obj.optString("requiredPermissions", "None"),
                            riskLevel = risk,
                            inputSchema = obj.optString("inputSchema", "{}"),
                            outputSchema = obj.optString("outputSchema", "{}"),
                            procedure = obj.optString("procedure", ""),
                            verificationMethod = obj.optString("verificationMethod", "Manual"),
                            version = obj.optString("version", "1.0.0"),
                            isLearnedFromExperience = obj.optBoolean("isLearnedFromExperience", false),
                            source = src
                        )
                    )
                    importedCount++
                }
            }
            if (root.has("experiences")) {
                val expArray = root.getJSONArray("experiences")
                for (i in 0 until expArray.length()) {
                    val obj = expArray.getJSONObject(i)
                    val srcStr = obj.optString("source", ExperienceSource.LOCAL_PLANNER.name)
                    val src = try { ExperienceSource.valueOf(srcStr) } catch (e: Exception) { ExperienceSource.LOCAL_PLANNER }
                    dao.insertExperience(
                        ExperienceEntity(
                            goal = obj.optString("goal", "Imported goal"),
                            appPackage = obj.optString("appPackage", "unknown"),
                            initialScreenSummary = "Imported",
                            actionsTakenJson = obj.optString("actionsTakenJson", "[]"),
                            verificationSummary = "Imported experience",
                            isSuccess = obj.optBoolean("isSuccess", true),
                            source = src
                        )
                    )
                    importedCount++
                }
            }
            if (root.has("corrections")) {
                val corrArray = root.getJSONArray("corrections")
                for (i in 0 until corrArray.length()) {
                    val obj = corrArray.getJSONObject(i)
                    dao.insertUserCorrection(
                        UserCorrectionEntity(
                            userGoal = obj.optString("userGoal", ""),
                            previousAssumption = obj.optString("previousAssumption", ""),
                            userCorrection = obj.optString("userCorrection", ""),
                            correctedAction = obj.optString("correctedAction", ""),
                            actualTarget = obj.optString("actualTarget", ""),
                            appPackage = obj.optString("appPackage", "unknown"),
                            screenContext = "general"
                        )
                    )
                    importedCount++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext importedCount
    }

    // === PHASE 15: DEVICE CAPABILITIES & ACTIONS ===
    val allDeviceCapabilities: Flow<List<com.example.data.local.entity.DeviceCapabilityEntity>> = dao.getAllDeviceCapabilities()
    val allRegisteredApps: Flow<List<com.example.data.local.entity.AppRegistryEntity>> = dao.getAllRegisteredApps()
    val recentDeviceActions: Flow<List<com.example.data.local.entity.DeviceActionHistoryEntity>> = dao.getRecentDeviceActions()

    suspend fun insertDeviceAction(action: com.example.data.local.entity.DeviceActionHistoryEntity): Long = withContext(Dispatchers.IO) {
        dao.insertDeviceAction(action)
    }

    suspend fun clearDeviceActionHistory() = withContext(Dispatchers.IO) {
        dao.clearDeviceActionHistory()
    }
}
