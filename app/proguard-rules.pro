# R8 / ProGuard rules for the release build.
#
# Release now runs with isMinifyEnabled = true, so anything reflected over at runtime has to be
# kept explicitly. Most of the libraries here ship their own consumer rules; the entries below
# cover the cases that are specific to this app.

# ---- Kotlin / coroutines ----
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---- Room ----
# Room generates implementations that reference the entity by name.
-keep class com.example.data.WorkLogEntity { *; }
-keep class com.example.data.WageType { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ---- Firestore ----
# Firestore maps documents onto model classes reflectively.
-keepclassmembers class com.example.data.** {
  <init>();
  <fields>;
}
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---- Credential Manager / Google ID ----
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ---- WorkManager ----
# Workers are instantiated by class name from the WorkManager database.
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }
-keep class com.example.data.sync.SyncWorker { *; }

# ---- Moshi / Retrofit / OkHttp (kept from the template's dependency set) ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
  @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *

# Keep line numbers so Play Console crash reports stay readable, but hide the original
# file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
