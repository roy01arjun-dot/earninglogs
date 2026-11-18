package com.example

import com.example.data.DailyReminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DailyReminderTest {
    private val day = LocalDate.of(2026, 11, 18)

    @Test fun morningSchedulesTonight() =
        assertEquals(day, DailyReminder.nextReminderDate(LocalDateTime.of(2026, 11, 18, 9, 0), lastShown = null))

    @Test fun insideWindowAndNotShownYetStillRemindsToday() =
        assertEquals(day, DailyReminder.nextReminderDate(LocalDateTime.of(2026, 11, 18, 23, 20), lastShown = day.minusDays(1)))

    @Test fun alreadyShownTodayMovesToTomorrow() =
        assertEquals(day.plusDays(1), DailyReminder.nextReminderDate(LocalDateTime.of(2026, 11, 18, 23, 10), lastShown = day))

    @Test fun tooLateTonightMovesToTomorrow() =
        assertEquals(day.plusDays(1), DailyReminder.nextReminderDate(LocalDateTime.of(2026, 11, 18, 23, 57), lastShown = null))

    @Test fun afterMidnightSchedulesThatNight() =
        assertEquals(day, DailyReminder.nextReminderDate(LocalDateTime.of(2026, 11, 18, 0, 30), lastShown = day.minusDays(1)))
}
