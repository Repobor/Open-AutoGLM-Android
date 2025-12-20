package io.repobor.autoglm.actions

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.delay
import io.repobor.autoglm.accessibility.AutoGLMAccessibilityService
import io.repobor.autoglm.accessibility.GestureExecutor
import io.repobor.autoglm.accessibility.AppLauncher
import io.repobor.autoglm.config.AppPackages

/**
 * Handles execution of parsed actions.
 * Coordinates with AccessibilityService to perform gestures and app control.
 */
class ActionHandler(
    private val service: AutoGLMAccessibilityService,
    private val gestureExecutor: GestureExecutor,
    private val appLauncher: AppLauncher,
    private val screenWidth: Int,
    private val screenHeight: Int
) {

    companion object {
        private const val TAG = "ActionHandler"
        private const val ACTION_DELAY_MS = 200L
        private const val COORDINATE_MAX = 1000 // Model outputs coordinates in 0-999 range
    }

    /**
     * Execute an action based on parsed action map.
     *
     * @param action The action map from ActionParser
     * @return ActionResult indicating success/failure and whether to finish
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
            "call_api" -> executeCallApi(action)
            "interact" -> executeInteract()
            "take_over" -> executeTakeOver(action)
            else -> ActionResult(false, false, "Unknown action: $actionType")
        }
    }

    /**
     * Execute a tap action.
     * Converts normalized coordinates (0-999) to screen pixels.
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

        val success = gestureExecutor.tap(x, y)

        val message = action["message"] as? String
        val requiresConfirmation = message != null

        return ActionResult(success, false, message, requiresConfirmation)
    }

    /**
     * Execute a type action.
     * Finds focused EditText and sets text using ACTION_SET_TEXT.
     */
    private suspend fun executeType(action: Map<String, Any>): ActionResult {
        val text = action["text"] as? String
            ?: return ActionResult(false, false, "Missing text parameter")

        Log.d(TAG, "Type: $text")

        return try {
            val rootNode = service.rootInActiveWindow
            if (rootNode == null) {
                return ActionResult(false, false, "No active window")
            }

            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode == null) {
                return ActionResult(false, false, "No focused input field")
            }

            // Clear existing text and set new text
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )

            val success = focusedNode.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )
            ActionResult(success, false, if (success) null else "Failed to set text")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing type action", e)
            ActionResult(false, false, "Error: ${e.message}")
        }
    }

    /**
     * Execute a swipe action.
     * Converts normalized coordinates to pixels.
     */
    private suspend fun executeSwipe(action: Map<String, Any>): ActionResult {
        val start = action["start"] as? List<*>
            ?: return ActionResult(false, false, "Missing start coordinates")
        val end = action["end"] as? List<*>
            ?: return ActionResult(false, false, "Missing end coordinates")

        if (start.size < 2 || end.size < 2) {
            return ActionResult(false, false, "Invalid coordinates")
        }

        val x1 = ((start[0] as? Number)?.toInt() ?: 0) * screenWidth / COORDINATE_MAX
        val y1 = ((start[1] as? Number)?.toInt() ?: 0) * screenHeight / COORDINATE_MAX
        val x2 = ((end[0] as? Number)?.toInt() ?: 0) * screenWidth / COORDINATE_MAX
        val y2 = ((end[1] as? Number)?.toInt() ?: 0) * screenHeight / COORDINATE_MAX

        Log.d(TAG, "Swipe: ($x1, $y1) -> ($x2, $y2)")

        val success = gestureExecutor.swipe(x1, y1, x2, y2)
        return ActionResult(success, false)
    }

    /**
     * Execute a launch action.
     * Launches app by name using AppPackages mapping.
     */
    private suspend fun executeLaunch(action: Map<String, Any>): ActionResult {
        val appName = action["app"] as? String
            ?: return ActionResult(false, false, "Missing app parameter")

        Log.d(TAG, "Launch: $appName")

        // Try to resolve app name to package name
        val packageName = AppPackages.getPackageName(appName)
        Log.d(TAG, "packageName: $packageName")
        val success = if (packageName != null) {
            appLauncher.launchApp(packageName)
        } else {
            // Try direct package name if not in mapping
            appLauncher.launchApp(appName)
        }

        return ActionResult(
            success,
            false,
            if (!success) "Failed to launch app: $appName" else null
        )
    }

    /**
     * Execute back button press.
     */
    private suspend fun executeBack(): ActionResult {
        Log.d(TAG, "Press Back")
        val success = gestureExecutor.pressBack()
        return ActionResult(success, false)
    }

    /**
     * Execute home button press.
     */
    private suspend fun executeHome(): ActionResult {
        Log.d(TAG, "Press Home")
        val success = gestureExecutor.pressHome()
        return ActionResult(success, false)
    }

    /**
     * Execute long press action.
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

        val success = gestureExecutor.longPress(x, y)
        return ActionResult(success, false)
    }

    /**
     * Execute double tap action.
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

        val success = gestureExecutor.doubleTap(x, y)
        return ActionResult(success, false)
    }

    /**
     * Execute wait action.
     * Parses duration string and delays.
     */
    private suspend fun executeWait(action: Map<String, Any>): ActionResult {
        val durationStr = action["duration"] as? String
            ?: return ActionResult(false, false, "Missing duration parameter")

        Log.d(TAG, "Wait: $durationStr")

        // Parse duration (e.g., "3 seconds", "5s", "2")
        val seconds = parseDuration(durationStr)

        if (seconds <= 0) {
            return ActionResult(false, false, "Invalid duration: $durationStr")
        }

        delay(seconds * 1000L)
        return ActionResult(true, false, "Waited $seconds seconds")
    }

    /**
     * Parse duration string to seconds.
     */
    private fun parseDuration(durationStr: String): Long {
        return try {
            // Try to extract number from string
            val numberPattern = Regex("""(\d+)""")
            val match = numberPattern.find(durationStr)
            match?.groupValues?.get(1)?.toLongOrNull() ?: 3L // Default to 3 seconds
        } catch (e: Exception) {
            3L // Default to 3 seconds on error
        }
    }

    /**
     * Execute finish action.
     * Signals task completion.
     */
    private suspend fun executeFinish(action: Map<String, Any>): ActionResult {
        val message = action["message"] as? String ?: "Task completed"
        Log.i(TAG, "Finish: $message")
        return ActionResult(true, true, message)
    }

    /**
     * Execute note action (记录页面内容).
     * Currently just returns success.
     */
    private suspend fun executeNote(action: Map<String, Any>): ActionResult {
        val message = action["message"] as? String
        Log.d(TAG, "Note: $message")
        return ActionResult(true, false, "Note recorded")
    }

    /**
     * Execute call API action (总结或评论).
     * Currently just returns success.
     */
    private suspend fun executeCallApi(action: Map<String, Any>): ActionResult {
        val instruction = action["instruction"] as? String
        Log.d(TAG, "Call API: $instruction")
        return ActionResult(true, false, "API called")
    }

    /**
     * Execute interact action (询问用户选择).
     */
    private suspend fun executeInteract(): ActionResult {
        Log.d(TAG, "Interact: Requires user input")
        return ActionResult(false, false, "User interaction required", true)
    }

    /**
     * Execute take over action (需要用户协助).
     */
    private suspend fun executeTakeOver(action: Map<String, Any>): ActionResult {
        val message = action["message"] as? String ?: "User assistance required"
        Log.d(TAG, "Take over: $message")
        return ActionResult(false, false, message, true)
    }

    /**
     * Get screen dimensions for coordinate conversion.
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        return Pair(screenWidth, screenHeight)
    }
}
