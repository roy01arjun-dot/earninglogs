package com.example

import com.example.data.ShiftTimerState
import com.example.data.WorkLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftTimerUnitTest {

    @Test
    fun testInitialTimerState() {
        val state = ShiftTimerState()
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(0L, state.currentElapsedMillis())
        assertEquals(0.0, state.currentElapsedHours(), 0.001)
        assertEquals("00:00:00", state.formattedDuration())
    }

    @Test
    fun testStartTimerCalculatesElapsedHours() {
        val startTime = 1_000_000L
        val runningState = ShiftTimerState(
            startTimeMillis = startTime,
            isRunning = true,
            workplace = "Warehouse"
        )
        assertTrue(runningState.isRunning)
        assertFalse(runningState.isPaused)
        assertEquals("Warehouse", runningState.workplace)

        // 2 hours and 30 minutes later = 2.5 hours = 9_000_000 ms
        val currentTime = startTime + (2 * 3600 + 30 * 60) * 1000L
        val elapsedMillis = runningState.currentElapsedMillis(currentTime)
        assertEquals(9_000_000L, elapsedMillis)

        val elapsedHours = runningState.currentElapsedHours(currentTime)
        assertEquals(2.5, elapsedHours, 0.01)

        val formattedDuration = runningState.formattedDuration(currentTime)
        assertEquals("02:30:00", formattedDuration)

        val formattedForInput = ShiftTimerState.formatHoursForInput(elapsedHours)
        assertEquals("2.5", formattedForInput)
    }

    @Test
    fun testTimerPauseAndResume() {
        val startTime = 1000L
        val pauseTime = 1000L + 3600_000L // 1 hour running
        val pausedState = ShiftTimerState(
            startTimeMillis = startTime,
            accumulatedDurationMillis = 3600_000L,
            isRunning = true,
            isPaused = true
        )

        // Even if 5 hours have passed on the clock, elapsed time remains 1 hour while paused
        val muchLaterTime = pauseTime + 5 * 3600_000L
        assertEquals(3600_000L, pausedState.currentElapsedMillis(muchLaterTime))
        assertEquals(1.0, pausedState.currentElapsedHours(muchLaterTime), 0.001)
        assertEquals("01:00:00", pausedState.formattedDuration(muchLaterTime))
    }

    @Test
    fun testOvertimeDefaultsToZero() {
        // When user logs a shift with normal hours and overtime defaults to 0:
        val normalHours = 8.0
        val overtimeHours = 0.0
        val totalShiftHours = normalHours + overtimeHours
        val wageRate = 25.0

        val entity = WorkLogEntity(
            id = 1,
            date = "2026-09-03",
            hoursWorked = totalShiftHours,
            breakMinutes = 0,
            wageRate = wageRate,
            overtimeThresholdHours = normalHours, // threshold set to normal hours
            overtimeMultiplier = 1.5
        )

        assertEquals(8.0, entity.regularHours, 0.001)
        assertEquals(0.0, entity.overtimeHours, 0.001)
        // 8 hours * $25 = $200.00
        assertEquals(200.0, entity.totalPay, 0.001)
    }

    @Test
    fun testNormalHoursWithOvertimeCalculatesCorrectly() {
        // User works 8 normal hours and 2 overtime hours
        val normalHours = 8.0
        val overtimeHours = 2.0
        val totalShiftHours = normalHours + overtimeHours
        val wageRate = 20.0
        val otMultiplier = 1.5

        val entity = WorkLogEntity(
            id = 2,
            date = "2026-09-03",
            hoursWorked = totalShiftHours,
            breakMinutes = 0,
            wageRate = wageRate,
            overtimeThresholdHours = normalHours,
            overtimeMultiplier = otMultiplier
        )

        assertEquals(8.0, entity.regularHours, 0.001)
        assertEquals(2.0, entity.overtimeHours, 0.001)
        // Normal pay = 8 * 20 = 160. Overtime pay = 2 * 20 * 1.5 = 60. Total = 220
        assertEquals(160.0, entity.regularPay, 0.001)
        assertEquals(60.0, entity.overtimePay, 0.001)
        assertEquals(220.0, entity.totalPay, 0.001)
    }
}
