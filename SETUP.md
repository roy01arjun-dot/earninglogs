# Earninglogs – setup for sign-in & cloud sync

The app works fully offline without any of this. These steps are only needed for accounts and multi-device sync.

## 1. Firebase project
1. Open https://console.firebase.google.com and select (or create) your project.
2. **Project settings → Your apps → Add app → Android**
   - Package name: `com.aistudio.workhourstracker.pxmkzq` (from `app/build.gradle.kts`)
   - Add your **SHA-1** debug/release fingerprints (needed for Google sign-in).
     In Android Studio: *Gradle panel → app → Tasks → android → signingReport*.
3. Download **google-services.json** and put it in the `app/` folder.

## 2. Authentication
*Build → Authentication → Sign-in method* — enable:
- **Email/Password**
- **Google** (after enabling, download google-services.json again – it now contains the web client id the app needs)

## 3. Firestore
1. *Build → Firestore Database → Create database* (production mode, any region).
2. *Rules* tab → paste the contents of `firestore.rules` from this folder → **Publish**.

## 4. Run
Open the project in Android Studio, let Gradle sync, and run the `app` configuration.

### Data layout
```
users/{uid}                 payroll settings (rate, overtime, currency, workplaces…)
users/{uid}/shifts/{uuid}   one document per shift (deleted shifts keep isDeleted = true)
```

### Troubleshooting (shown in the Account tab)
| Message | Fix |
|---|---|
| "Cloud accounts aren't configured" | google-services.json is missing from `app/` |
| "The server refused access" | Publish `firestore.rules` (step 3) |
| "Firestore isn't enabled" | Create the Firestore database (step 3) |
| "This sign-in method is disabled" | Enable Email/Password or Google (step 2) |
| Google sign-in says not enabled / fails immediately | Add SHA-1, enable Google, re-download google-services.json |
