package io.repobor.autoglm.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for communicating with OpenAI-compatible API.
 * Handles chat completions with vision support.
 */
class ModelClient(private val config: ModelConfig) {

    companion object {
        private const val TAG = "ModelClient"
        private const val TIMEOUT_SECONDS = 300L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Send a chat completion request with messages.
     *
     * @param messages List of message maps (system, user, assistant)
     * @return The model's response content string
     * @throws IOException if network request fails
     * @throws Exception if response parsing fails
     */
    suspend fun chatCompletion(messages: List<Map<String, Any>>): String = withContext(Dispatchers.IO) {
        try {
            // Build request body
            val requestBody = buildRequestBody(messages)
            val requestBodyJson = Json.encodeToString(JsonObject.serializer(), requestBody)

            Log.d(TAG, "Request URL: ${config.baseUrl}/chat/completions")
            Log.d(TAG, "Request body size: ${requestBodyJson.length} chars")

            // Build HTTP request
            val request = Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            // Execute request
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "API error: ${response.code} - $errorBody")
                throw IOException("API request failed: ${response.code} - ${response.message}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            Log.d(TAG, "Response received, length: ${responseBody.length}")

            // Parse response
            val responseJson = json.parseToJsonElement(responseBody).jsonObject
            val content = extractContent(responseJson)

            Log.d(TAG, "Extracted content: $content")

            return@withContext content
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in chat completion", e)
            throw e
        }
    }

    /**
     * Build the request body JSON object.
     *
     * @param messages List of message maps
     * @return JsonObject ready for serialization
     */
    private fun buildRequestBody(messages: List<Map<String, Any>>): JsonObject {
        return buildJsonObject {
            put("model", config.modelName)
            put("messages", buildJsonArray {
                messages.forEach { message ->
                    add(convertMessageToJson(message))
                }
            })
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature)
            put("top_p", config.topP)
            put("frequency_penalty", config.frequencyPenalty)

            // Add extra parameters if present
            config.extraBody.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
    }

    /**
     * Convert a message map to JsonObject.
     *
     * @param message The message map with role and content
     * @return JsonObject representation
     */
    private fun convertMessageToJson(message: Map<String, Any>): JsonObject {
        return buildJsonObject {
            val role = message["role"] as? String ?: "user"
            put("role", role)

            val content = message["content"]
            when (content) {
                is String -> {
                    put("content", content)
                }
                is List<*> -> {
                    put("content", buildJsonArray {
                        @Suppress("UNCHECKED_CAST")
                        (content as List<Map<String, Any>>).forEach { item ->
                            add(convertContentItemToJson(item))
                        }
                    })
                }
                else -> {
                    put("content", content.toString())
                }
            }
        }
    }

    /**
     * Convert a content item (text or image_url) to JsonObject.
     *
     * @param item The content item map
     * @return JsonObject representation
     */
    private fun convertContentItemToJson(item: Map<String, Any>): JsonObject {
        return buildJsonObject {
            val type = item["type"] as? String ?: "text"
            put("type", type)

            when (type) {
                "text" -> {
                    val text = item["text"] as? String ?: ""
                    put("text", text)
                }
                "image_url" -> {
                    val imageUrl = item["image_url"] as? Map<*, *>
                    if (imageUrl != null) {
                        put("image_url", buildJsonObject {
                            val url = imageUrl["url"] as? String ?: ""
                            put("url", url)
                        })
                    }
                }
            }
        }
    }

    /**
     * Extract content string from API response JSON.
     * Follows OpenAI response format: choices[0].message.content
     *
     * @param responseJson The parsed response JSON object
     * @return The content string
     */
    private fun extractContent(responseJson: JsonObject): String {
        val choices = responseJson["choices"]?.jsonArray
            ?: throw Exception("No 'choices' in response")

        if (choices.isEmpty()) {
            throw Exception("Empty choices array")
        }

        val firstChoice = choices[0].jsonObject
        val message = firstChoice["message"]?.jsonObject
            ?: throw Exception("No 'message' in first choice")

        val content = message["content"]?.jsonPrimitive?.content
            ?: throw Exception("No 'content' in message")

        return content
    }

    /**
     * Test the API connection.
     *
     * @return true if connection is successful, false otherwise
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val testMessages = listOf(
                mapOf(
                    "role" to "user",
                    "content" to "Hello"
                )
            )

            chatCompletion(testMessages)
            Log.i(TAG, "Connection test successful")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }

    /**
     * Get model information.
     *
     * @return String with model name and base URL
     */
    fun getModelInfo(): String {
        return "Model: ${config.modelName}, Endpoint: ${config.baseUrl}"
    }
}
