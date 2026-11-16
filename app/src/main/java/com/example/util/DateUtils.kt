package com.example.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object DateUtils {
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val SHORT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun todayString(): String = LocalDate.now().format(DATE_FORMATTER)

    fun parseDate(dateStr: String): LocalDate {
        return try {
            LocalDate.parse(dateStr, DATE_FORMATTER)
        } catch (e: Exception) {
            LocalDate.now()
        }
    }

    fun isValidDate(dateStr: String): Boolean =
        runCatching { LocalDate.parse(dateStr, DATE_FORMATTER) }.isSuccess

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)

    fun formatDisplayDate(dateStr: String): String {
        val date = parseDate(dateStr)
        return date.format(DISPLAY_DATE_FORMATTER)
    }

    fun formatShortDate(dateStr: String): String {
        val date = parseDate(dateStr)
        return date.format(SHORT_DATE_FORMATTER)
    }

    fun getStartOfWeek(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun getEndOfWeek(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    }

    fun getDaysInWeek(startOfWeek: LocalDate): List<LocalDate> {
        return (0..6).map { startOfWeek.plusDays(it.toLong()) }
    }

    fun getStartOfMonth(yearMonth: YearMonth): LocalDate {
        return yearMonth.atDay(1)
    }

    fun getEndOfMonth(yearMonth: YearMonth): LocalDate {
        return yearMonth.atEndOfMonth()
    }

    fun formatMonthYear(yearMonth: YearMonth): String {
        return yearMonth.format(MONTH_YEAR_FORMATTER)
    }

    fun formatMonthYear(dateStr: String): String {
        return parseDate(dateStr).format(MONTH_YEAR_FORMATTER)
    }

    fun getDayOfWeekShort(date: LocalDate): String {
        return date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
