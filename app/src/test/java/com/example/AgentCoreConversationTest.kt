package com.example

import android.graphics.Rect
import com.example.core.agent.conversation.ConversationSessionManager
import com.example.core.agent.conversation.FollowUpResolver
import com.example.core.agent.conversation.IntentCategory
import com.example.core.agent.conversation.NaturalLanguageIntentParser
import com.example.core.agent.telemetry.AgentDebugTraceExporter
import com.example.core.agent.telemetry.AgentTelemetryState
import com.example.core.vision.GroundedVisualTarget
import com.example.core.vision.SemanticScreenModel
import com.example.core.vision.SemanticTarget
import com.example.core.vision.SemanticUIElement
import com.example.core.vision.UniversalVisualGroundingEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentCoreConversationTest {

    @Test
    fun `test NaturalLanguageIntentParser parses Bengali and English commands`() {
        val parser = NaturalLanguageIntentParser()

        // 1. Bengali Flashlight
        val bnTorch = parser.parse("ফ্ল্যাশলাইট অন করো")
        assertEquals(IntentCategory.DEVICE_SETTING, bnTorch.category)
        assertEquals("TOGGLE_FLASHLIGHT", bnTorch.canonicalAction)

        // 2. English YouTube search
        val enYoutube = parser.parse("Open YouTube and play Tom and Jerry")
        assertEquals(IntentCategory.APP_CONTROL, enYoutube.category)
        assertTrue(enYoutube.isMultiStep)
        assertEquals("YouTube", enYoutube.entities.appName)

        // 3. Bengali Volume
        val bnVolume = parser.parse("ভলিউম বাড়াও")
        assertEquals(IntentCategory.DEVICE_SETTING, bnVolume.category)
        assertEquals("VOLUME_UP", bnVolume.canonicalAction)

        // 4. Ordinal follow-up
        val ordinalTurn = parser.parse("play the 2nd one")
        assertTrue(ordinalTurn.isFollowUp)
        assertEquals(1, ordinalTurn.entities.ordinalIndex)
    }

    @Test
    fun `test FollowUpResolver resolves ordinal references from screen`() {
        val resolver = FollowUpResolver()
        val session = ConversationSessionManager()
        val parser = NaturalLanguageIntentParser()

        val mockScreen = SemanticScreenModel(
            packageName = "com.google.android.youtube",
            screenTitle = "Search Results",
            elements = listOf(
                SemanticUIElement(
                    id = "node_1",
                    role = SemanticTarget.SEARCH,
                    label = "Search",
                    bounds = Rect(0, 0, 100, 100)
                ),
                SemanticUIElement(
                    id = "node_2",
                    role = SemanticTarget.VIDEO_ITEM,
                    label = "Tom and Jerry Episode 1",
                    bounds = Rect(0, 100, 1080, 400),
                    clickable = true
                ),
                SemanticUIElement(
                    id = "node_3",
                    role = SemanticTarget.VIDEO_ITEM,
                    label = "Tom and Jerry Episode 2",
                    bounds = Rect(0, 410, 1080, 710),
                    clickable = true
                )
            )
        )

        val intent = parser.parse("play the 2nd video")
        val resolved = resolver.resolve(intent, session.conversationState.value, mockScreen)

        assertTrue(resolved.isResolved)
        assertNotNull(resolved.resolvedToolIntent)
        assertEquals("tap", resolved.resolvedToolIntent?.toolName)
    }

    @Test
    fun `test ConversationSessionManager maintains turn history and context`() {
        val session = ConversationSessionManager()
        val parser = NaturalLanguageIntentParser()

        val turn1Intent = parser.parse("Open Chrome")
        session.recordTurn(
            userInput = "Open Chrome",
            intent = turn1Intent,
            toolIntent = turn1Intent.directToolIntent,
            actionSuccess = true,
            isVerified = true,
            agentResponse = "Opening Chrome"
        )

        assertEquals(1, session.conversationState.value.turnCount)
        assertEquals("open_app", session.conversationState.value.lastActionName)

        val turn2Intent = parser.parse("search for weather today")
        session.recordTurn(
            userInput = "search for weather today",
            intent = turn2Intent,
            toolIntent = turn2Intent.directToolIntent,
            actionSuccess = true,
            isVerified = true,
            agentResponse = "Searching for weather today"
        )

        assertEquals(2, session.conversationState.value.turnCount)
        assertEquals(2, session.conversationState.value.turnHistory.size)
    }

    @Test
    fun `test UniversalVisualGroundingEngine selects correct UI target`() = runBlocking {
        val groundingEngine = UniversalVisualGroundingEngine()

        val mockScreen = SemanticScreenModel(
            packageName = "com.google.android.youtube",
            screenTitle = "YouTube Home",
            elements = listOf(
                SemanticUIElement(
                    id = "btn_search",
                    role = SemanticTarget.SEARCH,
                    label = "Search YouTube",
                    bounds = Rect(900, 50, 1050, 150),
                    clickable = true,
                    confidence = 0.95f
                ),
                SemanticUIElement(
                    id = "btn_cast",
                    role = SemanticTarget.BUTTON,
                    label = "Cast",
                    bounds = Rect(750, 50, 850, 150),
                    clickable = true,
                    confidence = 0.8f
                )
            )
        )

        val groundedTarget = groundingEngine.groundTarget(
            semanticGoal = "search",
            screen = mockScreen,
            screenshot = null
        )

        assertTrue(groundedTarget.isGrounded)
        assertEquals(SemanticTarget.SEARCH, groundedTarget.role)
        assertEquals("btn_search", groundedTarget.element?.id)
    }

    @Test
    fun `test AgentDebugTraceExporter exports comprehensive diagnostic JSON`() {
        val telemetry = AgentTelemetryState(
            currentGoal = "Open YouTube and play cartoon",
            currentApp = "com.google.android.youtube",
            currentScreen = "Search Results",
            targetSelected = "Tom and Jerry #1",
            action = "tap",
            actionResult = "SUCCESS",
            verificationResult = "VERIFIED"
        )

        val session = ConversationSessionManager()
        val traceJson = AgentDebugTraceExporter.exportAsJson(telemetry, session.conversationState.value)

        assertTrue(traceJson.contains("agent_debug_trace"))
        assertTrue(traceJson.contains("com.google.android.youtube"))
        assertTrue(traceJson.contains("Open YouTube and play cartoon"))
    }
}
