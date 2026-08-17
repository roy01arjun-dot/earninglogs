package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.UserSettings
import com.example.data.UserSettingsRepository
import com.example.data.WageType
import com.example.data.WorkLogDao
import com.example.data.WorkLogEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Push-then-pull sync against Firestore at `users/{uid}/entries/{uuid}`.
 *
 * Design notes:
 *  - The document id is the entry's stable [WorkLogEntity.uuid], not the Room autoincrement id.
 *    Using the local int id meant two devices both created "entry_1" and overwrote each other.
 *  - Deletes travel as tombstones (`deletedAt` set) rather than document deletions, so a delete
 *    made on one device actually disappears on the others. Firestore document deletion is
 *    invisible to an incremental `updatedAt >` query.
 *  - Conflicts resolve last-write-wins on `updatedAt`, applied in [WorkLogDao.applyRemote].
 *  - Nothing here silently succeeds. If Firestore rejects a write, the exception propagates so
 *    WorkManager can retry and the UI can say so.
 */
class SyncEngine(
    private val appContext: Context,
    private val dao: WorkLogDao,
    private val settingsRepo: UserSettingsRepository
) {

    private val firestore: FirebaseFirestore by lazy {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        FirebaseFirestore.getInstance().also { fs ->
            runCatching {
                fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }

    /**
     * Runs a full sync cycle. Throws on backend failure so the caller (WorkManager) can retry.
     */
    suspend fun sync(uid: String): SyncResult {
        val pushed = push(uid)
        val pulled = pull(uid)
        val now = System.currentTimeMillis()

        settingsRepo.updateSettings(
            settingsRepo.settings.value.copy(lastCloudSyncTimestamp = now)
        )

        // Tombstones that have been pushed are no longer needed after a grace period long
        // enough for other devices to have pulled them.
        runCatching { dao.purgeSyncedTombstones(now - TOMBSTONE_RETENTION_MS) }

        return SyncResult(pushed = pushed, pulled = pulled, at = now)
    }

    /** Uploads every locally-changed row. Returns how many documents were written. */
    private suspend fun push(uid: String): Int {
        val pending = dao.getPendingSync()
        if (pending.isEmpty()) return 0

        val entries = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(SUBCOLLECTION_ENTRIES)

        // Firestore caps a batch at 500 writes.
        pending.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { log ->
                batch.set(entries.document(log.uuid), log.toRemoteMap(), SetOptions.merge())
            }
            batch.commit().await()
            dao.clearPendingFlags(chunk.map { it.uuid })
        }

        pushProfile(uid, settingsRepo.settings.value, pending.size)
        return pending.size
    }

    /** Fetches rows changed since the stored cursor and merges them in. */
    private suspend fun pull(uid: String): Int {
        val settings = settingsRepo.settings.value
        val since = settings.lastPullCursor

        val snapshot = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(SUBCOLLECTION_ENTRIES)
            .whereGreaterThan(FIELD_UPDATED_AT, since)
            .orderBy(FIELD_UPDATED_AT, Query.Direction.ASCENDING)
            .limit(PULL_PAGE_SIZE)
            .get()
            .await()

        if (snapshot.isEmpty) return 0

        val remotes = snapshot.documents.mapNotNull { it.toWorkLog() }
        if (remotes.isEmpty()) return 0

        dao.applyRemoteBatch(remotes)

        val newCursor = remotes.maxOf { it.updatedAt }
        settingsRepo.updateSettings(
            settingsRepo.settings.value.copy(lastPullCursor = newCursor)
        )
        return remotes.size
    }

    /** Small profile doc so a fresh device can restore currency/wage defaults with the data. */
    private suspend fun pushProfile(uid: String, settings: UserSettings, entryCount: Int) {
        runCatching {
            firestore.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUBCOLLECTION_META)
                .document(DOC_PROFILE)
                .set(
                    mapOf(
                        "currencySymbol" to settings.currencySymbol,
                        "defaultWageRate" to settings.defaultWageRate,
                        "defaultWageType" to settings.defaultWageType.name,
                        "defaultOvertimeThreshold" to settings.defaultOvertimeThreshold,
                        "defaultOvertimeMultiplier" to settings.defaultOvertimeMultiplier,
                        "monthlyTargetEarnings" to settings.monthlyTargetEarnings,
                        "payPeriodType" to settings.payPeriodType,
                        "workplaces" to settings.workplaces,
                        "lastPushedEntryCount" to entryCount,
                        "lastPushedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()
        }.onFailure { Log.w(TAG, "Profile push failed (non-fatal): ${it.message}") }
    }

    /**
     * Full restore for a device with no local history: ignores the incremental cursor and
     * pulls everything, including the profile document.
     */
    suspend fun restoreAll(uid: String): Int {
        val userRef = firestore.collection(COLLECTION_USERS).document(uid)

        val snapshot = userRef.collection(SUBCOLLECTION_ENTRIES).get().await()
        val remotes = snapshot.documents.mapNotNull { it.toWorkLog() }
        if (remotes.isNotEmpty()) {
            dao.applyRemoteBatch(remotes)
        }

        runCatching {
            val profile = userRef.collection(SUBCOLLECTION_META).document(DOC_PROFILE).get().await()
            if (profile.exists()) {
                val current = settingsRepo.settings.value
                @Suppress("UNCHECKED_CAST")
                val workplaces = (profile.get("workplaces") as? List<String>)?.takeIf { it.isNotEmpty() }
                settingsRepo.updateSettings(
                    current.copy(
                        currencySymbol = profile.getString("currencySymbol") ?: current.currencySymbol,
                        defaultWageRate = profile.getDouble("defaultWageRate") ?: current.defaultWageRate,
                        defaultWageType = runCatching {
                            WageType.valueOf(profile.getString("defaultWageType") ?: current.defaultWageType.name)
                        }.getOrDefault(current.defaultWageType),
                        defaultOvertimeThreshold = profile.getDouble("defaultOvertimeThreshold")
                            ?: current.defaultOvertimeThreshold,
                        defaultOvertimeMultiplier = profile.getDouble("defaultOvertimeMultiplier")
                            ?: current.defaultOvertimeMultiplier,
                        monthlyTargetEarnings = profile.getDouble("monthlyTargetEarnings")
                            ?: current.monthlyTargetEarnings,
                        payPeriodType = profile.getString("payPeriodType") ?: current.payPeriodType,
                        workplaces = workplaces ?: current.workplaces
                    )
                )
            }
        }.onFailure { Log.w(TAG, "Profile restore failed (non-fatal): ${it.message}") }

        val cursor = remotes.maxOfOrNull { it.updatedAt } ?: 0L
        if (cursor > 0) {
            settingsRepo.updateSettings(settingsRepo.settings.value.copy(lastPullCursor = cursor))
        }
        return remotes.count { it.deletedAt == null }
    }

    private fun WorkLogEntity.toRemoteMap(): Map<String, Any?> = mapOf(
        "uuid" to uuid,
        "date" to date,
        "hoursWorked" to hoursWorked,
        "wageType" to wageType.name,
        "wageRate" to wageRate,
        "overtimeThresholdHours" to overtimeThresholdHours,
        "overtimeMultiplier" to overtimeMultiplier,
        "note" to note,
        "breakMinutes" to breakMinutes,
        "isPaid" to isPaid,
        "workplace" to workplace,
        FIELD_UPDATED_AT to updatedAt,
        "deletedAt" to deletedAt
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toWorkLog(): WorkLogEntity? {
        return try {
            val date = getString("date") ?: return null
            if (date.isBlank()) return null
            WorkLogEntity(
                id = 0,
                uuid = getString("uuid") ?: id,
                date = date,
                hoursWorked = getDouble("hoursWorked") ?: 0.0,
                wageType = runCatching {
                    WageType.valueOf(getString("wageType") ?: WageType.HOURLY.name)
                }.getOrDefault(WageType.HOURLY),
                wageRate = getDouble("wageRate") ?: 0.0,
                overtimeThresholdHours = getDouble("overtimeThresholdHours") ?: 8.0,
                overtimeMultiplier = getDouble("overtimeMultiplier") ?: 1.5,
                note = getString("note").orEmpty(),
                breakMinutes = (getLong("breakMinutes") ?: 0L).toInt(),
                isPaid = getBoolean("isPaid") ?: false,
                workplace = getString("workplace") ?: "Main Workplace",
                updatedAt = getLong(FIELD_UPDATED_AT) ?: 0L,
                deletedAt = getLong("deletedAt"),
                pendingSync = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Skipping unparseable remote doc $id: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val COLLECTION_USERS = "users"
        const val SUBCOLLECTION_ENTRIES = "entries"
        const val SUBCOLLECTION_META = "meta"
        const val DOC_PROFILE = "profile"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val BATCH_SIZE = 400
        const val PULL_PAGE_SIZE = 500L
        const val TOMBSTONE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    }
}
