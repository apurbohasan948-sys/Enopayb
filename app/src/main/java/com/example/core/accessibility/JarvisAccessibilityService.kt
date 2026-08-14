package com.example.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ObservedNode(
    val text: String,
    val contentDescription: String,
    val viewId: String? = null,
    val className: String,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val isFocused: Boolean = false,
    val bounds: Rect = Rect()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("text", text)
            put("contentDescription", contentDescription)
            put("viewId", viewId ?: "")
            put("className", className)
            put("clickable", isClickable)
            put("editable", isEditable)
            put("scrollable", isScrollable)
            put("enabled", isEnabled)
            put("bounds", JSONObject().apply {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
            })
        }
    }
}

data class ObservedScreen(
    val packageName: String,
    val totalNodes: Int,
    val clickableCount: Int,
    val editableCount: Int,
    val scrollableCount: Int,
    val elements: List<ObservedNode>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val root = JSONObject().apply {
            put("package", packageName)
            put("timestamp", timestamp)
            put("totalNodes", totalNodes)
            put("clickableCount", clickableCount)
            put("editableCount", editableCount)
            put("scrollableCount", scrollableCount)
            val elementsArr = JSONArray()
            elements.forEach { elementsArr.put(it.toJsonObject()) }
            put("elements", elementsArr)
        }
        return root.toString(2)
    }

    fun getSummary(): String {
        val topTexts = elements.map { it.text.ifEmpty { it.contentDescription } }
            .filter { it.isNotBlank() }
            .take(12)
        return "App: $packageName | Elements: ${elements.size} | Visible: ${topTexts.joinToString(", ")}"
    }
}

data class AccessibilityDiagnostics(
    val isEnabled: Boolean,
    val isConnected: Boolean,
    val currentPackage: String,
    val isRootAvailable: Boolean,
    val totalNodes: Int,
    val clickableNodes: Int,
    val editableNodes: Int,
    val scrollableNodes: Int,
    val recentElements: List<ObservedNode>
)

