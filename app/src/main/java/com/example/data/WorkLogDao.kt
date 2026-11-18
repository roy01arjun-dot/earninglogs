package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkLogDao {

    // --- Reads (tombstones are always hidden from the UI) ---

    @Query("SELECT * FROM work_logs WHERE isDeleted = 0 ORDER BY date DESC, id DESC")
    fun observeActiveLogs(): Flow<List<WorkLogEntity>>

    @Query("SELECT * FROM work_logs WHERE isDeleted = 0")
    suspend fun getActiveLogs(): List<WorkLogEntity>

    @Query("SELECT * FROM work_logs WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): WorkLogEntity?

    @Query("SELECT * FROM work_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): WorkLogEntity?

    @Query("SELECT COUNT(*) FROM work_logs WHERE isDeleted = 0 AND date = :date")
    suspend fun countActiveForDate(date: String): Int

    @Query("SELECT * FROM work_logs WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<WorkLogEntity>

    @Query("SELECT COUNT(*) FROM work_logs WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    // --- Writes ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: WorkLogEntity): Long

    @Update
    suspend fun update(log: WorkLogEntity)

    @Update
    suspend fun updateAll(logs: List<WorkLogEntity>)

    @Query(
        "UPDATE work_logs SET isDeleted = 1, pendingSync = 1, updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun softDeleteById(id: Int, now: Long)

    @Query(
        "UPDATE work_logs SET isDeleted = 1, pendingSync = 1, updatedAt = :now " +
            "WHERE isDeleted = 0 AND date = :date"
    )
    suspend fun softDeleteForDate(date: String, now: Long)

    @Query(
        "UPDATE work_logs SET isDeleted = 1, pendingSync = 1, updatedAt = :now " +
            "WHERE isDeleted = 0 AND date >= :startDate AND date <= :endDate"
    )
    suspend fun softDeleteInRange(startDate: String, endDate: String, now: Long)

    @Query("UPDATE work_logs SET isDeleted = 1, pendingSync = 1, updatedAt = :now WHERE isDeleted = 0")
    suspend fun softDeleteAll(now: Long)

    @Query(
        "UPDATE work_logs SET workplace = :newName, pendingSync = 1, updatedAt = :now " +
            "WHERE isDeleted = 0 AND workplace = :oldName"
    )
    suspend fun renameWorkplace(oldName: String, newName: String, now: Long)

    @Query(
        "UPDATE work_logs SET isPaid = 1, pendingSync = 1, updatedAt = :now " +
            "WHERE isDeleted = 0 AND isPaid = 0 AND id IN (:ids)"
    )
    suspend fun markPaid(ids: List<Int>, now: Long)

    /** Called after a row was handed to Firestore. Only clears the flag if it wasn't edited meanwhile. */
    @Query("UPDATE work_logs SET pendingSync = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun markSynced(uuid: String, updatedAt: Long)

    /** Tombstones that the cloud already knows about are no longer needed locally. */
    @Query("DELETE FROM work_logs WHERE isDeleted = 1 AND pendingSync = 0")
    suspend fun purgeSyncedTombstones()

    /** Used when the user signs out with "remove data from this device". */
    @Query("DELETE FROM work_logs")
    suspend fun wipeLocal()

    /** After sign-in to a new account every local row must be uploaded to that account. */
    @Query("UPDATE work_logs SET pendingSync = 1")
    suspend fun markAllPending()
}
