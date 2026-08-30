package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.core.vision.ScreenElement
import com.example.core.vision.SemanticTarget
import com.example.core.vision.SemanticTargetResolver
import com.example.core.vision.UnifiedScreen
import com.example.core.vision.ocr.LocalOCRProvider
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
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS", appName)
    }

    @Test
    fun `test semantic target normalization`() {
        assertEquals(SemanticTarget.SEARCH, SemanticTarget.normalizeIntent("search for videos"))
        assertEquals(SemanticTarget.PLAY, SemanticTarget.normalizeIntent("play music"))
        assertEquals(SemanticTarget.BACK, SemanticTarget.normalizeIntent("go back"))
        assertEquals(SemanticTarget.HOME, SemanticTarget.normalizeIntent("go to home"))
        assertEquals(SemanticTarget.SEND, SemanticTarget.normalizeIntent("send message"))
    }

    @Test
    fun `test semantic target resolver for youtube`() {
        val resolver = SemanticTargetResolver()
        val screen = UnifiedScreen(
            packageName = "com.google.android.youtube",
            totalNodes = 20,
            elements = listOf(
                ScreenElement(
                    semanticRole = SemanticTarget.SEARCH,
                    text = null,
                    contentDescription = "Search YouTube",
                    bounds = Rect(800, 100, 950, 200),
                    isClickable = true
                ),
                ScreenElement(
                    semanticRole = SemanticTarget.VIDEO_ITEM,
                    text = "Tom and Jerry Episode 1",
                    contentDescription = "Tom and Jerry Full Episode",
                    bounds = Rect(50, 350, 1000, 800),
                    isClickable = true
                )
            )
        )

        val target = resolver.resolveTarget("Search YouTube", screen)
        assertTrue(target.isConfident)
        assertEquals(SemanticTarget.SEARCH, target.targetRole)
        assertNotNull(target.element)
    }

    @Test
    fun `test local ocr on bitmap`() = runBlocking {
        val ocr = LocalOCRProvider()
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val result = ocr.extractText(bitmap)
        assertNotNull(result)
    }

    @Test
    fun `test app resolver mapping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appResolver = com.example.core.agent.AppResolver(context)
        val ytMapping = appResolver.resolveApp("YouTube")
        assertEquals("com.google.android.youtube", ytMapping?.packageName)
        assertTrue((ytMapping?.confidence ?: 0f) >= 0.95f)

        val chromeMapping = appResolver.resolveApp("Chrome")
        assertEquals("com.android.chrome", chromeMapping?.packageName)

        val settingsMapping = appResolver.resolveApp("Settings")
        assertEquals("com.android.settings", settingsMapping?.packageName)
    }

    @Test
    fun `test universal target resolver 8-stage resolution`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localVision = com.example.core.vision.LocalVisionProvider()
        val geminiVision = com.example.core.vision.GeminiVisionProvider()
        val hybridProvider = com.example.core.vision.HybridVisionProvider(localVision, geminiVision)
        val screenEngine = com.example.core.vision.ScreenUnderstandingEngine(context, hybridProvider)
        val targetResolver = com.example.core.agent.UniversalTargetResolver(context, screenEngine)

        val testScreen = UnifiedScreen(
            packageName = "com.google.android.youtube",
            totalNodes = 10,
            elements = listOf(
                ScreenElement(
                    semanticRole = "SEARCH",
                    text = "Search",
                    contentDescription = "Search YouTube",
                    bounds = Rect(800, 100, 950, 200),
                    isClickable = true
                )
            )
        )

        val resolved = targetResolver.resolveTarget("SEARCH", testScreen)
        assertTrue(resolved.found)
        assertEquals("SEARCH", resolved.semanticRole)
        assertTrue(resolved.confidence >= 0.85f)
    }

    // === PHASE 12 VOICE, WAKE-WORD & CONVERSATION TESTS ===

    @Test
    fun `test multilingual voice command parser english bangla banglish`() {
        // English
        val enCmd = com.example.core.voice.VoiceCommandParser.parse("Open YouTube and search Tom and Jerry")
        assertEquals("Open YouTube and search Tom and Jerry", enCmd.normalizedGoal)
        assertEquals("YouTube", enCmd.suggestedApp)
        assertEquals("EN", enCmd.detectedLanguage)

        // Bangla
        val bnCmd = com.example.core.voice.VoiceCommandParser.parse("ইউটিউব ওপেন করো")
        assertEquals("Open YouTube", bnCmd.normalizedGoal)
        assertEquals("YouTube", bnCmd.suggestedApp)
        assertEquals("BN", bnCmd.detectedLanguage)

        // Banglish
        val banglishCmd = com.example.core.voice.VoiceCommandParser.parse("YouTube open koro")
        assertEquals("Open YouTube", banglishCmd.normalizedGoal)
        assertEquals("YouTube", banglishCmd.suggestedApp)

        // Stop commands
        assertTrue(com.example.core.voice.VoiceCommandParser.isStopCommand("Stop"))
        assertTrue(com.example.core.voice.VoiceCommandParser.isStopCommand("থামো"))
        assertTrue(com.example.core.voice.VoiceCommandParser.isStopCommand("bondho koro"))

        // Confirmations
        assertTrue(com.example.core.voice.VoiceCommandParser.isAffirmative("yes"))
        assertTrue(com.example.core.voice.VoiceCommandParser.isAffirmative("হ্যাঁ"))
        assertTrue(com.example.core.voice.VoiceCommandParser.isNegative("no"))
        assertTrue(com.example.core.voice.VoiceCommandParser.isNegative("না"))
    }

    @Test
    fun `test conversation manager contextual follow up`() {
        val convManager = com.example.core.voice.context.ConversationManager()

        // Turn 1: Open YouTube
        convManager.recordTurn(
            utterance = "Open YouTube",
            targetApp = "YouTube",
            taskName = "Open YouTube",
            resultSummary = "Opened YouTube."
        )

        assertTrue(convManager.isContextActive())
        assertEquals("YouTube", convManager.state.value.lastApplication)

        // Turn 2: Follow-up "Search Tom and Jerry"
        val enriched = convManager.enrichFollowUpUtterance("Search Tom and Jerry")
        assertEquals("Search Tom and Jerry on YouTube", enriched)
    }

    @Test
    fun `test voice assistant state machine and diagnostics`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceManager = com.example.core.voice.VoiceManager(context)

        assertNotNull(voiceManager.assistantManager)
        val diags = voiceManager.getVoiceDiagnostics()
        assertNotNull(diags["Wake Word Enabled"])
        assertNotNull(diags["Speech Recognizer"])
        assertNotNull(diags["TTS Engine"])
        assertNotNull(diags["Current State"])

        // Test wake word toggle
        voiceManager.setWakeWordEnabled(true)
        assertTrue(voiceManager.assistantManager.wakeWordEngine.isWakeWordEnabled.value)

        voiceManager.setWakeWordEnabled(false)
        assertEquals(false, voiceManager.assistantManager.wakeWordEngine.isWakeWordEnabled.value)
    }
}

