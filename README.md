# Earninglogs

Android app for logging work shifts and computing overtime pay, with optional cloud backup.

Offline-first: everything works with no account and no network. Signing in with Google adds a
backup in your own Firebase project so you can move to a new phone.

- **Stack:** Kotlin, Jetpack Compose, Material 3 (dynamic color), Room, WorkManager,
  Firebase Auth + Firestore
- **Min SDK** 24, **target/compile SDK** 36
- **Project location:** `C:\dev\earninglogs` — deliberately *not* in OneDrive, see below

## Build

```bash
cd C:\dev\earninglogs && .\gradlew.bat assembleDebug
```

Requires JDK 21 and the Android SDK (`%LOCALAPPDATA%\Android\Sdk`); `local.properties` points at it.
For release builds and Play submission see [RELEASE.md](RELEASE.md).

> Do not move this project into OneDrive or onto the Desktop. Gradle writes tens of thousands of
> files under `build/`, OneDrive holds them open while syncing, and the build fails with
> `AccessDeniedException`.

## Architecture

```
data/
  WorkLogEntity          shift + sync fields (uuid, updatedAt, deletedAt, pendingSync)
  WorkLogDao             reads exclude tombstones; applyRemote() holds the merge rules
  WorkLogDatabase        v12, real migration from v11 (no destructive fallback)
  WorkLogRepository      the only thing the UI touches; writes Room, then queues a sync
  UserSettingsRepository preferences + salted PIN hash
  auth/AuthRepository    Credential Manager -> Firebase Auth; sole source of identity
  sync/SyncEngine        push dirty rows, pull by updatedAt cursor, last-write-wins
  sync/SyncWorker        WorkManager: retries, network constraint, survives process death
ui/
  WorkLogViewModel       screen state and summaries; contains no network code
  theme/                 M3 dynamic color + semantic AppColors accessors
  screens/, components/
```

### How sync works

1. A local edit writes to Room with `pendingSync = 1` and a fresh `updatedAt`.
2. The repository enqueues `SyncWorker` (unique work, `REPLACE`, so an edit burst collapses
   into one upload).
3. The worker pushes every pending row to `users/{uid}/entries/{uuid}`, then pulls rows with
   `updatedAt >` the stored cursor.
4. Conflicts resolve last-write-wins, except that an unpushed local edit is never overwritten
   by a remote row at the same or older timestamp.
5. Deletes are tombstones (`deletedAt` set), not document deletions, so they propagate to other
   devices. Pushed tombstones are purged after 90 days.

Signed out, steps 2–5 don't run and nothing is lost — rows just stay pending until you sign in.

## Firebase

Project `work-hours-tracker-9e02b`. The debug keystore's SHA-1 is already registered, so Google
sign-in works on debug builds. Security rules live in [`firestore.rules`](firestore.rules).
See [FIREBASE_SETUP.md](FIREBASE_SETUP.md) for the original setup notes.

## Tests

```bash
.\gradlew.bat testDebugUnitTest
```

19 tests. `SecurityAndSyncTest` covers the PIN hashing and the sync merge rules — including a
regression test that the old hardcoded master PINs no longer unlock the app.

## Notes on the rewrite

This version replaced a cloud-sync implementation that never contacted Firebase. Sign-in was a
hardcoded list of three email addresses plus a one-time code the app generated, displayed, and
verified against itself; `FirebaseAuth.signIn*` was never called. Because `request.auth` was
therefore always null, every Firestore write was denied by the security rules, caught, and
redirected to a local file named `cloud_vault_remote_storage.json` — while the UI reported
"Synced ✓ · Online".

Also fixed: a hardcoded master PIN (`1234` / `0000`) that unlocked any device, a cleartext PIN in
SharedPreferences, `restoreFromCloudVault()` returning a hardcoded `true`, `fallbackToDestructiveMigration`
silently wiping pay history on schema change, and a release signing config pointing at a
nonexistent keystore that made `assembleRelease` impossible to run.
