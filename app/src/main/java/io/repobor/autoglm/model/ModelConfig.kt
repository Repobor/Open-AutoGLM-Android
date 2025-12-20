package io.repobor.autoglm.model

/**
 * Configuration for the AI model API
 */
data class ModelConfig(
    val baseUrl: String = "http://localhost:8000/v1",
    val apiKey: String = "EMPTY",
    val modelName: String = "autoglm-phone-9b",
    val maxTokens: Int = 3000,
    val temperature: Float = 0.0f,
    val topP: Float = 0.85f,
    val frequencyPenalty: Float = 0.2f,
    val extraBody: Map<String, Any> = emptyMap()
)
