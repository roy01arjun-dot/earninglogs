package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.data.WageType
import com.example.data.WorkLogEntity
import com.example.ui.MonthlyPayrollSummary
import com.example.ui.WeeklyPayrollSummary
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.ShiftsScreen
import com.example.ui.theme.WorkLogTheme
import com.example.util.DateUtils
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Renders real screens to PNG without a device (build/screenshots). Run with:
 *   ./gradlew testDebugUnitTest --tests "*ScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1500dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private val settings = UserSettings(workplaces = listOf("Cafe Bloom", "Night Mart"), monthlyTargetEarnings = 2_000_000.0)
    private val today: LocalDate = LocalDate.now()

    private fun shift(daysAgo: Long, hours: Double, wp: String, paid: Boolean, note: String = "") = WorkLogEntity(
        id = daysAgo.toInt() + 1, date = DateUtils.formatDate(today.minusDays(daysAgo)), hoursWorked = hours,
        wageType = WageType.HOURLY, wageRate = 10_030.0, breakMinutes = 30, isPaid = paid, workplace = wp, note = note
    )

    private val logs = listOf(
        shift(0, 9.5, "Cafe Bloom", false, "Closing shift"),
        shift(1, 8.0, "Night Mart", false),
        shift(2, 10.0, "Cafe Bloom", true),
        shift(3, 6.0, "Night Mart", true, "Covered for Mina"),
        shift(4, 8.5, "Cafe Bloom", true)
    )

    private fun monthly() = MonthlyPayrollSummary(
        yearMonth = YearMonth.now(), logs = logs,
        totalHours = logs.sumOf { it.netHoursWorked }, regularHours = logs.sumOf { it.regularHours },
        overtimeHours = logs.sumOf { it.overtimeHours }, regularPay = logs.sumOf { it.regularPay },
        overtimePay = logs.sumOf { it.overtimePay }, totalEarnings = logs.sumOf { it.totalPay } + 900_000,
        paidEarnings = logs.filter { it.isPaid }.sumOf { it.totalPay } + 900_000,
        unpaidEarnings = logs.filter { !it.isPaid }.sumOf { it.totalPay },
        daysWorked = 5, averageEarningsPerDay = 80_000.0, weeklyBreakdown = emptyList()
    )

    @Composable
    private fun Frame(dark: Boolean, content: @Composable () -> Unit) {
        WorkLogTheme(darkTheme = dark) {
            androidx.compose.runtime.CompositionLocalProvider(com.example.ui.theme.LocalWorkplaces provides settings.workplaces) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
    }

    @Composable
    private fun Home() = HomeScreen(
        userName = "Arjun", monthly = monthly(), settings = settings,
        contentPadding = PaddingValues(top = 16.dp),
        onNavigateMonth = {}, onCurrentMonth = {}, onMarkMonthPaid = {}, onAddShift = {}, onEditShift = {},
        onDeleteShift = {}, onTogglePaid = {}, onSeeAllShifts = {}
    )

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent { Frame(dark, content) }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun homeLight() = shoot("home_light") { Home() }

    @Test fun homeDark() = shoot("home_dark", dark = true) { Home() }

    @Test fun reportsLight() = shoot("reports_light") {
        val start = DateUtils.getStartOfWeek(today)
        val weekLogs = (0L..4L).map { i ->
            WorkLogEntity(
                id = i.toInt() + 1, date = DateUtils.formatDate(start.plusDays(i)), hoursWorked = 7.0 + i,
                wageRate = 10_030.0, isPaid = i < 2, workplace = if (i % 2 == 0L) "Cafe Bloom" else "Night Mart"
            )
        }
        ReportsScreen(
            weekly = WeeklyPayrollSummary.from(start, DateUtils.getEndOfWeek(start), weekLogs), settings = settings,
            contentPadding = PaddingValues(top = 16.dp), onNavigateWeek = {}, onCurrentWeek = {}, onExport = {}, onAddShift = {}
        )
    }

    @Test fun shiftsLight() = shoot("shifts_light") {
        ShiftsScreen(
            logs = logs, settings = settings, contentPadding = PaddingValues(top = 16.dp),
            onAddShift = {}, onEditShift = {}, onDeleteShift = {}, onTogglePaid = {}
        )
    }
}
