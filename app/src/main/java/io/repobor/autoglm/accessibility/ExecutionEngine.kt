package io.repobor.autoglm.accessibility

/**
 * Unified interface for both Accessibility and Shizuku execution modes.
 */
interface ExecutionEngine {
    /**
     * Capture a screenshot.
     * @return Screenshot object containing base64 image data and dimensions
     */
    suspend fun captureScreenshot(): Screenshot

    /**
     * Perform a tap gesture at specified coordinates.
     * @param x X coordinate in pixels
     * @param y Y coordinate in pixels
     * @return true if gesture was executed successfully
     */
    suspend fun tap(x: Int, y: Int): Boolean

    /**
     * Perform a swipe gesture from start to end coordinates.
     * @param startX Start X coordinate
     * @param startY Start Y coordinate
     * @param endX End X coordinate
     * @param endY End Y coordinate
     * @param duration Swipe duration in milliseconds (default: 300ms)
     * @return true if gesture was executed successfully
     */
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 300
    ): Boolean

    /**
     * Perform a long press gesture at specified coordinates.
     * @param x X coordinate in pixels
     * @param y Y coordinate in pixels
     * @param duration Long press duration in milliseconds (default: 1000ms)
     * @return true if gesture was executed successfully
     */
    suspend fun longPress(x: Int, y: Int, duration: Long = 1000): Boolean

    /**
     * Type text.
     * @param text Text to type
     * @return true if text was typed successfully
     */
    suspend fun typeText(text: String): Boolean

    /**
     * Press back button.
     * @return true if back button was pressed successfully
     */
    suspend fun pressBack(): Boolean

    /**
     * Press home button.
     * @return true if home button was pressed successfully
     */
    suspend fun pressHome(): Boolean

    /**
     * Launch app by package name.
     * @param packageName Package name of the app
     * @return true if app was launched successfully
     */
    suspend fun launchApp(packageName: String): Boolean

    /**
     * Get the display name of the currently active app.
     * @return The display name of the current app
     */
    suspend fun getCurrentAppName(): String

    /**
     * Check if the execution engine is available and ready to use.
     * @return true if engine is available
     */
    fun isAvailable(): Boolean
}
