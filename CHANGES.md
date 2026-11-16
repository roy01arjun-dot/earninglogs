# What changed

## Bugs fixed

### Sign-in / login page
- **Firebase Auth was never actually used.** `getAuthInstance()` contained
  `FirebaseApp.getApps(null as? Context ?: return null)`, which always returns `null`.
  Every login silently fell back to a fake local account stored in SharedPreferences.
- Signing in with an unknown email **silently created an account** instead of showing an error.
- The **Google** and **"device session"** buttons were fake (hard-coded `worker@google.com`, `device_user@vault.local`).
- Password reset always said "sent" even when nothing was sent.
- Lock screen advertised a **master PIN `1234` / `0000`** that unlocked anyone's app, and the PIN was stored in plain text.
- App lock was **on by default** and re-locked on *every* resume – including returning from the
  fingerprint prompt itself and the file pickers – which caused lock loops.

Now: real Firebase email/password, real Google sign-in (Credential Manager), clear error messages,
a clean sign-in page with "Continue without an account", lock is opt-in, re-locks only after 30 s in the
background, and the optional PIN is hashed.

### Sync
- Because auth was fake, the Firestore uid was invented (`user_name_gmail_com`), so security rules rejected
  every write. The error was caught and the app **still showed "Successfully synced"**.
- Shifts were stored as `entry_<local id>` – two phones each creating shift #5 **overwrote each other**.
- Deletes never reached the cloud (except single deletes while online), so deleted shifts **came back** on next sync.
- Pulling from the cloud **overwrote local shifts that had the same local id**.
- Restoring from the cloud **reset local settings** (tax rate, monthly goal, lock settings) to defaults.
- A second copy of all data was written to an extra `work_log_backups` doc (could exceed the 1 MB doc limit).
- The background sync captured settings before running and wrote them back afterwards, undoing any change
  made in between.

Now: every shift has a UUID, `updatedAt` (last-write-wins) and a delete tombstone; changes are queued
(`pendingSync`) and uploaded when online; a realtime listener merges changes from other devices; payroll
settings sync separately; errors are reported honestly with a fix hint. See `SETUP.md` and `firestore.rules`.

### Other
- Database upgrades no longer **wipe all shifts** (proper Room migration 11 → 12; the old code deleted the DB
  on any schema problem).
- Workplace filter on the dashboard compared `"All"` vs `"ALL"` – filtering never matched "All".
- Settings stored as Float lost precision for large won amounts; workplace names containing commas broke.
- Build failed out-of-the-box in Android Studio because `debug.keystore` was required; signing is now optional.
- Added the missing `kotlinx-coroutines-play-services` dependency used by `.await()`.
- Removed dead code: unused `WorkLogEntry` table, fake "Cloud Vault", unused timer/chart components.

## Redesign
- New design system (`ui/theme`, `ui/components/DesignSystem.kt`): indigo brand colour, status colours for
  paid / unpaid / overtime, consistent cards, spacing and tabular numbers. **Full dark mode** (the old UI hard-coded light colours).
- Screens: **Sign in**, **Lock**, **Home** (earnings hero + goal progress, stats, shift timer, payout & tax,
  workplace breakdown, recent shifts), **Shifts** (search, paid filter, grouped by month),
  **Reports** (weekly chart, breakdown, shareable pay statement), **Account** (profile, sync status,
  security, backup), **Pay & workplaces** settings.
- Full-screen shift editor with live pay estimate, validation and date picker.
- New adaptive app icon.
