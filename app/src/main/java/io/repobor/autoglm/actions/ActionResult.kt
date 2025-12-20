package io.repobor.autoglm.actions

/**
 * Result of an action execution
 */
data class ActionResult(
    val success: Boolean,
    val shouldFinish: Boolean,
    val message: String? = null,
    val requiresConfirmation: Boolean = false
)
