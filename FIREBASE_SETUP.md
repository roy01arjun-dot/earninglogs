# Firebase Cloud Backup — Setup Notes

The app is connected to the Firebase project **Work Hours Tracker**
(`work-hours-tracker-9e02b`, console: https://console.firebase.google.com/project/work-hours-tracker-9e02b).

## Already done

- Firebase project created (Spark / free plan, Google Analytics off)
- Android app registered: `com.aistudio.workhourstracker.pxmkzq`
- `app/google-services.json` downloaded and placed (includes the Google sign-in OAuth client)
- **Authentication**: Google sign-in provider enabled
- **Cloud Firestore**: database created in `asia-northeast3` (Seoul) with these rules:

  ```
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      match /users/{userId}/{document=**} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
  ```

- App code: `CloudBackupManager.kt` + "Cloud Backup (Google Account)" section in Settings.
  Backups are stored at `users/{uid}/backups/latest` as the same JSON the local
  Export/Import feature uses.

## SHA-1 fingerprint — DONE

A debug keystore was generated at the project root (`debug.keystore`, store/key
password `android`, alias `androiddebugkey`) — the same file `app/build.gradle.kts`
debugConfig points at, so debug builds on any machine sign with it. Its SHA-1

```
70:BD:51:24:BD:28:6E:F8:1A:8E:25:C0:A4:A7:2C:B3:78:FC:BA:00
```

is registered in Firebase, and `app/google-services.json` was re-downloaded
afterwards (it contains the Android OAuth client). Nothing more to do for
debug builds.

If you later build a **release** APK or publish via Google Play, add that
signing key's SHA-1 the same way: Firebase console → Project settings →
Your apps → **Add fingerprint** (for Play, copy the App signing key SHA-1 from
Play Console → Setup → App integrity).

## How to use in the app

Settings (gear icon) → **Cloud Backup (Google Account)**:

- **Back Up** — signs you in with Google (first time) and uploads all work logs
  and settings to your Firebase account.
- **Restore** — downloads the latest cloud backup and merges it into the app.
  Works on any phone where you sign in with the same Google account.
