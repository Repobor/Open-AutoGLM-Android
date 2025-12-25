package io.repobor.autoglm.agent

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import io.repobor.autoglm.accessibility.AutoGLMAccessibilityService
import io.repobor.autoglm.accessibility.ExecutionEngine
import io.repobor.autoglm.accessibility.ScreenshotCapture
import io.repobor.autoglm.actions.ActionHandler
import io.repobor.autoglm.actions.ActionParser
import io.repobor.autoglm.actions.ExecutionEngineActionHandler
import io.repobor.autoglm.model.ModelClient
import kotlinx.coroutines.currentCoroutineContext

/**
 * Main orchestrator for phone automation tasks.
 * Coordinates screenshot capture, model interaction, and action execution.
 */
class PhoneAgent(
    private val context: Context,
    private val modelClient: ModelClient,
    private val agentConfig: AgentConfig,
    private val executionEngine: ExecutionEngine,
    private val screenWidth: Int = 1080,
    private val screenHeight: Int = 2400
) {

    companion object {
        private const val TAG = "PhoneAgent"
        private const val ACTION_DELAY_MS = 200L
    }

    /**
     * Execution logger for tracking step details.
     */
    private val executionLogger = ExecutionLogger()

    /**
     * Conversation context (message history).
     */
    private val messageContext = mutableListOf<Map<String, Any>>()

    /**
     * Current step number (internal mutable state).
     */
    private var currentStep = 0

    /**
     * Current step number as StateFlow for reactive UI updates.
     */
    private val _currentStepFlow = MutableStateFlow(0)
    val currentStepFlow: StateFlow<Int> = _currentStepFlow.asStateFlow()

    /**
     * Current step detail for UI display (thinking, action, etc).
     */
    private val _currentStepDetailFlow = MutableStateFlow("")
    val currentStepDetailFlow: StateFlow<String> = _currentStepDetailFlow.asStateFlow()

    /**
     * Whether the agent is currently running.
     */
    @Volatile
    private var isRunning = false

    /**
     * Mutex to prevent concurrent task execution.
     */
    private val executionMutex = Mutex()

    /**
     * Run a complete task until finished or max steps reached.
     *
     * @param task The user task description
     * @return Final result message
     */
    suspend fun run(task: String): String {
        // Acquire lock to prevent concurrent execution
        return executionMutex.withLock {
            try {
                // Double-check running state after acquiring lock
                if (isRunning) {
                    Log.w(TAG, "Task already running, ignoring duplicate request")
                    return@withLock "Task already running"
                }

                reset()
                isRunning = true
                Log.i(TAG, "Starting task: $task")

                executeTaskInternal(task)
            } finally {
                isRunning = false
            }
        }
    }

    /**
     * Internal method to execute the task.
     * Must be called while holding the executionMutex.
     *
     * The task will respond to cancellation requests immediately at:
     * - Coroutine checkpoints (coroutineContext.ensureActive())
     * - Between steps (loop condition)
     * - During long-running operations (model API calls)
     * - After action execution
     */
    private suspend fun executeTaskInternal(task: String): String {
        try {
            // Start execution logging
            executionLogger.start()

            // Add system prompt
            messageContext.add(MessageBuilder.createSystemMessage(agentConfig.systemPrompt))

            var finished = false
            var finalMessage = "Task incomplete"

            while (currentStep < agentConfig.maxSteps && !finished && isRunning) {
                // Check if coroutine was cancelled - this will throw CancellationException if cancelled
                // Allows immediate response to stop request even in middle of loop
                currentCoroutineContext().ensureActive()

                // Double-check running flag for user-initiated stops
                // (ensureActive() handles Job.cancel() from ViewModel)
                if (!isRunning) {
                    Log.i(TAG, "Task stopped by user at step $currentStep")
                    finalMessage = "Task stopped by user"
                    break
                }

                currentStep++
                _currentStepFlow.value = currentStep
                Log.i(TAG, "CurrentStep = $currentStep")

                val stepResult = if (currentStep == 1) {
                    // First step: include full task description
                    executeStep(task, isFirst = true)
                } else {
                    // Subsequent steps: continue without repeating task
                    executeStep(null, isFirst = false)
                }

                // Update step detail for UI display
                val stepDetail = buildStepDisplayText(currentStep, stepResult)
                _currentStepDetailFlow.value = stepDetail

                if (!stepResult.success) {
                    Log.w(TAG, "Step $currentStep failed: ${stepResult.message}")
                    if (stepResult.message != null) {
                        finalMessage = stepResult.message
                    }
                    // Continue despite failure unless explicitly finished
                }

                if (stepResult.finished) {
                    finished = true
                    finalMessage = stepResult.message ?: "Task completed"
                    Log.i(TAG, "Task finished at step $currentStep: $finalMessage")
                }

                if (agentConfig.verbose) {
                    Log.d(TAG, "Step $currentStep: ${stepResult.thinking}")
                    if (stepResult.action != null) {
                        Log.d(TAG, "Action: ${ActionParser.getActionDescription(stepResult.action)}")
                    }
                }
            }

            if (currentStep >= agentConfig.maxSteps && !finished) {
                finalMessage = "Max steps (${agentConfig.maxSteps}) reached"
                Log.w(TAG, finalMessage)
            }

            // End execution logging
            executionLogger.end()

            return finalMessage
        } catch (e: CancellationException) {
            // Task was cancelled - immediately propagate to allow proper cleanup in caller
            Log.i(TAG, "Task cancelled at step $currentStep")
            executionLogger.end()
            throw e  // Re-throw to let ViewModel handle it
        } catch (e: Exception) {
            executionLogger.end()
            Log.e(TAG, "Unexpected error in task execution", e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Execute a single step of the agent.
     *
     * This method will respond to cancellation requests at multiple checkpoints:
     * - At start (before screenshot)
     * - After screenshot (before model call)
     * - After model call (before action execution)
     * - After action execution
     *
     * @param userPrompt Optional user prompt (used in first step)
     * @param isFirst Whether this is the first step
     * @return StepResult containing action and result
     */
    private suspend fun executeStep(userPrompt: String?, isFirst: Boolean): StepResult {
        try {
            // Check for cancellation at step start
            coroutineContext.ensureActive()

            // Check if execution engine is available
            if (!executionEngine.isAvailable()) {
                return StepResult(
                    success = false,
                    finished = false,
                    action = null,
                    thinking = "Execution engine not available",
                    message = "Please enable the selected execution mode (Accessibility Service or Shizuku)"
                )
            }

            // Check for cancellation before screenshot
            coroutineContext.ensureActive()

            // Capture screenshot
            val screenshot = executionEngine.captureScreenshot()

            // Check for cancellation after screenshot (before model call)
            coroutineContext.ensureActive()

            // Get current app using execution engine (works for both Accessibility and Shizuku modes)
            val currentApp = try {
                executionEngine.getCurrentAppName()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get current app name", e)
                "Unknown"
            }
            // Build user message
            val userMessage = if (isFirst && userPrompt != null) {
                MessageBuilder.buildTaskMessage(
                    task = userPrompt,
                    imageBase64 = screenshot.base64Data,
                    currentApp = currentApp,
                    stepNumber = currentStep,
                    isFirstStep = true
                )
            } else {
                MessageBuilder.buildContinuationMessage(
                    imageBase64 = screenshot.base64Data,
                    currentApp = currentApp,
                    stepNumber = currentStep
                )
            }

            // Add to context
            messageContext.add(userMessage)

            // Check for cancellation before model call
            coroutineContext.ensureActive()

            // Call model
            val modelResponse = try {
                modelClient.chatCompletion(messageContext)
            } catch (e: Exception) {
                Log.e(TAG, "Model call failed", e)
                return StepResult(
                    success = false,
                    finished = false,
                    action = null,
                    thinking = "Model call failed: ${e.message}",
                    message = "Error: ${e.message}"
                )
            }

            // Check for cancellation after model call (before parsing)
            coroutineContext.ensureActive()

            // Extract thinking
            val thinking = ActionParser.extractThinking(modelResponse) ?: "No thinking provided"

            // Parse action
            val action = ActionParser.parseResponse(modelResponse)
            if (action == null) {
                Log.w(TAG, "Failed to parse action from response: $modelResponse")
                return StepResult(
                    success = false,
                    finished = false,
                    action = null,
                    thinking = thinking,
                    message = "Failed to parse action"
                )
            }

            // Validate action
            if (!ActionParser.validateAction(action)) {
                Log.w(TAG, "Invalid action: $action")
                return StepResult(
                    success = false,
                    finished = false,
                    action = action,
                    thinking = thinking,
                    message = "Invalid action parameters"
                )
            }

            // Check for cancellation before cleanup and action execution
            coroutineContext.ensureActive()

            // Remove image from context to save memory
            messageContext[messageContext.lastIndex] = MessageBuilder.removeImagesFromMessage(messageContext.last())

            // Add assistant response to context
            messageContext.add(MessageBuilder.createAssistantMessage(modelResponse))

            // Check if finish action
            if (action["action"] == "finish") {
                val finishMessage = action["message"] as? String ?: "Task completed"
                return StepResult(
                    success = true,
                    finished = true,
                    action = action,
                    thinking = thinking,
                    message = finishMessage
                )
            }

            // Check for cancellation before action execution - this is critical
            // so that we don't execute gestures/actions after user clicks stop
            coroutineContext.ensureActive()

            // Execute action
            val actionHandler = ExecutionEngineActionHandler(
                executionEngine = executionEngine,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                language = agentConfig.lang
            )

            val actionResult = actionHandler.executeAction(action)

            // Check if coroutine was cancelled after action execution
            coroutineContext.ensureActive()

            // Add delay after action
            delay(ACTION_DELAY_MS)

            // Log this step
            val actionType = action["action"] as? String ?: "unknown"
            val actionDesc = ActionParser.getActionDescription(action)
            executionLogger.addStep(
                stepNumber = currentStep,
                action = actionType,
                actionDescription = actionDesc,
                thinking = thinking,
                success = actionResult.success,
                message = actionResult.message
            )

            return StepResult(
                success = actionResult.success,
                finished = actionResult.shouldFinish,
                action = action,
                thinking = thinking,
                message = actionResult.message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in executeStep", e)
            return StepResult(
                success = false,
                finished = false,
                action = null,
                thinking = "Error: ${e.message}",
                message = "Unexpected error: ${e.message}"
            )
        }
    }

    /**
     * Execute a single step manually (for UI control).
     *
     * @param task Optional task description (used in first step)
     * @return StepResult
     */
    suspend fun step(task: String? = null): StepResult {
        currentStep++
        _currentStepFlow.value = currentStep
        return executeStep(task, isFirst = task != null)
    }

    /**
     * Reset the agent state.
     * Clears conversation context and resets step counter.
     */
    fun reset() {
        messageContext.clear()
        currentStep = 0
        _currentStepFlow.value = 0
        isRunning = false
        executionLogger.reset()
        Log.d(TAG, "Agent reset")
    }

    /**
     * Stop the current task execution.
     */
    fun stop() {
        isRunning = false
        Log.i(TAG, "Agent stopped")
    }

    /**
     * Get current step number.
     */
    fun getCurrentStep(): Int = currentStep

    /**
     * Get total number of messages in context.
     */
    fun getContextSize(): Int = messageContext.size

    /**
     * Get number of images in context.
     */
    fun getImageCount(): Int = MessageBuilder.countTotalImages(messageContext)

    /**
     * Check if agent is currently running.
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Get conversation context (for debugging/logging).
     */
    fun getContext(): List<Map<String, Any>> = messageContext.toList()

    /**
     * Get execution logger for retrieving step details.
     */
    fun getExecutionLogger(): ExecutionLogger = executionLogger

    /**
     * Build display text for a step showing thinking, action, and result.
     */
    private fun buildStepDisplayText(stepNumber: Int, stepResult: StepResult): String {
        val lines = mutableListOf<String>()

        lines.add("━━━━━━━━━━━━━━━━━━━━━")
        lines.add("步骤 $stepNumber")

        // Add thinking
        if (stepResult.thinking.isNotEmpty() && stepResult.thinking != "No thinking provided") {
            val thinkingPreview = if (stepResult.thinking.length > 100) {
                stepResult.thinking.substring(0, 100) + "..."
            } else {
                stepResult.thinking
            }
            lines.add("💭 思考: $thinkingPreview")
        }

        // Add action description
        if (stepResult.action != null) {
            val actionType = stepResult.action["action"] as? String ?: "unknown"
            val actionDesc = ActionParser.getActionDescription(stepResult.action)
            lines.add("🎯 动作: $actionDesc")
        }

        // Add result
        val statusIcon = if (stepResult.success) "✓" else "✗"
        val statusText = if (stepResult.success) "成功" else "失败"
        lines.add("$statusIcon 结果: $statusText")

        if (stepResult.message != null && stepResult.message.isNotEmpty()) {
            lines.add("   ${stepResult.message}")
        }

        return lines.joinToString("\n")
    }
}
