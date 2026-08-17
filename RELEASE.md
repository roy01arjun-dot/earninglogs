# Shipping Earninglogs to Google Play

Everything below has been verified on this machine except the steps that need your own
credentials (creating the upload key, and the Play Console itself) — those are yours to run.

Build toolchain already installed here:

- JDK 21 — `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`
- Android SDK — `%LOCALAPPDATA%\Android\Sdk` (platform 36 + 36.1, build-tools 36.0.0)
- Gradle 9.7 via the wrapper (`gradlew.bat`)

> **The project lives at `C:\dev\earninglogs`, not on the Desktop.**
> Android builds create tens of thousands of files under `build/`. Inside a OneDrive-synced
> folder, OneDrive holds those files open mid-sync and Gradle fails with
> `AccessDeniedException` / `Unable to delete directory`. That is exactly what happened on the
> first build attempt. Keep the project outside OneDrive.

---

## 1. Create your upload key

**You run this — it sets a password, so it should not pass through anyone else's hands.**
Pick a strong password and store it in your password manager. If you lose this file *and* its
password before enabling Play App Signing, you cannot ship an update to the same listing.

```bash
keytool -genkeypair -v -keystore C:\dev\earninglogs\upload-key.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Then create `C:\dev\earninglogs\keystore.properties` (already gitignored, alongside `*.jks`):

```properties
storeFile=C:\\dev\\earninglogs\\upload-key.jks
storePassword=<what you just typed>
keyAlias=upload
keyPassword=<what you just typed>
```

The build reads this file if present, otherwise falls back to `KEYSTORE_PATH` / `STORE_PASSWORD` /
`KEY_ALIAS` / `KEY_PASSWORD` environment variables, otherwise produces an **unsigned** bundle
rather than failing.

## 2. Register the upload key's SHA-1 with Firebase

Google Sign-In only works for builds signed with a certificate Firebase knows about.

```bash
keytool -list -v -keystore C:\dev\earninglogs\upload-key.jks -alias upload
```

Copy the `SHA1:` line into
[Firebase console → Project settings → Your apps → Add fingerprint](https://console.firebase.google.com/project/work-hours-tracker-9e02b/settings/general),
then **re-download `google-services.json`** into `app/` — it embeds the OAuth client tied to
those fingerprints.

The debug key is already registered (`70:BD:51:24:BD:28:6E:F8:1A:8E:25:C0:A4:A7:2C:B3:78:FC:BA:00`),
which is why sign-in works on debug builds today.

## 3. Build the bundle

```bash
cd C:\dev\earninglogs && .\gradlew.bat bundleRelease
```

Output: `app\build\outputs\bundle\release\app-release.aab` (~9 MB with R8 enabled; the debug
APK is 27 MB for comparison).

## 4. Publish Firestore rules

Paste [`firestore.rules`](firestore.rules) into
[Firestore → Rules](https://console.firebase.google.com/project/work-hours-tracker-9e02b/firestore/rules)
and press **Publish**. The ownership rule matches what is already live; the new file adds
per-field validation and explicitly denies everything outside `users/{uid}`.

## 5. Play Console — internal testing

1. **One-time:** create a developer account at
   [play.google.com/console](https://play.google.com/console) (US$25, once, not per app).
2. **Create app** — name *Earninglogs*, app (not game), free. You cannot change free→paid later.
3. **Testing → Internal testing → Create new release.**
4. Upload `app-release.aab`. Leave **Play App Signing** enabled (the default).
5. **Important:** after the first upload, Play generates its own app signing key. Copy its
   SHA-1 from **Test and release → Setup → App integrity → App signing key certificate** and add
   it to Firebase the same way as step 2. Without this, Google Sign-In fails for testers even
   though it works locally — Play re-signs your bundle with a different key than your upload key.
6. **Testers** — add your own Google account to an email list, then share the opt-in link.
7. Internal testing skips full review, so it's usually available in minutes.

Before promoting to production you'll also need: a privacy policy URL, the Data safety form
(this app collects shift times, wage rates and a Google account email; all of it is stored in
your own Firebase project), a 512×512 icon, a 1024×500 feature graphic, and at least two
phone screenshots.

## Raising the version for each subsequent upload

Play rejects any bundle whose `versionCode` is not strictly greater than the last one.
In `app/build.gradle.kts`:

```kotlin
versionCode = 2      // bump every upload
versionName = "2.0"  // what users see
```

---

## Verified on this machine

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` | passes, 0 errors |
| `:app:assembleDebug` | passes — `app-debug.apk`, 27.3 MB |
| `:app:testDebugUnitTest` | passes |
| `:app:bundleRelease` (R8 + resource shrinking) | passes — `app-release.aab`, 9.1 MB, unsigned |
| Debug keystore SHA-1 vs. Firebase | matches |

Not verified — needs a physical device or emulator, which this machine has no SDK image for:
Google sign-in round trip, Firestore read/write against the live rules, the v11→v12 Room
migration on real data, and biometric unlock. Run these on your phone before promoting the
build past internal testing.
