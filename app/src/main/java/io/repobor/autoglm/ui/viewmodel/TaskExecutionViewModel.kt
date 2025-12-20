package io.repobor.autoglm.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.repobor.autoglm.accessibility.AccessibilityExecutionEngine
import io.repobor.autoglm.accessibility.ExecutionEngine
import io.repobor.autoglm.accessibility.ShizukuExecutionEngine
import io.repobor.autoglm.accessibility.AccessibilityServiceHelper
import io.repobor.autoglm.accessibility.ShizukuHelper
import io.repobor.autoglm.accessibility.ShizukuServiceHelper
import io.repobor.autoglm.agent.AgentConfig
import io.repobor.autoglm.agent.PhoneAgent
import io.repobor.autoglm.data.ExecutionLogRepository
import io.repobor.autoglm.data.ExecutionMode
import io.repobor.autoglm.data.SettingsRepository
import io.repobor.autoglm.model.ModelClient
import io.repobor.autoglm.model.ModelConfig
import io.repobor.autoglm.ui.overlay.FloatingWindowService
import io.repobor.autoglm.util.ConversationFormatter

/**
 * ViewModel for managing task execution state across Activity lifecycle and PiP transitions.
 * Centralizes state management for PhoneAgent and task execution.
 */
class TaskExecutionViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "TaskExecutionViewModel"
    }

    private val logRepository = ExecutionLogRepository(context)

    // Task execution state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _maxSteps = MutableStateFlow(30)
    val maxSteps: StateFlow<Int> = _maxSteps.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    private val _finalResult = MutableStateFlow<String?>(null)
    val finalResult: StateFlow<String?> = _finalResult.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    private val _conversationLog = MutableStateFlow<List<ConversationFormatter.ConversationItem>>(emptyList())
    val conversationLog: StateFlow<List<ConversationFormatter.ConversationItem>> = _conversationLog.asStateFlow()

    private val _conversationDisplayLog = MutableStateFlow<List<String>>(emptyList())
    val conversationDisplayLog: StateFlow<List<String>> = _conversationDisplayLog.asStateFlow()

    // Task completion event - emitted when task completes
    private val _taskCompletedFlow = MutableStateFlow(false)
    val taskCompletedFlow: StateFlow<Boolean> = _taskCompletedFlow.asStateFlow()

    private var phoneAgent: PhoneAgent? = null
    private var executionJob: Job? = null
    private var stepCollectionJob: Job? = null

    init {
        // Register this ViewModel with FloatingWindowStateHolder for access from service
        io.repobor.autoglm.ui.overlay.FloatingWindowStateHolder.setViewModel(this)
    }

    /**
     * Start a new task execution.
     *
     * @param task Task description from user
     * @param context Android context for display metrics
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun startTask(task: String, context: Context) {
        if (_isRunning.value) {
            Log.w(TAG, "Task already running, ignoring duplicate start request")
            return
        }

        // 使用 Dispatchers.Default 在后台线程线程池执行任务，而不是主线程
        // 这防止了ANR (Application Not Responding) 当任务进行长时间操作时
        // - 截图操作
        // - 网络API调用
        // - 手势执行
        // UI更新通过 StateFlow.value 自动回到主线程
        executionJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                // 在主线程上更新UI状态
                withContext(Dispatchers.Main) {
                    _isRunning.value = true
                    _logEntries.value = emptyList()
                    _conversationLog.value = emptyList()
                    _conversationDisplayLog.value = emptyList()
                    _finalResult.value = null
                    _currentStep.value = 0
                    _statusMessage.value = "Starting..."
                    _hasError.value = false
                    addLog("[Starting] Task starting...")
                }

                // 获取屏幕尺寸（可以在Default线程上执行）
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                context.display?.getRealMetrics(displayMetrics)
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                // 在主线程上启动浮窗服务（需要Context操作）
                if (canDrawOverlays(context)) {
                    withContext(Dispatchers.Main) {
                        startFloatingWindowService(context)
                        addLog("[Info] Floating window started")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        addLog("[Warning] Overlay permission not granted, floating window disabled")
                    }
                }

                // 获取设置（这是suspend操作）
                val apiEndpoint = settingsRepository.apiEndpoint.first()
                val apiKey = settingsRepository.apiKey.first()
                val modelName = settingsRepository.modelName.first()
                val maxSteps = settingsRepository.maxSteps.first()
                val language = settingsRepository.language.first()
                val executionMode = settingsRepository.executionMode.first()

                // 在主线程上更新maxSteps
                withContext(Dispatchers.Main) {
                    _maxSteps.value = maxSteps
                }

                // Create execution engine based on mode
                val executionEngine: ExecutionEngine = when (executionMode) {
                    ExecutionMode.ACCESSIBILITY -> {
                        addLog("[Info] Using Accessibility Service mode")

                        // Check if accessibility service is enabled
                        if (!AccessibilityServiceHelper.isAccessibilityServiceEnabled(context)) {
                            addLog("[Warning] Accessibility service not enabled")

                            // Try to auto-enable via WRITE_SECURE_SETTINGS
                            if (AccessibilityServiceHelper.hasWriteSecureSettingsPermission(context)) {
                                addLog("[Info] Attempting to auto-enable via WRITE_SECURE_SETTINGS...")

                                // Call tryEnableAccessibilityServiceDirect directly
                                val directEnabled = try {
                                    val service = android.content.ComponentName(context, io.repobor.autoglm.accessibility.AutoGLMAccessibilityService::class.java)
                                    val flattenedService = service.flattenToString()

                                    val enabledServices = android.provider.Settings.Secure.getString(
                                        context.contentResolver,
                                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                                    ) ?: ""

                                    val newEnabledServices = if (enabledServices.isEmpty()) {
                                        flattenedService
                                    } else {
                                        "$enabledServices:$flattenedService"
                                    }

                                    android.provider.Settings.Secure.putString(
                                        context.contentResolver,
                                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                                        newEnabledServices
                                    )

                                    android.provider.Settings.Secure.putInt(
                                        context.contentResolver,
                                        android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                                        1
                                    )

                                    true
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to enable accessibility service directly", e)
                                    false
                                }

                                if (directEnabled) {
                                    addLog("[Success] Accessibility service enabled via WRITE_SECURE_SETTINGS")
                                    // Wait for service to start
                                    kotlinx.coroutines.delay(1500)
                                } else {
                                    // Failed to enable - abort task
                                    val errorMsg = if (language == "zh") {
                                        "错误，没有打开无障碍"
                                    } else {
                                        "Error: Accessibility service not enabled"
                                    }
                                    addLog("[$errorMsg]")
                                    _finalResult.value = errorMsg
                                    _isRunning.value = false
                                    return@launch
                                }
                            } else {
                                // No permission to auto-enable - abort task
                                val errorMsg = if (language == "zh") {
                                    "错误：没有打开无障碍"
                                } else {
                                    "Error: Accessibility service not enabled"
                                }
                                addLog("[$errorMsg]")
                                _finalResult.value = errorMsg
                                _isRunning.value = false
                                return@launch
                            }
                        }

                        AccessibilityExecutionEngine(
                            context = context,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                            language = language
                        )
                    }
                    ExecutionMode.SHIZUKU -> {
                        addLog("[Info] Using Shizuku mode")

                        // Check if Shizuku is running
                        if (!ShizukuHelper.isShizukuRunning()) {
                            val errorMsg = "Shizuku is not running. Please start Shizuku first."
                            addLog("[$errorMsg]")
                            _finalResult.value = errorMsg
                            _isRunning.value = false
                            return@launch
                        }

                        // Check if we have Shizuku permission
                        if (!ShizukuHelper.checkShizukuPermission(context)) {
                            val errorMsg = "Shizuku permission not granted. Please grant permission in Shizuku app."
                            addLog("[$errorMsg]")
                            _finalResult.value = errorMsg
                            _isRunning.value = false
                            return@launch
                        }

                        // For Shizuku mode, try to bind the service if not already connected
                        if (!ShizukuHelper.isUserServiceConnected()) {
                            addLog("[Info] Waiting for Shizuku UserService to connect...")
                            // Try explicit bind first
                            io.repobor.autoglm.accessibility.ShizukuServiceHelper.getInstance().bindService()

                            // Wait for connection
                            val shizukuHelper = io.repobor.autoglm.accessibility.ShizukuHelper
                            if (!shizukuHelper.waitForUserService(timeoutMs = 10000)) {
                                val errorMsg = "Failed to connect to Shizuku UserService after timeout. Please check Shizuku is running properly."
                                addLog("[$errorMsg]")
                                _finalResult.value = errorMsg
                                _isRunning.value = false
                                return@launch
                            }
                        }

                        ShizukuExecutionEngine(
                            context = context,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight
                        )
                    }
                }

                // Check if execution engine is available (double-check)
                if (!executionEngine.isAvailable()) {
                    val errorMsg = when (executionMode) {
                        ExecutionMode.ACCESSIBILITY -> {
                            if (language == "zh") "错误，没有打开无障碍" else "Error: Accessibility service not enabled"
                        }
                        ExecutionMode.SHIZUKU -> "Shizuku is not running or permission not granted. Please start Shizuku and grant permission."
                    }
                    addLog("[$errorMsg]")
                    _finalResult.value = errorMsg
                    _isRunning.value = false
                    return@launch
                }

                // Create model client
                val modelConfig = ModelConfig(
                    baseUrl = apiEndpoint,
                    apiKey = apiKey,
                    modelName = modelName
                )
                val modelClient = ModelClient(modelConfig)

                // Create agent config
                val agentConfig = AgentConfig(
                    maxSteps = maxSteps,
                    lang = language,
                    verbose = true
                )

                // Create phone agent
                phoneAgent = PhoneAgent(
                    context = context,
                    modelClient = modelClient,
                    agentConfig = agentConfig,
                    executionEngine = executionEngine,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )

                // Start collecting step updates from PhoneAgent
                stepCollectionJob = launch {
                    // Collect both step number and detail
                    launch {
                        phoneAgent!!.currentStepFlow.collect { step ->
                            _currentStep.value = step
                        }
                    }

                    launch {
                        phoneAgent!!.currentStepDetailFlow.collect { detail ->
                            if (detail.isNotEmpty()) {
                                addLog(detail)
                            }
                        }
                    }
                }

                // Run task
                val startTime = System.currentTimeMillis()
                val result = phoneAgent!!.run(task)
                val endTime = System.currentTimeMillis()

                addLog("[Completed] $result")
                _finalResult.value = result
                _statusMessage.value = result
                _hasError.value = !result.startsWith("Error") && !result.contains("failed", ignoreCase = true)

                // Save execution log to database
                try {
                    val agent = phoneAgent!!
                    val logger = agent.getExecutionLogger()
                    val isSuccess = !result.startsWith("Error") && !result.contains("failed", ignoreCase = true)
                    val status = when {
                        isSuccess -> if (language == "zh") "成功" else "Success"
                        result.contains("stopped", ignoreCase = true) || result.contains("停止", ignoreCase = false) ->
                            if (language == "zh") "停止" else "Stopped"
                        else -> if (language == "zh") "失败" else "Failed"
                    }

                    // Format conversation for display (happens before saving log)
                    val conversationList = agent.getContext()
                    val stepDetails = logger.getSteps()
                    val formattedItems = ConversationFormatter.formatConversationFromList(conversationList, stepDetails, language)
                    val displayLog = ConversationFormatter.formatItemsForDisplay(formattedItems, language)

                    // Update conversation display log
                    withContext(Dispatchers.Main) {
                        _conversationLog.value = formattedItems
                        _conversationDisplayLog.value = displayLog
                    }

                    val logId = logRepository.insertLog(
                        task = task,
                        status = status,
                        result = result,
                        steps = agent.getCurrentStep(),
                        startTimestamp = logger.getStartTimestamp(),
                        endTimestamp = logger.getEndTimestamp(),
                        language = language,
                        modelName = modelName,
                        conversationHistory = conversationList,
                        stepDetails = stepDetails,
                        isSuccess = isSuccess,
                        errorMessage = if (!isSuccess) result else null
                    )
                    Log.d(TAG, "Saved execution log with ID: $logId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save execution log", e)
                }

                // Emit task completion event to trigger UI navigation to logs
                withContext(Dispatchers.Main) {
                    _taskCompletedFlow.value = true
                    // Expand floating window to show results
                    viewModelScope.launch {
                        try {
                            io.repobor.autoglm.ui.overlay.FloatingWindowStateHolder.requestExpand()
                        } catch (e: Exception) {
                            Log.d(TAG, "Failed to request expand floating window", e)
                        }
                    }
                }

            } catch (e: CancellationException) {
                // Task was cancelled - this is expected when user stops the task
                Log.i(TAG, "Task cancelled by user")
                val language = settingsRepository.language.first()
                val cancelMsg = if (language == "zh") "任务已停止" else "Task stopped"
                addLog("[Stopped] $cancelMsg")
                _finalResult.value = cancelMsg
                _statusMessage.value = cancelMsg

                // Save cancellation log to database
                try {
                    val modelName = settingsRepository.modelName.first()
                    val status = if (language == "zh") "停止" else "Stopped"

                    logRepository.insertLog(
                        task = task,
                        status = status,
                        result = cancelMsg,
                        steps = _currentStep.value,
                        startTimestamp = System.currentTimeMillis(),
                        endTimestamp = System.currentTimeMillis(),
                        language = language,
                        modelName = modelName,
                        conversationHistory = phoneAgent?.getContext() ?: emptyList(),
                        stepDetails = phoneAgent?.getExecutionLogger()?.getSteps() ?: emptyList(),
                        isSuccess = false,
                        errorMessage = cancelMsg
                    )
                } catch (logError: Exception) {
                    Log.e(TAG, "Failed to save cancellation log", logError)
                }

                // Re-throw to properly cancel the coroutine
                throw e

            } catch (e: Exception) {
                Log.e(TAG, "Task execution failed", e)
                val errorMsg = "Error: ${e.message}"
                addLog("[Error] $errorMsg")
                _finalResult.value = errorMsg
                _statusMessage.value = errorMsg
                _hasError.value = true

                // Save error log to database
                try {
                    val language = settingsRepository.language.first()
                    val modelName = settingsRepository.modelName.first()
                    val status = if (language == "zh") "失败" else "Failed"

                    logRepository.insertLog(
                        task = task,
                        status = status,
                        result = errorMsg,
                        steps = _currentStep.value,
                        startTimestamp = System.currentTimeMillis(),
                        endTimestamp = System.currentTimeMillis(),
                        language = language,
                        modelName = modelName,
                        conversationHistory = emptyList(),
                        stepDetails = emptyList(),
                        isSuccess = false,
                        errorMessage = errorMsg
                    )
                } catch (logError: Exception) {
                    Log.e(TAG, "Failed to save error log", logError)
                }
            } finally {
                _isRunning.value = false
                stepCollectionJob?.cancel()
                stepCollectionJob = null

                // Stop floating window service
//                stopFloatingWindowService(context)
            }
        }
    }

    /**
     * Stop the current task execution immediately.
     *
     * This will:
     * 1. Set isRunning flag to false to break the main loop
     * 2. Cancel the execution Job (causes CancellationException in any suspend function)
     * 3. Cancel step collection job
     * 4. Immediately stop floating window service
     *
     * The task will stop at the next cancellation checkpoint:
     * - ensureActive() call in the loop
     * - Any suspend function (delay, IO operations)
     * - Between steps
     */
    fun stopTask() {
        if (!_isRunning.value) {
            Log.w(TAG, "No task running to stop")
            return
        }

        Log.i(TAG, "Stopping task immediately")

        // 1. Set running flag to false
        phoneAgent?.stop()

        // 2. Cancel the execution job - this interrupts any ongoing operations
        // CancellationException will be thrown at the next suspension point
        executionJob?.cancel()

        // 3. Immediately cancel step collection
        stepCollectionJob?.cancel()

        // 4. Update UI state
        _isRunning.value = false
        addLogSync("[Stopped] Task stopped by user")

        Log.i(TAG, "Task cancellation initiated")
    }

    /**
     * Reset the task completion flag.
     * Called by UI after navigating to logs screen.
     */
    fun resetTaskCompletedFlag() {
        _taskCompletedFlow.value = false
    }

    /**
     * Check if app has overlay permission.
     */
    private fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Permission not needed before Android M
        }
    }

    /**
     * Start floating window service.
     */
    private fun startFloatingWindowService(context: Context) {
        try {
            val intent = Intent(context, FloatingWindowService::class.java)
            try {
                context.startForegroundService(intent)
                Log.d(TAG, "Floating window service started as foreground service")
            } catch (e: Exception) {
                // If foreground service fails (timeout or other issues), fall back to regular service
                Log.w(TAG, "Failed to start foreground service (${e.javaClass.simpleName}), falling back to regular service", e)
                try {
                    context.startService(intent)
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Failed to start service even with fallback", fallbackError)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start floating window service", e)
        }
    }

    /**
     * Stop floating window service.
     */
    private fun stopFloatingWindowService(context: Context) {
        try {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Floating window service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop floating window service", e)
        }
    }

    /**
     * Add a log entry (coroutine-safe).
     * Can be called from any thread/dispatcher.
     * UI updates are automatically dispatched to Main thread.
     */
    private suspend fun addLog(message: String) {
        withContext(Dispatchers.Main) {
            _logEntries.value = _logEntries.value + message
        }
    }

    /**
     * Add a log entry synchronously (for non-coroutine contexts like stopTask).
     * Uses viewModelScope to dispatch to main thread safely.
     */
    private fun addLogSync(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _logEntries.value = _logEntries.value + message
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, cleaning up resources")
        phoneAgent?.stop()
        executionJob?.cancel()
        stepCollectionJob?.cancel()
        io.repobor.autoglm.ui.overlay.FloatingWindowStateHolder.clearViewModel()
    }
}
