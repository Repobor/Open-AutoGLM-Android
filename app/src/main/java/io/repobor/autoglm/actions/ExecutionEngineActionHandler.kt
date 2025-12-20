package io.repobor.autoglm.actions

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.repobor.autoglm.accessibility.ExecutionEngine
import io.repobor.autoglm.config.AppPackages
import kotlinx.coroutines.delay

/**
 * Action handler that works with ExecutionEngine interface.
 * Supports both Accessibility and Shizuku execution modes.
 */
class ExecutionEngineActionHandler(
    private val executionEngine: ExecutionEngine,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val language: String = "zh"
) {

    companion object {
        private const val TAG = "ExecutionEngineActionHandler"
        private const val ACTION_DELAY_MS = 200L
        private const val COORDINATE_MAX = 1000 // Model outputs coordinates in 0-999 range
    }

    /**
     * Execute an action based on parsed action map.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun executeAction(action: Map<String, Any>): ActionResult {
        val actionType = action["action"] as? String
            ?: return ActionResult(false, false, "No action type specified")

        Log.i(TAG, "Executing action: $actionType")

        // Add delay before action to prevent race conditions
        delay(ACTION_DELAY_MS)

        return when (actionType.lowercase()) {
            "tap" -> executeTap(action)
            "type", "type_name" -> executeType(action)
            "swipe" -> executeSwipe(action)
            "launch" -> executeLaunch(action)
            "back" -> executeBack()
            "home" -> executeHome()
            "long press" -> executeLongPress(action)
            "double tap" -> executeDoubleTap(action)
            "wait" -> executeWait(action)
            "finish" -> executeFinish(action)
            "note" -> executeNote(action)
            else -> ActionResult(false, false, "Unknown action: $actionType")
        }
    }

    /**
     * Execute a tap action.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun executeTap(action: Map<String, Any>): ActionResult {
        val element = action["element"] as? List<*>
            ?: return ActionResult(false, false, "Missing element coordinates")

        if (element.size < 2) {
            return ActionResult(false, false, "Invalid element coordinates")
        }

        val normalizedX = (element[0] as? Number)?.toInt() ?: 0
        val normalizedY = (element[1] as? Number)?.toInt() ?: 0

        // Convert normalized coordinates to pixels
        val x = (normalizedX / 1000.0 * screenWidth).toInt()
        val y = (normalizedY / 1000.0 * screenHeight).toInt()

        Log.d(TAG, "Tap: normalized ($normalizedX, $normalizedY) -> pixels ($x, $y)")

        val success = executionEngine.tap(x, y)
        val message = action["message"] as? String
        val requiresConfirmation = message != null

        return ActionResult(success, false, message, requiresConfirmation)
    }

    /**
     * Execute a type action.
     */
    private suspend fun executeType(action: Map<String, Any>): ActionResult {
        val text = action["text"] as? String
            ?: return ActionResult(false, false, "Missing text to type")

        Log.d(TAG, "Type: $text")

        val success = executionEngine.typeText(text)
        return ActionResult(success, false, null)
    }

    /**
     * Execute a swipe action.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun executeSwipe(action: Map<String, Any>): ActionResult {
        val start = action["start"] as? List<*>
        val end = action["end"] as? List<*>

        if (start == null || end == null || start.size < 2 || end.size < 2) {
            return ActionResult(false, false, "Invalid swipe coordinates")
        }

        val startX = ((start[0] as? Number)?.toInt() ?: 0) * screenWidth / COORDINATE_MAX
        val startY = ((start[1] as? Number)?.toInt() ?: 0) * screenHeight / COORDINATE_MAX
        val endX = ((end[0] as? Number)?.toInt() ?: 0) * screenWidth / COORDINATE_MAX
        val endY = ((end[1] as? Number)?.toInt() ?: 0) * screenHeight / COORDINATE_MAX

        Log.d(TAG, "Swipe: ($startX, $startY) -> ($endX, $endY)")

        val success = executionEngine.swipe(startX, startY, endX, endY)
        return ActionResult(success, false, null)
    }

    /**
     * Execute a long press action.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun executeLongPress(action: Map<String, Any>): ActionResult {
        val element = action["element"] as? List<*>
            ?: return ActionResult(false, false, "Missing element coordinates")

        if (element.size < 2) {
            return ActionResult(false, false, "Invalid element coordinates")
        }

        val x = ((element[0] as? Number)?.toInt() ?: 0) * screenWidth / COORDINATE_MAX
        val y = ((element[1] as? Number)?.toInt() ?: 0) * screenHeight / COORDINATE_MAX

        Log.d(TAG, "Long press: ($x, $y)")

        val success = executionEngine.longPress(x, y)
        return ActionResult(success, false, null)
    }

    /**
     * Execute a double tap action.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun executeDoubleTap(action: Map<String, Any>): ActionResult {
        val element = action["element"] as? List<*>
            ?: return ActionResult(false, false, "Missing element coordinates")

        if (element.size < 2) {
            return ActionResult(false, false, "Invalid element coordinates")
        }

        val x = ((element[0] as? Number)?.toInt() ?: 0) * screenWidth / COORDINATE_MAX
        val y = ((element[1] as? Number)?.toInt() ?: 0) * screenHeight / COORDINATE_MAX

        Log.d(TAG, "Double tap: ($x, $y)")

        // Tap twice with a small delay
        val success1 = executionEngine.tap(x, y)
        delay(100)
        val success2 = executionEngine.tap(x, y)

        return ActionResult(success1 && success2, false, null)
    }

    /**
     * Execute a launch action.
     */
    private suspend fun executeLaunch(action: Map<String, Any>): ActionResult {
        val appName = action["app"] as? String
            ?: return ActionResult(false, false, "Missing app name")

        Log.d(TAG, "Launch: $appName")

        // Get package name from app name
        val packageName = AppPackages.getPackageName(appName) ?: appName // Use app name directly if not found in mapping
        val success = executionEngine.launchApp(packageName)
        return ActionResult(success, false, null)
    }

    /**
     * Execute a back action.
     */
    private suspend fun executeBack(): ActionResult {
        Log.d(TAG, "Back")
        val success = executionEngine.pressBack()
        return ActionResult(success, false, null)
    }

    /**
     * Execute a home action.
     */
    private suspend fun executeHome(): ActionResult {
        Log.d(TAG, "Home")
        val success = executionEngine.pressHome()
        return ActionResult(success, false, null)
    }

    /**
     * Execute a wait action.
     */
    private suspend fun executeWait(action: Map<String, Any>): ActionResult {
        val duration = action["duration"] as? String ?: "1 second"

        Log.d(TAG, "Wait: $duration")

        // Parse duration (simple parsing for common formats)
        val delayMs = when {
            duration.contains("second", ignoreCase = true) -> {
                val seconds = duration.filter { it.isDigit() }.toIntOrNull() ?: 1
                seconds * 1000L
            }
            duration.contains("ms", ignoreCase = true) -> {
                duration.filter { it.isDigit() }.toLongOrNull() ?: 1000L
            }
            else -> 1000L
        }

        delay(delayMs)
        return ActionResult(true, false, null)
    }

    /**
     * Execute a finish action.
     */
    private suspend fun executeFinish(action: Map<String, Any>): ActionResult {
        val message = action["message"] as? String ?: "Task completed"
        Log.i(TAG, "Finish: $message")
        return ActionResult(true, true, message)
    }

    /**
     * Execute a note action (informational only).
     */
    private suspend fun executeNote(action: Map<String, Any>): ActionResult {
        val message = action["message"] as? String ?: "Note"
        Log.d(TAG, "Note: $message")
        return ActionResult(true, false, message)
    }
}