data class ActionExecutionDetails(
    val success: Boolean,
    val methodUsed: String,
    val target: String,
    val matchedNode: ObservedNode? = null,
    val evidence: String,
    val error: String? = null
)

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.i("JarvisAccessibility", "JARVIS Accessibility Service Connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            _currentForegroundApp.value = packageName
        }
    }

    override fun onInterrupt() {
        Log.w("JarvisAccessibility", "JARVIS Accessibility Service Interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceActive.value = false
        }
    }

    companion object {
        private const val TAG = "JarvisAccessibility"
        private var instance: JarvisAccessibilityService? = null

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _currentForegroundApp = MutableStateFlow("com.example")
        val currentForegroundApp: StateFlow<String> = _currentForegroundApp.asStateFlow()

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${JarvisAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun getDiagnostics(context: Context): AccessibilityDiagnostics {
            val enabled = isAccessibilityEnabled(context)
            val connected = instance != null
            val pkg = _currentForegroundApp.value
            val root = instance?.rootInActiveWindow
            val isRootAvail = root != null

            var total = 0
            var clickable = 0
            var editable = 0
            var scrollable = 0
            val nodes = mutableListOf<ObservedNode>()

            if (root != null) {
                fun countAndCollect(node: AccessibilityNodeInfo?) {
                    if (node == null) return
                    total++
                    if (node.isClickable) clickable++
                    if (node.isEditable || node.isPassword || node.className?.contains("EditText", ignoreCase = true) == true) editable++
                    if (node.isScrollable) scrollable++

                    val text = node.text?.toString()?.trim().orEmpty()
                    val desc = node.contentDescription?.toString()?.trim().orEmpty()
                    if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isEditable) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        nodes.add(
                            ObservedNode(
                                text = text,
                                contentDescription = desc,
                                viewId = node.viewIdResourceName,
                                className = node.className?.toString() ?: "View",
                                isClickable = node.isClickable,
                                isEditable = node.isEditable,
                                isScrollable = node.isScrollable,
                                isEnabled = node.isEnabled,
                                isFocused = node.isFocused,
                                bounds = rect
                            )
                        )
                    }

                    for (i in 0 until node.childCount) {
                        countAndCollect(node.getChild(i))
                    }
                }
                countAndCollect(root)
            }

            return AccessibilityDiagnostics(
                isEnabled = enabled,
                isConnected = connected,
                currentPackage = pkg,
                isRootAvailable = isRootAvail,
                totalNodes = total,
                clickableNodes = clickable,
                editableNodes = editable,
                scrollableNodes = scrollable,
                recentElements = nodes.take(40)
            )
        }

        // ==========================================
        // PHASE B: Real observeScreen()
        // ==========================================

        fun observeScreen(): ObservedScreen? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return null
            val elements = mutableListOf<ObservedNode>()
            var total = 0
            var clickable = 0
            var editable = 0
            var scrollable = 0

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null) return
                total++
                if (node.isClickable) clickable++
                if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) editable++
                if (node.isScrollable) scrollable++

                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val viewId = node.viewIdResourceName
                val className = node.className?.toString() ?: "View"

                val isEditableNode = node.isEditable ||
                        node.isPassword ||
                        className.contains("EditText", ignoreCase = true) ||
                        className.contains("AutoCompleteTextView", ignoreCase = true)

                val rect = Rect()
                node.getBoundsInScreen(rect)

                // Include all informative or interactive nodes
                if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || isEditableNode || node.isScrollable) {
                    elements.add(
                        ObservedNode(
                            text = text,
                            contentDescription = desc,
                            viewId = viewId,
                            className = className,
                            isClickable = node.isClickable,
                            isEditable = isEditableNode,
                            isScrollable = node.isScrollable,
                            isEnabled = node.isEnabled,
                            isFocused = node.isFocused,
                            bounds = rect
                        )
                    )
                }

                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i))
                }
            }

            traverse(root)
            val currentPkg = root.packageName?.toString() ?: _currentForegroundApp.value

            return ObservedScreen(
                packageName = currentPkg,
                totalNodes = total,
                clickableCount = clickable,
                editableCount = editable,
                scrollableCount = scrollable,
                elements = elements
            )
        }

        // ==========================================
        // PHASE C: findElement()
        // ==========================================

        fun findElement(query: String): Pair<AccessibilityNodeInfo?, ObservedNode?> {
            val service = instance ?: return Pair(null, null)
            val root = service.rootInActiveWindow ?: return Pair(null, null)
            val trimmed = query.trim().lowercase()
            if (trimmed.isEmpty()) return Pair(null, null)

            var bestNode: AccessibilityNodeInfo? = null
            var bestObserved: ObservedNode? = null
            var highestPriority = 0

            fun scoreAndSearch(node: AccessibilityNodeInfo?) {
                if (node == null) return

                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val viewId = node.viewIdResourceName.orEmpty()
                val className = node.className?.toString().orEmpty()

                val textLower = text.lowercase()
                val descLower = desc.lowercase()
                val idLower = viewId.lowercase()

                var score = 0

                // Priority 1: Exact text
                if (textLower == trimmed) {
                    score = 100
                }
                // Priority 2: Exact content description
                else if (descLower == trimmed) {
                    score = 90
                }
                // Priority 3: Exact resource ID match
                else if (idLower.endsWith("/$trimmed") || idLower == trimmed) {
                    score = 85
                }
                // Priority 4: Partial text match
                else if (textLower.contains(trimmed)) {
                    score = 75
                }
                // Priority 5: Partial description match
                else if (descLower.contains(trimmed)) {
                    score = 70
                }
                // Priority 6: Semantic search keywords (e.g. "search", "magnifier", "play", "send")
                else if (trimmed == "search" && (idLower.contains("search") || descLower.contains("search") || textLower.contains("search"))) {
                    score = 80
                } else if (trimmed == "play" && (idLower.contains("play") || descLower.contains("play") || textLower.contains("play"))) {
                    score = 80
                } else if (trimmed == "send" && (idLower.contains("send") || descLower.contains("send") || textLower.contains("send"))) {
                    score = 80
                }

                if (score > highestPriority) {
                    highestPriority = score
                    bestNode = node
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    bestObserved = ObservedNode(
                        text = text,
                        contentDescription = desc,
                        viewId = node.viewIdResourceName,
                        className = className,
                        isClickable = node.isClickable,
                        isEditable = node.isEditable,
                        isScrollable = node.isScrollable,
                        isEnabled = node.isEnabled,
                        isFocused = node.isFocused,
                        bounds = rect
                    )
                }

                for (i in 0 until node.childCount) {
                    scoreAndSearch(node.getChild(i))
                }
            }

            scoreAndSearch(root)
            return Pair(bestNode, bestObserved)
        }

        // ==========================================
        // PHASE D: clickElement()
        // ==========================================

        fun clickElement(target: String): ActionExecutionDetails {
            val service = instance ?: return ActionExecutionDetails(
                success = false,
                methodUsed = "NONE",
                target = target,
                evidence = "Accessibility Service is not connected.",
                error = "Service disconnected"
            )

            val (node, observed) = findElement(target)
            if (node != null) {
                // 1. Direct Click on Node
                if (node.isClickable) {
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        return ActionExecutionDetails(
                            success = true,
                            methodUsed = "ACTION_CLICK",
                            target = target,
                            matchedNode = observed,
                            evidence = "Successfully performed ACTION_CLICK on node: '${observed?.text?.ifEmpty { observed?.contentDescription } ?: ""}' (ID: ${observed?.viewId ?: "none"})"
                        )
                    }
                }

                // 2. Walk up parent hierarchy
                var parent = node.parent
                var depth = 0
                while (parent != null && depth < 6) {
                    if (parent.isClickable) {
                        val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (parentClicked) {
                            return ActionExecutionDetails(
                                success = true,
                                methodUsed = "PARENT_ACTION_CLICK",
                                target = target,
                                matchedNode = observed,
                                evidence = "Successfully clicked parent container (depth $depth) of '$target'"
                            )
                        }
                    }
                    parent = parent.parent
                    depth++
                }

                // 3. Fallback: Coordinate Gesture Tap at node's screen center
                if (observed != null && !observed.bounds.isEmpty) {
                    val centerX = observed.bounds.centerX().toFloat()
                    val centerY = observed.bounds.centerY().toFloat()
                    val gestureOk = performSwipeGesture(centerX, centerY, centerX, centerY, 50)
                    if (gestureOk) {
                        return ActionExecutionDetails(
                            success = true,
                            methodUsed = "GESTURE_TAP_FALLBACK",
                            target = target,
                            matchedNode = observed,
                            evidence = "Dispatched coordinate tap gesture at center ($centerX, $centerY) for '$target'"
                        )
                    }
                }
            }

            // 4. Try find by text list fallback
            val root = service.rootInActiveWindow
            if (root != null) {
                val list = root.findAccessibilityNodeInfosByText(target)
                for (item in list) {
                    if (item.isClickable && item.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return ActionExecutionDetails(
                            success = true,
                            methodUsed = "FIND_BY_TEXT_CLICK",
                            target = target,
                            evidence = "Clicked node via findAccessibilityNodeInfosByText for '$target'"
                        )
                    }
                }
            }

            return ActionExecutionDetails(
                success = false,
                methodUsed = "CLICK_FAILED",
                target = target,
                matchedNode = observed,
                evidence = "Element '$target' found in tree but click action could not be performed.",
                error = "Target element not interactive"
            )
        }

        // ==========================================
        // PHASE E: setText()
        // ==========================================

        fun typeText(targetQuery: String?, text: String, context: Context): ActionExecutionDetails {
            val service = instance ?: return ActionExecutionDetails(
                success = false,
                methodUsed = "NONE",
                target = targetQuery ?: "ACTIVE_INPUT",
                evidence = "Accessibility Service is not connected.",
                error = "Service disconnected"
            )

            val root = service.rootInActiveWindow ?: return ActionExecutionDetails(
                success = false,
                methodUsed = "NONE",
                target = targetQuery ?: "ACTIVE_INPUT",
                evidence = "No active window available to type.",
                error = "Root window null"
            )

            var targetNode: AccessibilityNodeInfo? = null
            var matchedObserved: ObservedNode? = null

            // 1. Search for specific field if target query is given
            if (!targetQuery.isNullOrBlank()) {
                val (foundNode, foundObserved) = findElement(targetQuery)
                if (foundNode != null && (foundNode.isEditable || foundNode.className?.contains("EditText", ignoreCase = true) == true)) {
                    targetNode = foundNode
                    matchedObserved = foundObserved
                }
            }

            // 2. If not found, look for currently focused node
            if (targetNode == null) {
                targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }

            // 3. If still not found, search the tree for first editable node
            if (targetNode == null) {
                fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (node == null) return null
                    if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) {
                        return node
                    }
                    for (i in 0 until node.childCount) {
                        val res = findFirstEditable(node.getChild(i))
                        if (res != null) return res
                    }
                    return null
                }
                targetNode = findFirstEditable(root)
            }

            if (targetNode != null) {
                // Focus the node
                targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                // Perform ACTION_SET_TEXT
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val setOk = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (setOk) {
                    return ActionExecutionDetails(
                        success = true,
                        methodUsed = "ACTION_SET_TEXT",
                        target = targetQuery ?: "EDITABLE_FIELD",
                        matchedNode = matchedObserved,
                        evidence = "Successfully typed \"$text\" into editable field using ACTION_SET_TEXT."
                    )
                }

                // Fallback: Clipboard Paste
                val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipManager != null) {
                    val clip = ClipData.newPlainText("JARVIS_INPUT", text)
                    clipManager.setPrimaryClip(clip)
                    val pasteOk = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (pasteOk) {
                        return ActionExecutionDetails(
                            success = true,
                            methodUsed = "ACTION_PASTE",
                            target = targetQuery ?: "EDITABLE_FIELD",
                            matchedNode = matchedObserved,
                            evidence = "Pasted \"$text\" into field via ACTION_PASTE."
                        )
                    }
                }
            }

            // Universal clipboard backup
            val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipManager?.setPrimaryClip(ClipData.newPlainText("JARVIS_INPUT", text))

            return ActionExecutionDetails(
                success = true,
                methodUsed = "CLIPBOARD_STAGED",
                target = targetQuery ?: "INPUT",
                evidence = "No focused editable node detected in active window. Staged \"$text\" to clipboard for instant pasting."
            )
        }

        // ==========================================
        // PHASE F: scrollScreen()
        // ==========================================

        fun scrollScreen(forward: Boolean = true): ActionExecutionDetails {
            val service = instance ?: return ActionExecutionDetails(
                success = false,
                methodUsed = "NONE",
                target = "SCREEN",
                evidence = "Accessibility Service is not connected.",
                error = "Service disconnected"
            )

            val root = service.rootInActiveWindow ?: return ActionExecutionDetails(
                success = false,
                methodUsed = "NONE",
                target = "SCREEN",
                evidence = "No active window to scroll.",
                error = "Root window null"
            )

            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

            fun performScroll(node: AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.isScrollable && node.performAction(action)) {
                    return true
                }
                for (i in 0 until node.childCount) {
                    if (performScroll(node.getChild(i))) return true
                }
                return false
            }

            val scrolled = performScroll(root)
            if (scrolled) {
                return ActionExecutionDetails(
                    success = true,
                    methodUsed = if (forward) "ACTION_SCROLL_FORWARD" else "ACTION_SCROLL_BACKWARD",
                    target = "SCROLLABLE_CONTAINER",
                    evidence = "Successfully scrolled ${if (forward) "down/forward" else "up/backward"} on active container."
                )
            }

            // Fallback: Gesture Swipe
            val metrics = service.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()
            val startY = if (forward) h * 0.75f else h * 0.25f
            val endY = if (forward) h * 0.25f else h * 0.75f
            val gestureOk = performSwipeGesture(w / 2f, startY, w / 2f, endY, 300)

            return if (gestureOk) {
                ActionExecutionDetails(
                    success = true,
                    methodUsed = "GESTURE_SWIPE_SCROLL",
                    target = "SCREEN_COORDINATES",
                    evidence = "Dispatched swipe gesture (${if (forward) "UP" else "DOWN"}) to scroll content."
                )
            } else {
                ActionExecutionDetails(
                    success = false,
                    methodUsed = "SCROLL_FAILED",
                    target = "SCREEN",
                    evidence = "No scrollable container found and swipe gesture could not be dispatched.",
                    error = "Scroll failed"
                )
            }
        }

        // ==========================================
        // System Navigation Actions
        // ==========================================

        fun pressBack(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun pressHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun performSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
            val service = instance ?: return false
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }
    }
}
