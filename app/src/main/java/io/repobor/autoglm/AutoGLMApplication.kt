package io.repobor.autoglm

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.repobor.autoglm.accessibility.ShizukuHelper
import io.repobor.autoglm.accessibility.ShizukuServiceHelper
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/**
 * AutoGLMApplication - Application class for AutoGLM.
 * Initializes Shizuku and HiddenApiBypass for proper operation.
 */
class AutoGLMApplication : Application() {

    companion object {
        private const val TAG = "AutoGLMApplication"
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received in Application")
        initializeShizukuService()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead in Application")
        ShizukuServiceHelper.getInstance().unbindService()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // Initialize HiddenApiBypass for Android P and above
        // This allows accessing hidden APIs needed for system operations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
                Log.d(TAG, "HiddenApiBypass initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize HiddenApiBypass", e)
            }
        }

        // Initialize ShizukuProvider for IPC with Shizuku
        // This must be called in attachBaseContext for provider initialization
        try {
            ShizukuProvider.enableMultiProcessSupport(false)
            Log.d(TAG, "ShizukuProvider multi-process support initialized")
        } catch (e: Exception) {
            Log.d(TAG, "ShizukuProvider initialization (expected if not using multi-process): ${e.message}")
        }

        Log.d(TAG, "Shizuku provider initialized")
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "AutoGLMApplication onCreate - registering Shizuku listeners")

        // Register Shizuku listeners as early as possible
        // Using sticky listener ensures we get called if binder is already available
        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
            Log.d(TAG, "Shizuku listeners registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }
    }

    /**
     * Initialize Shizuku service when binder is available
     */
    private fun initializeShizukuService() {
        Log.d(TAG, "Initializing Shizuku service...")

        if (!Shizuku.isPreV11()) {
            // Check if we already have permission
            if (ShizukuHelper.checkShizukuPermission(this)) {
                Log.d(TAG, "Shizuku permission already granted, binding UserService")
                ShizukuServiceHelper.getInstance().bindService()
            } else if (ShizukuHelper.isShizukuRunning()) {
                // Request permission
                Log.i(TAG, "Requesting Shizuku permission from Application")
                ShizukuHelper.requestShizukuPermission()
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "AutoGLMApplication terminating - unbinding Shizuku service")

        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing Shizuku listeners", e)
        }

        ShizukuServiceHelper.getInstance().unbindService()
    }
}
