package io.repobor.autoglm.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

/**
 * Helper class for managing accessibility service settings.
 * Provides utilities to check and enable the accessibility service programmatically.
 */
object AccessibilityServiceHelper {

    private const val TAG = "AccessibilityServiceHelper"

    /**
     * Check if the app has WRITE_SECURE_SETTINGS permission.
     * @param context Application context
     * @return true if permission is granted, false otherwise
     */
    fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return try {
            // Try to write and read back to verify permission
            val testKey = "test_write_permission"
            val testValue = System.currentTimeMillis().toString()

            Settings.Secure.putString(
                context.contentResolver,
                testKey,
                testValue
            )

            val readBack = Settings.Secure.getString(
                context.contentResolver,
                testKey
            )

            // Clean up test value
            Settings.Secure.putString(context.contentResolver, testKey, null)

            readBack == testValue
        } catch (e: SecurityException) {
            Log.w(TAG, "No WRITE_SECURE_SETTINGS permission", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check WRITE_SECURE_SETTINGS permission", e)
            false
        }
    }

    /**
     * Check if AutoGLM accessibility service is enabled.
     * @param context Application context
     * @return true if service is enabled, false otherwise
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = ComponentName(context, AutoGLMAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentString)
            if (enabledService != null && enabledService == service) {
                return true
            }
        }

        return false
    }

    /**
     * Enable AutoGLM accessibility service by writing to Secure Settings.
     * Tries direct write first (requires WRITE_SECURE_SETTINGS permission).
     * If that fails and Shizuku is available, uses Shizuku.
     *
     * @param context Application context
     * @return true if successfully enabled, false otherwise
     */
    suspend fun enableAccessibilityService(context: Context): Boolean {
        // Check if already enabled
        if (isAccessibilityServiceEnabled(context)) {
            Log.i(TAG, "Accessibility service already enabled")
            return true
        }

        // Try direct write first (requires WRITE_SECURE_SETTINGS permission)
        val directResult = tryEnableAccessibilityServiceDirect(context)
        if (directResult) {
            return true
        }

        // If direct write failed, try Shizuku
        return tryEnableAccessibilityServiceShizuku(context)
    }

    /**
     * Try to enable accessibility service directly via WRITE_SECURE_SETTINGS permission.
     */
    private fun tryEnableAccessibilityServiceDirect(context: Context): Boolean {
        return try {
            val service = ComponentName(context, AutoGLMAccessibilityService::class.java)
            val flattenedService = service.flattenToString()

            // Get currently enabled services
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            // Build new service list
            val newEnabledServices = if (enabledServices.isEmpty()) {
                flattenedService
            } else {
                "$enabledServices:$flattenedService"
            }

            // Write to secure settings
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newEnabledServices
            )

            // Enable accessibility globally
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )

            Log.i(TAG, "Successfully enabled accessibility service via direct write")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "No WRITE_SECURE_SETTINGS permission for direct write", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service via direct write", e)
            false
        }
    }

    /**
     * Try to enable accessibility service via Shizuku.
     */
    private suspend fun tryEnableAccessibilityServiceShizuku(context: Context): Boolean {
        if (!ShizukuHelper.isShizukuRunning() || !ShizukuHelper.checkShizukuPermission(context)) {
            Log.w(TAG, "Shizuku is not available")
            return false
        }

        val service = ComponentName(context, AutoGLMAccessibilityService::class.java)
        val flattenedService = service.flattenToString()

        val result = ShizukuHelper.enableAccessibilityService(context, flattenedService)
        return if (result.isSuccess) {
            Log.i(TAG, "Successfully enabled accessibility service via Shizuku")
            true
        } else {
            Log.e(TAG, "Failed to enable accessibility service via Shizuku", result.exceptionOrNull())
            false
        }
    }

    /**
     * Disable AutoGLM accessibility service by writing to Secure Settings.
     * Requires WRITE_SECURE_SETTINGS permission.
     *
     * @param context Application context
     * @return true if successfully disabled, false otherwise
     */
    fun disableAccessibilityService(context: Context): Boolean {
        return try {
            val service = ComponentName(context, AutoGLMAccessibilityService::class.java)
            val flattenedService = service.flattenToString()

            // Get currently enabled services
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            // Remove our service from the list
            val serviceList = enabledServices.split(":")
                .filter { it.isNotEmpty() && it != flattenedService }
                .joinToString(":")

            // Write back to secure settings
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                serviceList
            )

            Log.i(TAG, "Successfully disabled accessibility service")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied - grant WRITE_SECURE_SETTINGS via ADB", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable accessibility service", e)
            false
        }
    }

    /**
     * Get ADB command to grant WRITE_SECURE_SETTINGS permission.
     * @param context Application context
     * @return ADB command string
     */
    fun getAdbPermissionCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Get instructions for enabling accessibility service.
     * @param context Application context
     * @param language Language code ("zh" or "en")
     * @return Instruction text
     */
    fun getEnableInstructions(context: Context, language: String): String {
        return if (language == "zh") {
            """
            无障碍服务未启用。请选择以下方式之一启用：

            方式1：手动启用（推荐）
            1. 打开系统设置 → 无障碍
            2. 找到并启用 "AutoGLM"

            方式2：通过ADB自动启用
            1. 在电脑上运行以下命令：
            ${getAdbPermissionCommand(context)}

            2. 重启应用，服务将自动启用
            """.trimIndent()
        } else {
            """
            Accessibility service is not enabled. Please use one of the following methods:

            Method 1: Manual Enable (Recommended)
            1. Open Settings → Accessibility
            2. Find and enable "AutoGLM"

            Method 2: Auto-enable via ADB
            1. Run this command on your computer:
            ${getAdbPermissionCommand(context)}

            2. Restart the app, service will be auto-enabled
            """.trimIndent()
        }
    }
}
