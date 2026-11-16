package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.security.MessageDigest

/**
 * Single source of truth for settings. It is a process-wide singleton (see [AppContainer]);
 * the old code created several instances whose StateFlows drifted apart.
 */
class UserSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("user_work_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    val current: UserSettings get() = _settings.value

    private fun loadSettings(): UserSettings {
        val d = UserSettings()
        val wageType = runCatching {
            WageType.valueOf(prefs.getString(K_WAGE_TYPE, d.defaultWageType.name)!!)
        }.getOrDefault(d.defaultWageType)

        val workplaces = readWorkplaces().ifEmpty { d.workplaces }

        // One-time migration of the old plain-text PIN to a hash.
        var pinHash = prefs.getString(K_PIN_HASH, "") ?: ""
        val legacyPin = prefs.getString(K_LEGACY_PIN, "") ?: ""
        if (pinHash.isBlank() && legacyPin.isNotBlank()) {
            pinHash = hashPin(legacyPin)
            prefs.edit { putString(K_PIN_HASH, pinHash); remove(K_LEGACY_PIN) }
        }

        return UserSettings(
            defaultWageType = wageType,
            defaultWageRate = getDouble(K_WAGE_RATE, d.defaultWageRate),
            defaultOvertimeThreshold = getDouble(K_OT_THRESHOLD, d.defaultOvertimeThreshold),
            defaultOvertimeMultiplier = getDouble(K_OT_MULTIPLIER, d.defaultOvertimeMultiplier),
            currencySymbol = prefs.getString(K_CURRENCY, d.currencySymbol) ?: d.currencySymbol,
            monthlyTargetEarnings = getDouble(K_MONTHLY_TARGET, d.monthlyTargetEarnings),
            payPeriodType = prefs.getString(K_PAY_PERIOD, d.payPeriodType) ?: d.payPeriodType,
            workplaces = workplaces,
            defaultTaxRate = getDouble(K_TAX_RATE, d.defaultTaxRate),
            payrollUpdatedAt = runCatching { prefs.getLong(K_PAYROLL_UPDATED, 0L) }.getOrDefault(0L),
            onboardingComplete = runCatching { prefs.getBoolean(K_ONBOARDING, false) }.getOrDefault(false),
            autoCloudSyncEnabled = runCatching { prefs.getBoolean(K_AUTO_SYNC, true) }.getOrDefault(true),
            lastCloudSyncTimestamp = runCatching { prefs.getLong(K_LAST_SYNC, 0L) }.getOrDefault(0L),
            biometricLockEnabled = runCatching { prefs.getBoolean(K_BIOMETRIC, false) }.getOrDefault(false),
            requireBiometricOnResume = runCatching { prefs.getBoolean(K_LOCK_ON_RESUME, true) }.getOrDefault(true),
            securityPinHash = pinHash
        )
    }

    /** Older builds stored either a Float or a comma separated string; read both. */
    private fun getDouble(key: String, default: Double): Double {
        val raw = prefs.all[key] ?: return default
        return when (raw) {
            is Float -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: default
            is Long -> java.lang.Double.longBitsToDouble(raw)
            is Int -> raw.toDouble()
            else -> default
        }
    }

    private fun readWorkplaces(): List<String> {
        val json = prefs.getString(K_WORKPLACES_JSON, null)
        if (json != null) {
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() }.distinct()
            }.getOrDefault(emptyList())
        }
        val legacy = prefs.getString(K_LEGACY_WORKPLACES, null) ?: return emptyList()
        return legacy.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        synchronized(this) {
            val old = _settings.value
            var new = transform(old)
            // Any change to a synced payroll field bumps its timestamp, unless the caller set one.
            if (!new.payrollEquals(old) && new.payrollUpdatedAt == old.payrollUpdatedAt) {
                new = new.copy(payrollUpdatedAt = System.currentTimeMillis())
            }
            persist(new)
            _settings.value = new
        }
    }

    private fun persist(s: UserSettings) {
        prefs.edit {
            putString(K_WAGE_TYPE, s.defaultWageType.name)
            // Stored as Long bits so Doubles keep full precision (Floats lost cents).
            putLong(K_WAGE_RATE, java.lang.Double.doubleToRawLongBits(s.defaultWageRate))
            putLong(K_OT_THRESHOLD, java.lang.Double.doubleToRawLongBits(s.defaultOvertimeThreshold))
            putLong(K_OT_MULTIPLIER, java.lang.Double.doubleToRawLongBits(s.defaultOvertimeMultiplier))
            putString(K_CURRENCY, s.currencySymbol)
            putLong(K_MONTHLY_TARGET, java.lang.Double.doubleToRawLongBits(s.monthlyTargetEarnings))
            putString(K_PAY_PERIOD, s.payPeriodType)
            putString(K_WORKPLACES_JSON, JSONArray(s.workplaces).toString())
            remove(K_LEGACY_WORKPLACES)
            putLong(K_TAX_RATE, java.lang.Double.doubleToRawLongBits(s.defaultTaxRate))
            putLong(K_PAYROLL_UPDATED, s.payrollUpdatedAt)
            putBoolean(K_ONBOARDING, s.onboardingComplete)
            putBoolean(K_AUTO_SYNC, s.autoCloudSyncEnabled)
            putLong(K_LAST_SYNC, s.lastCloudSyncTimestamp)
            putBoolean(K_BIOMETRIC, s.biometricLockEnabled)
            putBoolean(K_LOCK_ON_RESUME, s.requireBiometricOnResume)
            putString(K_PIN_HASH, s.securityPinHash)
            // Remove leftovers of the old fake-login implementation.
            remove("is_logged_in"); remove("user_email"); remove("user_name"); remove("auth_provider")
            remove("cloud_backup_count")
        }
    }

    companion object {
        private const val K_WAGE_TYPE = "default_wage_type"
        private const val K_WAGE_RATE = "default_wage_rate"
        private const val K_OT_THRESHOLD = "default_overtime_threshold"
        private const val K_OT_MULTIPLIER = "default_overtime_multiplier"
        private const val K_CURRENCY = "currency_symbol"
        private const val K_MONTHLY_TARGET = "monthly_target"
        private const val K_PAY_PERIOD = "pay_period_type"
        private const val K_WORKPLACES_JSON = "workplaces_json"
        private const val K_LEGACY_WORKPLACES = "workplaces_list"
        private const val K_TAX_RATE = "default_tax_rate"
        private const val K_PAYROLL_UPDATED = "payroll_updated_at"
        private const val K_ONBOARDING = "onboarding_complete"
        private const val K_AUTO_SYNC = "auto_cloud_sync"
        private const val K_LAST_SYNC = "last_cloud_sync"
        private const val K_BIOMETRIC = "biometric_lock_enabled"
        private const val K_LOCK_ON_RESUME = "require_biometric_on_resume"
        private const val K_PIN_HASH = "security_pin_hash"
        private const val K_LEGACY_PIN = "security_pin"

        fun hashPin(pin: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(("earninglogs:" + pin).toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
