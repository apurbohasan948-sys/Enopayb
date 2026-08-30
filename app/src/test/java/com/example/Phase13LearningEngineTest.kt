package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.agent.PlanStep
import com.example.core.agent.StepExecutionRecord
import com.example.core.agent.TaskPlan
import com.example.core.learning.ExperienceEvaluator
import com.example.core.learning.ExperienceRecorder
import com.example.core.learning.GeneralizedSkillModel
import com.example.core.learning.SkillCandidateGenerator
import com.example.core.learning.SkillLifecycleStatus
import com.example.core.learning.SkillManager
import com.example.core.model.ToolIntent
import com.example.core.tools.ToolExecutionResult
import com.example.data.local.dao.JarvisDao
import com.example.data.local.database.JarvisDatabase
import com.example.data.local.entity.ExperienceSource
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRiskLevel
import com.example.data.local.entity.SkillSource
import com.example.data.local.preference.JarvisPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase13LearningEngineTest {

    private lateinit var context: Context
    private lateinit var db: JarvisDatabase
    private lateinit var dao: JarvisDao
    private lateinit var evaluator: ExperienceEvaluator
    private lateinit var candidateGenerator: SkillCandidateGenerator
    private lateinit var skillManager: SkillManager
    private lateinit var experienceRecorder: ExperienceRecorder
    private lateinit var preferences: JarvisPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.jarvisDao()
        evaluator = ExperienceEvaluator()
        candidateGenerator = SkillCandidateGenerator(dao)
        skillManager = SkillManager(dao)
        preferences = JarvisPreferences(context).apply {
            isLearningEnabled = true
            isStoreExperiencesEnabled = true
            isAutoSkillCreationEnabled = true
            isPrivacyFilteringEnabled = true
        }
        experienceRecorder = ExperienceRecorder(
            dao = dao,
            preferences = preferences,
            evaluator = evaluator,
            candidateGenerator = candidateGenerator,
            context = context
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // TEST 1: YouTube search pattern extraction and skill candidate generation
    // =========================================================================
    @Test
    fun test1_youtubeSearchPatternExtractionAndCandidateGeneration() = runBlocking {
        val goal = "Open YouTube and search Tom and Jerry"
        val steps = listOf(
            StepExecutionRecord(
                step = PlanStep(1, "Launch YouTube", ToolIntent("open_app", mapOf("app_name" to "YouTube"), "LOW"), "YouTube open"),
                result = ToolExecutionResult(success = true, output = "Launched"),
                beforeScreenSummary = "Home",
                afterScreenSummary = "YouTube Feed",
                isVerified = true
            ),
            StepExecutionRecord(
                step = PlanStep(2, "Tap Search", ToolIntent("tap", mapOf("target_text" to "Search"), "LOW"), "Search field open"),
                result = ToolExecutionResult(success = true, output = "Tapped"),
                beforeScreenSummary = "YouTube Feed",
                afterScreenSummary = "Search Box",
                isVerified = true
            ),
            StepExecutionRecord(
                step = PlanStep(3, "Type query", ToolIntent("type_text", mapOf("text" to "Tom and Jerry"), "LOW"), "Typed query"),
                result = ToolExecutionResult(success = true, output = "Typed"),
                beforeScreenSummary = "Search Box",
                afterScreenSummary = "Text Inputted",
                isVerified = true
            ),
            StepExecutionRecord(
                step = PlanStep(4, "Press Enter", ToolIntent("press_key", mapOf("key" to "ENTER"), "LOW"), "Search submitted"),
                result = ToolExecutionResult(success = true, output = "Pressed"),
                beforeScreenSummary = "Text Inputted",
                afterScreenSummary = "Search Results",
                isVerified = true
            )
        )

        val candidateSkill = candidateGenerator.generateCandidateFromExperience(
            goal = goal,
            appPackage = "com.google.android.youtube",
            appVersion = 100L,
            stepRecords = steps,
            qualityScore = 0.95f
        )

        assertNotNull("Candidate skill entity should be generated", candidateSkill)
        val genModel = GeneralizedSkillModel.fromJson(candidateSkill?.procedure ?: "")
        assertNotNull("Generalized model should parse cleanly", genModel)
        assertEquals(SkillLifecycleStatus.CANDIDATE, genModel?.status)
        assertTrue("Should detect query slot parameter", genModel?.parameters?.any { it.name == "query" } == true)
        assertEquals("Tom and Jerry", genModel?.parameters?.firstOrNull { it.name == "query" }?.defaultValue)

        // Verify that steps have {{query}} template
        val typeStep = genModel?.steps?.find { it.tool == "type_text" }
        assertNotNull(typeStep)
        assertEquals("{{query}}", typeStep?.argumentsTemplate?.get("text"))
    }

    // =========================================================================
    // TEST 2: Failure/recovery logic (failed tasks must NOT be promoted)
    // =========================================================================
    @Test
    fun test2_failedTaskEvaluationAndRejection() = runBlocking {
        val goal = "Open YouTube and search cartoons"
        val steps = listOf(
            StepExecutionRecord(
                step = PlanStep(1, "Launch YouTube", ToolIntent("open_app", mapOf("app_name" to "YouTube"), "LOW"), "YouTube open"),
                result = ToolExecutionResult(success = false, output = "App crashed", errorMessage = "App crash"),
                beforeScreenSummary = "Home",
                afterScreenSummary = "Home",
                isVerified = false
            )
        )

        val eval = evaluator.evaluateExperience(
            isSuccess = false,
            stepRecords = steps,
            hasUserCorrection = false,
            hadRecovery = false,
            recoverySuccess = false,
            averageTargetConfidence = 0.30f,
            durationMs = 5000L
        )

        assertTrue("Failed experience score must be low (<50)", eval.score < 50)
        assertEquals("Failed experience grade must be UNRELIABLE", com.example.core.learning.EvaluationGrade.UNRELIABLE, eval.grade)

        // Attempting to record failed run
        val result = experienceRecorder.recordTaskRun(
            goal = goal,
            appPackage = "com.google.android.youtube",
            initialScreenSummary = "Home",
            stepRecords = steps,
            isSuccess = false,
            failedStrategy = "App crashed",
            recoveryStrategy = null,
            hadRecovery = false,
            recoverySuccess = false,
            hasUserCorrection = false,
            durationMs = 5000L,
            modelUsed = "LOCAL_PLANNER",
            source = ExperienceSource.LOCAL_PLANNER
        )

        assertEquals("No skill candidate should be generated from a failed task", null, result.generatedSkillId)
    }

    // =========================================================================
    // TEST 3: Skill lifecycle transitions (CANDIDATE -> VALIDATING -> VERIFIED -> ACTIVE)
    // =========================================================================
    @Test
    fun test3_skillLifecycleTransitions() = runBlocking {
        val skillName = "search_video_youtube"
        val candidateSkill = SkillEntity(
            name = skillName,
            description = "Search videos on YouTube",
            requiredPermissions = "",
            inputSchema = "{}",
            outputSchema = "{}",
            riskLevel = SkillRiskLevel.LOW,
            procedure = "{\"skillId\":\"search_video_youtube\",\"status\":\"CANDIDATE\",\"parameters\":[{\"name\":\"query\",\"type\":\"string\",\"required\":true,\"defaultValue\":\"\",\"description\":\"\"}],\"steps\":[]}",
            verificationMethod = "TRANSITION",
            version = "1.0.0",
            isEnabled = true,
            executionCount = 0,
            successCount = 0,
            failureCount = 0,
            isLearnedFromExperience = true,
            source = SkillSource.EXPERIENCE_EXTRACTED
        )
        dao.insertSkill(candidateSkill)

        // 1st Success: CANDIDATE -> VALIDATING
        skillManager.recordExecution(skillName, success = true)
        val s1 = dao.getSkillByName(skillName)
        assertNotNull(s1)
        val model1 = GeneralizedSkillModel.fromJson(s1?.procedure ?: "")
        assertEquals(SkillLifecycleStatus.VALIDATING, model1?.status)

        // 2nd Success: VALIDATING -> VALIDATING
        skillManager.recordExecution(skillName, success = true)
        val s2 = dao.getSkillByName(skillName)
        val model2 = GeneralizedSkillModel.fromJson(s2?.procedure ?: "")
        assertEquals(SkillLifecycleStatus.VALIDATING, model2?.status)

        // 3rd Success: Threshold reached -> VERIFIED
        skillManager.recordExecution(skillName, success = true)
        val s3 = dao.getSkillByName(skillName)
        val model3 = GeneralizedSkillModel.fromJson(s3?.procedure ?: "")
        assertEquals(SkillLifecycleStatus.VERIFIED, model3?.status)

        // 5th Success: Promoted to ACTIVE
        skillManager.recordExecution(skillName, success = true)
        skillManager.recordExecution(skillName, success = true)
        val s5 = dao.getSkillByName(skillName)
        val model5 = GeneralizedSkillModel.fromJson(s5?.procedure ?: "")
        assertEquals(SkillLifecycleStatus.ACTIVE, model5?.status)
    }

    // =========================================================================
    // TEST 4: Parameter binding with generalized template
    // =========================================================================
    @Test
    fun test4_parameterBinding() = runBlocking {
        val procedureJson = "{\"skillId\":\"search_youtube\",\"status\":\"ACTIVE\",\"intentArchetype\":\"search_youtube\",\"targetAppPackage\":\"com.google.android.youtube\",\"parameters\":[{\"name\":\"query\",\"type\":\"string\",\"required\":true,\"defaultValue\":\"\",\"description\":\"\"}],\"steps\":[{\"stepNumber\":1,\"description\":\"Type query\",\"tool\":\"type_text\",\"target\":\"\",\"arguments\":{\"text\":\"{{query}}\"},\"expectedOutcome\":\"Typed\"}]}"
        val skill = SkillEntity(
            name = "search_youtube",
            description = "Search YouTube with query",
            requiredPermissions = "",
            inputSchema = "{}",
            outputSchema = "{}",
            riskLevel = SkillRiskLevel.LOW,
            procedure = procedureJson,
            verificationMethod = "TRANSITION",
            version = "1.0.0",
            isEnabled = true,
            executionCount = 10,
            successCount = 10,
            failureCount = 0,
            isLearnedFromExperience = true,
            source = SkillSource.EXPERIENCE_EXTRACTED
        )
        dao.insertSkill(skill)

        val match = skillManager.findMatchingSkill("Open YouTube and search Tom and Jerry", "com.google.android.youtube")
        assertNotNull("Skill should be matched and parameterized", match)
        val typeStep = match?.second?.steps?.find { it.toolIntent.toolName == "type_text" }
        assertNotNull(typeStep)
        assertEquals("Tom and Jerry", typeStep?.toolIntent?.arguments?.get("text"))
    }

    // =========================================================================
    // TEST 5: Privacy sanitization (passwords, tokens, pins, credentials redacted)
    // =========================================================================
    @Test
    fun test5_privacySanitization() = runBlocking {
        val sensitiveGoal = "Login with password SuperSecret123 and token Bearer_xyz_secret"
        val steps = listOf(
            StepExecutionRecord(
                step = PlanStep(1, "Type password SuperSecret123", ToolIntent("type_text", mapOf("password" to "SuperSecret123", "text" to "my_pin_9876"), "LOW"), "Typed"),
                result = ToolExecutionResult(success = true, output = "Success token=Bearer_xyz_secret"),
                beforeScreenSummary = "Login screen with password field",
                afterScreenSummary = "Logged In",
                isVerified = true
            )
        )

        val result = experienceRecorder.recordTaskRun(
            goal = sensitiveGoal,
            appPackage = "com.banking.app",
            initialScreenSummary = "Login screen with password field",
            stepRecords = steps,
            isSuccess = true,
            failedStrategy = null,
            recoveryStrategy = null,
            hadRecovery = false,
            recoverySuccess = false,
            hasUserCorrection = false,
            durationMs = 1000L,
            modelUsed = "LOCAL_PLANNER",
            source = ExperienceSource.LOCAL_PLANNER
        )

        val storedExp = dao.getAllExperiencesList().firstOrNull { it.id == result.experienceId }
        assertNotNull(storedExp)
        assertFalse("Goal must not contain raw password", storedExp?.goal?.contains("SuperSecret123") == true)
        assertTrue("Goal must contain [REDACTED]", storedExp?.goal?.contains("[REDACTED]") == true)
        assertFalse("Screen summary must not contain raw pin", storedExp?.initialScreenSummary?.contains("9876") == true)
        assertFalse("Actions trace must not contain raw bearer token", storedExp?.actionsTakenJson?.contains("Bearer_xyz_secret") == true)
    }

    // =========================================================================
    // TEST 6: Local Skill Replay without Gemini (Deterministic Zero-Token Path)
    // =========================================================================
    @Test
    fun test6_localSkillReplayWithoutGemini() = runBlocking {
        val procedureJson = "{\"skillId\":\"play_lofi\",\"status\":\"ACTIVE\",\"intentArchetype\":\"play_lofi\",\"targetAppPackage\":\"com.google.android.youtube\",\"parameters\":[],\"steps\":[{\"stepNumber\":1,\"description\":\"Open YouTube\",\"tool\":\"open_app\",\"target\":\"\",\"arguments\":{\"app_name\":\"YouTube\"},\"expectedOutcome\":\"Opened\"}]}"
        val skill = SkillEntity(
            name = "play_lofi_music",
            description = "Play lofi music",
            requiredPermissions = "",
            inputSchema = "{}",
            outputSchema = "{}",
            riskLevel = SkillRiskLevel.LOW,
            procedure = procedureJson,
            verificationMethod = "TRANSITION",
            version = "2.0.0",
            isEnabled = true,
            executionCount = 21,
            successCount = 20,
            failureCount = 1,
            isLearnedFromExperience = true,
            source = SkillSource.EXPERIENCE_EXTRACTED
        )
        dao.insertSkill(skill)

        val match = skillManager.findMatchingSkill("play lofi music")
        assertNotNull("Skill should be retrieved locally from Room", match)
        assertEquals("play_lofi_music", match?.first?.name)
        assertEquals(1, match?.second?.steps?.size)
        assertEquals("open_app", match?.second?.steps?.get(0)?.toolIntent?.toolName)
    }

    // =========================================================================
    // TEST 7: Skill deprecation on repeated consecutive failures
    // =========================================================================
    @Test
    fun test7_skillDeprecationOnConsecutiveFailures() = runBlocking {
        val skillName = "fragile_automation_skill"
        val activeSkill = SkillEntity(
            name = skillName,
            description = "Fragile task",
            requiredPermissions = "",
            inputSchema = "{}",
            outputSchema = "{}",
            riskLevel = SkillRiskLevel.LOW,
            procedure = "{\"skillId\":\"fragile_automation_skill\",\"status\":\"ACTIVE\",\"parameters\":[],\"steps\":[]}",
            verificationMethod = "TRANSITION",
            version = "1.0.0",
            isEnabled = true,
            executionCount = 5,
            successCount = 5,
            failureCount = 0,
            isLearnedFromExperience = true,
            source = SkillSource.EXPERIENCE_EXTRACTED
        )
        dao.insertSkill(activeSkill)

        // 3 consecutive failures
        skillManager.recordExecution(skillName, success = false)
        skillManager.recordExecution(skillName, success = false)
        skillManager.recordExecution(skillName, success = false)

        val updated = dao.getSkillByName(skillName)
        assertNotNull(updated)
        assertEquals("Skill with 3 consecutive failures must be disabled", false, updated?.isEnabled)
        val model = GeneralizedSkillModel.fromJson(updated?.procedure ?: "")
        assertEquals("Skill status must transition to DEPRECATED", SkillLifecycleStatus.DEPRECATED, model?.status)
    }
}
