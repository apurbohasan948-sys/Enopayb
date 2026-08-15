package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.accessibility.ActionExecutionDetails
import com.example.core.accessibility.JarvisAccessibilityService
import com.example.data.local.entity.VisualExperienceEntity
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * ScreenUnderstandingEngine.
 * Unified brain responsible for multimodal screen perception:
 * 1. Semantic Android UI Accessibility Tree
 * 2. Visual icon and layout understanding
 * 3. Fast-path intent matching (e.g. 🔍 Search even when icon has zero text)
 * 4. Experience database learning & storage
 */
class ScreenUnderstandingEngine(
    private val context: Context,
    val hybridVisionProvider: HybridVisionProvider,
    private val repository: JarvisRepository? = null
) {
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
     * B. Screenshot when visual analysis is required
     * C. Local layout heuristics & visual understanding
     * D. Cloud Vision fallback when necessary
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
            val role = inferSemanticRoleFromNode(node, currentPackage)
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
                    confidence = 1.0f,
                    source = "ACCESSIBILITY"
                )
            )
        }

        // Determine if visual scan is required:
        // A. Explicitly requested OR
        // B. Target semantic role (e.g. SEARCH / PLAY / SEND) is not found in the accessibility tree with text
        val targetRole = semanticGoal?.let { SemanticTarget.normalizeIntent(it) }
        val hasSemanticMatch = targetRole != null && accessibilityElements.any {
            it.semanticRole == targetRole && (it.text != null || it.contentDescription != null)
        }
        val shouldScanVisuals = forceVisualScan || !hasSemanticMatch

        var visualElements = emptyList<VisualElement>()
        var screenshotBase64: String? = null

        if (shouldScanVisuals) {
            val bitmap = JarvisAccessibilityService.takeScreenshotBitmap()
            _latestScreenshotBitmap.value = bitmap

            if (bitmap != null) {
                try {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    screenshotBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val visionResult = hybridVisionProvider.analyzeScreenshot(
                    bitmap = bitmap,
                    prompt = "Detect UI controls for goal: ${semanticGoal ?: "general"}",
                    semanticGoal = semanticGoal,
                    appPackage = currentPackage,
                    screenWidth = bitmap.width,
                    screenHeight = bitmap.height
                )

                visualElements = visionResult.elements
                _lastDetectedElements.value = visualElements
            }
        }

        // Merge visual elements into unified elements
        val combinedElements = mutableListOf<ScreenElement>()
        combinedElements.addAll(accessibilityElements)

        visualElements.forEach { vis ->
            // Check if there is already an overlapping accessibility element
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
                    source = "HYBRID"
                )
            } else {
                combinedElements.add(
                    ScreenElement(
                        semanticRole = vis.semanticRole,
                        text = null,
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
     * Find element by Intent (e.g. findElementByIntent("SEARCH") finds 🔍 even with zero text).
     * Priority:
     * 1. Accessibility exact text / content description
     * 2. Accessibility resource ID
     * 3. Visual icon detection (Local Heuristic / Gemini Vision)
     * 4. Past Experience DB
     */
    suspend fun findElementByIntent(
        rawIntentOrQuery: String,
        currentScreen: UnifiedScreen? = null
    ): Pair<ScreenElement?, AccessibilityNodeInfo?> = withContext(Dispatchers.Default) {
        val targetRole = SemanticTarget.normalizeIntent(rawIntentOrQuery)
        val trimmed = rawIntentOrQuery.trim().lowercase()

        val screen = currentScreen ?: observeScreen(semanticGoal = targetRole)

        // Priority 1: Check Accessibility tree for high confidence match
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

        // Priority 2: Check unified screen elements by Semantic Role
        val matchingElement = screen.elements.firstOrNull {
            it.semanticRole.equals(targetRole, ignoreCase = true) ||
                    (it.visualDescription?.contains(trimmed, ignoreCase = true) == true)
        }

        if (matchingElement != null) {
            // Find corresponding node if exists at coordinates
            val root = JarvisAccessibilityService.instance?.rootInActiveWindow
            val matchedNode = findNodeAtCoordinates(root, matchingElement.bounds.centerX(), matchingElement.bounds.centerY())
            return@withContext Pair(matchingElement, matchedNode)
        }

        // Priority 3: Check visual elements directly
        val visualMatch = screen.visualElements.firstOrNull {
            it.semanticRole.equals(targetRole, ignoreCase = true) ||
                    it.visualDescription.contains(trimmed, ignoreCase = true)
        }

        if (visualMatch != null) {
            val elem = ScreenElement(
                semanticRole = visualMatch.semanticRole,
                bounds = visualMatch.bounds,
                confidence = visualMatch.confidence,
                source = visualMatch.source,
                visualDescription = visualMatch.visualDescription,
                isClickable = true
            )
            return@withContext Pair(elem, null)
        }

        Pair(null, null)
    }

    /**
     * Executes tap by intent with comprehensive fallback:
     * 1. Accessibility ACTION_CLICK
     * 2. Parent ACTION_CLICK
     * 3. Accessibility Gesture Coordinate Tap based on visual bounds
     * 4. Verification and learning database recording
     */
    suspend fun tapElementByIntent(
        rawIntentOrQuery: String,
        contextQuery: String = ""
    ): ActionExecutionDetails = withContext(Dispatchers.Main) {
        val targetRole = SemanticTarget.normalizeIntent(rawIntentOrQuery)

        // Observe screen with semantic intent
        val beforeScreen = observeScreen(semanticGoal = targetRole)
        val (element, node) = findElementByIntent(rawIntentOrQuery, beforeScreen)

        if (element == null) {
            return@withContext ActionExecutionDetails(
                success = false,
                methodUsed = "INTENT_NOT_FOUND",
                target = rawIntentOrQuery,
                evidence = "Could not find element for intent '$rawIntentOrQuery' via Accessibility or Multimodal Vision.",
                error = "Target not detected"
            )
        }

        // 1. If we have an AccessibilityNodeInfo and it is clickable
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
            t.contains("search") || d.contains("search") || id.contains("search") || id.contains("menu_item_search") -> SemanticTarget.SEARCH
            t.contains("play") || d.contains("play") || id.contains("play") -> SemanticTarget.PLAY
            t.contains("pause") || d.contains("pause") -> SemanticTarget.PAUSE
            t.contains("more options") || d.contains("more options") || id.contains("menu") -> SemanticTarget.MORE_OPTIONS
            t.contains("back") || d.contains("navigate up") || d.contains("back") -> SemanticTarget.BACK
            t.contains("home") || d.contains("home") -> SemanticTarget.HOME
            t.contains("settings") || d.contains("settings") -> SemanticTarget.SETTINGS
            t.contains("share") || d.contains("share") -> SemanticTarget.SHARE
            t.contains("download") || d.contains("download") -> SemanticTarget.DOWNLOAD
            node.isEditable || node.className.contains("EditText", ignoreCase = true) -> SemanticTarget.INPUT_FIELD
            pkg.contains("youtube") && (node.className.contains("ViewGroup") || node.isClickable) && node.bounds.height() > 200 -> SemanticTarget.VIDEO_ITEM
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
