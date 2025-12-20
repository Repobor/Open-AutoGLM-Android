package io.repobor.autoglm.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import io.repobor.autoglm.config.AppPackages
import java.io.ByteArrayOutputStream

/**
 * Accessibility Service-based implementation of ExecutionEngine.
 */
class AccessibilityExecutionEngine(
    private val context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val language: String
) : ExecutionEngine {

    companion object {
        private const val TAG = "AccessibilityExecutionEngine"
    }

    private val service: AutoGLMAccessibilityService?
        get() = AutoGLMAccessibilityService.getInstance()

    override suspend fun captureScreenshot(): Screenshot {
        val svc = service
        if (svc == null) {
            Log.e(TAG, "Accessibility service is not available")
            return createBlackScreenshot()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                ScreenshotCapture.captureScreenshot(svc)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture screenshot", e)
                createBlackScreenshot()
            }
        } else {
            Log.w(TAG, "Screenshot requires Android R (API 30) or higher")
            createBlackScreenshot()
        }
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        val svc = service
        if (svc == null) {
            Log.e(TAG, "Accessibility service is not available")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            svc.gestureExecutor.tap(x, y)
        } else {
            Log.e(TAG, "Gestures require Android N (API 24) or higher")
            false
        }
    }

    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): Boolean {
        val svc = service
        if (svc == null) {
            Log.e(TAG, "Accessibility service is not available")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            svc.gestureExecutor.swipe(startX, startY, endX, endY, duration)
        } else {
            Log.e(TAG, "Gestures require Android N (API 24) or higher")
            false
        }
    }

    override suspend fun longPress(x: Int, y: Int, duration: Long): Boolean {
        val svc = service
        if (svc == null) {
            Log.e(TAG, "Accessibility service is not available")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            svc.gestureExecutor.longPress(x, y, duration)
        } else {
            Log.e(TAG, "Gestures require Android N (API 24) or higher")
            false
        }
    }

    override suspend fun typeText(text: String): Boolean {
        val svc = service
        if (svc == null) {
            Log.e(TAG, "Accessibility service is not available")
            return false
        }

        // Find focused text field and set text
        val rootNode = svc.rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)

        return if (focusedNode != null) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            val success = focusedNode.performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )
            success
        } else {
            Log.e(TAG, "No focused text field found")
            false
        }
    }

    override suspend fun pressBack(): Boolean {
        val svc = service ?: return false
        return svc.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        )
    }

    override suspend fun pressHome(): Boolean {
        val svc = service ?: return false
        return svc.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
        )
    }

    override suspend fun launchApp(packageName: String): Boolean {
        val svc = service
        if (svc == null) {
            Log.e(TAG, "Accessibility service is not available")
            return false
        }

        return svc.appLauncher.launchApp(packageName)
    }

    override suspend fun getCurrentAppName(): String {
        val svc = service
        if (svc == null) {
            Log.e(TAG, "Accessibility service is not available")
            return "Unknown"
        }

        return svc.getCurrentAppName() ?: "Unknown"
    }

    override fun isAvailable(): Boolean {
        return AutoGLMAccessibilityService.isRunning()
    }

    /**
     * Create a black placeholder screenshot when screenshot capture fails.
     */
    private fun createBlackScreenshot(): Screenshot {
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLACK)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val base64Data = Base64.encodeToString(
            outputStream.toByteArray(),
            Base64.NO_WRAP
        )

        bitmap.recycle()
        outputStream.close()

        return Screenshot(
            base64Data = base64Data,
            width = screenWidth,
            height = screenHeight,
            isSensitive = false
        )
    }
}
