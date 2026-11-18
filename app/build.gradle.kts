import java.util.Properties
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Must stay the same: google-services.json and the Play listing are keyed to it.
    applicationId = "com.aistudio.workhourstracker.pxmkzq"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "1.2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Upload key for Google Play. Read from keystore.properties (gitignored, created by
    // scripts/create-upload-key.ps1), falling back to environment variables for CI.
    // Nothing secret lives in the repo.
    val signingProps = Properties().apply {
      val f = rootProject.file("keystore.properties")
      if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
      signingProps.getProperty(prop)?.takeIf { it.isNotBlank() } ?: System.getenv(env)
    val releaseStorePath = signingValue("storeFile", "KEYSTORE_PATH")
    if (releaseStorePath != null && file(releaseStorePath).exists()) {
      create("release") {
        storeFile = file(releaseStorePath)
        storePassword = signingValue("storePassword", "STORE_PASSWORD")
        keyAlias = signingValue("keyAlias", "KEY_ALIAS") ?: "upload"
        keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
      }
    }
    // Shared debug keystore (its SHA-1 is registered in Firebase so Google sign-in works on debug builds).
    val debugKeystore = file("${rootDir}/debug.keystore")
    if (debugKeystore.exists()) {
      create("debugConfig") {
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      // R8 shrinking + obfuscation for a smaller production APK/AAB.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    debug { signingConfigs.findByName("debugConfig")?.let { signingConfig = it } }
  }
  compileOptions {
    // java.time (LocalDate etc.) is used everywhere but only exists natively on Android 8+.
    // Without desugaring the app crashes on Android 7.x (minSdk 24).
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Lets ScreenshotTest write PNGs to app/build/screenshots.
      all { it.systemProperty("roborazzi.test.record", "true") }
    }
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.biometric)

  // Sign-in & sync
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  // Offscreen screenshots of real screens (see ScreenshotTest)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
}
