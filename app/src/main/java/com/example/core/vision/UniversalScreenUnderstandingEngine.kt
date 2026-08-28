package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.accessibility.ObservedNode
import com.example.core.vision.ocr.LocalOCRProvider
import com.example.core.vision.ocr.OCRProvider
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * UniversalScreenUnderstandingEngine.
 * Core visual perception engine that transforms raw Android screen state into a unified SemanticScreenModel.
 *
 * Implements the Phase 10 Vision Pipeline:
 * 1. Accessibility Tree (Instant / Zero-AI cost)
 * 2. Cached Semantic Screen (Avoids redundant scans)
 * 3. Icon Semantic Recognizer (Identifies 20+ symbols & contextual meanings)
 * 4. Local OCR Layer (Extracts visible text on canvas/graphical UI)
 * 5. Local Vision Heuristics (Spatial anchors & layout archetypes)
 * 6. Gemini Vision Fallback (Cloud fallback when permitted and local confidence < 0.65)
 * 7. Dialog & Security Detection (Permission, Confirmation, Sensitive alerts)
 */
class UniversalScreenUnderstandingEngine(
    private val context: Context,
    val hybridVisionProvider: HybridVisionProvider,
    private val repository: JarvisRepository? = null,
    val screenCaptureManager: ScreenCaptureManager = ScreenCaptureManager(context),
    val ocrProvider: OCRProvider = LocalOCRProvider(),
    val iconRecognizer: IconSemanticRecognizer = IconSemanticRecognizer(),
    val screenStateCache: ScreenStateCache = ScreenStateCache(),
    val targetMatcher: VisualTargetMatcher = VisualTargetMatcher()
) {
    companion object {
        private const val TAG = "UniversalScreenEngine"
    }

    private val _latestSemanticScreen = MutableStateFlow<SemanticScreenModel?>(null)
    val latestSemanticScreen: StateFlow<SemanticScreenModel?> = _latestSemanticScreen.asStateFlow()

    private val _latestScreenshotBitmap = MutableStateFlow<Bitmap?>(null)
    val latestScreenshotBitmap: StateFlow<Bitmap?> = _latestScreenshotBitmap.asStateFlow()

    private val screenHistory = mutableListOf<SemanticScreenModel>()

    /**
     * Observes and builds the complete SemanticScreenModel for the active screen.
     */
    suspend fun observeScreen(
        taskGoal: String? = null,
        previousAction: String? = null,
        forceVisualScan: Boolean = false
    ): SemanticScreenModel = withContext(Dispatchers.Default) {
        val diag = JarvisAccessibilityService.getDiagnostics(context)
        val observedScreen = JarvisAccessibilityService.observeScreen()
        val currentPackage = observedScreen?.packageName ?: diag.currentPackage
        val rawNodes = observedScreen?.elements ?: emptyList()

        // 1. Check ScreenStateCache if not forcing refresh
        val windowHash = computeWindowHash(rawNodes, currentPackage)
        if (!forceVisualScan) {
            val cached = screenStateCache.getValidCache(currentPackage, rawNodes.size, windowHash)
            if (cached != null && _latestSemanticScreen.value != null) {
                Log.d(TAG, "Reusing cached SemanticScreenModel for $currentPackage")
                return@withContext _latestSemanticScreen.value!!
            }
        }

        val semanticElements = mutableListOf<SemanticUIElement>()
        var elementIdCounter = 0

        // 2. Parse Accessibility Tree & Apply Icon Semantic Recognition
        rawNodes.forEach { node ->
            val recognizedIcon = iconRecognizer.recognizeIcon(
                text = node.text,
                contentDescription = node.contentDescription,
                viewId = node.viewId,
                bounds = node.bounds,
                appPackage = currentPackage,
                screenContext = "",
                taskGoal = taskGoal
            )

            val role = recognizedIcon?.contextualRole ?: inferRoleFromNode(node, currentPackage)
            val iconMeaning = recognizedIcon?.meaning

            val isSensitive = isSensitiveAction(node.text, node.contentDescription, node.viewId, role)

            semanticElements.add(
                SemanticUIElement(
                    id = "acc_${elementIdCounter++}",
                    role = role,
                    label = node.text.ifEmpty { null },
                    description = node.contentDescription.ifEmpty { null },
                    iconMeaning = iconMeaning,
                    bounds = node.bounds,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    enabled = true,
                    visible = true,
                    confidence = recognizedIcon?.confidence ?: 0.95f,
                    source = if (recognizedIcon != null) "ICON_RECOGNIZER" else "ACCESSIBILITY",
                    isDialogElement = false,
                    isSensitive = isSensitive,
                    originalViewId = node.viewId,
                    className = node.className
                )
            )
        }

        // 3. Determine if OCR / Visual Capture is strictly necessary
        val isGraphicalUI = rawNodes.size < 3
        val targetRole = taskGoal?.let { SemanticTarget.normalizeIntent(it) }
        val hasReliableTarget = targetRole != null && semanticElements.any {
            it.role == targetRole && (it.clickable || it.editable) && it.confidence >= 0.85f
        }

        val prefs = com.example.data.local.preference.JarvisPreferences(context)
        val isVisionAllowed = prefs.isVisionEnabled && !prefs.isSafeModeEnabled

        val needsVisualScan = isVisionAllowed && (forceVisualScan ||
                isGraphicalUI ||
                (!hasReliableTarget && taskGoal != null) ||
                screenCaptureManager.shouldCaptureScreenshot(diag.totalNodes, diag.clickableNodes, currentPackage, taskGoal))

        var screenshotBase64: String? = null
        var bitmap: Bitmap? = null

        if (needsVisualScan) {
            try {
                bitmap = screenCaptureManager.captureScreen(force = forceVisualScan)
                _latestScreenshotBitmap.value = bitmap

                if (bitmap != null) {
                    try {
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        screenshotBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 4. OCR Integration
                    val ocrResult = ocrProvider.extractText(bitmap)
                    ocrResult.elements.forEach { ocrElem ->
                        val isDuplicate = semanticElements.any { elem ->
                            elem.bounds.contains(ocrElem.boundingBox.centerX(), ocrElem.boundingBox.centerY()) ||
                                    ocrElem.boundingBox.contains(elem.bounds.centerX(), elem.bounds.centerY())
                        }

                        if (!isDuplicate && ocrElem.text.isNotBlank()) {
                            val ocrRole = SemanticTarget.normalizeIntent(ocrElem.text)
                            semanticElements.add(
                                SemanticUIElement(
                                    id = "ocr_${elementIdCounter++}",
                                    role = ocrRole,
                                    label = ocrElem.text,
                                    description = null,
                                    iconMeaning = null,
                                    bounds = ocrElem.boundingBox,
                                    clickable = true,
                                    editable = ocrRole == SemanticTarget.INPUT_FIELD,
                                    scrollable = false,
                                    confidence = ocrElem.confidence,
                                    source = "OCR"
                                )
                            )
                        }
                    }

                    // 5. Local Vision / Heuristics / Gemini Multimodal Fallback
                    val visionResult = hybridVisionProvider.analyzeScreenshot(
                        bitmap = bitmap,
                        prompt = "Analyze UI controls for task: ${taskGoal ?: "general navigation"}",
                        semanticGoal = taskGoal,
                        appPackage = currentPackage,
                        screenWidth = bitmap.width,
                        screenHeight = bitmap.height
                    )

                    visionResult.elements.forEach { vis ->
                        val isCovered = semanticElements.any { elem ->
                            elem.bounds.contains(vis.bounds.centerX(), vis.bounds.centerY())
                        }
                        if (!isCovered) {
                            semanticElements.add(
                                SemanticUIElement(
                                    id = "vis_${elementIdCounter++}",
                                    role = vis.semanticRole,
                                    label = null,
                                    description = vis.visualDescription,
                                    iconMeaning = vis.visualDescription,
                                    bounds = vis.bounds,
                                    clickable = true,
                                    editable = vis.semanticRole == SemanticTarget.INPUT_FIELD,
                                    scrollable = false,
                                    confidence = vis.confidence,
                                    source = vis.source
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Visual scanning failed safely: ${e.message}")
            }
        }

        // 6. Dialog Detection & Classification
        val (isDialogActive, dialogType) = detectDialog(semanticElements, currentPackage)
        if (isDialogActive) {
            for (i in semanticElements.indices) {
                semanticElements[i] = semanticElements[i].copy(
                    isDialogElement = true,
                    isSensitive = isSensitiveAction(
                        semanticElements[i].label,
                        semanticElements[i].description,
                        semanticElements[i].originalViewId,
                        semanticElements[i].role
                    )
                )
            }
        }

        // 7. Screen Title & Type Classification
        val (screenTitle, screenType) = classifyScreen(currentPackage, semanticElements, isDialogActive, dialogType)

        val primaryActions = semanticElements.filter { it.clickable }.map { it.role }.distinct().take(8)

        val semanticModel = SemanticScreenModel(
            packageName = currentPackage,
            screenTitle = screenTitle,
            screenType = screenType,
            elements = semanticElements,
            isDialogActive = isDialogActive,
            dialogType = dialogType,
            primaryActions = primaryActions,
            screenConfidence = if (semanticElements.isNotEmpty()) 0.95f else 0.50f,
            timestamp = System.currentTimeMillis(),
            screenshotBase64 = screenshotBase64,
            hasGraphicalUIOnly = isGraphicalUI
        )

        // Cache result
        screenStateCache.updateCache(
            packageName = currentPackage,
            nodeCount = rawNodes.size,
            sampleBoundsSummary = windowHash,
            elements = emptyList(),
            visualElements = emptyList()
        )

        _latestSemanticScreen.value = semanticModel
        synchronized(screenHistory) {
            if (screenHistory.size >= 10) screenHistory.removeAt(0)
            screenHistory.add(semanticModel)
        }

        semanticModel
    }

    private fun detectDialog(elements: List<SemanticUIElement>, pkg: String): Pair<Boolean, String?> {
        val texts = elements.mapNotNull { it.label ?: it.description }.map { it.lowercase() }
        val allText = texts.joinToString(" ")

        return when {
            allText.contains("allow") && (allText.contains("permission") || allText.contains("access") || allText.contains("photos") || allText.contains("location")) -> {
                Pair(true, "PERMISSION")
            }
            allText.contains("are you sure") || (allText.contains("confirm") && allText.contains("delete")) -> {
                Pair(true, "CONFIRMATION")
            }
            allText.contains("sign in") || allText.contains("log in") || allText.contains("password") -> {
                Pair(true, "LOGIN")
            }
            allText.contains("error") || allText.contains("unfortunately") || allText.contains("stopped") -> {
                Pair(true, "ERROR")
            }
            pkg.contains("android") && elements.any { it.label?.contains("Allow", ignoreCase = true) == true } -> {
                Pair(true, "SYSTEM_ALERT")
            }
            else -> Pair(false, null)
        }
    }

    private fun isSensitiveAction(text: String?, desc: String?, viewId: String?, role: String): Boolean {
        val combined = "$text $desc $viewId $role".lowercase()
        return combined.contains("delete") ||
                combined.contains("allow") ||
                combined.contains("permission") ||
                combined.contains("format") ||
                combined.contains("uninstall") ||
                combined.contains("purchase") ||
                combined.contains("pay") ||
                combined.contains("transfer")
    }

    private fun classifyScreen(
        pkg: String,
        elements: List<SemanticUIElement>,
        isDialogActive: Boolean,
        dialogType: String?
    ): Pair<String, ScreenType> {
        if (isDialogActive) {
            return Pair("Dialog: ${dialogType ?: "Alert"}", when (dialogType) {
                "PERMISSION" -> ScreenType.DIALOG_PERMISSION
                "CONFIRMATION" -> ScreenType.DIALOG_CONFIRMATION
                else -> ScreenType.DIALOG_SYSTEM
            })
        }

        val lowerPkg = pkg.lowercase()
        return when {
            lowerPkg.contains("youtube") -> {
                if (elements.any { it.role == SemanticTarget.INPUT_FIELD || it.label?.contains("Search", ignoreCase = true) == true }) {
                    Pair("YouTube Search", ScreenType.SEARCH_SCREEN)
                } else {
                    Pair("YouTube Home", ScreenType.APP_HOME)
                }
            }
            lowerPkg.contains("chrome") -> Pair("Chrome Browser", ScreenType.SEARCH_SCREEN)
            lowerPkg.contains("setting") -> Pair("Settings", ScreenType.SETTINGS_PAGE)
            lowerPkg.contains("whatsapp") -> Pair("WhatsApp Chats", ScreenType.APP_HOME)
            elements.size < 3 -> Pair("Graphical Canvas View", ScreenType.GRAPHICAL_CANVAS)
            else -> Pair(pkg.substringAfterLast("."), ScreenType.APP_HOME)
        }
    }

    private fun inferRoleFromNode(node: ObservedNode, pkg: String): String {
        val t = node.text.lowercase()
        val d = node.contentDescription.lowercase()
        val id = node.viewId?.lowercase().orEmpty()

        return when {
            t.contains("search") || d.contains("search") || id.contains("search") || id.contains("query") -> SemanticTarget.SEARCH
            t.contains("play") || d.contains("play") || id.contains("play") -> SemanticTarget.PLAY
            t.contains("pause") || d.contains("pause") -> SemanticTarget.PAUSE
            t.contains("back") || d.contains("navigate up") || id.contains("back") -> SemanticTarget.BACK
            t.contains("home") || d.contains("home") || id.contains("home") -> SemanticTarget.HOME
            t.contains("settings") || d.contains("settings") || id.contains("settings") -> SemanticTarget.SETTINGS
            node.isEditable || node.className.contains("EditText", ignoreCase = true) -> SemanticTarget.INPUT_FIELD
            pkg.contains("youtube") && node.bounds.height() > 180 && node.isClickable -> SemanticTarget.VIDEO_ITEM
            else -> SemanticTarget.UNKNOWN
        }
    }

    private fun computeWindowHash(nodes: List<ObservedNode>, pkg: String): Int {
        var hash = pkg.hashCode()
        nodes.take(8).forEach {
            hash = 31 * hash + it.bounds.hashCode() + it.text.hashCode()
        }
        return hash
    }
}
