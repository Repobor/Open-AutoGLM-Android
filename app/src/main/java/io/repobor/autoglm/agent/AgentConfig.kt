package io.repobor.autoglm.agent

import io.repobor.autoglm.config.SystemPrompts

/**
 * Configuration for the PhoneAgent
 */
data class AgentConfig(
    val maxSteps: Int = 100,
    val deviceId: String? = null,
    val lang: String = "zh",
    val verbose: Boolean = true
) {
    val systemPrompt: String
        get() = SystemPrompts.getPromptByLanguage(lang)
}
