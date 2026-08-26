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
}

