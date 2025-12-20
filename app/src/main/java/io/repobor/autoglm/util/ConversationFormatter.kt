package io.repobor.autoglm.util

import android.util.Log
import io.repobor.autoglm.actions.ActionParser
import io.repobor.autoglm.data.database.StepDetail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Formats conversation history from database for UI display.
 * Converts JSON conversation data into human-readable conversation items.
 */
object ConversationFormatter {

    private const val TAG = "ConversationFormatter"
    private val json = Json { ignoreUnknownKeys = true }

    data class ConversationItem(
        val type: ItemType,
        val content: String,
        val stepNumber: Int? = null
    )

    enum class ItemType {
        USER,      // User task message
        THINKING,  // Model thinking from <think> tags
        ACTION     // Model action from <answer> tags
    }

    /**
     * Format conversation history from List<Map> and step details for display.
     * Overloaded version that accepts both JSON string and List<Map>.
     *
     * @param conversationList List of conversation messages (Map<String, Any>)
     * @param stepDetails List of step detail objects
     * @param language Language code for localization
     * @return List of formatted conversation items
     */
    fun formatConversationFromList(
        conversationList: List<Map<String, Any>>,
        stepDetails: List<StepDetail>,
        language: String = "zh"
    ): List<ConversationItem> {
        val items = mutableListOf<ConversationItem>()
        var stepIndex = 0

        for (message in conversationList) {
            val role = message["role"] as? String ?: continue

            when (role) {
                "system" -> {
                    // Skip system messages
                }
                "user" -> {
                    // Extract user message content
                    val userMessage = extractUserMessageFromMap(message)
                    if (userMessage.isNotEmpty()) {
                        items.add(ConversationItem(ItemType.USER, userMessage, stepIndex))
                    }
                }
                "assistant" -> {
                    // Extract assistant response - split into thinking and action
                    val content = message["content"] as? String ?: continue

                    // Extract thinking
                    val thinking = ActionParser.extractThinking(content)
                    if (!thinking.isNullOrEmpty()) {
                        items.add(ConversationItem(ItemType.THINKING, thinking, stepIndex))
                    }

                    // Extract action description from step details
                    if (stepIndex < stepDetails.size) {
                        val stepDetail = stepDetails[stepIndex]
                        val actionDesc = stepDetail.actionDescription
                        if (actionDesc.isNotEmpty()) {
                            items.add(
                                ConversationItem(
                                    ItemType.ACTION,
                                    actionDesc,
                                    stepIndex + 1
                                )
                            )
                        }
                    }

                    stepIndex++
                }
            }
        }

        return items
    }

    /**
     * Extract text content from user message (Map version).
     * User messages can be either:
     * 1. Simple string: "task description"
     * 2. Array of content blocks: [{"type": "image_url", ...}, {"type": "text", "text": "..."}]
     */
    private fun extractUserMessageFromMap(message: Map<String, Any>): String {
        val content = message["content"]

        // Handle array/list content (with images)
        val contentList = content as? List<*>
        if (contentList != null) {
            for (item in contentList) {
                val obj = item as? Map<*, *>
                if (obj != null) {
                    val type = obj["type"] as? String
                    if (type == "text") {
                        val text = obj["text"] as? String
                        if (!text.isNullOrEmpty()) {
                            return text
                        }
                    }
                }
            }
            return ""
        }

        // Handle direct string content
        val stringContent = content as? String
        return stringContent ?: ""
    }

    /**
     * Format a list of conversation items into display strings for UI.
     *
     * @param items The conversation items to format
     * @param language Language code ("zh" or "en")
     * @return List of formatted strings ready for display
     */
    fun formatItemsForDisplay(
        items: List<ConversationItem>,
        language: String = "zh"
    ): List<String> {
        val displayStrings = mutableListOf<String>()

        for (item in items) {
            val prefix = when (item.type) {
                ItemType.USER -> if (language == "zh") "[用户]" else "[User]"
                ItemType.THINKING -> if (language == "zh") "[思考]" else "[Thinking]"
                ItemType.ACTION -> if (language == "zh") "[动作]" else "[Action]"
            }

            // Truncate long thinking content
            val displayContent = when (item.type) {
                ItemType.THINKING -> {
                    val trimmed = item.content.trim()
                    if (trimmed.length > 200) {
                        trimmed.substring(0, 200) + "..."
                    } else {
                        trimmed
                    }
                }
                else -> item.content
            }

            displayStrings.add("$prefix $displayContent")
            displayStrings.add("")  // Add blank line for spacing
        }

        return displayStrings
    }
}

// Type alias for JsonObject to work with kotlinx.serialization
typealias JsonObject = kotlinx.serialization.json.JsonObject
