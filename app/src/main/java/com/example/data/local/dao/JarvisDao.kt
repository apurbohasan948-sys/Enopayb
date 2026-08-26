package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ExperienceEntity
import com.example.data.local.entity.GeminiTeacherSessionEntity
import com.example.data.local.entity.KnowledgeChunkEntity
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.SecurityEventEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.TrainingExampleEntity
import com.example.data.local.entity.UserCorrectionEntity
import com.example.data.local.entity.VisualExperienceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {

    // === MEMORIES ===
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesSync(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    fun getMemoriesByCategory(category: MemoryCategory): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY importance DESC, usageCount DESC LIMIT :limit")
    suspend fun getTopMemories(limit: Int = 20): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntity>)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("UPDATE memories SET usageCount = usageCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementMemoryUsage(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()

    @Query("DELETE FROM memories WHERE category = :category")
    suspend fun clearMemoriesByCategory(category: MemoryCategory)

    // === EXPERIENCES ===
    @Query("SELECT * FROM experiences ORDER BY timestamp DESC")
    fun getAllExperiences(): Flow<List<ExperienceEntity>>

    @Query("SELECT * FROM experiences WHERE isSuccess = 1 ORDER BY timestamp DESC")
    fun getSuccessfulExperiences(): Flow<List<ExperienceEntity>>

    @Query("SELECT * FROM experiences WHERE isSuccess = 0 ORDER BY timestamp DESC")
    fun getFailedExperiences(): Flow<List<ExperienceEntity>>

    @Query("SELECT * FROM experiences WHERE goal LIKE '%' || :query || '%' OR appPackage LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchExperiences(query: String, limit: Int = 10): List<ExperienceEntity>

    @Query("SELECT * FROM experiences WHERE appPackage = :pkg AND isSuccess = 1 ORDER BY confidence DESC, timestamp DESC LIMIT 5")
    suspend fun getSuccessfulExperiencesForPackage(pkg: String): List<ExperienceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperience(experience: ExperienceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiences(experiences: List<ExperienceEntity>)

    @Delete
    suspend fun deleteExperience(experience: ExperienceEntity)

    @Query("DELETE FROM experiences")
    suspend fun clearAllExperiences()

    // === SKILLS ===
    @Query("SELECT * FROM skills ORDER BY name ASC")
    fun getAllSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY name ASC")
    suspend fun getAllSkillsSync(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE isEnabled = 1 ORDER BY successRate DESC, confidence DESC")
    fun getEnabledSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE isLearnedFromExperience = 1 ORDER BY lastExecutedAt DESC")
    fun getLearnedSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE name = :name LIMIT 1")
    suspend fun getSkillByName(name: String): SkillEntity?

    @Query("SELECT * FROM skills WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    suspend fun searchSkills(query: String): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkills(skills: List<SkillEntity>)

    @Update
    suspend fun updateSkill(skill: SkillEntity)

    @Delete
    suspend fun deleteSkill(skill: SkillEntity)

    @Query("UPDATE skills SET executionCount = executionCount + 1, lastExecutedAt = :timestamp WHERE name = :name")
    suspend fun incrementSkillUsage(name: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE skills SET executionCount = executionCount + 1, successCount = successCount + 1, lastExecutedAt = :timestamp, lastSuccessAt = :timestamp, successRate = CAST(successCount + 1 AS REAL) / CAST(executionCount + 1 AS REAL) WHERE name = :name")
    suspend fun recordSkillSuccess(name: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE skills SET executionCount = executionCount + 1, failureCount = failureCount + 1, lastExecutedAt = :timestamp, successRate = CAST(successCount AS REAL) / CAST(executionCount + 1 AS REAL) WHERE name = :name")
    suspend fun recordSkillFailure(name: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM skills WHERE isLearnedFromExperience = 1")
    suspend fun clearLearnedSkills()

    // === USER CORRECTIONS ===
    @Query("SELECT * FROM user_corrections ORDER BY timestamp DESC")
    fun getAllUserCorrections(): Flow<List<UserCorrectionEntity>>

    @Query("SELECT * FROM user_corrections WHERE appPackage = :pkg OR screenContext = :screenContext ORDER BY timestamp DESC LIMIT 10")
    suspend fun getCorrectionsForContext(pkg: String, screenContext: String): List<UserCorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserCorrection(correction: UserCorrectionEntity): Long

    @Query("UPDATE user_corrections SET appliedCount = appliedCount + 1 WHERE id = :id")
    suspend fun incrementCorrectionApplied(id: Long)

    @Delete
    suspend fun deleteUserCorrection(correction: UserCorrectionEntity)

    @Query("DELETE FROM user_corrections")
    suspend fun clearAllUserCorrections()

    // === TRAINING DATASET ===
    @Query("SELECT * FROM training_dataset ORDER BY qualityScore DESC, timestamp DESC")
    fun getAllTrainingExamples(): Flow<List<TrainingExampleEntity>>

    @Query("SELECT * FROM training_dataset WHERE isCurated = 1 ORDER BY timestamp DESC")
    suspend fun getCuratedTrainingExamples(): List<TrainingExampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainingExample(example: TrainingExampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainingExamples(examples: List<TrainingExampleEntity>)

    @Delete
    suspend fun deleteTrainingExample(example: TrainingExampleEntity)

    @Query("DELETE FROM training_dataset")
    suspend fun clearTrainingDataset()

    // === GEMINI TEACHER SESSIONS ===
    @Query("SELECT * FROM gemini_teacher_sessions ORDER BY timestamp DESC")
    fun getAllTeacherSessions(): Flow<List<GeminiTeacherSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacherSession(session: GeminiTeacherSessionEntity): Long

    @Query("DELETE FROM gemini_teacher_sessions")
    suspend fun clearTeacherSessions()

    // === SECURITY EVENTS ===
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT 100")
    fun getAllSecurityEvents(): Flow<List<SecurityEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityEvent(event: SecurityEventEntity): Long

    @Query("DELETE FROM security_events")
    suspend fun clearSecurityEvents()

    // === CHAT MESSAGES ===
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC LIMIT 200")
    fun getRecentChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatMessages()

    // === KNOWLEDGE CHUNKS ===
    @Query("SELECT * FROM knowledge_chunks ORDER BY timestamp DESC")
    fun getAllKnowledgeChunks(): Flow<List<KnowledgeChunkEntity>>

    @Query("SELECT * FROM knowledge_chunks ORDER BY timestamp DESC")
    suspend fun getAllKnowledgeChunksSync(): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    suspend fun searchKnowledgeChunks(query: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    suspend fun searchKnowledge(query: String): List<KnowledgeChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeChunk(chunk: KnowledgeChunkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeChunks(chunks: List<KnowledgeChunkEntity>)

    @Delete
    suspend fun deleteKnowledgeChunk(chunk: KnowledgeChunkEntity)

    @Query("DELETE FROM knowledge_chunks")
    suspend fun clearKnowledgeChunks()

    // === VISUAL EXPERIENCES ===
    @Query("SELECT * FROM visual_experiences ORDER BY timestamp DESC")
    fun getAllVisualExperiences(): Flow<List<VisualExperienceEntity>>

    @Query("SELECT * FROM visual_experiences WHERE appPackage = :pkg AND semanticRole = :role ORDER BY confidence DESC, timestamp DESC LIMIT 5")
    suspend fun getExperiencesForRole(pkg: String, role: String): List<VisualExperienceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisualExperience(exp: VisualExperienceEntity): Long

    @Query("DELETE FROM visual_experiences")
    suspend fun clearVisualExperiences()

    // === SCHEDULED TASKS ===
    @Query("SELECT * FROM scheduled_tasks ORDER BY scheduledTimeMillis ASC")
    fun getAllScheduledTasks(): Flow<List<com.example.data.local.entity.ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE isEnabled = 1 AND isCompleted = 0 ORDER BY scheduledTimeMillis ASC")
    fun getActiveScheduledTasks(): Flow<List<com.example.data.local.entity.ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun getScheduledTaskById(id: Long): com.example.data.local.entity.ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledTask(task: com.example.data.local.entity.ScheduledTaskEntity): Long

    @Update
    suspend fun updateScheduledTask(task: com.example.data.local.entity.ScheduledTaskEntity)

    @Delete
    suspend fun deleteScheduledTask(task: com.example.data.local.entity.ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks")
    suspend fun clearAllScheduledTasks()

    // === AUTONOMOUS TASKS ===
    @Query("SELECT * FROM autonomous_tasks ORDER BY createdAt DESC")
    fun getAllAutonomousTasks(): Flow<List<com.example.data.local.entity.AutonomousTaskEntity>>

    @Query("SELECT * FROM autonomous_tasks WHERE status = 'RUNNING' OR status = 'QUEUED' ORDER BY priority DESC, createdAt ASC")
    fun getPendingAutonomousTasks(): Flow<List<com.example.data.local.entity.AutonomousTaskEntity>>

    @Query("SELECT * FROM autonomous_tasks WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    suspend fun getTasksByStatusSync(status: String): List<com.example.data.local.entity.AutonomousTaskEntity>

    @Query("SELECT * FROM autonomous_tasks WHERE status = 'RUNNING' LIMIT 1")
    suspend fun getRunningAutonomousTask(): com.example.data.local.entity.AutonomousTaskEntity?

    @Query("SELECT * FROM autonomous_tasks WHERE id = :id LIMIT 1")
    suspend fun getAutonomousTaskById(id: Long): com.example.data.local.entity.AutonomousTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutonomousTask(task: com.example.data.local.entity.AutonomousTaskEntity): Long

    @Update
    suspend fun updateAutonomousTask(task: com.example.data.local.entity.AutonomousTaskEntity)

    @Delete
    suspend fun deleteAutonomousTask(task: com.example.data.local.entity.AutonomousTaskEntity)

    @Query("DELETE FROM autonomous_tasks")
    suspend fun clearAllAutonomousTasks()

    // === KNOWLEDGE VERSIONS ===
    @Query("SELECT * FROM knowledge_versions ORDER BY updatedAt DESC")
    fun getAllKnowledgeVersions(): Flow<List<com.example.data.local.entity.KnowledgeVersionEntity>>

    @Query("SELECT * FROM knowledge_versions WHERE knowledgeKey = :key ORDER BY version DESC")
    fun getVersionsForKey(key: String): Flow<List<com.example.data.local.entity.KnowledgeVersionEntity>>

    @Query("SELECT * FROM knowledge_versions WHERE knowledgeKey = :key AND status = 'ACTIVE' ORDER BY version DESC LIMIT 1")
    suspend fun getLatestActiveKnowledge(key: String): com.example.data.local.entity.KnowledgeVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeVersion(version: com.example.data.local.entity.KnowledgeVersionEntity): Long

    @Update
    suspend fun updateKnowledgeVersion(version: com.example.data.local.entity.KnowledgeVersionEntity)

    @Delete
    suspend fun deleteKnowledgeVersion(version: com.example.data.local.entity.KnowledgeVersionEntity)

    @Query("DELETE FROM knowledge_versions")
    suspend fun clearAllKnowledgeVersions()

    // === HEALTH EVENTS ===
    @Query("SELECT * FROM health_events ORDER BY timestamp DESC LIMIT 100")
    fun getAllHealthEvents(): Flow<List<com.example.data.local.entity.HealthEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthEvent(event: com.example.data.local.entity.HealthEventEntity): Long

    @Query("DELETE FROM health_events")
    suspend fun clearHealthEvents()

    // === WEB RESEARCH RECORDS ===
    @Query("SELECT * FROM web_research_records ORDER BY createdAt DESC")
    fun getAllWebResearchRecords(): Flow<List<com.example.data.local.entity.WebResearchRecordEntity>>

    @Query("SELECT * FROM web_research_records WHERE id = :id LIMIT 1")
    suspend fun getWebResearchRecordById(id: Long): com.example.data.local.entity.WebResearchRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebResearchRecord(record: com.example.data.local.entity.WebResearchRecordEntity): Long

    @Update
    suspend fun updateWebResearchRecord(record: com.example.data.local.entity.WebResearchRecordEntity)

    @Delete
    suspend fun deleteWebResearchRecord(record: com.example.data.local.entity.WebResearchRecordEntity)

    @Query("DELETE FROM web_research_records")
    suspend fun clearAllWebResearchRecords()
}
