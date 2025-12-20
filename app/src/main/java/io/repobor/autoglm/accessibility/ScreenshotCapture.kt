package io.repobor.autoglm.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.graphics.createBitmap

/**
 * Screenshot data class containing image information.
 */
data class Screenshot(
    val base64Data: String,
    val width: Int,
    val height: Int,
    val isSensitive: Boolean = false
)

/**
 * Handles screenshot capture using AccessibilityService API.
 * Requires Android R (API 30) or higher.
 */
object ScreenshotCapture {

    private const val TAG = "ScreenshotCapture"
    private const val SCREENSHOT_QUALITY = 50 // JPEG quality (0-100). Aggressive compression to avoid Binder buffer overflow in Shizuku mode
    private val SCREENSHOT_FORMAT = Bitmap.CompressFormat.JPEG

    /**
     * Capture a screenshot using AccessibilityService.
     * Requires Android R (API 30) or higher.
     *
     * @param service The AccessibilityService instance
     * @return Screenshot object containing base64 image data and dimensions
     * @throws UnsupportedOperationException if Android version is below R
     * @throws Exception if screenshot capture fails
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshot(service: AccessibilityService): Screenshot = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            continuation.resumeWithException(
                UnsupportedOperationException("Screenshot capture requires Android R (API 30) or higher")
            )
            return@suspendCancellableCoroutine
        }

        try {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        try {
                            val hardwareBitmap = screenshotResult.hardwareBuffer.let { buffer ->
                                Bitmap.wrapHardwareBuffer(buffer, screenshotResult.colorSpace)
                                    ?: throw IllegalStateException("Failed to wrap hardware buffer")
                            }

                            // Convert hardware bitmap to software bitmap for processing
                            val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                ?: throw IllegalStateException("Failed to copy bitmap")

                            // Convert bitmap to base64 string
                            val base64Data = bitmapToBase64(bitmap)

                            // Get dimensions
                            val width = bitmap.width
                            val height = bitmap.height

                            // Check if content is sensitive (from screenshot result)
                            val isSensitive = screenshotResult.timestamp > 0

                            // Clean up
                            screenshotResult.hardwareBuffer.close()
                            bitmap.recycle()

                            Log.d(TAG, "Screenshot captured successfully: ${width}x${height}")

                            val screenshot = Screenshot(
                                base64Data = base64Data,
                                width = width,
                                height = height,
                                isSensitive = isSensitive
                            )

                            continuation.resume(screenshot)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot", e)
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val errorMessage = when (errorCode) {
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR ->
                                "Internal error occurred"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                                "Screenshot interval too short"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                                "No accessibility access"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
                                "Invalid display"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW ->
                                "Invalid window"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                                "Secure window cannot be captured"
                            else -> "Unknown error: $errorCode"
                        }
                        Log.e(TAG, "Screenshot failed: $errorMessage")
                        continuation.resumeWithException(
                            Exception("Screenshot capture failed: $errorMessage")
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating screenshot", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Convert Bitmap to base64 encoded string.
     *
     * @param bitmap The bitmap to convert
     * @return Base64 encoded string (without data URI prefix)
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(SCREENSHOT_FORMAT, SCREENSHOT_QUALITY, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Get data URI for base64 image (for use in OpenAI API).
     *
     * @param base64Data Base64 encoded image data
     * @param format Image format (default: PNG)
     * @return Data URI string like "data:image/png;base64,..."
     */
    fun getDataUri(base64Data: String, format: String = "png"): String {
        return "data:image/$format;base64,$base64Data"
    }

    /**
     * Capture screenshot and return as data URI.
     *
     * @param service The AccessibilityService instance
     * @return Data URI string for direct use in API calls
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshotAsDataUri(service: AccessibilityService): String {
        val screenshot = captureScreenshot(service)
        return getDataUri(screenshot.base64Data)
    }

    /**
     * Create a fallback screenshot (black image) when capture fails.
     *
     * @param width Image width (default: 1080)
     * @param height Image height (default: 2400)
     * @return Screenshot with black image
     */
    fun createFallbackScreenshot(width: Int = 1080, height: Int = 2400): Screenshot {
        val bitmap = createBitmap(width, height)
        bitmap.eraseColor(android.graphics.Color.BLACK)
        val base64Data = bitmapToBase64(bitmap)
        bitmap.recycle()

        Log.w(TAG, "Created fallback screenshot: ${width}x${height}")

        return Screenshot(
            base64Data = base64Data,
            width = width,
            height = height,
            isSensitive = false
        )
    }
}
