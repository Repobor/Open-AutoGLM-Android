package io.repobor.autoglm.accessibility

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Shell command executor running in Shizuku's privileged process.
 * This class executes shell commands with Shizuku's privilege.
 * Since this runs in the Shizuku UserService (privileged process),
 * we can execute shell commands directly without additional privilege escalation.
 */
object ShellExecutor {
    private const val TAG = "ShellExecutor"

    /**
     * Execute a shell command and return the output.
     * This should only be called from within the Shizuku UserService process,
     * which already has elevated privileges.
     *
     * @param command The shell command to execute
     * @return The command output, or empty string on error
     */
    fun executeCommand(command: String, timeoutMs: Long = 10_000): String {
        Log.d(TAG, "Executing shell command: $command")

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            val stdoutSb = StringBuilder()
            val stderrSb = StringBuilder()

            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { stdoutSb.appendLine(it) }
                    }
                } catch (e: IOException) {
                    // ✅ 关键：忽略 read 被 close 打断
                    Log.d(TAG, "stdout reader interrupted")
                }
            }

            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { stderrSb.appendLine(it) }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "stderr reader interrupted")
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

            if (!finished) {
                process.destroy()
                // 等 reader 线程自然退出
                stdoutThread.join(200)
                stderrThread.join(200)
                return ""
            }

            stdoutThread.join()
            stderrThread.join()

            val stdout = stdoutSb.toString().trim()
            val exitCode = process.exitValue()

            if (exitCode == 0 && stdout.isNotEmpty()) {
                stdout
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeCommand failed", e)
            ""
        }
    }



    /**
     * Capture screenshot and return PNG bytes.
     * Uses -p flag to output PNG format (not raw RGBA buffer).
     * The caller is responsible for further compression or encoding.
     */
    fun captureScreenshotPng(): ByteArray? {
        return try {
            Log.d(TAG, "Capturing screenshot via screencap -p (PNG format)")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p"))

            try {
                val screenshotBytes = process.inputStream.use { it.readBytes() }
                val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()

                if (errorOutput.isNotEmpty()) {
                    Log.w(TAG, "screencap stderr: ${errorOutput.trim()}")
                }

                if (exitCode != 0) {
                    Log.e(TAG, "screencap failed with exit code $exitCode")
                    null
                } else {
                    Log.d(TAG, "Screenshot captured, size: ${screenshotBytes.size} bytes")
                    screenshotBytes
                }
            } finally {
                process.outputStream.close()
                process.destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            null
        }
    }

    /**
     * Tap at screen coordinates.
     * Runs: input tap x y
     */
    fun tap(x: Int, y: Int): Boolean {
        val result = executeCommand("input tap $x $y")
        return result.isEmpty() // Empty result means success
    }

    /**
     * Swipe on screen.
     * Runs: input swipe startX startY endX endY duration
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        val result = executeCommand("input swipe $startX $startY $endX $endY $duration")
        return result.isEmpty()
    }

    /**
     * Long press on screen.
     * Runs: input swipe x y x y duration (same position)
     */
    fun longPress(x: Int, y: Int, duration: Long): Boolean {
        val result = executeCommand("input swipe $x $y $x $y $duration")
        return result.isEmpty()
    }

    /**
     * Type text on screen with input field clearing.
     *
     * Strategy: Before typing new text, clear any existing content in the input field.
     * This ensures text is not appended but replaces existing content.
     *
     * Sequence:
     * 1. Move cursor to end: input keyevent KEYCODE_MOVE_END
     * 2. Select all by moving to start (shifts selection): input keyevent KEYCODE_MOVE_HOME + SHIFT
     * 3. Since SHIFT combinations aren't supported by input command, use alternative:
     *    - Move to end: KEYCODE_MOVE_END
     *    - Select by sending DEL multiple times to clear existing text
     * 4. Type new text: input text "escaped_text"
     *
     * Note: input text command doesn't support non-ASCII characters directly.
     * For non-ASCII text, use accessibility service from client side.
     */
    fun typeText(text: String): Boolean {
        return try {
            Log.d(TAG, "Clearing input field and typing new text: $text")

            // Strategy: Move to end and delete backwards to clear field
            // This is safer than trying to select all (which may not work on all devices)
            executeCommand("input keyevent KEYCODE_MOVE_END")

            // Delete backwards to clear the field
            // Typically EditText fields have < 1000 chars, so 500 DEL should cover most cases
            // Do it more efficiently by batching with a shell loop instead of individual commands
            val clearScript = "i=0; while [ \$i -lt 500 ]; do input keyevent KEYCODE_DEL; i=\$((i+1)); done"
            executeCommand(clearScript)

            // Now type the new text
            // Escape spaces and quotes for input text command
            val escapedText = text.replace(" ", "%s").replace("\"", "\\\"")
            Log.d(TAG, "Typing cleared text: $text")
            val typeResult = executeCommand("input text \"$escapedText\"")

            Log.d(TAG, "Type operation completed successfully")
            typeResult.isEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error during typeText operation", e)
            false
        }
    }

    /**
     * Press back button.
     * Runs: input keyevent KEYCODE_BACK
     */
    fun pressBack(): Boolean {
        val result = executeCommand("input keyevent KEYCODE_BACK")
        return result.isEmpty()
    }

    /**
     * Press home button.
     * Runs: input keyevent KEYCODE_HOME
     */
    fun pressHome(): Boolean {
        val result = executeCommand("input keyevent KEYCODE_HOME")
        return result.isEmpty()
    }

    /**
     * Launch app by package name.
     * Runs: monkey -p packageName -c android.intent.category.LAUNCHER 1
     */
    fun launchApp(packageName: String): Boolean {
        val result = executeCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        return true
    }

    /**
     * Enable accessibility service via secure settings.
     * Requires WRITE_SECURE_SETTINGS permission.
     * Returns empty string on success.
     */
    fun enableAccessibilityService(serviceComponentName: String): String {
        return try {
            // Get current enabled services
            val getCommand = "settings get secure enabled_accessibility_services"
            val currentServices = executeCommand(getCommand).trim()

            // Check if already enabled
            if (currentServices.contains(serviceComponentName)) {
                Log.d(TAG, "Accessibility service already enabled: $serviceComponentName")
                return ""
            }

            // Add service to enabled list
            val newServices = if (currentServices.isEmpty() || currentServices == "null") {
                serviceComponentName
            } else {
                "$currentServices:$serviceComponentName"
            }

            // Enable accessibility
            val enableAccessibilityCommand = "settings put secure accessibility_enabled 1"
            executeCommand(enableAccessibilityCommand)

            // Set enabled services
            val setCommand = "settings put secure enabled_accessibility_services \"$newServices\""
            val result = executeCommand(setCommand)

            if (result.isEmpty()) {
                Log.d(TAG, "Successfully enabled accessibility service: $serviceComponentName")
                ""
            } else {
                Log.e(TAG, "Failed to set accessibility service: $result")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service", e)
            e.message ?: "Unknown error"
        }
    }

    /**
     * Get the package name of the currently active app.
     * Uses multiple fallback methods to ensure compatibility across Android versions.
     */
    fun getCurrentAppPackageName(): String {
        return try {
            Log.d(TAG, "=== getCurrentAppPackageName START ===")

            var result = executeCommand("dumpsys activity activities | grep -E \"mResumedActivity|topResumedActivity\"").trim()

            if (result.isNotEmpty()) {
                // Try to find mCurrentFocus
                val focusRegex = Regex("""\b([a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+)/""")
                var match = focusRegex.find(result)
                if (match != null) {
                    val packageName = match.groupValues[1]
                    Log.d(TAG, "SUCCESS: Found : $packageName")
                    return packageName
                }
            }
            Log.w(TAG, "=== FAILED: All methods returned empty or no match ===")
            "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "EXCEPTION: Failed to get current app", e)
            "Unknown"
        }
    }
}
