package com.example

import android.graphics.Rect
import com.example.core.vision.IconSemanticRecognizer
import com.example.core.vision.ScreenDiffEngine
import com.example.core.vision.ScreenType
import com.example.core.vision.SemanticScreenModel
import com.example.core.vision.SemanticTarget
import com.example.core.vision.SemanticUIElement
import com.example.core.vision.VisualTargetMatcher
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
class Phase10VisualUnderstandingTest {

    private val iconRecognizer = IconSemanticRecognizer()
    private val targetMatcher = VisualTargetMatcher()
    private val diffEngine = ScreenDiffEngine()

    @Test
    fun `test IconSemanticRecognizer detects search icon by glyph`() {
        val recognized = iconRecognizer.recognizeIcon(
            text = "🔍",
            contentDescription = "",
            viewId = null,
            bounds = Rect(900, 100, 1000, 200),
            appPackage = "com.google.android.youtube",
            screenContext = "home_feed",
            taskGoal = "Search for Tom and Jerry"
        )

        assertNotNull(recognized)
        assertEquals(SemanticTarget.SEARCH, recognized!!.contextualRole)
        assertTrue(recognized.confidence >= 0.90f)
        assertTrue(recognized.meaning.contains("magnifying", ignoreCase = true) || recognized.meaning.contains("search", ignoreCase = true))
    }

    @Test
    fun `test IconSemanticRecognizer detects play and back icons by viewId and context`() {
        val playIcon = iconRecognizer.recognizeIcon(
            text = "",
            contentDescription = "",
            viewId = "btn_play_video_stream",
            bounds = Rect(400, 500, 600, 700),
            appPackage = "com.google.android.youtube",
            screenContext = "",
            taskGoal = "Play"
        )
        assertNotNull(playIcon)
        assertEquals(SemanticTarget.PLAY, playIcon!!.contextualRole)

        val backIcon = iconRecognizer.recognizeIcon(
            text = "←",
            contentDescription = "",
            viewId = null,
            bounds = Rect(20, 80, 120, 180),
            appPackage = "com.android.settings",
            screenContext = "",
            taskGoal = "Go back"
        )
        assertNotNull(backIcon)
        assertEquals(SemanticTarget.BACK, backIcon!!.contextualRole)
    }

    @Test
    fun `test VisualTargetMatcher resolves search target on YouTube screen without text Search`() {
        val elements = listOf(
            SemanticUIElement(
                id = "elem_1",
                role = SemanticTarget.UNKNOWN,
                label = "Home",
                description = "Home Tab",
                iconMeaning = null,
                bounds = Rect(0, 2200, 300, 2400),
                clickable = true,
                editable = false,
                scrollable = false,
                confidence = 0.95f,
                source = "ACCESSIBILITY"
            ),
            SemanticUIElement(
                id = "elem_search_icon",
                role = SemanticTarget.SEARCH,
                label = null,
                description = null,
                iconMeaning = "Search / Query Input",
                bounds = Rect(920, 90, 1040, 210),
                clickable = true,
                editable = false,
                scrollable = false,
                confidence = 0.92f,
                source = "ICON_RECOGNIZER"
            )
        )

        val screenModel = SemanticScreenModel(
            packageName = "com.google.android.youtube",
            screenTitle = "YouTube Home Feed",
            screenType = ScreenType.APP_HOME,
            elements = elements,
            isDialogActive = false,
            dialogType = null,
            primaryActions = listOf(SemanticTarget.SEARCH),
            screenConfidence = 0.95f,
            timestamp = System.currentTimeMillis()
        )

        val match = targetMatcher.matchTarget("Search for Tom and Jerry", screenModel)
        assertNotNull(match.selectedElement)
        assertEquals("elem_search_icon", match.selectedElement!!.id)
        assertEquals(SemanticTarget.SEARCH, match.targetRole)
        assertTrue(match.confidence >= 0.70f)
    }

