package com.example.data.sync

/**
 * What the sync engine is doing, for display only.
 *
 * Deliberately has no "synced" state that can be reached without the backend confirming a write.
 * The previous version showed "Synced ✓ · Online" after writing to a local file it called a
 * "cloud vault", which is the single most misleading thing an app can tell someone about their
 * backup.
 */
sealed interface SyncStatus {
    data object Idle : SyncStatus

    /** Signed out. Local-only by design, not an error. */
    data object NotSignedIn : SyncStatus

    data object Syncing : SyncStatus

    data class Synced(val at: Long, val pushed: Int, val pulled: Int) : SyncStatus

    /** Changes are queued locally; WorkManager will retry when the network returns. */
    data class Pending(val count: Int) : SyncStatus

    data class Failed(val message: String) : SyncStatus
}

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val at: Long
)
