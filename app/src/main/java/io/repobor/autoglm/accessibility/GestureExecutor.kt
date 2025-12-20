package io.repobor.autoglm.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Executes gesture actions using AccessibilityService.
 * Requires Android N (API 24) or higher.
 */
class GestureExecutor(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "GestureExecutor"

        // Default durations in milliseconds
        private const val TAP_DURATION = 100L
        private const val SWIPE_DURATION = 300L
        private const val LONG_PRESS_DURATION = 1000L
        private const val DOUBLE_TAP_INTERVAL = 100L
    }

    /**
     * Perform a tap gesture at specified coordinates.
     *
     * @param x X coordinate in pixels
     * @param y Y coordinate in pixels
     * @param duration Tap duration in milliseconds (default: 100ms)
     * @return true if gesture was dispatched successfully
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun tap(x: Int, y: Int, duration: Long = TAP_DURATION): Boolean {
        Log.d(TAG, "Tap at ($x, $y)")

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(path, 0, duration)
        )

        return dispatchGesture(gestureBuilder.build())
    }

    /**
     * Perform a swipe gesture from start to end coordinates.
     *
     * @param x1 Start X coordinate
     * @param y1 Start Y coordinate
     * @param x2 End X coordinate
     * @param y2 End Y coordinate
     * @param duration Swipe duration in milliseconds (default: 300ms)
     * @return true if gesture was dispatched successfully
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        duration: Long = SWIPE_DURATION
    ): Boolean {
        Log.d(TAG, "Swipe from ($x1, $y1) to ($x2, $y2)")

        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(path, 0, duration)
        )

        return dispatchGesture(gestureBuilder.build())
    }

    /**
     * Perform a long press gesture at specified coordinates.
     *
     * @param x X coordinate in pixels
     * @param y Y coordinate in pixels
     * @param duration Long press duration in milliseconds (default: 1000ms)
     * @return true if gesture was dispatched successfully
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun longPress(x: Int, y: Int, duration: Long = LONG_PRESS_DURATION): Boolean {
        Log.d(TAG, "Long press at ($x, $y) for ${duration}ms")

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(path, 0, duration)
        )

        return dispatchGesture(gestureBuilder.build())
    }

    /**
     * Perform a double tap gesture at specified coordinates.
     *
     * @param x X coordinate in pixels
     * @param y Y coordinate in pixels
     * @return true if both taps were dispatched successfully
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun doubleTap(x: Int, y: Int): Boolean {
        Log.d(TAG, "Double tap at ($x, $y)")

        // First tap
        val success1 = tap(x, y, TAP_DURATION)
        if (!success1) return false

        // Wait briefly between taps
        kotlinx.coroutines.delay(DOUBLE_TAP_INTERVAL)

        // Second tap
        return tap(x, y, TAP_DURATION)
    }

    /**
     * Perform a scroll gesture (vertical swipe).
     *
     * @param startX Start X coordinate
     * @param startY Start Y coordinate
     * @param distance Distance to scroll (positive = down, negative = up)
     * @param duration Scroll duration in milliseconds (default: 300ms)
     * @return true if gesture was dispatched successfully
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun scroll(
        startX: Int,
        startY: Int,
        distance: Int,
        duration: Long = SWIPE_DURATION
    ): Boolean {
        val endY = startY + distance
        return swipe(startX, startY, startX, endY, duration)
    }

    /**
     * Perform a pinch gesture (zoom in/out).
     * Requires Android O (API 26) or higher for multi-touch.
     *
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param startDistance Initial distance between fingers
     * @param endDistance Final distance between fingers
     * @param duration Pinch duration in milliseconds (default: 300ms)
     * @return true if gesture was dispatched successfully
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun pinch(
        centerX: Int,
        centerY: Int,
        startDistance: Int,
        endDistance: Int,
        duration: Long = SWIPE_DURATION
    ): Boolean {
        Log.d(TAG, "Pinch at ($centerX, $centerY) from $startDistance to $endDistance")

        // Calculate start and end points for two fingers
        val startHalfDist = startDistance / 2
        val endHalfDist = endDistance / 2

        val path1 = Path().apply {
            moveTo(centerX.toFloat() - startHalfDist, centerY.toFloat())
            lineTo(centerX.toFloat() - endHalfDist, centerY.toFloat())
        }

        val path2 = Path().apply {
            moveTo(centerX.toFloat() + startHalfDist, centerY.toFloat())
            lineTo(centerX.toFloat() + endHalfDist, centerY.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path2, 0, duration))

        return dispatchGesture(gestureBuilder.build())
    }

    /**
     * Dispatch a gesture description and wait for result.
     *
     * @param gesture The GestureDescription to dispatch
     * @return true if gesture was dispatched successfully
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun dispatchGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                continuation.resumeWithException(
                    UnsupportedOperationException("Gesture dispatch requires Android N (API 24) or higher")
                )
                return@suspendCancellableCoroutine
            }

            try {
                service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            Log.d(TAG, "Gesture completed successfully")
                            continuation.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            Log.w(TAG, "Gesture was cancelled")
                            continuation.resume(false)
                        }
                    },
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching gesture", e)
                continuation.resumeWithException(e)
            }
        }

    /**
     * Perform global action (Back, Home, etc.).
     *
     * @param action The global action to perform
     * @return true if action was performed successfully
     */
    fun performGlobalAction(action: Int): Boolean {
        return try {
            val success = service.performGlobalAction(action)
            if (success) {
                Log.d(TAG, "Global action $action performed successfully")
            } else {
                Log.w(TAG, "Failed to perform global action $action")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error performing global action $action", e)
            false
        }
    }

    /**
     * Press the Back button.
     */
    fun pressBack(): Boolean {
        Log.d(TAG, "Pressing Back button")
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /**
     * Press the Home button.
     */
    fun pressHome(): Boolean {
        Log.d(TAG, "Pressing Home button")
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * Open recent apps.
     */
    fun openRecents(): Boolean {
        Log.d(TAG, "Opening recent apps")
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    /**
     * Open notifications.
     */
    fun openNotifications(): Boolean {
        Log.d(TAG, "Opening notifications")
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * Open quick settings.
     */
    fun openQuickSettings(): Boolean {
        Log.d(TAG, "Opening quick settings")
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }
}
