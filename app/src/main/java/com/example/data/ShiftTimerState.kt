package com.example.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

/**
 * State representing an active, paused, or completed work shift timer.
 */
data class ShiftTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val startTimeMillis: Long = 0L,
    val accumulatedDurationMillis: Long = 0L,
    val workplace: String = "Main Workplace",
    val startFormattedTime: String = ""
) {
    /**
     * Calculates the total elapsed milliseconds from active and accumulated intervals.
     */
    fun currentElapsedMillis(now: Long = System.currentTimeMillis()): Long {
        return if (isRunning && !isPaused && startTimeMillis > 0L) {
            accumulatedDurationMillis + max(0L, now - startTimeMillis)
        } else {
            accumulatedDurationMillis
        }
    }

    /**
     * Calculates total elapsed hours as a Double rounded to two decimal places.
     */
    fun currentElapsedHours(now: Long = System.currentTimeMillis()): Double {
        val millis = currentElapsedMillis(now)
        val hours = millis / 3_600_000.0
        return max(0.0, round(hours * 100.0) / 100.0)
    }

    /**
     * Formats duration as HH:MM:SS (e.g. "02:15:30")
     */
    fun formattedDuration(now: Long = System.currentTimeMillis()): String {
        val totalSeconds = max(0L, currentElapsedMillis(now) / 1000)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        fun formatTimestamp(epochMillis: Long): String {
            return try {
                Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter)
            } catch (e: Exception) {
                ""
            }
        }

        fun formatHoursForInput(hours: Double): String {
            return if (hours <= 0.0) {
                ""
            } else if (hours == hours.toLong().toDouble()) {
                hours.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", hours).trimEnd('0').trimEnd('.')
            }
        }
    }
}
