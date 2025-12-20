package io.repobor.autoglm.data

import android.content.Context
import io.repobor.autoglm.data.database.AppDatabase
import io.repobor.autoglm.data.database.ExecutionLogEntity
import io.repobor.autoglm.data.database.StepDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository for managing execution logs.
 */
class ExecutionLogRepository(context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val dao = database.executionLogDao()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Insert a new execution log.
     */
    suspend fun insertLog(
        task: String,
        status: String,
        result: String,
        steps: Int,
        startTimestamp: Long,
        endTimestamp: Long,
        language: String,
        modelName: String,
        conversationHistory: List<Map<String, Any>>,
        stepDetails: List<StepDetail>,
        isSuccess: Boolean,
        errorMessage: String? = null
    ): Long {
        // Convert conversation history to JSON string manually
        val conversationJson = convertConversationHistoryToJson(conversationHistory)

        val log = ExecutionLogEntity(
            task = task,
            status = status,
            result = result,
            steps = steps,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            language = language,
            modelName = modelName,
            conversationHistory = conversationJson,
            stepDetails = json.encodeToString(stepDetails),
            isSuccess = isSuccess,
            errorMessage = errorMessage
        )
        return dao.insert(log)
    }

    /**
     * Update an existing log.
     */
    suspend fun updateLog(log: ExecutionLogEntity) {
        dao.update(log)
    }

    /**
     * Delete a log.
     */
    suspend fun deleteLog(log: ExecutionLogEntity) {
        dao.delete(log)
    }

    /**
     * Get a log by ID.
     */
    suspend fun getLogById(id: Long): ExecutionLogEntity? {
        return dao.getById(id)
    }

    /**
     * Get all logs as a Flow.
     */
    fun getAllLogs(): Flow<List<ExecutionLogEntity>> {
        return dao.getAllLogs()
    }

    /**
     * Get all logs as a list.
     */
    suspend fun getAllLogsList(): List<ExecutionLogEntity> {
        return dao.getAllLogsList()
    }

    /**
     * Get successful logs only.
     */
    fun getSuccessfulLogs(): Flow<List<ExecutionLogEntity>> {
        return dao.getSuccessfulLogs()
    }

    /**
     * Search logs by task description.
     */
    fun searchLogs(query: String): Flow<List<ExecutionLogEntity>> {
        return dao.searchLogs(query)
    }

    /**
     * Delete all logs.
     */
    suspend fun deleteAllLogs() {
        dao.deleteAll()
    }

    /**
     * Get count of logs.
     */
    suspend fun getLogCount(): Int {
        return dao.getCount()
    }

    /**
     * Get the most recent log.
     */
    suspend fun getMostRecentLog(): ExecutionLogEntity? {
        return dao.getMostRecent()
    }

    /**
     * Parse conversation history from JSON string.
     */
    fun parseConversationHistory(jsonString: String): List<Map<String, Any>> {
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse step details from JSON string.
     */
    fun parseStepDetails(jsonString: String): List<StepDetail> {
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Convert conversation history to JSON string manually.
     * This avoids Kotlin Serialization issues with Map<String, Any>.
     */
    private fun convertConversationHistoryToJson(history: List<Map<String, Any>>): String {
        return try {
            val jsonArray = JSONArray()
            for (message in history) {
                val jsonObject = JSONObject()
                for ((key, value) in message) {
                    jsonObject.put(key, convertValueToJson(value))
                }
                jsonArray.put(jsonObject)
            }
            jsonArray.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    /**
     * Convert a value to JSON-compatible format.
     */
    private fun convertValueToJson(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is String, is Number, is Boolean -> value
            is List<*> -> {
                val array = JSONArray()
                value.forEach { array.put(convertValueToJson(it)) }
                array
            }
            is Map<*, *> -> {
                val obj = JSONObject()
                value.forEach { (k, v) ->
                    obj.put(k.toString(), convertValueToJson(v))
                }
                obj
            }
            else -> value.toString()
        }
    }
}
