package io.repobor.autoglm

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.repobor.autoglm.accessibility.ShizukuHelper
import io.repobor.autoglm.accessibility.ShizukuServiceHelper
import io.repobor.autoglm.config.I18n
import io.repobor.autoglm.data.SettingsRepository
import io.repobor.autoglm.ui.screens.NewLogScreen
import io.repobor.autoglm.ui.screens.SettingsScreen
import io.repobor.autoglm.ui.screens.TaskScreen
import io.repobor.autoglm.ui.theme.AutoGLMTheme
import io.repobor.autoglm.ui.viewmodel.TaskExecutionViewModel
import io.repobor.autoglm.ui.viewmodel.TaskExecutionViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku

/**
 * Main activity for AutoGLM application.
 * Provides navigation between Settings, Task, and Log screens.
 * Supports Picture-in-Picture (PiP) mode for task monitoring.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var settingsRepository: SettingsRepository
    private var isInPipMode by mutableStateOf(false)

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        if (!Shizuku.isPreV11()) {
            // Request permission when binder is received
            if (ShizukuHelper.isShizukuRunning() && !ShizukuHelper.checkShizukuPermission(
                    applicationContext
                )
            ) {
                Log.i(TAG, "Requesting Shizuku permission")
                ShizukuHelper.requestShizukuPermission()
            } else if (ShizukuHelper.isShizukuRunning() && ShizukuHelper.checkShizukuPermission(
                    applicationContext
                )
            ) {
                // Permission already granted, bind UserService
                Log.i(TAG, "Shizuku permission already granted, binding UserService")
                ShizukuServiceHelper.getInstance().bindService()
            }
        }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead - service disconnected, unbinding UserService")
        ShizukuServiceHelper.getInstance().unbindService()
    }

    private val shizukuRequestListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Shizuku permission ${if (granted) "granted" else "denied"}")

            // Bind UserService after permission is granted
            if (granted) {
                Log.i(TAG, "Shizuku permission granted, binding UserService")
                ShizukuServiceHelper.getInstance().bindService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize settings repository
        settingsRepository = SettingsRepository(applicationContext)

        // Register Shizuku listeners with sticky binder received listener
        // This ensures we get called immediately if binder is already available
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuRequestListener)

        setContent {
            AutoGLMTheme {
                AutoGLMApp(
                    settingsRepository = settingsRepository,
                    isInPipMode = isInPipMode
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind UserService
        ShizukuServiceHelper.getInstance().unbindService()
        // Remove all Shizuku listeners
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuRequestListener)
    }

    /**
     * Called when user leaves the activity (e.g., presses Home).
     * Automatically enter PiP mode if task is running.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    /**
     * Called when PiP mode changes.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        Log.d(TAG, "PiP mode changed: $isInPictureInPictureMode")
    }


    /**
     * Navigation destinations.
     */
    enum class Screen(val title: String, val icon: ImageVector) {
        SETTINGS("设置", Icons.Default.Settings),
        TASK("任务", Icons.Default.PlayArrow),
        LOG("日志", Icons.AutoMirrored.Filled.List)
    }

    /**
     * Main app composable with navigation.
     * Supports conditional rendering for PiP mode.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AutoGLMApp(
        settingsRepository: SettingsRepository,
        isInPipMode: Boolean
    ) {
        val context = LocalContext.current

        // Collect language setting
        val language by settingsRepository.language.collectAsState(initial = "zh")

        // Get ViewModel for task execution state
        val viewModel: TaskExecutionViewModel = viewModel(
            factory = TaskExecutionViewModelFactory(context, settingsRepository)
        )

        // Collect task state from ViewModel
        val isRunning by viewModel.isRunning.collectAsState()
        val currentStep by viewModel.currentStep.collectAsState()
        val maxSteps by viewModel.maxSteps.collectAsState()
        val logEntries by viewModel.logEntries.collectAsState()

        // Show full navigation UI
        var currentScreen by remember { mutableStateOf(Screen.SETTINGS) }
        var initialTaskForScreen by remember { mutableStateOf("") }


        // Collect task completion event
        val taskCompleted by viewModel.taskCompletedFlow.collectAsState()

        // Auto-navigate to TASK screen when task completes and bring app to foreground
        LaunchedEffect(taskCompleted) {
            if (taskCompleted) {

                // Switch to TASK screen
                currentScreen = Screen.TASK

                // Reset the completion flag so it doesn't navigate multiple times
                viewModel.resetTaskCompletedFlag()
            }
        }

        // Get localized strings
        val strings = I18n.getStringBundle(language)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.appName) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    Screen.values().forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(getScreenTitle(screen, language)) },
                            selected = currentScreen == screen,
                            onClick = {
                                currentScreen = screen
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentScreen) {
                    Screen.SETTINGS -> SettingsScreen(settingsRepository)
                    Screen.TASK -> {
                        TaskScreen(settingsRepository, viewModel, initialTaskForScreen)
                        // Reset initial task after assignment
                        if (initialTaskForScreen.isNotEmpty()) {
                            initialTaskForScreen = ""
                        }
                    }

                    Screen.LOG -> NewLogScreen(
                        onRepeatTask = { task ->
                            initialTaskForScreen = task
                            currentScreen = Screen.TASK
                        }
                    )
                }
            }
        }
    }

    /**
     * Get localized screen title.
     */
    private fun getScreenTitle(screen: Screen, language: String): String {
        return if (language == "zh") {
            when (screen) {
                Screen.SETTINGS -> I18n.Chinese.NAV_SETTINGS
                Screen.TASK -> I18n.Chinese.NAV_TASK
                Screen.LOG -> I18n.Chinese.NAV_LOG
            }
        } else {
            when (screen) {
                Screen.SETTINGS -> I18n.English.NAV_SETTINGS
                Screen.TASK -> I18n.English.NAV_TASK
                Screen.LOG -> I18n.English.NAV_LOG
            }
        }
    }
}
