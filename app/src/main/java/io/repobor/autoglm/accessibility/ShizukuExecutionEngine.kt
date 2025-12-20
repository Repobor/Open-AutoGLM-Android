package io.repobor.autoglm.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Shizuku-based implementation of ExecutionEngine.
 */
class ShizukuExecutionEngine(
    private val context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int
) : ExecutionEngine {

    companion object {
        private const val TAG = "ShizukuExecutionEngine"
    }

    /**
     * Ensure UserService is connected before operations.
     * Only waits once per execution flow to avoid repeated delays.
     */
    private suspend fun ensureUserServiceConnected(): Boolean {
        if (!ShizukuHelper.isUserServiceConnected()) {
            Log.d(TAG, "UserService not connected, waiting for connection...")
            if (!ShizukuHelper.waitForUserService()) {
                Log.w(TAG, "UserService connection timeout")
                return false
            }
        }
        return true
    }

    override suspend fun captureScreenshot(): Screenshot {
        // Wait for UserService to be connected before attempting screenshot
        if (!ensureUserServiceConnected()) {
            return createBlackScreenshot()
        }

        val result = ShizukuHelper.captureScreenshot()

        return if (result.isSuccess) {
            val base64Data = result.getOrNull() ?: ""
            Screenshot(
                base64Data = base64Data,
                width = screenWidth,
                height = screenHeight,
                isSensitive = false
            )
        } else {
            Log.e(TAG, "Failed to capture screenshot", result.exceptionOrNull())
            // Return black placeholder
            createBlackScreenshot()
        }
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        // Wait for UserService to be connected
        if (!ensureUserServiceConnected()) {
            Log.w(TAG, "UserService connection failed, tap cancelled")
            return false
        }

        val result = ShizukuHelper.tap(x, y)
        if (result.isFailure) {
            Log.e(TAG, "Failed to tap at ($x, $y)", result.exceptionOrNull())
        }
        return result.isSuccess
    }

    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): Boolean {
        // Wait for UserService to be connected
        if (!ensureUserServiceConnected()) {
            Log.w(TAG, "UserService connection failed, swipe cancelled")
            return false
        }

        val result = ShizukuHelper.swipe(startX, startY, endX, endY, duration)
        if (result.isFailure) {
            Log.e(TAG, "Failed to swipe", result.exceptionOrNull())
        }
        return result.isSuccess
    }

    override suspend fun longPress(x: Int, y: Int, duration: Long): Boolean {
        // Wait for UserService to be connected
        if (!ensureUserServiceConnected()) {
            Log.w(TAG, "UserService connection failed, long press cancelled")
            return false
        }

        val result = ShizukuHelper.longPress(x, y, duration)
        if (result.isFailure) {
            Log.e(TAG, "Failed to long press at ($x, $y)", result.exceptionOrNull())
        }
        return result.isSuccess
    }

    override suspend fun typeText(text: String): Boolean {
        // Check if text contains non-ASCII characters (e.g., Chinese)
        // adb input text does not support non-ASCII characters
        val hasNonAscii = text.any { it.code > 127 }

        return if (hasNonAscii) {
            // Fallback to accessibility service for non-ASCII text
            Log.d(TAG, "Text contains non-ASCII characters, falling back to accessibility service")
            val accessibilityEngine = AccessibilityExecutionEngine(context, screenWidth, screenHeight, "zh")
            accessibilityEngine.typeText(text)
        } else {
            // Wait for UserService to be connected
            if (!ensureUserServiceConnected()) {
                Log.w(TAG, "UserService connection failed, type text cancelled")
                return false
            }

            // Use Shizuku for ASCII text
            val result = ShizukuHelper.typeText(text)
            if (result.isFailure) {
                Log.e(TAG, "Failed to type text via Shizuku", result.exceptionOrNull())
            }
            result.isSuccess
        }
    }

    override suspend fun pressBack(): Boolean {
        // Wait for UserService to be connected
        if (!ensureUserServiceConnected()) {
            Log.w(TAG, "UserService connection failed, press back cancelled")
            return false
        }

        val result = ShizukuHelper.pressBack()
        if (result.isFailure) {
            Log.e(TAG, "Failed to press back", result.exceptionOrNull())
        }
        return result.isSuccess
    }

    override suspend fun pressHome(): Boolean {
        // Wait for UserService to be connected
        if (!ensureUserServiceConnected()) {
            Log.w(TAG, "UserService connection failed, press home cancelled")
            return false
        }

        val result = ShizukuHelper.pressHome()
        if (result.isFailure) {
            Log.e(TAG, "Failed to press home", result.exceptionOrNull())
        }
        return result.isSuccess
    }

    override suspend fun launchApp(packageName: String): Boolean {
        // Wait for UserService to be connected
        if (!ensureUserServiceConnected()) {
            Log.w(TAG, "UserService connection failed, launch app cancelled")
            return false
        }

        val result = ShizukuHelper.launchApp(packageName)
        if (result.isFailure) {
            Log.e(TAG, "Failed to launch app: $packageName", result.exceptionOrNull())
        }
        return result.isSuccess
    }

    override suspend fun getCurrentAppName(): String {
        // Wait for UserService to be connected
        if (!ensureUserServiceConnected()) {
            Log.w(TAG, "UserService connection failed, returning Unknown app name")
            return "Unknown"
        }

        return ShizukuHelper.getCurrentAppName()
    }

    override fun isAvailable(): Boolean {
        val shizukuRunning = ShizukuHelper.isShizukuRunning()
        val hasPermission = ShizukuHelper.checkShizukuPermission(context)
        val userServiceConnected = ShizukuHelper.isUserServiceConnected()

        val result = shizukuRunning && hasPermission

        if (!result) {
            Log.d(TAG, "Shizuku not available - Running:$shizukuRunning Permission:$hasPermission UserService:$userServiceConnected")
        }

        // Return true if Shizuku is running and we have permission
        // The service will wait for UserService connection when needed
        return result
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