    @Test
    fun `test VisualTargetMatcher resolves play video on search results screen`() {
        val elements = listOf(
            SemanticUIElement(
                id = "search_header",
                role = SemanticTarget.SEARCH,
                label = "Tom and Jerry",
                description = null,
                iconMeaning = null,
                bounds = Rect(50, 80, 900, 200),
                clickable = true,
                editable = true,
                scrollable = false,
                confidence = 0.95f,
                source = "ACCESSIBILITY"
            ),
            SemanticUIElement(
                id = "video_item_1",
                role = SemanticTarget.VIDEO_ITEM,
                label = "Tom and Jerry - Classic Cartoon Episode 1",
                description = "10M views • 10 minutes",
                iconMeaning = null,
                bounds = Rect(0, 300, 1080, 900),
                clickable = true,
                editable = false,
                scrollable = false,
                confidence = 0.90f,
                source = "ACCESSIBILITY"
            )
        )

        val screenModel = SemanticScreenModel(
            packageName = "com.google.android.youtube",
            screenTitle = "YouTube Search Results",
            screenType = ScreenType.SEARCH_SCREEN,
            elements = elements,
            isDialogActive = false,
            dialogType = null,
            primaryActions = listOf(SemanticTarget.VIDEO_ITEM),
            screenConfidence = 0.95f,
            timestamp = System.currentTimeMillis()
        )

        val match = targetMatcher.matchTarget("Play the first video", screenModel)
        assertNotNull(match.selectedElement)
        assertEquals("video_item_1", match.selectedElement!!.id)
        assertEquals(SemanticTarget.VIDEO_ITEM, match.targetRole)
    }

    @Test
    fun `test ScreenDiffEngine detects transition when dialog opens`() {
        val beforeScreen = SemanticScreenModel(
            packageName = "com.example.app",
            screenTitle = "Main Screen",
            screenType = ScreenType.APP_HOME,
            elements = listOf(
                SemanticUIElement("1", SemanticTarget.BUTTON, "Submit", null, null, Rect(100, 100, 300, 200), clickable = true, editable = false, scrollable = false, confidence = 0.9f, source = "ACCESSIBILITY")
            ),
            isDialogActive = false,
            dialogType = null,
            primaryActions = emptyList(),
            screenConfidence = 0.9f,
            timestamp = System.currentTimeMillis()
        )

        val afterScreen = SemanticScreenModel(
            packageName = "com.example.app",
            screenTitle = "Dialog: Permission",
            screenType = ScreenType.DIALOG_PERMISSION,
            elements = listOf(
                SemanticUIElement("dialog_allow", SemanticTarget.BUTTON, "Allow", "Allow Photos Access", null, Rect(200, 1200, 500, 1300), clickable = true, editable = false, scrollable = false, confidence = 0.95f, source = "ACCESSIBILITY", isDialogElement = true, isSensitive = true)
            ),
            isDialogActive = true,
            dialogType = "PERMISSION",
            primaryActions = emptyList(),
            screenConfidence = 0.95f,
            timestamp = System.currentTimeMillis() + 500
        )

        val diff = diffEngine.computeDiff(beforeScreen, afterScreen, expectedOutcome = "Permission dialog opens")
        assertTrue(diff.transitionOccurred)
        assertTrue(diff.isDialogOpened)
        assertEquals("DIALOG_OPENED", diff.transitionType)
        assertTrue(diff.matchedExpectedOutcome)
    }

    @Test
    fun `test ScreenDiffEngine detects app switch navigation`() {
        val beforeScreen = SemanticScreenModel(
            packageName = "com.example.assistant",
            screenTitle = "Assistant Home",
            screenType = ScreenType.APP_HOME,
            elements = emptyList(),
            isDialogActive = false,
            dialogType = null,
            primaryActions = emptyList(),
            screenConfidence = 0.9f,
            timestamp = System.currentTimeMillis()
        )

        val afterScreen = SemanticScreenModel(
            packageName = "com.google.android.youtube",
            screenTitle = "YouTube Home",
            screenType = ScreenType.APP_HOME,
            elements = emptyList(),
            isDialogActive = false,
            dialogType = null,
            primaryActions = emptyList(),
            screenConfidence = 0.9f,
            timestamp = System.currentTimeMillis() + 500
        )

        val diff = diffEngine.computeDiff(beforeScreen, afterScreen)
        assertTrue(diff.transitionOccurred)
        assertTrue(diff.isNavigationOccurred)
        assertEquals("APP_SWITCH", diff.transitionType)
    }
}
