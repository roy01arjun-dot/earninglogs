package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons (simple manual DI). */
object AppContainer {
    @Volatile private var initialized = false

    lateinit var settingsRepo: UserSettingsRepository
        private set
    lateinit var repository: WorkLogRepository
        private set
    lateinit var syncManager: FirestoreSyncManager
        private set

    /** Outlives any single screen/ViewModel so uploads aren't cancelled on rotation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            FirebaseAuthManager.initialize(app)
            // The shift timer was removed in 1.3.0; drop its leftover saved state.
            app.deleteSharedPreferences("shift_timer_prefs")
            settingsRepo = UserSettingsRepository(app)
            repository = WorkLogRepository(WorkLogDatabase.getDatabase(app), settingsRepo)
            syncManager = FirestoreSyncManager(repository, settingsRepo, appScope)
            initialized = true
        }
    }
}
