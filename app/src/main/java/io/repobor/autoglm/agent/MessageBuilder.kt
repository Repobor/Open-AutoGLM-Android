package io.repobor.autoglm.agent

import android.util.Log

/**
 * Builds message objects for OpenAI-compatible API calls.
 * Handles message formatting with text and image content.
 */
object MessageBuilder {
    private const val TAG = "MessageBuilder"

    /**
     * Create a system message.
     *
     * @param content The system prompt content
     * @return Message map with role and content
     */
    fun createSystemMessage(content: String): Map<String, Any> {
        return mapOf(
            "role" to "system",
            "content" to content
        )
    }

    /**
     * Create a user message with optional image.
     *
     * @param text The text content
     * @param imageBase64 Optional base64-encoded image (without data URI prefix)
     * @return Message map with role and content (text + optional image)
     */
    fun createUserMessage(text: String, imageBase64: String? = null): Map<String, Any> {
        val contentList = mutableListOf<Map<String, Any>>()

        // Add image first if present (vision models typically expect image before text)
        if (imageBase64 != null) {
            val imageSizeMB = (imageBase64.length / 1024.0 / 1024.0)
            Log.d(TAG, "Adding image to message: ${String.format("%.2f", imageSizeMB)} MB (${imageBase64.length} bytes)")
            contentList.add(
                mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf(
                        "url" to "data:image/jpeg;base64,$imageBase64"
                    )
                )
            )
        }

        // Add text content
        contentList.add(
            mapOf(
                "type" to "text",
                "text" to text
            )
        )

        return mapOf(
            "role" to "user",
            "content" to contentList
        )
    }

    /**
     * Create an assistant message.
     *
     * @param content The assistant response content
     * @return Message map with role and content
     */
    fun createAssistantMessage(content: String): Map<String, Any> {
        return mapOf(
            "role" to "assistant",
            "content" to content
        )
    }

    /**
     * Remove images from a message to save memory.
     * Keeps only text content from user messages.
     *
     * @param message The message to process
     * @return Message with images removed
     */
    fun removeImagesFromMessage(message: Map<String, Any>): Map<String, Any> {
        val role = message["role"] as? String ?: return message
        val content = message["content"]

        // If content is a string, return as-is
        if (content is String) {
            return message
        }

        // If content is a list, filter out images
        if (content is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val contentList = content as List<Map<String, Any>>

            val textOnlyContent = contentList.filter { item ->
                item["type"] != "image_url"
            }

            // If only one text item remains, simplify to string
            if (textOnlyContent.size == 1 && textOnlyContent[0]["type"] == "text") {
                return mapOf(
                    "role" to role,
                    "content" to (textOnlyContent[0]["text"] as? String ?: "")
                )
            }

            return mapOf(
                "role" to role,
                "content" to textOnlyContent
            )
        }

        return message
    }

    /**
     * Build screen information text for the user prompt.
     *
     * @param currentApp The current foreground app name
     * @param extraInfo Additional information to include (e.g., step number, previous action)
     * @return Formatted screen info string
     */
    fun buildScreenInfo(currentApp: String, extraInfo: Map<String, Any> = emptyMap()): String {
        val parts = mutableListOf<String>()

        // Add current app info
        parts.add("当前应用: $currentApp")

        // Add extra info if present
        if (extraInfo.isNotEmpty()) {
            val stepNumber = extraInfo["step"] as? Int
            if (stepNumber != null) {
                parts.add("步骤: $stepNumber")
            }

            val previousAction = extraInfo["previous_action"] as? String
            if (previousAction != null) {
                parts.add("上一步操作: $previousAction")
            }

            val previousResult = extraInfo["previous_result"] as? String
            if (previousResult != null) {
                parts.add("操作结果: $previousResult")
            }
        }

        return parts.joinToString("\n")
    }

    /**
     * Build a complete user message with screenshot and task description.
     *
     * @param task The user task description
     * @param imageBase64 Base64-encoded screenshot
     * @param currentApp Current foreground app name
     * @param stepNumber Current step number
     * @param isFirstStep Whether this is the first step
     * @return Formatted user message
     */
    fun buildTaskMessage(
        task: String,
        imageBase64: String,
        currentApp: String,
        stepNumber: Int,
        isFirstStep: Boolean = false
    ): Map<String, Any> {
        val text = if (isFirstStep) {
            "任务: $task\n当前应用: $currentApp"
        } else {
            "当前应用: $currentApp\n步骤: $stepNumber"
        }

        return createUserMessage(text, imageBase64)
    }

    /**
     * Create a continuation message (without repeating the full task).
     *
     * @param imageBase64 Base64-encoded screenshot
     * @param currentApp Current foreground app name
     * @param stepNumber Current step number
     * @return Formatted continuation message
     */
    fun buildContinuationMessage(
        imageBase64: String,
        currentApp: String,
        stepNumber: Int
    ): Map<String, Any> {
        val text = "当前应用: $currentApp\n步骤: $stepNumber"
        return createUserMessage(text, imageBase64)
    }

    /**
     * Convert a list of messages to a JSON-ready format.
     *
     * @param messages List of message maps
     * @return List ready for JSON serialization
     */
    fun prepareMessagesForApi(messages: List<Map<String, Any>>): List<Map<String, Any>> {
        return messages
    }

    /**
     * Get message content as text (extract from complex content).
     *
     * @param message The message to extract text from
     * @return The text content, or empty string if not found
     */
    fun getMessageText(message: Map<String, Any>): String {
        val content = message["content"]

        // If content is a string, return it
        if (content is String) {
            return content
        }

        // If content is a list, find text items
        if (content is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val contentList = content as List<Map<String, Any>>

            val textItems = contentList.filter { it["type"] == "text" }
            return textItems.joinToString("\n") { it["text"] as? String ?: "" }
        }

        return ""
    }

    /**
     * Count the number of images in a message.
     *
     * @param message The message to check
     * @return Number of images in the message
     */
    fun countImages(message: Map<String, Any>): Int {
        val content = message["content"]

        if (content is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val contentList = content as List<Map<String, Any>>
            return contentList.count { it["type"] == "image_url" }
        }

        return 0
    }

    /**
     * Get the total number of images in a message list.
     *
     * @param messages List of messages
     * @return Total number of images
     */
    fun countTotalImages(messages: List<Map<String, Any>>): Int {
        return messages.sumOf { countImages(it) }
    }
}
