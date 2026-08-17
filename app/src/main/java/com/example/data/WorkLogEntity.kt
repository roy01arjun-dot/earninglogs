package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class WageType {
    HOURLY,
    DAILY
}

/**
 * A single logged shift.
 *
 * Sync fields:
 *  - [uuid] is the stable identity of a shift across devices, and is the Firestore document id.
 *    [id] is a local autoincrement key only; two devices independently creating shifts would both
 *    get id 1, which is exactly why it cannot be the sync key.
 *  - [updatedAt] drives last-write-wins conflict resolution.
 *  - [deletedAt] is a tombstone. Rows are never hard-deleted while a delete may still need to
 *    propagate, otherwise a delete on one device is invisible to the others.
 *  - [pendingSync] marks a local change the backend has not acknowledged yet.
 */
@Entity(
    tableName = "work_logs",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["pendingSync"]),
        Index(value = ["date"])
    ]
)
data class WorkLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val date: String, // ISO format "YYYY-MM-DD"
    val hoursWorked: Double,
    val wageType: WageType = WageType.HOURLY,
    val wageRate: Double, // Rate per hour OR rate per day
    val overtimeThresholdHours: Double = 8.0, // Daily hours before overtime kicks in
    val overtimeMultiplier: Double = 1.5, // e.g. 1.5x rate
    val note: String = "",
    val breakMinutes: Int = 0,
    val isPaid: Boolean = false,
    val workplace: String = "Main Workplace",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val pendingSync: Boolean = true
) {
    // Effective actual work hours (subtracting unpaid break if any)
    val netHoursWorked: Double
        get() = max(0.0, hoursWorked - (breakMinutes / 60.0))

    // Regular hours within threshold
    val regularHours: Double
        get() = min(netHoursWorked, overtimeThresholdHours)

    // Overtime hours beyond threshold
    val overtimeHours: Double
        get() = max(0.0, netHoursWorked - overtimeThresholdHours)

    // Effective hourly rate for overtime calculations when using daily wage
    val effectiveHourlyRate: Double
        get() = when (wageType) {
            WageType.HOURLY -> wageRate
            WageType.DAILY -> if (overtimeThresholdHours > 0) wageRate / overtimeThresholdHours else 0.0
        }

    // Overtime pay rate
    val overtimeHourlyRate: Double
        get() = effectiveHourlyRate * overtimeMultiplier

    // Base regular pay
    val regularPay: Double
        get() = when (wageType) {
            WageType.HOURLY -> regularHours * wageRate
            WageType.DAILY -> wageRate // Daily wage pays full day base rate
        }

    // Overtime pay
    val overtimePay: Double
        get() = overtimeHours * overtimeHourlyRate

    // Total gross pay for the log
    val totalPay: Double
        get() = regularPay + overtimePay

    /** Stamps a local edit so the sync engine picks it up on the next run. */
    fun markDirty(now: Long = System.currentTimeMillis()): WorkLogEntity =
        copy(updatedAt = now, pendingSync = true)
}
