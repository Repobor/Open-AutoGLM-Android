package io.repobor.autoglm.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Main Accessibility Service for AutoGLM.
 * Provides screenshot capture, gesture execution, and app control capabilities.
 */
class AutoGLMAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoGLMAccessibilityService"

        /**
         * Static instance for global access.
         * This allows other components to access the service when it's running.
         */
        @Volatile
        private var instance: AutoGLMAccessibilityService? = null

        /**
         * Get the current service instance.
         * @return The service instance, or null if service is not running
         */
        fun getInstance(): AutoGLMAccessibilityService? = instance

        /**
         * Check if the service is running.
         * @return true if service is available, false otherwise
         */
        fun isRunning(): Boolean = instance != null
    }

    /**
     * Gesture executor for performing touch actions.
     */
    lateinit var gestureExecutor: GestureExecutor
        private set

    /**
     * App launcher for starting apps and getting current app info.
     */
    lateinit var appLauncher: AppLauncher
        private set

    /**
     * Current foreground app package name.
     */
    private var currentApp: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AutoGLM Accessibility Service connected")

        // Set static instance
        instance = this

        // Initialize components
        gestureExecutor = GestureExecutor(this)
        appLauncher = AppLauncher(this, this)

        // Get initial app
        currentApp = appLauncher.getCurrentApp()
        Log.d(TAG, "Initial app: $currentApp")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Track window state changes to detect app switches
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (packageName != null && packageName != currentApp) {
                    currentApp = packageName
                    Log.d(TAG, "App changed to: $currentApp")
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Could be used to detect UI updates if needed
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AutoGLM Accessibility Service destroyed")

        // Clear static instance
        instance = null
    }

    /**
     * Get the current foreground app package name.
     * @return Package name of current app, or null if unavailable
     */
    fun getCurrentAppPackage(): String? {
        return currentApp ?: appLauncher.getCurrentApp()
    }

    /**
     * Get the current foreground app display name.
     * @return Display name of current app, or null if unavailable
     */
    fun getCurrentAppName(): String? {
        return appLauncher.getCurrentAppName()
    }
}
