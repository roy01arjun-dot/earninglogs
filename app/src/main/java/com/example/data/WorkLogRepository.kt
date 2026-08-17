package com.example.data

import android.content.Context
import com.example.data.sync.SyncWorker
import kotlinx.coroutines.flow.Flow

/**
 * The only thing the UI layer talks to for shift data.
 *
 * Every mutating call does two things and only two things: write to Room, then ask WorkManager
 * to sync. It never touches Firestore directly, so a write is durable the moment it lands
 * locally whether or not there is a network, an account, or a working backend.
 */
class WorkLogRepository(
    private val appContext: Context,
    private val dao: WorkLogDao,
    private val settingsRepo: UserSettingsRepository
) {
    val allLogs: Flow<List<WorkLogEntity>> = dao.getAllLogs()
    val userSettings: Flow<UserSettings> = settingsRepo.settings
    val pendingSyncCount: Flow<Int> = dao.getPendingSyncCount()

    fun getLogsForRange(startDate: String, endDate: String): Flow<List<WorkLogEntity>> =
        dao.getLogsForRange(startDate, endDate)

    suspend fun insertLog(log: WorkLogEntity): Long {
        val id = dao.insertLog(log.markDirty())
        requestSync()
        return id
    }

    suspend fun insertLogs(logs: List<WorkLogEntity>) {
        if (logs.isEmpty()) return
        dao.insertLogs(logs.map { it.markDirty() })
        requestSync()
    }

    suspend fun updateLog(log: WorkLogEntity) {
        dao.updateLog(log.markDirty())
        requestSync()
    }

    suspend fun deleteLog(id: Int) {
        dao.softDeleteById(id)
        requestSync()
    }

    suspend fun clearAll() {
        dao.softDeleteAll()
        requestSync()
    }

    /** Used when signing out with "remove local copy" - no tombstones, nothing to propagate. */
    suspend fun hardDeleteAll() {
        dao.hardDeleteAll()
    }

    suspend fun renameWorkplace(oldName: String, newName: String) {
        dao.updateWorkplaceName(oldName, newName)
        requestSync()
    }

    fun updateSettings(settings: UserSettings) {
        settingsRepo.updateSettings(settings)
    }

    fun setPin(pin: String) = settingsRepo.setPin(pin)

    fun verifyPin(pin: String): Boolean = settingsRepo.verifyPin(pin)

    suspend fun getAllLogsOnce(): List<WorkLogEntity> = dao.getAllLogsOnce()

    private fun requestSync() {
        if (settingsRepo.settings.value.autoCloudSyncEnabled) {
            SyncWorker.enqueueOneShot(appContext)
        }
    }
}
