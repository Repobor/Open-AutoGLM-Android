package io.repobor.autoglm.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for execution logs.
 */
@Dao
interface ExecutionLogDao {

    /**
     * Insert a new execution log entry.
     * @return The row ID of the inserted log
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecutionLogEntity): Long

    /**
     * Update an existing execution log entry.
     */
    @Update
    suspend fun update(log: ExecutionLogEntity)

    /**
     * Delete a log entry.
     */
    @Delete
    suspend fun delete(log: ExecutionLogEntity)

    /**
     * Get a log entry by ID.
     */
    @Query("SELECT * FROM execution_logs WHERE id = :id")
    suspend fun getById(id: Long): ExecutionLogEntity?

    /**
     * Get all execution logs ordered by timestamp (newest first).
     */
    @Query("SELECT * FROM execution_logs ORDER BY startTimestamp DESC")
    fun getAllLogs(): Flow<List<ExecutionLogEntity>>

    /**
     * Get all execution logs as a list (for one-time queries).
     */
    @Query("SELECT * FROM execution_logs ORDER BY startTimestamp DESC")
    suspend fun getAllLogsList(): List<ExecutionLogEntity>

    /**
     * Get logs filtered by status.
     */
    @Query("SELECT * FROM execution_logs WHERE status = :status ORDER BY startTimestamp DESC")
    fun getLogsByStatus(status: String): Flow<List<ExecutionLogEntity>>

    /**
     * Get successful logs only.
     */
    @Query("SELECT * FROM execution_logs WHERE isSuccess = 1 ORDER BY startTimestamp DESC")
    fun getSuccessfulLogs(): Flow<List<ExecutionLogEntity>>

    /**
     * Get logs within a time range.
     */
    @Query("SELECT * FROM execution_logs WHERE startTimestamp >= :startTime AND startTimestamp <= :endTime ORDER BY startTimestamp DESC")
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<ExecutionLogEntity>>

    /**
     * Search logs by task description.
     */
    @Query("SELECT * FROM execution_logs WHERE task LIKE '%' || :query || '%' ORDER BY startTimestamp DESC")
    fun searchLogs(query: String): Flow<List<ExecutionLogEntity>>

    /**
     * Delete all logs.
     */
    @Query("DELETE FROM execution_logs")
    suspend fun deleteAll()

    /**
     * Delete logs older than a certain timestamp.
     */
    @Query("DELETE FROM execution_logs WHERE startTimestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Get count of all logs.
     */
    @Query("SELECT COUNT(*) FROM execution_logs")
    suspend fun getCount(): Int

    /**
     * Get the most recent log.
     */
    @Query("SELECT * FROM execution_logs ORDER BY startTimestamp DESC LIMIT 1")
    suspend fun getMostRecent(): ExecutionLogEntity?
}
