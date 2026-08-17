package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.util.PinHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(migrateThenLoad())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    /**
     * Earlier builds kept the app-lock PIN in cleartext under "security_pin", plus a mirror of
     * the signed-in identity that could disagree with FirebaseAuth. Convert the PIN to a salted
     * hash once, then delete the legacy keys.
     */
    private fun migrateThenLoad(): UserSettings {
        val legacyPin = prefs.getString(LEGACY_KEY_PIN, null)
        val legacyKeysPresent = prefs.contains(LEGACY_KEY_PIN) ||
            prefs.contains(LEGACY_KEY_IS_LOGGED_IN) ||
            prefs.contains(LEGACY_KEY_USER_EMAIL) ||
            prefs.contains(LEGACY_KEY_USER_NAME) ||
            prefs.contains(LEGACY_KEY_AUTH_PROVIDER)

        if (legacyKeysPresent) {
            prefs.edit().apply {
                if (!legacyPin.isNullOrBlank() && !prefs.contains(KEY_PIN_HASH)) {
                    val salt = PinHasher.newSalt()
                    putString(KEY_PIN_SALT, salt)
                    putString(KEY_PIN_HASH, PinHasher.hash(legacyPin, salt))
                }
                remove(LEGACY_KEY_PIN)
                remove(LEGACY_KEY_IS_LOGGED_IN)
                remove(LEGACY_KEY_USER_EMAIL)
                remove(LEGACY_KEY_USER_NAME)
                remove(LEGACY_KEY_AUTH_PROVIDER)
                apply()
            }
        }
        return load()
    }

    private fun load(): UserSettings {
        val wageType = runCatching {
            WageType.valueOf(prefs.getString(KEY_WAGE_TYPE, null) ?: WageType.HOURLY.name)
        }.getOrDefault(WageType.HOURLY)

        val workplaces = (prefs.getString(KEY_WORKPLACES, null) ?: DEFAULT_WORKPLACES)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { DEFAULT_WORKPLACES.split(",") }

        return UserSettings(
            defaultWageType = wageType,
            defaultWageRate = prefs.getFloat(KEY_WAGE_RATE, 25_000f).toDouble(),
            defaultOvertimeThreshold = prefs.getFloat(KEY_OT_THRESHOLD, 8f).toDouble(),
            defaultOvertimeMultiplier = prefs.getFloat(KEY_OT_MULTIPLIER, 1.5f).toDouble(),
            currencySymbol = prefs.getString(KEY_CURRENCY, "₩") ?: "₩",
            monthlyTargetEarnings = prefs.getFloat(KEY_MONTHLY_TARGET, 3_500_000f).toDouble(),
            payPeriodType = prefs.getString(KEY_PAY_PERIOD, "WEEKLY") ?: "WEEKLY",
            workplaces = workplaces,
            autoCloudSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, true),
            lastCloudSyncTimestamp = prefs.getLong(KEY_LAST_SYNC, 0L),
            lastPullCursor = prefs.getLong(KEY_PULL_CURSOR, 0L),
            appLockEnabled = prefs.getBoolean(KEY_APP_LOCK, false),
            requireAuthOnResume = prefs.getBoolean(KEY_LOCK_ON_RESUME, true),
            pinHash = prefs.getString(KEY_PIN_HASH, "") ?: "",
            pinSalt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        )
    }

    fun updateSettings(newSettings: UserSettings) {
        prefs.edit().apply {
            putString(KEY_WAGE_TYPE, newSettings.defaultWageType.name)
            putFloat(KEY_WAGE_RATE, newSettings.defaultWageRate.toFloat())
            putFloat(KEY_OT_THRESHOLD, newSettings.defaultOvertimeThreshold.toFloat())
            putFloat(KEY_OT_MULTIPLIER, newSettings.defaultOvertimeMultiplier.toFloat())
            putString(KEY_CURRENCY, newSettings.currencySymbol)
            putFloat(KEY_MONTHLY_TARGET, newSettings.monthlyTargetEarnings.toFloat())
            putString(KEY_PAY_PERIOD, newSettings.payPeriodType)
            putString(KEY_WORKPLACES, newSettings.workplaces.joinToString(","))
            putBoolean(KEY_AUTO_SYNC, newSettings.autoCloudSyncEnabled)
            putLong(KEY_LAST_SYNC, newSettings.lastCloudSyncTimestamp)
            putLong(KEY_PULL_CURSOR, newSettings.lastPullCursor)
            putBoolean(KEY_APP_LOCK, newSettings.appLockEnabled)
            putBoolean(KEY_LOCK_ON_RESUME, newSettings.requireAuthOnResume)
            putString(KEY_PIN_HASH, newSettings.pinHash)
            putString(KEY_PIN_SALT, newSettings.pinSalt)
            apply()
        }
        _settings.value = newSettings
    }

    /** Sets (or clears, when [pin] is blank) the fallback PIN. Never stores the PIN itself. */
    fun setPin(pin: String) {
        val current = _settings.value
        if (pin.isBlank()) {
            updateSettings(current.copy(pinHash = "", pinSalt = ""))
            return
        }
        val salt = PinHasher.newSalt()
        updateSettings(current.copy(pinSalt = salt, pinHash = PinHasher.hash(pin, salt)))
    }

    fun verifyPin(pin: String): Boolean {
        val current = _settings.value
        if (!current.hasPin) return false
        return PinHasher.verify(pin, current.pinSalt, current.pinHash)
    }

    private companion object {
        const val PREFS_NAME = "user_work_prefs"
        const val DEFAULT_WORKPLACES = "Main Workplace,Secondary Job,Freelance"

        const val KEY_WAGE_TYPE = "default_wage_type"
        const val KEY_WAGE_RATE = "default_wage_rate"
        const val KEY_OT_THRESHOLD = "default_overtime_threshold"
        const val KEY_OT_MULTIPLIER = "default_overtime_multiplier"
        const val KEY_CURRENCY = "currency_symbol"
        const val KEY_MONTHLY_TARGET = "monthly_target"
        const val KEY_PAY_PERIOD = "pay_period_type"
        const val KEY_WORKPLACES = "workplaces_list"
        const val KEY_AUTO_SYNC = "auto_cloud_sync"
        const val KEY_LAST_SYNC = "last_cloud_sync"
        const val KEY_PULL_CURSOR = "last_pull_cursor"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_LOCK_ON_RESUME = "require_auth_on_resume"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"

        // Removed on first launch of this version.
        const val LEGACY_KEY_PIN = "security_pin"
        const val LEGACY_KEY_IS_LOGGED_IN = "is_logged_in"
        const val LEGACY_KEY_USER_EMAIL = "user_email"
        const val LEGACY_KEY_USER_NAME = "user_name"
        const val LEGACY_KEY_AUTH_PROVIDER = "auth_provider"
    }
}
