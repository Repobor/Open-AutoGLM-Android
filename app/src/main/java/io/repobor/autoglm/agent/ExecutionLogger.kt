package io.repobor.autoglm.agent

import io.repobor.autoglm.data.database.StepDetail

/**
 * Helper class for tracking execution details during task execution.
 */
class ExecutionLogger {
    private val steps = mutableListOf<StepDetail>()
    private var startTimestamp: Long = 0L
    private var endTimestamp: Long = 0L

    /**
     * Mark the start of execution.
     */
    fun start() {
        startTimestamp = System.currentTimeMillis()
        steps.clear()
    }

    /**
     * Mark the end of execution.
     */
    fun end() {
        endTimestamp = System.currentTimeMillis()
    }

    /**
     * Add a step to the log.
     */
    fun addStep(
        stepNumber: Int,
        action: String,
        actionDescription: String,
        thinking: String,
        success: Boolean,
        message: String?
    ) {
        steps.add(
            StepDetail(
                stepNumber = stepNumber,
                timestamp = System.currentTimeMillis(),
                action = action,
                actionDescription = actionDescription,
                thinking = thinking,
                success = success,
                message = message
            )
        )
    }

    /**
     * Get all steps.
     */
    fun getSteps(): List<StepDetail> = steps.toList()

    /**
     * Get start timestamp.
     */
    fun getStartTimestamp(): Long = startTimestamp

    /**
     * Get end timestamp.
     */
    fun getEndTimestamp(): Long = endTimestamp

    /**
     * Get duration in milliseconds.
     */
    fun getDuration(): Long = endTimestamp - startTimestamp

    /**
     * Reset the logger.
     */
    fun reset() {
        steps.clear()
        startTimestamp = 0L
        endTimestamp = 0L
    }
}
