package com.example.data

/**
 * User preferences only.
 *
 * Note what is deliberately *not* here: signed-in state, email, display name and auth provider.
 * Those used to be mirrored into SharedPreferences, which let the app believe it was signed in
 * when Firebase disagreed. FirebaseAuth is now the single source of truth for identity - see
 * [com.example.data.auth.AuthRepository].
 */
data class UserSettings(
    val defaultWageType: WageType = WageType.HOURLY,
    val defaultWageRate: Double = 25000.0,
    val defaultOvertimeThreshold: Double = 8.0,
    val defaultOvertimeMultiplier: Double = 1.5,
    val currencySymbol: String = "₩",
    val monthlyTargetEarnings: Double = 3500000.0,
    val payPeriodType: String = "WEEKLY", // "WEEKLY" | "BIWEEKLY" | "MONTHLY"
    val workplaces: List<String> = listOf("Main Workplace", "Secondary Job", "Freelance"),

    // Sync preferences
    val autoCloudSyncEnabled: Boolean = true,
    val lastCloudSyncTimestamp: Long = 0L,
    /** Watermark for incremental pulls: only fetch remote rows changed after this. */
    val lastPullCursor: Long = 0L,

    // App lock
    val appLockEnabled: Boolean = false,
    val requireAuthOnResume: Boolean = true,
    /** PBKDF2 hash of the fallback PIN, Base64. Empty means no PIN is set. */
    val pinHash: String = "",
    /** Per-user random salt, Base64. */
    val pinSalt: String = ""
) {
    val hasPin: Boolean get() = pinHash.isNotBlank() && pinSalt.isNotBlank()
}
