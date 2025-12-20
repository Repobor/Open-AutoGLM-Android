package io.repobor.autoglm.actions

import android.util.Log
import java.util.regex.Pattern

/**
 * Parses action commands from model responses.
 * Supports formats like:
 * - do(action="Tap", element=[x, y])
 * - do(action="Type", text="hello")
 * - finish(message="Done")
 */
object ActionParser {

    private const val TAG = "ActionParser"

    /**
     * Parse a model response to extract the action command.
     * Priority:
     * 1. Check for finish(message=...)
     * 2. Check for do(action=...)
     * 3. Check for <answer>...</answer> tags
     * 4. Fallback to entire content
     *
     * @param response The model response text
     * @return Parsed action map with extracted parameters, or null if parsing fails
     */
    fun parseResponse(response: String): Map<String, Any>? {
        try {
            // Extract content from <answer> tags if present
            val answerContent = extractFromTag(response, "answer") ?: response

            // Check for finish() command first
            val finishMatch = extractFinish(answerContent)
            if (finishMatch != null) {
                return finishMatch
            }

            // Check for do() command
            val actionMatch = extractDo(answerContent)
            if (actionMatch != null) {
                return actionMatch
            }

            // If neither found, return null (invalid action)
            Log.w(TAG, "No valid action found in response: $answerContent")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            return null
        }
    }

    /**
     * Extract thinking/reasoning from <think> tags.
     *
     * @param response The model response text
     * @return The thinking content, or null if not found
     */
    fun extractThinking(response: String): String? {
        return extractFromTag(response, "think")
    }

    /**
     * Extract content from XML-style tags.
     *
     * @param text The text to search
     * @param tagName The tag name (without < >)
     * @return The content between tags, or null if not found
     */
    private fun extractFromTag(text: String, tagName: String): String? {
        val pattern = Pattern.compile("<$tagName>(.*?)</$tagName>", Pattern.DOTALL)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else {
            null
        }
    }

