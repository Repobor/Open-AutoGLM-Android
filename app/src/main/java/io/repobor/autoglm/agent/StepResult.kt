package io.repobor.autoglm.agent

/**
 * Result of a single agent step
 */
data class StepResult(
    val success: Boolean,
    val finished: Boolean,
    val action: Map<String, Any>?,
    val thinking: String,
    val message: String? = null
)
