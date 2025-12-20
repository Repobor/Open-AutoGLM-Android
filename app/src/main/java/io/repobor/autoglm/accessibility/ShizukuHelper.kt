package io.repobor.autoglm.accessibility

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.RemoteException
import android.util.Base64
import android.util.Log
import io.repobor.autoglm.IShizukuService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import androidx.core.graphics.createBitmap

/**
 * Helper class for Shizuku integration.
 * Provides Shizuku-based implementations for screenshot, gesture, and app launching.
 *
 * Architecture:
 * - ShizukuHelper (this, client process): Checks Shizuku availability and calls UserService via IPC
 * - ShizukuService (privileged process): Runs shell commands with elevated privileges
 * - ShellExecutor (privileged process): Executes actual shell commands
 */
object ShizukuHelper {
    private const val TAG = "ShizukuHelper"
    private var userService: IShizukuService? = null
    @Volatile
    private var userServiceConnectionAttempted = false

    /**
     * Set the UserService instance for IPC communication.
     * This is called by ShizukuServiceHelper after binding the service.
     */
    fun setUserService(service: IShizukuService?) {
        userService = service
        if (service != null) {
            userServiceConnectionAttempted = true
        }
        Log.d(TAG, "UserService set: ${if (service != null) "connected" else "disconnected"}")
    }

