package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * All local writes go through here so every change gets a fresh [WorkLogEntity.updatedAt]
 * and is flagged for upload. Deletes are soft (tombstones) so they sync to other devices.
 */
class WorkLogRepository(
    private val db: WorkLogDatabase,
    val settingsRepo: UserSettingsRepository
) {
    private val dao = db.workLogDao()

    val activeLogs: Flow<List<WorkLogEntity>> = dao.observeActiveLogs()
    val pendingSyncCount: Flow<Int> = dao.observePendingCount()
    val userSettings: Flow<UserSettings> = settingsRepo.settings

    private fun now() = System.currentTimeMillis()

    suspend fun countShiftsOn(date: String): Int = dao.countActiveForDate(date)

    suspend fun addLog(log: WorkLogEntity): Long =
        dao.insert(log.copy(id = 0, updatedAt = now(), pendingSync = true, isDeleted = false))

    suspend fun updateLog(log: WorkLogEntity) {
        dao.update(log.copy(updatedAt = now(), pendingSync = true))
    }

    suspend fun setPaid(log: WorkLogEntity, paid: Boolean) = updateLog(log.copy(isPaid = paid))

    suspend fun markPaid(ids: List<Int>) {
        if (ids.isEmpty()) return
        ids.chunked(500).forEach { dao.markPaid(it, now()) }
    }

    suspend fun deleteLog(id: Int) = dao.softDeleteById(id, now())
    suspend fun deleteLogsForDate(date: String) = dao.softDeleteForDate(date, now())
    suspend fun deleteLogsInRange(start: String, end: String) = dao.softDeleteInRange(start, end, now())
    suspend fun deleteAll() = dao.softDeleteAll(now())

    suspend fun renameWorkplace(oldName: String, newName: String) =
        dao.renameWorkplace(oldName, newName, now())

    /** Merge shifts from a JSON backup. Shifts with the same uuid are overwritten, others added. */
    suspend fun importLogs(logs: List<WorkLogEntity>) {
        db.withTransaction {
            logs.forEach { log ->
                val incoming = log.copy(updatedAt = now(), pendingSync = true, isDeleted = false)
                val existing = dao.getByUuid(incoming.uuid)
                if (existing == null) dao.insert(incoming.copy(id = 0)) else dao.update(incoming.copy(id = existing.id))
            }
        }
    }

    // ---- Used by the sync engine only ----

    suspend fun pendingForUpload(): List<WorkLogEntity> = dao.getPendingSync()

    suspend fun markSynced(log: WorkLogEntity) = dao.markSynced(log.uuid, log.updatedAt)

    suspend fun purgeSyncedTombstones() = dao.purgeSyncedTombstones()

    suspend fun markAllPending() = dao.markAllPending()

    suspend fun wipeLocal() = dao.wipeLocal()

    /**
     * Apply a change that came from the cloud using last-write-wins.
     * @return true if the local database changed.
     */
    suspend fun applyRemote(remote: WorkLogEntity): Boolean = db.withTransaction {
        val local = dao.getByUuid(remote.uuid)
        when {
            local == null && remote.isDeleted -> false
            local == null -> {
                dao.insert(remote.copy(id = 0, pendingSync = false)); true
            }
            remote.updatedAt > local.updatedAt -> {
                dao.update(remote.copy(id = local.id, pendingSync = false)); true
            }
            else -> false // local copy is newer (or identical) – it will be pushed
        }
    }
}
