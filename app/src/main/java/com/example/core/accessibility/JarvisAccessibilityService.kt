package com.example.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VisibleElement(
    val text: String,
    val className: String,
    val viewId: String? = null,
    val isClickable: Boolean = false,
    val bounds: Rect = Rect()
)

data class ScreenContext(
    val currentApp: String,
    val visibleElements: List<VisibleElement>,
    val timestamp: Long = System.currentTimeMillis()
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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

        fun getScreenContext(): ScreenContext? {
            val service = instance ?: return null
            val rootNode = service.rootInActiveWindow ?: return null
            val elements = mutableListOf<VisibleElement>()

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val text = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim()
                val rect = Rect()
                node.getBoundsInScreen(rect)

                if (!text.isNullOrBlank()) {
                    elements.add(
                        VisibleElement(
                            text = text,
                            className = node.className?.toString() ?: "View",
                            viewId = node.viewIdResourceName,
                            isClickable = node.isClickable,
                            bounds = rect
                        )
                    )
                }
                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i))
                }
            }

            traverse(rootNode)
            return ScreenContext(
                currentApp = _currentForegroundApp.value,
                visibleElements = elements.take(50) // Relevant compact context
            )
        }

        fun tapByText(targetText: String): Boolean {
            val service = instance ?: return false
            val rootNode = service.rootInActiveWindow ?: return false
            val matchedNodes = rootNode.findAccessibilityNodeInfosByText(targetText)
            for (node in matchedNodes) {
                if (node.isClickable) {
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                // Try parent if clickable
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    parent = parent.parent
                }
            }
            return false
        }

        fun scrollScreen(forward: Boolean = true): Boolean {
            val service = instance ?: return false
            val rootNode = service.rootInActiveWindow ?: return false
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            return performScrollOnNode(rootNode, action)
        }

        private fun performScrollOnNode(node: AccessibilityNodeInfo?, action: Int): Boolean {
            if (node == null) return false
            if (node.isScrollable && node.performAction(action)) {
                return true
            }
            for (i in 0 until node.childCount) {
                if (performScrollOnNode(node.getChild(i), action)) return true
            }
            return false
        }

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
