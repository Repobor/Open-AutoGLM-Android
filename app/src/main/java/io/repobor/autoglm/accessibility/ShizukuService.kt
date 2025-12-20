package io.repobor.autoglm.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.Keep
import io.repobor.autoglm.IShizukuService
import java.io.ByteArrayOutputStream

/**
 * Shizuku User Service
 * Runs in Shizuku's privileged process to perform system operations.
 *
 * This service is invoked from ShizukuHelper via IPC.
 * All operations here run with elevated privileges through Shizuku.
 */
class ShizukuService : IShizukuService.Stub {

    companion object {
        private const val TAG = "ShizukuService"
    }

    /**
     * Constructor is required.
     */
    constructor() {
        Log.i(TAG, "ShizukuService constructor called")
    }

    /**
     * Constructor with Context. This is only available from Shizuku API v13.
     */
    @Keep
    constructor(context: Context) {
        Log.i(TAG, "ShizukuService constructor with Context: $context")
    }

    /**
     * Reserved destroy method
     */
    override fun destroy() {
        Log.i(TAG, "ShizukuService destroy called")
        System.exit(0)
    }

    /**
     * Exit method
     */
    override fun exit() {
        destroy()
    }

    /**
     * Capture screenshot via Shizuku with in-service compression.
     *
     * Strategy: Compress screenshot to JPEG inside the service to avoid Binder overflow.
     * - screencap -p outputs PNG bytes
     * - Decode PNG -> Bitmap
     * - Compress Bitmap to JPEG (quality 40-60) to reduce size significantly
     * - Return compressed JPEG bytes (typically < 1MB, well under Binder limit of ~1MB)
     * - Client receives JPEG, can further decode/compress if needed
     *
     * Binder transaction limit: ~1MB
     * Typical screenshot sizes:
     * - Raw PNG: 1-2 MB (causes Binder overflow)
     * - Raw RGBA: 9+ MB (definitely causes overflow)
     * - Compressed JPEG (quality 50): 400-600 KB (safe)
     *
     * @return JPEG compressed screenshot bytes
     */
    override fun captureScreenshot(): ByteArray {
        return try {
            Log.d(TAG, "Capturing screenshot via Shizuku with in-service compression")
            val pngBytes = ShellExecutor.captureScreenshotPng()

            if (pngBytes == null || pngBytes.isEmpty()) {
                Log.w(TAG, "Screenshot capture returned no data")
                return ByteArray(0)
            }

            Log.d(TAG, "Raw PNG size: ${String.format("%.2f", pngBytes.size / 1024.0)} KB")

            // Decode PNG to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode PNG to Bitmap, returning empty")
                return ByteArray(0)
            }

            // Compress to JPEG (quality 50) for significant size reduction
            // Quality range: 0-100, where 50 is good balance between size and quality
            val jpegStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, jpegStream)
            val jpegBytes = jpegStream.toByteArray()
            jpegStream.close()
            bitmap.recycle()

            Log.d(TAG, "Compressed JPEG size: ${String.format("%.2f", jpegBytes.size / 1024.0)} KB (reduction: ${String.format("%.1f", (1 - jpegBytes.size.toDouble() / pngBytes.size) * 100)}%)")

            if (jpegBytes.size > 1_000_000) {
                Log.w(TAG, "JPEG size is ${String.format("%.2f", jpegBytes.size / 1024.0 / 1024.0)} MB, approaching Binder limit. Consider quality reduction.")
            }

            jpegBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            ByteArray(0)
        }
    }

    /**
     * Tap at coordinates via Shizuku
     */
    override fun tap(x: Int, y: Int): Boolean {
        return try {
            Log.d(TAG, "Tapping at ($x, $y)")
            ShellExecutor.tap(x, y)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to tap at ($x, $y)", e)
            false
        }
    }

    /**
     * Swipe gesture via Shizuku
     */
    override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        return try {
            Log.d(TAG, "Swiping from ($startX, $startY) to ($endX, $endY) for ${duration}ms")
            ShellExecutor.swipe(startX, startY, endX, endY, duration)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to swipe", e)
            false
        }
    }

    /**
     * Long press at coordinates via Shizuku
     */
    override fun longPress(x: Int, y: Int, duration: Long): Boolean {
        return try {
            Log.d(TAG, "Long pressing at ($x, $y) for ${duration}ms")
            ShellExecutor.longPress(x, y, duration)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to long press at ($x, $y)", e)
            false
        }
    }

    /**
     * Type text via Shizuku
     * Note: input text command doesn't support non-ASCII characters.
     * For Chinese/other non-ASCII text, client should use accessibility service instead.
     */
    override fun typeText(text: String): Boolean {
        return try {
            Log.d(TAG, "Typing text via Shizuku")
            ShellExecutor.typeText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to type text", e)
            false
        }
    }

    /**
     * Press back button via Shizuku
     */
    override fun pressBack(): Boolean {
        return try {
            Log.d(TAG, "Pressing back button")
            ShellExecutor.pressBack()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press back", e)
            false
        }
    }

    /**
     * Press home button via Shizuku
     */
    override fun pressHome(): Boolean {
        return try {
            Log.d(TAG, "Pressing home button")
            ShellExecutor.pressHome()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press home", e)
            false
        }
    }

    /**
     * Launch app by package name via Shizuku
     */
    override fun launchApp(packageName: String): Boolean {
        return try {
            Log.d(TAG, "Launching app: $packageName")
            ShellExecutor.launchApp(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $packageName", e)
            false
        }
    }

    /**
     * Enable accessibility service via Shizuku
     * Returns empty string on success, error message on failure
     */
    override fun enableAccessibilityService(serviceComponentName: String): String {
        return try {
            Log.d(TAG, "Enabling accessibility service: $serviceComponentName")
            ShellExecutor.enableAccessibilityService(serviceComponentName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service: $serviceComponentName", e)
            e.message ?: "Unknown error"
        }
    }

    /**
     * Get the display name of the currently active app
     */
    override fun getCurrentAppName(): String {
        return try {
            Log.d(TAG, "Getting current app name")
            val packageName = ShellExecutor.getCurrentAppPackageName()

            if (packageName.isEmpty() || packageName == "Unknown") {
                Log.w(TAG, "Could not get package name, returning Unknown")
                return "Unknown"
            }

            // Map package name to display name using AppPackages
            val displayName = io.repobor.autoglm.config.AppPackages.getAppName(packageName) ?: packageName
            Log.d(TAG, "Current app: $packageName -> $displayName")

            // Ensure we never return null
            displayName.ifEmpty { packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current app name", e)
            "Unknown"
        }
    }
}
