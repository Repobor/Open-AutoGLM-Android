package io.repobor.autoglm.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Entity representing an execution log entry in the database.
 */
@Entity(tableName = "execution_logs")
@TypeConverters(Converters::class)
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Task description */
    val task: String,

    /** Status: "成功", "失败", "停止" */
    val status: String,

    /** Final result message */
    val result: String,

    /** Number of steps executed */
    val steps: Int,

    /** Timestamp when the task started */
    val startTimestamp: Long,

    /** Timestamp when the task completed */
    val endTimestamp: Long,

    /** Language used: "zh" or "en" */
    val language: String,

    /** Model name used */
    val modelName: String,

    /** Full conversation history (JSON array of messages) */
    val conversationHistory: String,

    /** Step-by-step details (JSON array) */
    val stepDetails: String,

    /** Whether this execution completed successfully */
    val isSuccess: Boolean,

    /** Error message if any */
    val errorMessage: String? = null
)

/**
 * Step detail for a single execution step.
 */
@kotlinx.serialization.Serializable
data class StepDetail(
    val stepNumber: Int,
    val timestamp: Long,
    val action: String,
    val actionDescription: String,
    val thinking: String,
    val success: Boolean,
    val message: String?
)

/**
 * Type converters for Room database.
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStepDetailsList(value: List<StepDetail>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStepDetailsList(value: String): List<StepDetail> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