    /**
     * Check if Shizuku is running.
     * This checks if the Shizuku binder is available.
     */
    fun isShizukuRunning(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                // Pre-v11 versions are not supported
                false
            } else {
                // Try to check permission - if binder is available, returns permission status
                // If binder is not available yet, throws IllegalStateException
                try {
                    Shizuku.checkSelfPermission()
                } catch (e: RuntimeException) {
                    // checkSelfPermission throws RuntimeException if binder is unavailable
                    throw IllegalStateException("Binder unavailable", e)
                }
                true
            }
        } catch (e: IllegalStateException) {
            // Shizuku binder is not available - service not running
            Log.d(TAG, "Shizuku is not running: ${e.message}")
            false
        } catch (e: Exception) {
            // Other exceptions - Shizuku not available
            Log.d(TAG, "Shizuku check failed: ${e.message}")
            false
        }
    }

    /**
     * Check if app has Shizuku permission.
     * Returns false if Shizuku binder is not available or permission is not granted.
     */
    fun checkShizukuPermission(context: Context): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: IllegalStateException) {
            // Binder not received yet - Shizuku service not ready
            Log.d(TAG, "Shizuku binder not ready when checking permission: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission", e)
            false
        }
    }

    /**
     * Request Shizuku permission.
     */
    fun requestShizukuPermission() {
        if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
        }
    }

    /**
     * Check if UserService is connected via IPC.
     */
    fun isUserServiceConnected(): Boolean {
        return userService != null
    }

    /**
     * Get the UserService instance. Throws if not connected.
     */
    private fun getUserService(): IShizukuService {
        return userService ?: throw IllegalStateException("UserService not connected. Call bindUserService first.")
    }

    /**
     * Wait for UserService to be connected.
     * This is useful when the service is being bound but not yet available.
     * @param timeoutMs Maximum time to wait in milliseconds (default: 5000ms for faster failure)
     * @return true if service is connected within the timeout, false otherwise
     */
    suspend fun waitForUserService(timeoutMs: Long = 5000): Boolean = withContext(Dispatchers.IO) {
        // If already connected, return immediately
        if (isUserServiceConnected()) {
            Log.d(TAG, "UserService already connected")
            return@withContext true
        }

        // If connection was already attempted and failed, don't wait again
        if (userServiceConnectionAttempted) {
            Log.w(TAG, "UserService connection was already attempted, not waiting again")
            return@withContext false
        }

        val startTime = System.currentTimeMillis()
        var lastCheckTime = startTime
        var checkCount = 0

        while (!isUserServiceConnected()) {
            val elapsedTime = System.currentTimeMillis() - startTime

            if (elapsedTime > timeoutMs) {
                Log.w(TAG, "Timeout waiting for UserService connection after ${elapsedTime}ms (checks: $checkCount)")
                userServiceConnectionAttempted = true
                return@withContext false
            }

            // Log every 1 second
            if (System.currentTimeMillis() - lastCheckTime > 1000) {
                Log.d(TAG, "Still waiting for UserService... (${elapsedTime}ms elapsed, checks: $checkCount)")
                lastCheckTime = System.currentTimeMillis()
            }

            checkCount++
            delay(100) // Check every 100ms (increased from 50ms for less busy waiting)
        }

        val totalTime = System.currentTimeMillis() - startTime
        userServiceConnectionAttempted = true
        Log.d(TAG, "UserService connected after ${totalTime}ms (${checkCount} checks)")
        return@withContext true
    }

    /**
     * Capture screenshot using Shizuku with server-side compression.
     *
     * Flow:
     * 1. Shizuku service: screencap -p (PNG) -> decode -> compress to JPEG -> return compressed bytes
     * 2. Client: receive JPEG bytes (already compressed, typically < 1MB, safe for Binder)
     * 3. Client: decode JPEG -> Bitmap
     * 4. Client: compress Bitmap to JPEG 50% quality again for further size reduction (optional)
     * 5. Client: base64 encode final JPEG bytes -> final base64 for API
     *
     * Benefits:
     * - In-service compression avoids Binder overflow (raw PNG/RGBA = 1-10MB+)
     * - Service returns ~400-600KB JPEG (safe for Binder < 1MB limit)
     * - Client can do additional light compression if needed
     * - BitmapFactory.decodeByteArray handles JPEG transparently
     *
     * @return Base64 encoded JPEG image
     */
    suspend fun captureScreenshot(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isShizukuRunning()) {
                return@withContext Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return@withContext Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()

            try {
                // Step 1: Get JPEG bytes from Shizuku (already compressed in service)
                val jpegBytes = service.captureScreenshot()

                if (jpegBytes.isEmpty()) {
                    Log.w(TAG, "Screenshot is empty")
                    return@withContext Result.failure(Exception("Screenshot is empty"))
                }

                Log.d(TAG, "Received JPEG bytes from Shizuku, size: ${String.format("%.2f", jpegBytes.size / 1024.0)}KB")

//                // Step 2: Decode JPEG bytes -> Bitmap (verify integrity)
//                val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
//                if (bitmap == null) {
//                    Log.e(TAG, "Failed to decode JPEG to Bitmap")
//                    return@withContext Result.failure(Exception("Failed to decode JPEG"))
//                }
//
//                // Step 3: Optional client-side compression for further reduction
//                // Only do this if size is still large (> 500KB)
//                val finalJpegBytes = if (jpegBytes.size > 512_000) {
//                    Log.d(TAG, "JPEG size > 500KB, applying additional client-side compression")
//                    val compressedStream = ByteArrayOutputStream()
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 40, compressedStream)
//                    val compressed = compressedStream.toByteArray()
//                    compressedStream.close()
//                    Log.d(TAG, "Further compressed to: ${String.format("%.2f", compressed.size / 1024.0)}KB")
//                    compressed
//                } else {
//                    Log.d(TAG, "JPEG size OK at ${String.format("%.2f", jpegBytes.size / 1024.0)}KB, no further compression needed")
//                    jpegBytes
//                }
//
//                bitmap.recycle()

                // Step 4: Base64 encode final JPEG bytes for API
                val jpegBase64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                Log.d(TAG, "Final base64 encoded image, size: ${String.format("%.2f", jpegBase64.length / 1024.0 / 1024.0)}MB")

                Result.success(jpegBase64)
            } catch (e: RemoteException) {
                Log.e(TAG, "RemoteException while capturing screenshot (Binder overflow?): ${e.message}", e)
                // Return black screenshot as fallback
                val blackBitmap = createBitmap(1080, 2400)
                blackBitmap.eraseColor(Color.BLACK)
                val stream = ByteArrayOutputStream()
                blackBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                blackBitmap.recycle()
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                Log.w(TAG, "Returning black screenshot fallback, size: ${String.format("%.2f", base64.length / 1024.0 / 1024.0)}MB")
                Result.success(base64)
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while capturing screenshot", e)
            Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            Result.failure(e)
        }
    }

    /**
     * Tap at coordinates via Shizuku UserService.
     */
    suspend fun tap(x: Int, y: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isShizukuRunning()) {
                return@withContext Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return@withContext Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val success = service.tap(x, y)

            if (success) {
                Log.d(TAG, "Tap executed at ($x, $y)")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Tap failed at ($x, $y)"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while tapping at ($x, $y)", e)
            Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to tap at ($x, $y)", e)
            Result.failure(e)
        }
    }

    /**
     * Swipe gesture via Shizuku UserService.
     */
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long = 300): Result<Unit> {
        try {
            if (!isShizukuRunning()) {
                return Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val success = service.swipe(startX, startY, endX, endY, duration)

            if (success) {
                Log.d(TAG, "Swipe executed from ($startX, $startY) to ($endX, $endY)")
                return Result.success(Unit)
            } else {
                return Result.failure(Exception("Swipe failed"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while swiping", e)
            return Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to swipe", e)
            return Result.failure(e)
        }
    }

    /**
     * Long press at coordinates via Shizuku UserService.
     */
    suspend fun longPress(x: Int, y: Int, duration: Long = 1000): Result<Unit> {
        try {
            if (!isShizukuRunning()) {
                return Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val success = service.longPress(x, y, duration)

            if (success) {
                Log.d(TAG, "Long press executed at ($x, $y) for ${duration}ms")
                return Result.success(Unit)
            } else {
                return Result.failure(Exception("Long press failed"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while long pressing", e)
            return Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to long press", e)
            return Result.failure(e)
        }
    }

    /**
     * Type text via Shizuku UserService.
     */
    suspend fun typeText(text: String): Result<Unit> {
        try {
            if (!isShizukuRunning()) {
                return Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val success = service.typeText(text)

            if (success) {
                Log.d(TAG, "Text typed successfully")
                return Result.success(Unit)
            } else {
                return Result.failure(Exception("Type text failed"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while typing text", e)
            return Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to type text", e)
            return Result.failure(e)
        }
    }

    /**
     * Press back button via Shizuku UserService.
     */
    suspend fun pressBack(): Result<Unit> {
        try {
            if (!isShizukuRunning()) {
                return Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val success = service.pressBack()

            if (success) {
                Log.d(TAG, "Back button pressed")
                return Result.success(Unit)
            } else {
                return Result.failure(Exception("Press back failed"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while pressing back", e)
            return Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press back", e)
            return Result.failure(e)
        }
    }

    /**
     * Press home button via Shizuku UserService.
     */
    suspend fun pressHome(): Result<Unit> {
        try {
            if (!isShizukuRunning()) {
                return Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val success = service.pressHome()

            if (success) {
                Log.d(TAG, "Home button pressed")
                return Result.success(Unit)
            } else {
                return Result.failure(Exception("Press home failed"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while pressing home", e)
            return Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press home", e)
            return Result.failure(e)
        }
    }

    /**
     * Launch app by package name via Shizuku UserService.
     */
    suspend fun launchApp(packageName: String): Result<Unit> {
        try {
            if (!isShizukuRunning()) {
                return Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val success = service.launchApp(packageName)

            if (success) {
                Log.d(TAG, "App launched: $packageName")
                return Result.success(Unit)
            } else {
                return Result.failure(Exception("Failed to launch app: $packageName"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while launching app", e)
            return Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $packageName", e)
            return Result.failure(e)
        }
    }

    /**
     * Get the display name of the currently active app via Shizuku UserService.
     */
    suspend fun getCurrentAppName(): String = withContext(Dispatchers.IO) {
        try {
            if (!isShizukuRunning()) {
                Log.w(TAG, "Shizuku is not running")
                return@withContext "Unknown"
            }

            if (!isUserServiceConnected()) {
                Log.w(TAG, "Shizuku UserService not connected")
                return@withContext "Unknown"
            }

            val service = getUserService()
            val appName = service.getCurrentAppName()

            if (appName.isNullOrEmpty()) {
                Log.w(TAG, "Got empty app name from service")
                return@withContext "Unknown"
            }

            Log.d(TAG, "Current app: $appName")
            appName
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while getting current app", e)
            "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current app", e)
            "Unknown"
        }
    }

    /**
     * Enable accessibility service via Shizuku UserService.
     * Requires WRITE_SECURE_SETTINGS permission.
     */
    suspend fun enableAccessibilityService(
        context: Context,
        serviceComponentName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isShizukuRunning()) {
                return@withContext Result.failure(Exception("Shizuku is not running"))
            }

            if (!isUserServiceConnected()) {
                return@withContext Result.failure(Exception("Shizuku UserService not connected"))
            }

            val service = getUserService()
            val result = service.enableAccessibilityService(serviceComponentName)

            if (result.isEmpty()) {
                Log.d(TAG, "Successfully enabled accessibility service: $serviceComponentName")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to enable accessibility service: $result"))
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while enabling accessibility service", e)
            Result.failure(Exception("IPC error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service", e)
            Result.failure(e)
        }
    }

    /**
     * Bind to Shizuku UserService for IPC communication.
     * This allows executing privileged operations through Shizuku.
     */
    fun bindUserService(serviceConnection: android.content.ServiceConnection) {
        try {
            if (Shizuku.isPreV11()) {
                Log.w(TAG, "Shizuku pre-v11 is not supported, cannot bind user service")
                return
            }

            Log.d(TAG, "Attempting to bind Shizuku UserService")
            val userServiceArgs = Shizuku.UserServiceArgs(
                android.content.ComponentName("io.repobor.autoglm", "io.repobor.autoglm.accessibility.ShizukuService")
            )
                .daemon(false)
                .processNameSuffix("shizuku")
                .debuggable(false)
                .version(1)

            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            Log.d(TAG, "UserService binding requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UserService", e)
        }
    }

    /**
     * Unbind from Shizuku UserService.
     */
    fun unbindUserService(serviceConnection: android.content.ServiceConnection) {
        try {
            if (Shizuku.isPreV11()) {
                return
            }

            Log.d(TAG, "Unbinding UserService")
            val userServiceArgs = Shizuku.UserServiceArgs(
                android.content.ComponentName("io.repobor.autoglm", "io.repobor.autoglm.accessibility.ShizukuService")
            )
                .daemon(false)
                .processNameSuffix("shizuku")
                .debuggable(false)
                .version(1)

            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            Log.d(TAG, "UserService unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind UserService", e)
        }
    }
}
