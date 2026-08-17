package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * All read queries exclude tombstones (`deletedAt IS NULL`). Deleted rows stay in the table
 * until the sync engine has pushed the tombstone and [purgeSyncedTombstones] retires it.
 */
@Dao
interface WorkLogDao {

    // --- Reads (live) ---

    @Query("SELECT * FROM work_logs WHERE deletedAt IS NULL ORDER BY date DESC, id DESC")
    fun getAllLogs(): Flow<List<WorkLogEntity>>

    @Query(
        "SELECT * FROM work_logs WHERE deletedAt IS NULL AND date >= :startDate AND date <= :endDate " +
            "ORDER BY date ASC, id ASC"
    )
    fun getLogsForRange(startDate: String, endDate: String): Flow<List<WorkLogEntity>>

    @Query("SELECT * FROM work_logs WHERE deletedAt IS NULL AND date = :date ORDER BY id DESC")
    fun getLogsForDate(date: String): Flow<List<WorkLogEntity>>

    @Query("SELECT * FROM work_logs WHERE deletedAt IS NULL AND workplace = :workplace ORDER BY date DESC, id DESC")
    fun getLogsByWorkplace(workplace: String): Flow<List<WorkLogEntity>>

    // --- Reads (one-shot) ---

    @Query("SELECT * FROM work_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Int): WorkLogEntity?

    @Query("SELECT * FROM work_logs WHERE uuid = :uuid LIMIT 1")
    suspend fun getLogByUuid(uuid: String): WorkLogEntity?

    @Query("SELECT * FROM work_logs WHERE deletedAt IS NULL")
    suspend fun getAllLogsOnce(): List<WorkLogEntity>

    /** Everything the backend has not acknowledged yet, tombstones included. */
    @Query("SELECT * FROM work_logs WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<WorkLogEntity>

    @Query("SELECT COUNT(*) FROM work_logs WHERE pendingSync = 1")
    fun getPendingSyncCount(): Flow<Int>

    // --- Writes ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WorkLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<WorkLogEntity>): List<Long>

    @Update
    suspend fun updateLog(log: WorkLogEntity)

    @Query("UPDATE work_logs SET workplace = :newName, updatedAt = :now, pendingSync = 1 WHERE workplace = :oldName AND deletedAt IS NULL")
    suspend fun updateWorkplaceName(oldName: String, newName: String, now: Long = System.currentTimeMillis())

    /**
     * Soft delete. Keeps the row so the tombstone can reach other devices; the UI stops
     * showing it immediately because every read filters `deletedAt IS NULL`.
     */
    @Query("UPDATE work_logs SET deletedAt = :now, updatedAt = :now, pendingSync = 1 WHERE id = :id")
    suspend fun softDeleteById(id: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE work_logs SET deletedAt = :now, updatedAt = :now, pendingSync = 1 WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(now: Long = System.currentTimeMillis())

    // --- Sync bookkeeping ---

    @Query("UPDATE work_logs SET pendingSync = 0 WHERE uuid IN (:uuids)")
    suspend fun clearPendingFlags(uuids: List<String>)

    /**
     * Applies a record pulled from the backend, but only if the remote copy is actually newer
     * and we have no unpushed local edit. Without both guards a pull can silently revert a
     * change the user just made offline.
     */
    @Transaction
    suspend fun applyRemote(remote: WorkLogEntity) {
        val local = getLogByUuid(remote.uuid)
        if (local == null) {
            insertLog(remote.copy(id = 0, pendingSync = false))
            return
        }
        if (local.pendingSync && local.updatedAt >= remote.updatedAt) return
        if (remote.updatedAt <= local.updatedAt) return
        updateLog(remote.copy(id = local.id, pendingSync = false))
    }

    @Transaction
    suspend fun applyRemoteBatch(remotes: List<WorkLogEntity>) {
        remotes.forEach { applyRemote(it) }
    }

    /** Tombstones already pushed and older than [before] are no longer needed by anyone. */
    @Query("DELETE FROM work_logs WHERE deletedAt IS NOT NULL AND pendingSync = 0 AND deletedAt < :before")
    suspend fun purgeSyncedTombstones(before: Long)

    /** Hard wipe, used by "sign out and remove local data". */
    @Query("DELETE FROM work_logs")
    suspend fun hardDeleteAll()
}
