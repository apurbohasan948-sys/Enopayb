package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.accessibility.ActionExecutionDetails
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.core.vision.ocr.LocalOCRProvider
import com.example.core.vision.ocr.OCRProvider
import com.example.data.local.entity.VisualExperienceEntity
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * ScreenUnderstandingEngine.
 * Unified brain responsible for multimodal screen perception:
 * 1. Semantic Android UI Accessibility Tree
 * 2. OCR Visual Text Detection
 * 3. Icon recognition & Semantic Target Resolution (Phase 10)
 * 4. Universal Visual UI & Semantic Screen Modeling
 * 5. Experience database learning & storage
 */
class ScreenUnderstandingEngine(
    private val context: Context,
    val hybridVisionProvider: HybridVisionProvider,
    private val repository: JarvisRepository? = null,
    val screenCaptureManager: ScreenCaptureManager = ScreenCaptureManager(context),
    val ocrProvider: OCRProvider = LocalOCRProvider(),
    val targetResolver: SemanticTargetResolver = SemanticTargetResolver(),
    val iconRecognizer: IconSemanticRecognizer = IconSemanticRecognizer(),
    val screenStateCache: ScreenStateCache = ScreenStateCache(),
    val targetMatcher: VisualTargetMatcher = VisualTargetMatcher()
) {
    val universalEngine by lazy {
        UniversalScreenUnderstandingEngine(
            context = context,
            hybridVisionProvider = hybridVisionProvider,
            repository = repository,
            screenCaptureManager = screenCaptureManager,
            ocrProvider = ocrProvider,
            iconRecognizer = iconRecognizer,
            screenStateCache = screenStateCache,
            targetMatcher = targetMatcher
        )
    }

    val actionExecutor by lazy {
        SemanticActionExecutor(
            context = context,
            screenEngine = universalEngine,
            repository = repository
        )
    }

    private val _latestUnifiedScreen = MutableStateFlow<UnifiedScreen?>(null)
    val latestUnifiedScreen: StateFlow<UnifiedScreen?> = _latestUnifiedScreen.asStateFlow()

    private val _latestScreenshotBitmap = MutableStateFlow<Bitmap?>(null)
    val latestScreenshotBitmap: StateFlow<Bitmap?> = _latestScreenshotBitmap.asStateFlow()

    private val _lastDetectedElements = MutableStateFlow<List<VisualElement>>(emptyList())
    val lastDetectedElements: StateFlow<List<VisualElement>> = _lastDetectedElements.asStateFlow()

    /**
     * Unified Screen Observation.
     * Collects:
     * A. AccessibilityNodeInfo tree
     * B. Screen snapshot when required/insufficient
     * C. Local OCR text extraction
     * D. Visual icon and semantic layout understanding
     */
    suspend fun observeScreen(
        semanticGoal: String? = null,
        forceVisualScan: Boolean = false
    ): UnifiedScreen = withContext(Dispatchers.Default) {
        val diag = JarvisAccessibilityService.getDiagnostics(context)
        val accessibilityElements = mutableListOf<ScreenElement>()

        // 1. Accessibility Tree Traversal
        val observedScreen = JarvisAccessibilityService.observeScreen()
        val currentPackage = observedScreen?.packageName ?: diag.currentPackage

        observedScreen?.elements?.forEach { node ->
            val iconResult = iconRecognizer.recognizeIcon(
                text = node.text,
                contentDescription = node.contentDescription,
                viewId = node.viewId,
                bounds = node.bounds,
                appPackage = currentPackage,
                screenContext = "",
                taskGoal = semanticGoal
            )

            val role = iconResult?.contextualRole ?: inferSemanticRoleFromNode(node, currentPackage)
            accessibilityElements.add(
                ScreenElement(
                    semanticRole = role,
                    text = node.text.ifEmpty { null },
                    contentDescription = node.contentDescription.ifEmpty { null },
                    viewId = node.viewId,
                    className = node.className,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    bounds = node.bounds,
                    confidence = iconResult?.confidence ?: 1.0f,
                    source = if (iconResult != null) "ICON_RECOGNIZER" else "ACCESSIBILITY",
                    visualDescription = iconResult?.meaning
                )
            )
        }

        // Determine if visual/screenshot scan is needed
        val prefs = com.example.data.local.preference.JarvisPreferences(context)
        val isVisionAllowed = prefs.isVisionEnabled && !prefs.isSafeModeEnabled

        val targetRole = semanticGoal?.let { SemanticTarget.normalizeIntent(it) }
        val hasSemanticMatch = targetRole != null && accessibilityElements.any {
            it.semanticRole == targetRole && (it.text != null || it.contentDescription != null || it.isClickable)
        }
        val shouldScanVisuals = isVisionAllowed && (forceVisualScan ||
                !hasSemanticMatch ||
                screenCaptureManager.shouldCaptureScreenshot(diag.totalNodes, diag.clickableNodes, currentPackage, semanticGoal))

        val visualElements = mutableListOf<VisualElement>()
        var screenshotBase64: String? = null

        if (shouldScanVisuals) {
            try {
                val bitmap = screenCaptureManager.captureScreen(force = forceVisualScan)
                _latestScreenshotBitmap.value = bitmap

                if (bitmap != null) {
                    try {
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        screenshotBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 2. OCR Layer
                    val ocrResult = ocrProvider.extractText(bitmap)
                    ocrResult.elements.forEach { ocrElem ->
                        visualElements.add(
                            VisualElement(
                                semanticRole = SemanticTarget.normalizeIntent(ocrElem.text),
                                visualDescription = "OCR: ${ocrElem.text}",
                                bounds = ocrElem.boundingBox,
                                confidence = ocrElem.confidence,
                                source = "OCR"
                            )
                        )
                    }

                    // 3. Multimodal / Heuristic Vision Analysis
                    val visionResult = hybridVisionProvider.analyzeScreenshot(
                        bitmap = bitmap,
                        prompt = "Detect UI controls for goal: ${semanticGoal ?: "general"}",
                        semanticGoal = semanticGoal,
                        appPackage = currentPackage,
                        screenWidth = bitmap.width,
                        screenHeight = bitmap.height
                    )

                    visualElements.addAll(visionResult.elements)
                    _lastDetectedElements.value = visualElements
                }
            } catch (e: Exception) {
                android.util.Log.w("ScreenUnderstanding", "Visual scan failed safely", e)
            }
        }

        // Merge visual & OCR elements into unified elements
        val combinedElements = mutableListOf<ScreenElement>()
        combinedElements.addAll(accessibilityElements)

        visualElements.forEach { vis ->
            val existing = combinedElements.firstOrNull { elem ->
                elem.bounds.contains(vis.bounds.centerX(), vis.bounds.centerY()) ||
                        vis.bounds.contains(elem.bounds.centerX(), elem.bounds.centerY())
            }

            if (existing != null) {
                val index = combinedElements.indexOf(existing)
                combinedElements[index] = existing.copy(
                    semanticRole = if (existing.semanticRole == SemanticTarget.UNKNOWN) vis.semanticRole else existing.semanticRole,
                    visualDescription = vis.visualDescription,
                    confidence = maxOf(existing.confidence, vis.confidence),
                    source = if (vis.source == "OCR") "HYBRID_OCR" else "HYBRID_VISION"
                )
            } else {
                combinedElements.add(
                    ScreenElement(
                        semanticRole = vis.semanticRole,
                        text = if (vis.source == "OCR") vis.visualDescription.removePrefix("OCR: ") else null,
                        contentDescription = null,
                        viewId = null,
                        className = "android.view.View",
                        isClickable = true,
                        isEditable = vis.semanticRole == SemanticTarget.INPUT_FIELD,
                        isScrollable = false,
                        bounds = vis.bounds,
                        confidence = vis.confidence,
                        source = vis.source,
                        visualDescription = vis.visualDescription
                    )
                )
            }
        }

        val unified = UnifiedScreen(
            packageName = currentPackage,
            totalNodes = diag.totalNodes,
            elements = combinedElements,
            visualElements = visualElements,
            screenshotBase64 = screenshotBase64,
            hasVisualAnalysis = shouldScanVisuals
        )

        _latestUnifiedScreen.value = unified
        unified
    }

    /**
     * Resolves target UI control for a user goal.
     */
    fun resolveTargetForGoal(
        goal: String,
        screen: UnifiedScreen,
        previousAction: String? = null
    ): ResolvedTarget {
        return targetResolver.resolveTarget(goal, screen, previousAction)
    }

    /**
     * Find element by Intent.
     */
    suspend fun findElementByIntent(
        rawIntentOrQuery: String,
        currentScreen: UnifiedScreen? = null
    ): Pair<ScreenElement?, AccessibilityNodeInfo?> = withContext(Dispatchers.Default) {
        val targetRole = SemanticTarget.normalizeIntent(rawIntentOrQuery)
        val screen = currentScreen ?: observeScreen(semanticGoal = targetRole)

        // 1. Accessibility Tree Match
        val (node, observedNode) = JarvisAccessibilityService.findElement(rawIntentOrQuery)
        if (node != null && observedNode != null) {
            val element = ScreenElement(
                semanticRole = targetRole,
                text = observedNode.text.ifEmpty { null },
                contentDescription = observedNode.contentDescription.ifEmpty { null },
                viewId = observedNode.viewId,
                className = observedNode.className,
                isClickable = observedNode.isClickable,
                isEditable = observedNode.isEditable,
                isScrollable = observedNode.isScrollable,
                bounds = observedNode.bounds,
                confidence = 0.98f,
                source = "ACCESSIBILITY"
            )
            return@withContext Pair(element, node)
        }

        // 2. Semantic Target Resolver
        val resolved = targetResolver.resolveTarget(rawIntentOrQuery, screen)
        if (resolved.isConfident && resolved.element != null) {
            val root = JarvisAccessibilityService.instance?.rootInActiveWindow
            val matchedNode = findNodeAtCoordinates(root, resolved.element.bounds.centerX(), resolved.element.bounds.centerY())
            return@withContext Pair(resolved.element, matchedNode)
        }

        Pair(resolved.element, null)
    }

    /**
     * Executes tap by intent with comprehensive fallback:
     * 1. SemanticActionExecutor (dynamic resolution, verification, dialog protection)
     * 2. Accessibility ACTION_CLICK
     * 3. Parent ACTION_CLICK
     * 4. Accessibility Gesture Coordinate Tap based on visual bounds
     * 5. Verification and learning database recording
     */
    suspend fun tapElementByIntent(
        rawIntentOrQuery: String,
        contextQuery: String = ""
    ): ActionExecutionDetails = withContext(Dispatchers.Main) {
        val targetRole = SemanticTarget.normalizeIntent(rawIntentOrQuery)

        // Execute via Phase 10 SemanticActionExecutor
        val actionRes = actionExecutor.executeAction(
            targetGoalOrRole = rawIntentOrQuery,
            actionType = "CLICK",
            expectedOutcome = rawIntentOrQuery
        )

        if (actionRes.success) {
            return@withContext ActionExecutionDetails(
                success = true,
                methodUsed = actionRes.actionMethod,
                target = rawIntentOrQuery,
                evidence = actionRes.evidence
            )
        }

        val beforeScreen = observeScreen(semanticGoal = targetRole)
        val (element, node) = findElementByIntent(rawIntentOrQuery, beforeScreen)

        if (element == null) {
            return@withContext ActionExecutionDetails(
                success = false,
                methodUsed = "INTENT_NOT_FOUND",
                target = rawIntentOrQuery,
                evidence = "Could not find element for intent '$rawIntentOrQuery' via Accessibility, Icon Recognizer, OCR, or Multimodal Vision.",
                error = "Target not detected"
            )
        }

        // 1. Accessibility ACTION_CLICK
        if (node != null && node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                recordSuccessfulExperience(beforeScreen.packageName, targetRole, element, "ACTION_CLICK")
                return@withContext ActionExecutionDetails(
                    success = true,
                    methodUsed = "ACCESSIBILITY_ACTION_CLICK",
                    target = rawIntentOrQuery,
                    evidence = "Successfully clicked node for $targetRole: '${element.text ?: element.contentDescription ?: element.visualDescription}'"
                )
            }
        }

        // 2. Parent node hierarchy traversal
        if (node != null) {
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 6) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    recordSuccessfulExperience(beforeScreen.packageName, targetRole, element, "PARENT_CLICK")
                    return@withContext ActionExecutionDetails(
                        success = true,
                        methodUsed = "PARENT_ACTION_CLICK",
                        target = rawIntentOrQuery,
                        evidence = "Clicked parent container (depth $depth) for $targetRole"
                    )
                }
                parent = parent.parent
                depth++
            }
        }

        // 3. Coordinate Gesture Tap based on detected visual bounds
        val bounds = element.bounds
        if (!bounds.isEmpty) {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()
            val gestureOk = JarvisAccessibilityService.performSwipeGesture(cx, cy, cx, cy, 60)
            if (gestureOk) {
                recordSuccessfulExperience(beforeScreen.packageName, targetRole, element, "GESTURE_TAP")
                return@withContext ActionExecutionDetails(
                    success = true,
                    methodUsed = "MULTIMODAL_GESTURE_TAP",
                    target = rawIntentOrQuery,
                    evidence = "Dispatched coordinate tap at center ($cx, $cy) for role $targetRole (Source: ${element.source})"
                )
            }
        }

        // 4. Generic click element fallback
        val textFallback = JarvisAccessibilityService.clickElement(rawIntentOrQuery)
        if (textFallback.success) {
            return@withContext textFallback
        }

        ActionExecutionDetails(
            success = false,
            methodUsed = "INTENT_TAP_FAILED",
            target = rawIntentOrQuery,
            evidence = "Element detected with bounds $bounds but interaction failed.",
            error = "Interaction failed"
        )
    }

    private suspend fun recordSuccessfulExperience(
        pkg: String,
        role: String,
        element: ScreenElement,
        action: String
    ) {
        if (repository == null || pkg.isBlank()) return
        try {
            repository.insertVisualExperience(
                VisualExperienceEntity(
                    appPackage = pkg,
                    screenContext = "active_screen",
                    semanticRole = role,
                    visualDescription = element.visualDescription ?: "Visual icon for $role",
                    actionTaken = action,
                    result = "SUCCESS",
                    confidence = element.confidence,
                    boundsLeft = element.bounds.left,
                    boundsTop = element.bounds.top,
                    boundsRight = element.bounds.right,
                    boundsBottom = element.bounds.bottom,
                    source = element.source
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun inferSemanticRoleFromNode(node: com.example.core.accessibility.ObservedNode, pkg: String): String {
        val t = node.text.lowercase()
        val d = node.contentDescription.lowercase()
        val id = node.viewId?.lowercase().orEmpty()

        return when {
            t.contains("search") || d.contains("search") || id.contains("search") || id.contains("menu_item_search") || id.contains("search_button") -> SemanticTarget.SEARCH
            t.contains("play") || d.contains("play") || id.contains("play") -> SemanticTarget.PLAY
            t.contains("pause") || d.contains("pause") -> SemanticTarget.PAUSE
            t.contains("more options") || d.contains("more options") || id.contains("menu") || d.contains("overflow") -> SemanticTarget.MORE
            t.contains("back") || d.contains("navigate up") || d.contains("back") || id.contains("back") -> SemanticTarget.BACK
            t.contains("home") || d.contains("home") || id.contains("home") -> SemanticTarget.HOME
            t.contains("settings") || d.contains("settings") || id.contains("settings") -> SemanticTarget.SETTINGS
            t.contains("share") || d.contains("share") || id.contains("share") -> SemanticTarget.SHARE
            t.contains("download") || d.contains("download") || id.contains("download") -> SemanticTarget.DOWNLOAD
            t.contains("send") || d.contains("send") || id.contains("send") -> SemanticTarget.SEND
            t.contains("refresh") || d.contains("refresh") || id.contains("reload") -> SemanticTarget.REFRESH
            node.isEditable || node.className.contains("EditText", ignoreCase = true) || id.contains("search_src_text") || id.contains("url_bar") -> SemanticTarget.INPUT_FIELD
            pkg.contains("youtube") && (node.className.contains("ViewGroup") || node.isClickable) && node.bounds.height() > 180 -> SemanticTarget.VIDEO_ITEM
            else -> SemanticTarget.UNKNOWN
        }
    }

    private fun findNodeAtCoordinates(root: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (root == null) return null
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val match = findNodeAtCoordinates(child, x, y)
            if (match != null) return match
        }

        return root
    }
}

