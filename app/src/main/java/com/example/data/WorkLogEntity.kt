package com.example.data

import androidx.room.ColumnInfo
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
 * Sync notes (fixed in v12):
 *  - [uuid] is the stable, globally unique id used as the Firestore document id.
 *    The old code used the local auto-increment [id] ("entry_5"), so two phones
 *    both creating shift #5 overwrote each other in the cloud.
 *  - [updatedAt] drives last-write-wins conflict resolution between devices.
 *  - [isDeleted] is a tombstone so a delete on one device propagates to the
 *    others instead of the shift "coming back" on the next sync.
 *  - [pendingSync] marks rows that still need to be pushed to Firestore.
 */
@Entity(
    tableName = "work_logs",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class WorkLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String, // ISO format "YYYY-MM-DD"
    val hoursWorked: Double,
    val wageType: WageType = WageType.HOURLY,
    val wageRate: Double, // Rate per hour OR rate per day
    val overtimeThresholdHours: Double = 8.0,
    val overtimeMultiplier: Double = 1.5,
    val note: String = "",
    val breakMinutes: Int = 0,
    val isPaid: Boolean = false,
    val workplace: String = "Main Workplace",
    @ColumnInfo(defaultValue = "")
    val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val pendingSync: Boolean = true
) {
    /** Actual work hours after the unpaid break. */
    val netHoursWorked: Double
        get() = max(0.0, hoursWorked - (breakMinutes / 60.0))

    val regularHours: Double
        get() = min(netHoursWorked, overtimeThresholdHours)

    val overtimeHours: Double
        get() = max(0.0, netHoursWorked - overtimeThresholdHours)

    /** Hourly-equivalent rate, used for overtime when paid a daily wage. */
    val effectiveHourlyRate: Double
        get() = when (wageType) {
            WageType.HOURLY -> wageRate
            WageType.DAILY -> if (overtimeThresholdHours > 0) wageRate / overtimeThresholdHours else 0.0
        }

    val overtimeHourlyRate: Double
        get() = effectiveHourlyRate * overtimeMultiplier

    val regularPay: Double
        get() = when (wageType) {
            WageType.HOURLY -> regularHours * wageRate
            WageType.DAILY -> wageRate
        }

    val overtimePay: Double
        get() = overtimeHours * overtimeHourlyRate

    val totalPay: Double
        get() = regularPay + overtimePay
}