    /**
     * Extract finish() command and its message.
     * Format: finish(message="...")
     *
     * @param text The text to parse
     * @return Map with action "finish" and message parameter
     */
    private fun extractFinish(text: String): Map<String, Any>? {
        // Try to find finish( and extract the message using better parsing
        val startPattern = Pattern.compile("""finish\s*\(\s*message\s*=\s*["']""")
        val startMatcher = startPattern.matcher(text)

        if (!startMatcher.find()) {
            return null
        }

        // Find the opening quote
        val startPos = startMatcher.end()
        val openingQuote = text[startPos - 1]

        // Find the matching closing quote followed by )
        // We need to find the last occurrence of the quote before a closing parenthesis
        var endPos = -1
        var depth = 0

        for (i in startPos until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    if (depth == 0) {
                        // Check if there's a closing quote just before this
                        var j = i - 1
                        while (j >= startPos && text[j].isWhitespace()) {
                            j--
                        }
                        if (j >= startPos && (text[j] == '"' || text[j] == '\'')) {
                            endPos = j
                            break
                        }
                    } else {
                        depth--
                    }
                }
            }
        }

        if (endPos == -1) {
            // Fallback: try simpler pattern that allows any content
            val pattern = Pattern.compile("""finish\s*\(\s*message\s*=\s*["'](.*?)["']\s*\)""", Pattern.DOTALL)
            val matcher = pattern.matcher(text)
            return if (matcher.find()) {
                val message = matcher.group(1) ?: ""
                mapOf(
                    "action" to "finish",
                    "message" to message
                )
            } else {
                null
            }
        }

        val message = text.substring(startPos, endPos)
        return mapOf(
            "action" to "finish",
            "message" to message
        )
    }

    /**
     * Extract do() command and parse its parameters.
     * Supports multiple action formats.
     *
     * @param text The text to parse
     * @return Map with action type and parameters
     */
    private fun extractDo(text: String): Map<String, Any>? {
        // Pattern to match: do(action="...", ...)
        val pattern = Pattern.compile("""do\s*\((.*?)\)""", Pattern.DOTALL)
        val matcher = pattern.matcher(text)

        if (!matcher.find()) {
            return null
        }

        val paramsStr = matcher.group(1) ?: return null
        return parseDoParameters(paramsStr)
    }

    /**
     * Parse parameters from do() command string.
     *
     * @param paramsStr The parameters string (e.g., 'action="Tap", element=[x, y]')
     * @return Map with parsed parameters
     */
    private fun parseDoParameters(paramsStr: String): Map<String, Any>? {
        val result = mutableMapOf<String, Any>()

        try {
            // Extract action parameter
            val actionPattern = Pattern.compile("""action\s*=\s*["']([^"']*)["']""")
            val actionMatcher = actionPattern.matcher(paramsStr)
            if (!actionMatcher.find()) {
                Log.w(TAG, "No action parameter found")
                return null
            }
            val action = actionMatcher.group(1) ?: return null
            result["action"] = action.lowercase()

            // Parse based on action type
            when (action.lowercase()) {
                "tap", "long press", "double tap" -> {
                    val element = extractElement(paramsStr)
                    if (element != null) {
                        result["element"] = element
                    }
                    // Optional message parameter
                    val message = extractStringParameter(paramsStr, "message")
                    if (message != null) {
                        result["message"] = message
                    }
                }
                "type", "type_name" -> {
                    val text = extractStringParameter(paramsStr, "text")
                    if (text != null) {
                        result["text"] = text
                    }
                }
                "swipe" -> {
                    val start = extractArray(paramsStr, "start")
                    val end = extractArray(paramsStr, "end")
                    if (start != null && end != null) {
                        result["start"] = start
                        result["end"] = end
                    }
                }
                "launch" -> {
                    val app = extractStringParameter(paramsStr, "app")
                    if (app != null) {
                        result["app"] = app
                    }
                }
                "wait" -> {
                    val duration = extractStringParameter(paramsStr, "duration")
                    if (duration != null) {
                        result["duration"] = duration
                    }
                }
                "back", "home" -> {
                    // No additional parameters needed
                }
                "note" -> {
                    val message = extractStringParameter(paramsStr, "message")
                    if (message != null) {
                        result["message"] = message
                    }
                }
                "call_api" -> {
                    val instruction = extractStringParameter(paramsStr, "instruction")
                    if (instruction != null) {
                        result["instruction"] = instruction
                    }
                }
                "interact" -> {
                    // No additional parameters needed
                }
                "take_over" -> {
                    val message = extractStringParameter(paramsStr, "message")
                    if (message != null) {
                        result["message"] = message
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown action type: $action")
                }
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing do() parameters", e)
            return null
        }
    }

    /**
     * Extract element parameter as [x, y] coordinates.
     *
     * @param text The text to search
     * @return List of two integers [x, y], or null if not found
     */
    private fun extractElement(text: String): List<Int>? {
        return extractArray(text, "element")
    }

    /**
     * Extract array parameter like [x, y] or [x1, y1].
     *
     * @param text The text to search
     * @param paramName The parameter name (e.g., "element", "start", "end")
     * @return List of integers, or null if not found
     */
    private fun extractArray(text: String, paramName: String): List<Int>? {
        val pattern = Pattern.compile("""$paramName\s*=\s*\[([^\]]+)\]""")
        val matcher = pattern.matcher(text)

        if (!matcher.find()) {
            return null
        }

        val arrayStr = matcher.group(1) ?: return null
        val numbers = arrayStr.split(",").mapNotNull { it.trim().toIntOrNull() }

        return if (numbers.isNotEmpty()) numbers else null
    }

    /**
     * Extract string parameter value.
     * Handles strings with nested quotes by using non-greedy matching.
     *
     * @param text The text to search
     * @param paramName The parameter name (e.g., "text", "app", "message")
     * @return The string value, or null if not found
     */
    private fun extractStringParameter(text: String, paramName: String): String? {
        // First try with non-greedy matching to handle nested quotes
        val pattern = Pattern.compile("""$paramName\s*=\s*["'](.*?)["']""", Pattern.DOTALL)
        val matcher = pattern.matcher(text)

        if (matcher.find()) {
            return matcher.group(1)
        }

        // Fallback: try to match with explicit quote types
        val doubleQuotePattern = Pattern.compile("""$paramName\s*=\s*"([^"]*)"?""")
        val doubleQuoteMatcher = doubleQuotePattern.matcher(text)
        if (doubleQuoteMatcher.find()) {
            return doubleQuoteMatcher.group(1)
        }

        val singleQuotePattern = Pattern.compile("""$paramName\s*=\s*'([^']*)'?""")
        val singleQuoteMatcher = singleQuotePattern.matcher(text)
        if (singleQuoteMatcher.find()) {
            return singleQuoteMatcher.group(1)
        }

        return null
    }

    /**
     * Validate if an action map has all required parameters.
     *
     * @param action The action map to validate
     * @return true if valid, false otherwise
     */
    fun validateAction(action: Map<String, Any>): Boolean {
        val actionType = action["action"] as? String ?: return false

        return when (actionType.lowercase()) {
            "tap", "long press", "double tap" -> action.containsKey("element")
            "type", "type_name" -> action.containsKey("text")
            "swipe" -> action.containsKey("start") && action.containsKey("end")
            "launch" -> action.containsKey("app")
            "wait" -> action.containsKey("duration")
            "back", "home", "interact" -> true
            "finish" -> action.containsKey("message")
            "note", "take_over" -> action.containsKey("message")
            "call_api" -> action.containsKey("instruction")
            else -> false
        }
    }

    /**
     * Get a human-readable description of an action.
     *
     * @param action The action map
     * @return Description string
     */
    fun getActionDescription(action: Map<String, Any>): String {
        val actionType = action["action"] as? String ?: return "Unknown action"

        return when (actionType.lowercase()) {
            "tap" -> {
                val element = action["element"] as? List<*>
                "Tap at ${element}"
            }
            "type" -> {
                val text = action["text"] as? String
                "Type: $text"
            }
            "swipe" -> {
                val start = action["start"] as? List<*>
                val end = action["end"] as? List<*>
                "Swipe from $start to $end"
            }
            "launch" -> {
                val app = action["app"] as? String
                "Launch app: $app"
            }
            "back" -> "Press Back"
            "home" -> "Press Home"
            "long press" -> {
                val element = action["element"] as? List<*>
                "Long press at $element"
            }
            "double tap" -> {
                val element = action["element"] as? List<*>
                "Double tap at $element"
            }
            "wait" -> {
                val duration = action["duration"] as? String
                "Wait: $duration"
            }
            "finish" -> {
                val message = action["message"] as? String
                "Finish: $message"
            }
            else -> actionType
        }
    }
}
