# R8 rules for the release build.
# Room, Firebase, Compose and Credential Manager ship most of their own consumer rules;
# the entries below cover the gaps.

# Keep line numbers so Play Console crash reports are readable (file names are hidden).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager loads the Play Services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Google ID token credential is created from a Bundle by class name.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Enum names are persisted (Room converter, Firestore, JSON backups) - never rename them.
-keepclassmembers enum com.example.data.** { *; }
