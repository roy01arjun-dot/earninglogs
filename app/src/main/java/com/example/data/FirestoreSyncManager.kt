package com.example.data

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

enum class SyncStatus { SIGNED_OUT, NOT_CONFIGURED, IDLE, SYNCING, SYNCED, WAITING_FOR_NETWORK, PAUSED, ERROR }

data class SyncState(
    val status: SyncStatus = SyncStatus.SIGNED_OUT,
    val message: String = "",
    val lastSyncedAt: Long = 0L
)

/**
 * Two-way sync between Room and Firestore.
 *
 * Layout:  users/{uid}                 – { payroll: {...}, payrollUpdatedAt }
 *          users/{uid}/shifts/{uuid}   – one document per shift (tombstones have isDeleted = true)
 *
 * Rules:   last-write-wins on `updatedAt`. Local edits are flagged `pendingSync` and pushed;
 *          remote changes arrive through a realtime listener and are merged row by row.
 */
class FirestoreSyncManager(
    private val repo: WorkLogRepository,
    private val settingsRepo: UserSettingsRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(
        SyncState(lastSyncedAt = settingsRepo.current.lastCloudSyncTimestamp)
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val pushMutex = Mutex()
    private var shiftsListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null
    private var settingsJob: Job? = null
    private var activeUid: String? = null

    private val firestore: FirebaseFirestore? by lazy {
        if (!FirebaseAuthManager.isCloudAvailable) return@lazy null
        try {
            FirebaseFirestore.getInstance().also { fs ->
                runCatching {
                    fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore unavailable: ${e.message}")
            null
        }
    }

    private fun userDoc(uid: String) = firestore!!.collection("users").document(uid)
    private fun shiftsCol(uid: String) = userDoc(uid).collection("shifts")

    private fun setState(status: SyncStatus, message: String, synced: Boolean = false) {
        val ts = if (synced) System.currentTimeMillis() else _state.value.lastSyncedAt
        _state.value = SyncState(status, message, ts)
        if (synced) settingsRepo.update { it.copy(lastCloudSyncTimestamp = ts) }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Call when a user signs in (or the app starts with a signed-in user). */
    fun start(uid: String, isNewSignIn: Boolean) {
        if (firestore == null) {
            setState(SyncStatus.NOT_CONFIGURED, "Cloud sync isn't configured in this build")
            return
        }
        if (activeUid == uid && shiftsListener != null) return
        stop(keepState = true)
        activeUid = uid

        scope.launch {
            // Shifts created while signed out (or under another account) belong to this account now.
            if (isNewSignIn) repo.markAllPending()
            if (!settingsRepo.current.autoCloudSyncEnabled) {
                setState(SyncStatus.PAUSED, "Auto-sync is off. Tap Sync now to sync manually.")
                return@launch
            }
            attachListeners(uid)
            observeSettings(uid)
            pushPending()
        }
    }

    fun stop(keepState: Boolean = false) {
        shiftsListener?.remove(); shiftsListener = null
        profileListener?.remove(); profileListener = null
        settingsJob?.cancel(); settingsJob = null
        activeUid = null
        if (!keepState) setState(SyncStatus.SIGNED_OUT, "Sign in to back up and sync your shifts")
    }

    fun onAutoSyncChanged(enabled: Boolean) {
        val uid = FirebaseAuthManager.currentUid ?: return
        if (enabled) {
            activeUid = null
            start(uid, isNewSignIn = false)
        } else {
            stop(keepState = true)
            activeUid = uid
            setState(SyncStatus.PAUSED, "Auto-sync is off. Tap Sync now to sync manually.")
        }
    }

    // ------------------------------------------------------------------ push

    /** Upload every locally changed shift. Safe to call often. */
    fun requestPush() {
        if (firestore == null || FirebaseAuthManager.currentUid == null) return
        if (!settingsRepo.current.autoCloudSyncEnabled) return
        scope.launch { pushPending() }
    }

    /** Manual "Sync now": push local changes, then pull everything from the cloud. */
    suspend fun syncNow() {
        val uid = FirebaseAuthManager.currentUid
        if (firestore == null) { setState(SyncStatus.NOT_CONFIGURED, "Cloud sync isn't configured in this build"); return }
        if (uid == null) { setState(SyncStatus.SIGNED_OUT, "Sign in to sync"); return }
        if (!pushPending(force = true)) return
        try {
            setState(SyncStatus.SYNCING, "Downloading latest changes…")
            val snap = withTimeoutOrNull(20_000) { shiftsCol(uid).get().await() }
            if (snap == null) {
                setState(SyncStatus.WAITING_FOR_NETWORK, "Couldn't reach the server. Your data is safe on this device.")
                return
            }
            snap.documents.forEach { doc -> parseShift(doc)?.let { repo.applyRemote(it) } }
            repo.purgeSyncedTombstones()
            pushPayrollIfNewer(uid, force = false)
            setState(SyncStatus.SYNCED, "Everything is up to date", synced = true)
        } catch (e: Exception) {
            reportError(e)
        }
    }

    /** @return false if an error stopped the push. */
    private suspend fun pushPending(force: Boolean = false): Boolean = pushMutex.withLock {
        val uid = FirebaseAuthManager.currentUid ?: return@withLock false
        val fs = firestore ?: return@withLock false
        val pending = repo.pendingForUpload()
        if (pending.isEmpty()) {
            if (!force && _state.value.status != SyncStatus.ERROR) {
                setState(SyncStatus.SYNCED, "Everything is up to date", synced = true)
            }
            return@withLock true
        }
        setState(SyncStatus.SYNCING, "Uploading ${pending.size} change${if (pending.size == 1) "" else "s"}…")
        try {
            for (chunk in pending.chunked(400)) {
                val batch = fs.batch()
                chunk.forEach { log -> batch.set(shiftsCol(uid).document(log.uuid), log.toFirestore()) }
                // Firestore keeps the write in its offline queue even if we stop waiting.
                val acked = withTimeoutOrNull(15_000) { batch.commit().await(); true } ?: false
                if (!acked) {
                    setState(
                        SyncStatus.WAITING_FOR_NETWORK,
                        "Saved on this device. Will upload automatically when you're back online."
                    )
                    return@withLock true
                }
                chunk.forEach { repo.markSynced(it) }
            }
            repo.purgeSyncedTombstones()
            setState(SyncStatus.SYNCED, "Everything is up to date", synced = true)
            true
        } catch (e: Exception) {
            reportError(e)
            false
        }
    }

    // ------------------------------------------------------------------ pull

    private fun attachListeners(uid: String) {
        if (firestore == null) return
        shiftsListener = shiftsCol(uid).addSnapshotListener { snap, error ->
            if (error != null) { reportError(error); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            val changes = snap.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .filterNot { it.document.metadata.hasPendingWrites() } // our own local echo
                .mapNotNull { parseShift(it.document) }
            if (changes.isEmpty()) return@addSnapshotListener
            scope.launch {
                var changed = 0
                changes.forEach { if (repo.applyRemote(it)) changed++ }
                repo.purgeSyncedTombstones()
                if (!snap.metadata.isFromCache) {
                    setState(
                        SyncStatus.SYNCED,
                        if (changed > 0) "Received $changed update${if (changed == 1) "" else "s"} from your other devices"
                        else "Everything is up to date",
                        synced = true
                    )
                }
            }
        }
        profileListener = userDoc(uid).addSnapshotListener { doc, error ->
            if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
            val remoteTs = doc.getLong("payrollUpdatedAt") ?: 0L
            if (remoteTs > settingsRepo.current.payrollUpdatedAt) {
                val remote = parsePayroll(doc) ?: return@addSnapshotListener
                settingsRepo.update { it.withPayrollFrom(remote) }
            }
        }
    }

    private fun observeSettings(uid: String) {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepo.settings
                .distinctUntilChangedBy { it.payrollUpdatedAt }
                .collect { pushPayrollIfNewer(uid, force = false) }
        }
    }

    private var lastPushedPayrollAt = 0L

    private suspend fun pushPayrollIfNewer(uid: String, force: Boolean) {
        val s = settingsRepo.current
        if (s.payrollUpdatedAt == 0L) return
        if (!force && s.payrollUpdatedAt <= lastPushedPayrollAt) return
        try {
            val remote = withTimeoutOrNull(10_000) { userDoc(uid).get().await() }
            val remoteTs = remote?.getLong("payrollUpdatedAt") ?: 0L
            if (remote != null && remoteTs > s.payrollUpdatedAt) {
                parsePayroll(remote)?.let { p -> settingsRepo.update { it.withPayrollFrom(p) } }
                lastPushedPayrollAt = remoteTs
                return
            }
            val payload = mapOf(
                "payroll" to mapOf(
                    "defaultWageType" to s.defaultWageType.name,
                    "defaultWageRate" to s.defaultWageRate,
                    "defaultOvertimeThreshold" to s.defaultOvertimeThreshold,
                    "defaultOvertimeMultiplier" to s.defaultOvertimeMultiplier,
                    "currencySymbol" to s.currencySymbol,
                    "monthlyTargetEarnings" to s.monthlyTargetEarnings,
                    "payPeriodType" to s.payPeriodType,
                    "workplaces" to s.workplaces,
                    "defaultTaxRate" to s.defaultTaxRate
                ),
                "payrollUpdatedAt" to s.payrollUpdatedAt
            )
            userDoc(uid).set(payload, SetOptions.merge())
            lastPushedPayrollAt = s.payrollUpdatedAt
        } catch (e: Exception) {
            Log.w(TAG, "Payroll settings sync failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ mapping

    private fun WorkLogEntity.toFirestore(): Map<String, Any> = mapOf(
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
        "totalPay" to totalPay, // convenience for console/reporting only
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun parseShift(doc: DocumentSnapshot): WorkLogEntity? = try {
        val date = doc.getString("date").orEmpty()
        if (date.isBlank()) null else WorkLogEntity(
            uuid = doc.getString("uuid") ?: doc.id,
            date = date,
            hoursWorked = doc.getDouble("hoursWorked") ?: 0.0,
            wageType = runCatching { WageType.valueOf(doc.getString("wageType")!!) }.getOrDefault(WageType.HOURLY),
            wageRate = doc.getDouble("wageRate") ?: 0.0,
            overtimeThresholdHours = doc.getDouble("overtimeThresholdHours") ?: 8.0,
            overtimeMultiplier = doc.getDouble("overtimeMultiplier") ?: 1.5,
            note = doc.getString("note").orEmpty(),
            breakMinutes = (doc.getLong("breakMinutes") ?: 0L).toInt(),
            isPaid = doc.getBoolean("isPaid") ?: false,
            workplace = doc.getString("workplace") ?: "Main Workplace",
            updatedAt = doc.getLong("updatedAt") ?: 0L,
            isDeleted = doc.getBoolean("isDeleted") ?: false,
            pendingSync = false
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed shift ${doc.id}: ${e.message}")
        null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePayroll(doc: DocumentSnapshot): UserSettings? {
        val p = doc.get("payroll") as? Map<String, Any?> ?: return null
        val d = UserSettings()
        fun num(key: String, def: Double) = (p[key] as? Number)?.toDouble() ?: def
        return UserSettings(
            defaultWageType = runCatching { WageType.valueOf(p["defaultWageType"] as String) }.getOrDefault(d.defaultWageType),
            defaultWageRate = num("defaultWageRate", d.defaultWageRate),
            defaultOvertimeThreshold = num("defaultOvertimeThreshold", d.defaultOvertimeThreshold),
            defaultOvertimeMultiplier = num("defaultOvertimeMultiplier", d.defaultOvertimeMultiplier),
            currencySymbol = p["currencySymbol"] as? String ?: d.currencySymbol,
            monthlyTargetEarnings = num("monthlyTargetEarnings", d.monthlyTargetEarnings),
            payPeriodType = p["payPeriodType"] as? String ?: d.payPeriodType,
            workplaces = (p["workplaces"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            defaultTaxRate = num("defaultTaxRate", d.defaultTaxRate),
            payrollUpdatedAt = doc.getLong("payrollUpdatedAt") ?: 0L
        )
    }

    private fun reportError(e: Throwable) {
        Log.w(TAG, "Sync error", e)
        val msg = when ((e as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "The server refused access. Publish the firestore.rules from SETUP.md in Firebase Console."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Your session expired. Please sign in again."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Server unreachable. Changes are saved and will upload automatically."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "Firestore isn't enabled for this Firebase project yet (Firebase Console → Firestore Database)."
            else -> e.localizedMessage ?: "Sync failed"
        }
        val status = if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.UNAVAILABLE)
            SyncStatus.WAITING_FOR_NETWORK else SyncStatus.ERROR
        setState(status, msg)
    }

    companion object {
        private const val TAG = "FirestoreSync"
    }
}
