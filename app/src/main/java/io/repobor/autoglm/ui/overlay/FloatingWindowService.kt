package io.repobor.autoglm.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import io.repobor.autoglm.R
import io.repobor.autoglm.data.SettingsRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.repobor.autoglm.ui.theme.AutoGLMTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Floating window service that provides an overlay UI for task execution.
 * Replaces picture-in-picture with a draggable floating window.
 */
class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, CoroutineScope {

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private var isExpanded by mutableStateOf(false)
    private lateinit var settingsRepository: SettingsRepository

    // Lifecycle management
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    // ViewModel store
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    // SavedState management
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // Coroutine scope
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + job

    companion object {
        private const val TAG = "FloatingWindowService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_window_channel"
        const val ACTION_STOP = "io.repobor.autoglm.STOP_FLOATING_WINDOW"
        const val ACTION_TOGGLE_EXPAND = "io.repobor.autoglm.TOGGLE_EXPAND_FLOATING_WINDOW"

        // Singleton instance for communication
        private var instance: FloatingWindowService? = null
        fun getInstance(): FloatingWindowService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingWindowService creating")
        instance = this
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // Start as foreground service for Android O+ - DO THIS FIRST before any heavy operations
        try {
            createNotificationChannel()
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            // Continue anyway, the system will handle it
        }

        try {
            settingsRepository = SettingsRepository(this)
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            createFloatingView()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create floating view", e)
        }

        // Listen for expand floating window requests
        launch {
            FloatingWindowStateHolder.expandFloatingWindow.collect {
                if (!isExpanded) {
                    Log.d(TAG, "Expanding floating window per task completion request")
                    toggleExpand()
                }
            }
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        Log.d(TAG, "FloatingWindowService created successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_TOGGLE_EXPAND -> {
                toggleExpand()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createFloatingView() {
        // Setup window parameters
        val params = createWindowParams()

        // Create ComposeView
        floatingView = ComposeView(this).apply {
            // Set lifecycle, viewmodel, and savedstate owners
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)

            setContent {
                AutoGLMTheme {
                    // Collect the ViewModel from state holder - this will trigger recomposition when ViewModel is available
                    val viewModel = remember { FloatingWindowStateHolder.getViewModel() }
                    FloatingWindowContent(
                        isExpanded = isExpanded,
                        onToggleExpand = { toggleExpand() },
                        onClose = { stopSelf() },
                        viewModel = viewModel
                    )
                }
            }
        }

        // Add view to window
        try {
            windowManager.addView(floatingView, params)

            // Setup drag listener for the entire view (only works when collapsed)
            setupDragListener(floatingView!!, params)

            // Save initial position if not already saved (only on first launch)
            launch {
                try {
                    val currentX = settingsRepository.floatingWindowX.first()
                    val currentY = settingsRepository.floatingWindowY.first()

                    // If default position (100, 100), save the actual position for next time
                    if (currentX == 100 && currentY == 100) {
                        settingsRepository.saveFloatingWindowPosition(params.x, params.y)
                        Log.d(TAG, "Saved initial floating window position: x=${params.x}, y=${params.y}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save initial position", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
        }
    }

    private fun createWindowParams(): WindowManager.LayoutParams {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // When expanded, use FLAG_NOT_TOUCH_MODAL to allow clicks to pass through to background
        // When collapsed, use FLAG_NOT_FOCUSABLE to keep focus on the app
        val flags = if (isExpanded) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        // Load saved position from settings
        val savedX = runBlocking {
            try {
                settingsRepository.floatingWindowX.first()
            } catch (e: Exception) {
                100
            }
        }

        val savedY = runBlocking {
            try {
                settingsRepository.floatingWindowY.first()
            } catch (e: Exception) {
                100
            }
        }

        return WindowManager.LayoutParams(
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (isExpanded) Gravity.CENTER else Gravity.TOP or Gravity.START
            x = if (!isExpanded) savedX else 0
            y = if (!isExpanded) savedY else 0
        }
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var downTime = 0L

        val longPressThreshold = 500L // 500ms for long press
        val dragThreshold = 10 // 10 pixels movement threshold

        view.setOnTouchListener { v, event ->
            // Only allow interaction when collapsed
            if (isExpanded) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // If moved more than threshold, consider it a drag
                    if (!isDragging && (Math.abs(deltaX) > dragThreshold || Math.abs(deltaY) > dragThreshold)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        // Update position
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()

                        // Apply boundary constraints to keep window visible
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        // Keep at least 20% of the view visible
                        val viewWidth = view.width
                        val viewHeight = view.height
                        val minVisibleX = -(viewWidth * 0.8f).toInt()
                        val maxVisibleX = (screenWidth * 0.9f).toInt()
                        val minVisibleY = 0
                        val maxVisibleY = screenHeight - (viewHeight * 0.2f).toInt()

                        params.x = params.x.coerceIn(minVisibleX, maxVisibleX)
                        params.y = params.y.coerceIn(minVisibleY, maxVisibleY)

                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update view layout", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - downTime

                    if (!isDragging) {
                        // Check if it's a long press
                        if (pressDuration >= longPressThreshold) {
                            Log.i(TAG, "Long press detected - closing service")
                            stopSelf()
                        } else {
                            // Short press - toggle expand
                            Log.i(TAG, "Click detected - toggling expand")
                            toggleExpand()
                        }
                    } else {
                        Log.d(TAG, "Drag completed")
                        // Save the new position to settings
                        launch {
                            try {
                                settingsRepository.saveFloatingWindowPosition(params.x, params.y)
                                Log.d(TAG, "Saved floating window position: x=${params.x}, y=${params.y}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save floating window position", e)
                            }
                        }
                    }
                    false
                }
                else -> false
            }
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded

        // Update window params
        floatingView?.let { view ->
            val params = createWindowParams()
            windowManager.updateViewLayout(view, params)
        }
    }

    fun updateExpanded(expanded: Boolean) {
        if (isExpanded != expanded) {
            toggleExpand()
        }
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoGLM Floating Window",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating window for task monitoring"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("AutoGLM")
            .setContentText("Task monitoring active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        floatingView?.let {
            windowManager.removeView(it)
        }
        floatingView = null
        instance = null
        job.cancel()

        Log.d(TAG, "FloatingWindowService destroyed")
    }
}
