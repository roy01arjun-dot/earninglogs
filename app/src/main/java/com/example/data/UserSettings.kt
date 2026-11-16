package com.example.data

data class UserSettings(
    // --- Payroll defaults (synced to the cloud) ---
    val defaultWageType: WageType = WageType.HOURLY,
    val defaultWageRate: Double = 10030.0,
    val defaultOvertimeThreshold: Double = 8.0,
    val defaultOvertimeMultiplier: Double = 1.5,
    val currencySymbol: String = "₩",
    val monthlyTargetEarnings: Double = 2000000.0,
    val payPeriodType: String = "MONTHLY", // "WEEKLY", "BIWEEKLY" or "MONTHLY"
    val workplaces: List<String> = listOf("Main Workplace"),
    val defaultTaxRate: Double = 3.3,
    /** Last time any synced field above changed; used for last-write-wins between devices. */
    val payrollUpdatedAt: Long = 0L,

    // --- Device-only preferences (never synced) ---
    val onboardingComplete: Boolean = false,
    val autoCloudSyncEnabled: Boolean = true,
    val lastCloudSyncTimestamp: Long = 0L,
    val biometricLockEnabled: Boolean = false,
    val requireBiometricOnResume: Boolean = true,
    /** SHA-256 hash of the optional backup PIN. Empty = no PIN. */
    val securityPinHash: String = ""
) {
    val hasPin: Boolean get() = securityPinHash.isNotBlank()

    /** Copy of only the payroll fields from [other], keeping this device's local prefs. */
    fun withPayrollFrom(other: UserSettings): UserSettings = copy(
        defaultWageType = other.defaultWageType,
        defaultWageRate = other.defaultWageRate,
        defaultOvertimeThreshold = other.defaultOvertimeThreshold,
        defaultOvertimeMultiplier = other.defaultOvertimeMultiplier,
        currencySymbol = other.currencySymbol,
        monthlyTargetEarnings = other.monthlyTargetEarnings,
        payPeriodType = other.payPeriodType,
        workplaces = other.workplaces.ifEmpty { workplaces },
        defaultTaxRate = other.defaultTaxRate,
        payrollUpdatedAt = other.payrollUpdatedAt
    )

    fun payrollEquals(other: UserSettings): Boolean =
        defaultWageType == other.defaultWageType &&
            defaultWageRate == other.defaultWageRate &&
            defaultOvertimeThreshold == other.defaultOvertimeThreshold &&
            defaultOvertimeMultiplier == other.defaultOvertimeMultiplier &&
            currencySymbol == other.currencySymbol &&
            monthlyTargetEarnings == other.monthlyTargetEarnings &&
            payPeriodType == other.payPeriodType &&
            workplaces == other.workplaces &&
            defaultTaxRate == other.defaultTaxRate
}
