package com.example.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local Vision Provider.
 * Currently backed by text-only on-device inference architecture.
 * Explicitly reports that the local model is text-only, while providing robust local
 * UI layout heuristics and spatial anchor detection for standard Android views.
 */
class LocalVisionProvider : VisionProvider {

    override val providerName: String = "Local Vision (Text-Only Model Engine)"
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

        // Local Heuristics for standard Android applications (e.g. YouTube, Browser, System Toolbars)
        val w = if (bitmap != null && bitmap.width > 0) bitmap.width else screenWidth
        val h = if (bitmap != null && bitmap.height > 0) bitmap.height else screenHeight

        if (pkg.contains("youtube")) {
            // YouTube Top Bar Search Icon (Standard location: top right next to cast/notification/profile)
            val searchLeft = (w * 0.72f).toInt()
            val searchTop = (h * 0.04f).toInt()
            val searchRight = (w * 0.88f).toInt()
            val searchBottom = (h * 0.09f).toInt()

            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.SEARCH,
                    visualDescription = "YouTube magnifying-glass 🔍 search icon in top action bar",
                    bounds = Rect(searchLeft, searchTop, searchRight, searchBottom),
                    confidence = 0.94f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // YouTube First Video Thumbnail / Item in Feed
            val videoLeft = (w * 0.05f).toInt()
            val videoTop = (h * 0.18f).toInt()
            val videoRight = (w * 0.95f).toInt()
            val videoBottom = (h * 0.45f).toInt()

            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.VIDEO_ITEM,
                    visualDescription = "First video thumbnail item in active YouTube results/feed",
                    bounds = Rect(videoLeft, videoTop, videoRight, videoBottom),
                    confidence = 0.90f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // YouTube Cast / More options
            val moreLeft = (w * 0.89f).toInt()
            val moreTop = (h * 0.04f).toInt()
            val moreRight = (w * 0.98f).toInt()
            val moreBottom = (h * 0.09f).toInt()

            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.MORE_OPTIONS,
                    visualDescription = "YouTube top bar profile / more options menu",
                    bounds = Rect(moreLeft, moreTop, moreRight, moreBottom),
                    confidence = 0.88f,
                    source = "LOCAL_HEURISTIC"
                )
            )
        } else if (pkg.contains("whatsapp")) {
            // WhatsApp Top Search Icon
            val searchLeft = (w * 0.72f).toInt()
            val searchTop = (h * 0.04f).toInt()
            val searchRight = (w * 0.85f).toInt()
            val searchBottom = (h * 0.09f).toInt()

            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.SEARCH,
                    visualDescription = "WhatsApp search icon 🔍 in top header",
                    bounds = Rect(searchLeft, searchTop, searchRight, searchBottom),
                    confidence = 0.93f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // WhatsApp First Chat Row
            val chatLeft = (w * 0.04f).toInt()
            val chatTop = (h * 0.16f).toInt()
            val chatRight = (w * 0.96f).toInt()
            val chatBottom = (h * 0.25f).toInt()

            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.CONTACT_ITEM,
                    visualDescription = "First WhatsApp conversation item in chat list",
                    bounds = Rect(chatLeft, chatTop, chatRight, chatBottom),
                    confidence = 0.89f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // WhatsApp Bottom Right Send / Mic button
            val sendLeft = (w * 0.85f).toInt()
            val sendTop = (h * 0.91f).toInt()
            val sendRight = (w * 0.98f).toInt()
            val sendBottom = (h * 0.98f).toInt()

            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.SEND_BUTTON,
                    visualDescription = "WhatsApp circular send action button ✈",
                    bounds = Rect(sendLeft, sendTop, sendRight, sendBottom),
                    confidence = 0.92f,
                    source = "LOCAL_HEURISTIC"
                )
            )
        } else {
            // Universal Android Layout Anchors
            // Top Right Search / Action
            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.SEARCH,
                    visualDescription = "Top right search icon 🔍 area",
                    bounds = Rect((w * 0.75f).toInt(), (h * 0.04f).toInt(), (w * 0.90f).toInt(), (h * 0.09f).toInt()),
                    confidence = 0.80f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // Top Left Back
            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.BACK,
                    visualDescription = "Top left navigation back arrow ←",
                    bounds = Rect((w * 0.02f).toInt(), (h * 0.04f).toInt(), (w * 0.15f).toInt(), (h * 0.09f).toInt()),
                    confidence = 0.85f,
                    source = "LOCAL_HEURISTIC"
                )
            )

            // Top Right More Options ⋮
            detectedElements.add(
                VisualElement(
                    semanticRole = SemanticTarget.MORE_OPTIONS,
                    visualDescription = "Top right overflow menu ⋮",
                    bounds = Rect((w * 0.90f).toInt(), (h * 0.04f).toInt(), (w * 0.99f).toInt(), (h * 0.09f).toInt()),
                    confidence = 0.85f,
                    source = "LOCAL_HEURISTIC"
                )
            )
        }

        val latency = System.currentTimeMillis() - startTime
        VisualAnalysisResult(
            success = true,
            elements = detectedElements,
            description = "Local model does not support vision. Applied local spatial UI heuristics (${detectedElements.size} visual elements detected).",
            providerName = providerName,
            latencyMs = latency
        )
    }
}
