package com.example

import com.example.core.agent.PlanStep
import com.example.core.agent.ScreenTransitionVerifier
import com.example.core.agent.UniversalTaskPlanner
import com.example.core.model.ToolIntent
import com.example.core.tools.ToolExecutionResult
import com.example.core.vision.ScreenElement
import com.example.core.vision.SemanticTarget
import com.example.core.vision.UnifiedScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalAgentPlannerTest {

    @Test
    fun `test dynamic planning for YouTube scenario`() = runBlocking {
        val planner = UniversalTaskPlanner()
        val plan = planner.formulatePlan(
            goal = "Open YouTube and play Tom and Jerry",
            currentScreen = null,
            deviceState = null
        )

        assertNotNull(plan)
        assertTrue(plan.steps.isNotEmpty())
        assertEquals(5, plan.steps.size)
        assertEquals("open_app", plan.steps[0].toolIntent.toolName)
        assertEquals("YouTube", plan.steps[0].toolIntent.arguments["app_name"])
    }

    @Test
    fun `test dynamic planning for Chrome search scenario`() = runBlocking {
        val planner = UniversalTaskPlanner()
        val plan = planner.formulatePlan(
            goal = "Open Chrome and search for HSC result",
            currentScreen = null,
            deviceState = null
        )

        assertNotNull(plan)
        assertTrue(plan.steps.isNotEmpty())
        assertEquals("open_app", plan.steps[0].toolIntent.toolName)
        assertEquals("Chrome", plan.steps[0].toolIntent.arguments["app_name"])
    }

    @Test
    fun `test dynamic planning for Settings Bluetooth scenario`() = runBlocking {
        val planner = UniversalTaskPlanner()
        val plan = planner.formulatePlan(
            goal = "Open Settings and turn on Bluetooth",
            currentScreen = null,
            deviceState = null
        )

        assertNotNull(plan)
        assertTrue(plan.steps.isNotEmpty())
        assertEquals("open_app", plan.steps[0].toolIntent.toolName)
        assertEquals("Settings", plan.steps[0].toolIntent.arguments["app_name"])
    }

    @Test
    fun `test dynamic planning for Calculator scenario`() = runBlocking {
        val planner = UniversalTaskPlanner()
        val plan = planner.formulatePlan(
            goal = "Open the calculator and calculate 250 × 45",
            currentScreen = null,
            deviceState = null
        )

        assertNotNull(plan)
        assertTrue(plan.steps.isNotEmpty())
        assertEquals("open_app", plan.steps[0].toolIntent.toolName)
        assertEquals("Calculator", plan.steps[0].toolIntent.arguments["app_name"])
    }

    @Test
    fun `test dynamic planning for WhatsApp scenario`() = runBlocking {
        val planner = UniversalTaskPlanner()
        val plan = planner.formulatePlan(
            goal = "Open WhatsApp and find Hammad",
            currentScreen = null,
            deviceState = null
        )

        assertNotNull(plan)
        assertTrue(plan.steps.isNotEmpty())
        assertEquals("open_app", plan.steps[0].toolIntent.toolName)
        assertEquals("WhatsApp", plan.steps[0].toolIntent.arguments["app_name"])
    }

    @Test
    fun `test screen transition verifier on app switch`() {
        val verifier = ScreenTransitionVerifier()
        val before = UnifiedScreen(packageName = "com.example.jarvis", totalNodes = 10)
        val after = UnifiedScreen(packageName = "com.google.android.youtube", totalNodes = 15)
        val result = ToolExecutionResult(tool = "open_app", success = true, output = "Launched YouTube", verified = true)

        val evaluation = verifier.verifyTransition(
            expectedOutcome = "YouTube in foreground",
            beforeScreen = before,
            afterScreen = after,
            actionResult = result
        )

        assertTrue(evaluation.transitionOccurred)
        assertEquals("APP_SWITCH", evaluation.transitionType)
    }
}
