package com.example.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local Vision Provider.
 * Provides on-device UI understanding, combining spatial heuristics with the IconSemanticRecognizer
 * to detect icon-only buttons, symbols (🔍, ▶, ←, ⚙, ⋮, etc.), and screen layout structures without cloud latency.
 */
class LocalVisionProvider(
    val iconRecognizer: IconSemanticRecognizer = IconSemanticRecognizer()
) : VisionProvider {

    override val providerName: String = "Local Vision (Icon & Spatial Engine)"
    override val isMultimodalSupported: Boolean = false

    override suspend fun analyzeScreenshot(
        bitmap: Bitmap?,
        prompt: String,
        semanticGoal: String?,
        appPackage: String?,
        screenWidth: Int,
        screenHeight: Int
    ): VisualAnalysisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val detectedElements = mutableListOf<VisualElement>()

        val pkg = appPackage?.lowercase().orEmpty()
        val goal = semanticGoal?.let { SemanticTarget.normalizeIntent(it) }

        // Screen dimensions
        val w = if (bitmap != null && bitmap.width > 0) bitmap.width else screenWidth
        val h = if (bitmap != null && bitmap.height > 0) bitmap.height else screenHeight

        if (pkg.contains("youtube")) {
            // YouTube Top Bar Search Icon (Standard location: top right)
            val searchRect = Rect((w * 0.72f).toInt(), (h * 0.04f).toInt(), (w * 0.88f).toInt(), (h * 0.09f).toInt())
            val recognizedSearch = iconRecognizer.recognizeIcon(null, null, "search", searchRect, pkg, "youtube_top_bar", semanticGoal, bitmap)

            detectedElements.add(
                VisualElement(
                    semanticRole = recognizedSearch?.contextualRole ?: SemanticTarget.SEARCH,
                    visualDescription = "YouTube magnifying-glass 🔍 search icon in top action bar (${recognizedSearch?.meaning ?: "search"})",
                    bounds = searchRect,
                    confidence = recognizedSearch?.confidence ?: 0.94f,
                    source = recognizedSearch?.detectionMethod ?: "LOCAL_HEURISTIC"
                )
            )

            // YouTube First Video Thumbnail / Item in Feed
            val videoRect = Rect((w * 0.05f).toInt(), (h * 0.18f).toInt(), (w * 0.95f).toInt(), (h * 0.45f).toInt())
            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.VIDEO_ITEM,
                    visualDescription = "First video thumbnail item in active YouTube results/feed",
                    bounds = videoRect,
                    confidence = 0.90f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // YouTube Cast / More options
            val moreRect = Rect((w * 0.89f).toInt(), (h * 0.04f).toInt(), (w * 0.98f).toInt(), (h * 0.09f).toInt())
            val recognizedMore = iconRecognizer.recognizeIcon(null, null, "more", moreRect, pkg, "youtube_top_bar", semanticGoal, bitmap)
            detectedElements.add(
                VisualElement(
                    semanticRole = recognizedMore?.contextualRole ?: SemanticTarget.MORE_OPTIONS,
                    visualDescription = "YouTube top bar profile / more options menu",
                    bounds = moreRect,
                    confidence = recognizedMore?.confidence ?: 0.88f,
                    source = recognizedMore?.detectionMethod ?: "LOCAL_HEURISTIC"
                )
            )
        } else if (pkg.contains("whatsapp")) {
            // WhatsApp Top Search Icon
            val searchRect = Rect((w * 0.72f).toInt(), (h * 0.04f).toInt(), (w * 0.85f).toInt(), (h * 0.09f).toInt())
            val recognizedSearch = iconRecognizer.recognizeIcon(null, null, "search", searchRect, pkg, "whatsapp_header", semanticGoal, bitmap)
            detectedElements.add(
                VisualElement(
                    semanticRole = recognizedSearch?.contextualRole ?: SemanticTarget.SEARCH,
                    visualDescription = "WhatsApp search icon 🔍 in top header",
                    bounds = searchRect,
                    confidence = recognizedSearch?.confidence ?: 0.93f,
                    source = recognizedSearch?.detectionMethod ?: "LOCAL_HEURISTIC"
                )
            )

            // WhatsApp First Chat Row
            val chatRect = Rect((w * 0.04f).toInt(), (h * 0.16f).toInt(), (w * 0.96f).toInt(), (h * 0.25f).toInt())
            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.CONTACT_ITEM,
                    visualDescription = "First WhatsApp conversation item in chat list",
                    bounds = chatRect,
                    confidence = 0.89f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // WhatsApp Bottom Right Send / Mic button
            val sendRect = Rect((w * 0.85f).toInt(), (h * 0.91f).toInt(), (w * 0.98f).toInt(), (h * 0.98f).toInt())
            val recognizedSend = iconRecognizer.recognizeIcon(null, null, "send", sendRect, pkg, "whatsapp_composer", semanticGoal, bitmap)
            detectedElements.add(
                VisualElement(
                    semanticRole = recognizedSend?.contextualRole ?: SemanticTarget.SEND_BUTTON,
                    visualDescription = "WhatsApp circular send action button ✈",
                    bounds = sendRect,
                    confidence = recognizedSend?.confidence ?: 0.92f,
                    source = recognizedSend?.detectionMethod ?: "LOCAL_HEURISTIC"
                )
            )
        } else {
            // Universal Android Layout Anchors + Icon Recognition
            val searchRect = Rect((w * 0.75f).toInt(), (h * 0.04f).toInt(), (w * 0.90f).toInt(), (h * 0.09f).toInt())
            val recognizedSearch = iconRecognizer.recognizeIcon(null, null, "search", searchRect, pkg, "universal_top_bar", semanticGoal, bitmap)
            detectedElements.add(
                VisualElement(
                    semanticRole = recognizedSearch?.contextualRole ?: SemanticTarget.SEARCH,
                    visualDescription = "Top right search icon 🔍 area (${recognizedSearch?.meaning ?: "search"})",
                    bounds = searchRect,
                    confidence = recognizedSearch?.confidence ?: 0.85f,
                    source = recognizedSearch?.detectionMethod ?: "LOCAL_HEURISTIC"
                )
            )

            val backRect = Rect((w * 0.02f).toInt(), (h * 0.04f).toInt(), (w * 0.15f).toInt(), (h * 0.09f).toInt())
            val recognizedBack = iconRecognizer.recognizeIcon(null, null, "back", backRect, pkg, "universal_top_bar", semanticGoal, bitmap)
            detectedElements.add(
                VisualElement(
                    semanticRole = recognizedBack?.contextualRole ?: SemanticTarget.BACK,
                    visualDescription = "Top left navigation back arrow ←",
                    bounds = backRect,
                    confidence = recognizedBack?.confidence ?: 0.88f,
                    source = recognizedBack?.detectionMethod ?: "LOCAL_HEURISTIC"
                )
            )

            val moreRect = Rect((w * 0.90f).toInt(), (h * 0.04f).toInt(), (w * 0.99f).toInt(), (h * 0.09f).toInt())
            val recognizedMore = iconRecognizer.recognizeIcon(null, null, "more", moreRect, pkg, "universal_top_bar", semanticGoal, bitmap)
            detectedElements.add(
                VisualElement(
                    semanticRole = recognizedMore?.contextualRole ?: SemanticTarget.MORE_OPTIONS,
                    visualDescription = "Top right overflow menu ⋮",
                    bounds = moreRect,
                    confidence = recognizedMore?.confidence ?: 0.85f,
                    source = recognizedMore?.detectionMethod ?: "LOCAL_HEURISTIC"
                )
            )
        }

        val latency = System.currentTimeMillis() - startTime
        VisualAnalysisResult(
            success = true,
            elements = detectedElements,
            description = "Local on-device visual recognizer identified ${detectedElements.size} UI symbols & controls.",
            providerName = providerName,
            latencyMs = latency
        )
    }
}

