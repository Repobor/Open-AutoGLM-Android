package io.repobor.autoglm.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import io.repobor.autoglm.config.AppPackages

/**
 * Handles launching apps and getting current app information.
 */
class AppLauncher(
    private val context: Context,
    private val service: AccessibilityService
) {

    companion object {
        private const val TAG = "AppLauncher"
    }

    /**
     * Launch an app by its package name.
     *
     * @param packageName The Android package name of the app
     * @return true if app was launched successfully, false otherwise
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                Log.d(TAG, "Launched app: $packageName")
                true
            } else {
                Log.w(TAG, "No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: $packageName", e)
            false
        }
    }

    /**
     * Launch an app by its display name.
     * Uses AppPackages mapping to resolve app name to package name.
     *
     * @param appName The display name of the app (e.g., "微信", "WeChat")
     * @return true if app was launched successfully, false otherwise
     */
    fun launchAppByName(appName: String): Boolean {
        val packageName = AppPackages.getPackageName(appName)

        if (packageName == null) {
            Log.w(TAG, "App not supported: $appName")
            return false
        }

        return launchApp(packageName)
    }

    /**
     * Get the package name of the currently active app.
     * Uses AccessibilityService to get the root window's package name.
     *
     * @return The package name of the current app, or null if unavailable
     */
    fun getCurrentApp(): String? {
        return try {
            val rootNode = service.rootInActiveWindow
            val packageName = rootNode?.packageName?.toString()
            if (packageName != null) {
                Log.d(TAG, "Current app: $packageName")
            } else {
                Log.w(TAG, "Could not determine current app")
            }

            packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current app", e)
            null
        }
    }

    /**
     * Get the display name of the currently active app.
     * Uses AppPackages mapping to resolve package name to app name.
     *
     * @return The display name of the current app, or the package name if not in mapping
     */
    fun getCurrentAppName(): String? {
        val packageName = getCurrentApp() ?: return null
        return AppPackages.getAppName(packageName) ?: packageName
    }

    /**
     * Get the human-readable app name from PackageManager.
     *
     * @param packageName The package name to look up
     * @return The app label, or the package name if lookup fails
     */
    fun getAppLabel(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App not found: $packageName")
            packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app label", e)
            packageName
        }
    }

    /**
     * Check if an app is installed.
     *
     * @param packageName The package name to check
     * @return true if app is installed, false otherwise
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Check if an app is installed by its display name.
     *
     * @param appName The display name of the app
     * @return true if app is installed, false otherwise
     */
    fun isAppInstalledByName(appName: String): Boolean {
        val packageName = AppPackages.getPackageName(appName) ?: return false
        return isAppInstalled(packageName)
    }

    /**
     * Launch Android Settings app.
     *
     * @return true if Settings was launched successfully
     */
    fun launchSettings(): Boolean {
        return launchApp("com.android.settings")
    }

    /**
     * Launch Accessibility Settings.
     *
     * @return true if Accessibility Settings was launched successfully
     */
    fun launchAccessibilitySettings(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched Accessibility Settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Accessibility Settings", e)
            false
        }
    }

    /**
     * Get a list of all installed apps (package names).
     *
     * @return List of installed package names
     */
    fun getInstalledApps(): List<String> {
        return try {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            packages.map { it.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps", e)
            emptyList()
        }
    }

    /**
     * Get a list of launchable installed apps.
     *
     * @return List of package names for apps that can be launched
     */
    fun getLaunchableApps(): List<String> {
        return try {
            val packageManager = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0)
            resolveInfoList.map { it.activityInfo.packageName }.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting launchable apps", e)
            emptyList()
        }
    }

    /**
     * Check if current app matches a package name.
     *
     * @param packageName The package name to check against
     * @return true if current app matches, false otherwise
     */
    fun isCurrentApp(packageName: String): Boolean {
        val currentPackage = getCurrentApp()
        return currentPackage == packageName
    }

    /**
     * Check if current app matches an app name.
     *
     * @param appName The display name to check against
     * @return true if current app matches, false otherwise
     */
    fun isCurrentAppByName(appName: String): Boolean {
        val packageName = AppPackages.getPackageName(appName) ?: return false
        return isCurrentApp(packageName)
    }
}
