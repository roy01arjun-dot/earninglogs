package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.UserSettingsRepository
import com.example.data.WorkLogDatabase
import com.example.data.auth.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Runs sync outside the UI.
 *
 * This is the "sync belongs in the backend" change: nothing in the ViewModel kicks off network
 * work any more. The app writes to Room, marks the row pending, and enqueues this worker.
 * WorkManager owns the retry policy, the network constraint, and surviving process death - all
 * of which the old `viewModelScope.launch` sync silently lost the moment the screen went away.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = AuthRepository(applicationContext).currentUid()
            ?: return Result.success() // Signed out: local-only is a valid state, not a failure.

        val settingsRepo = UserSettingsRepository(applicationContext)
        if (!settingsRepo.settings.value.autoCloudSyncEnabled && !isManualRun()) {
            return Result.success()
        }

        val dao = WorkLogDatabase.getDatabase(applicationContext).workLogDao()
        val engine = SyncEngine(applicationContext, dao, settingsRepo)

        return try {
            val result = engine.sync(uid)
            Log.i(TAG, "Sync ok: pushed=${result.pushed} pulled=${result.pulled}")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed (attempt ${runAttemptCount + 1}): ${e.message}")
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private fun isManualRun(): Boolean = inputData.getBoolean(KEY_MANUAL, false)

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_ATTEMPTS = 5
        private const val KEY_MANUAL = "manual"

        const val UNIQUE_ONE_SHOT = "earninglogs_sync_now"
        const val UNIQUE_PERIODIC = "earninglogs_sync_periodic"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Queue a sync as soon as the network allows. Safe to call on every local edit -
         * [ExistingWorkPolicy.REPLACE] collapses a burst of edits into one upload.
         */
        fun enqueueOneShot(context: Context, manual: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(androidx.work.workDataOf(KEY_MANUAL to manual))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Safety net so a device that missed a push still converges. */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(UNIQUE_ONE_SHOT)
                cancelUniqueWork(UNIQUE_PERIODIC)
            }
        }

        /** True while a sync is queued or running, for the UI indicator. */
        fun isRunning(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(UNIQUE_ONE_SHOT)
                .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
    }
}
