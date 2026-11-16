package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.ui.WeeklyPayrollSummary
import com.example.ui.components.AppCard
import com.example.ui.components.ControlShape
import com.example.ui.components.EmptyState
import com.example.ui.components.KeyValueRow
import com.example.ui.components.PeriodSwitcher
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShiftRow
import com.example.ui.components.Spacing
import com.example.ui.components.asHours
import com.example.ui.components.asPlain
import com.example.ui.theme.AppTheme
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.LocalDate

@Composable
fun ReportsScreen(
    weekly: WeeklyPayrollSummary,
    settings: UserSettings,
    contentPadding: PaddingValues,
    onNavigateWeek: (Long) -> Unit,
    onCurrentWeek: () -> Unit,
    onExport: () -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (WorkLogEntity) -> Unit,
    onDeleteShift: (Int) -> Unit,
    onTogglePaid: (WorkLogEntity) -> Unit
) {
    val currency = settings.currencySymbol
    val status = AppTheme.status
    val isThisWeek = weekly.startOfWeek == DateUtils.getStartOfWeek(LocalDate.now())
    val tax = weekly.totalEarnings * settings.defaultTaxRate / 100.0

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.screen, end = Spacing.screen,
            top = contentPadding.calculateTopPadding() + Spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            PeriodSwitcher(
                label = "${DateUtils.formatShortDate(weekly.startOfWeek.toString())} – ${DateUtils.formatShortDate(weekly.endOfWeek.toString())}",
                sublabel = if (isThisWeek) "This week" else weekly.startOfWeek.year.toString(),
                onPrevious = { onNavigateWeek(-1) },
                onNext = { onNavigateWeek(1) },
                showReset = !isThisWeek,
                resetLabel = "Back to this week",
                onReset = onCurrentWeek
            )
        }

        item {
            AppCard {
                Text("Week total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyUtils.formatCurrency(weekly.totalEarnings, currency),
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    "${weekly.totalHours.asHours()} across ${weekly.daysWorked} day${if (weekly.daysWorked == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.lg))
                WeekBarChart(weekly)
            }
        }

        item {
            AppCard {
                Text("Breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                KeyValueRow("Regular · ${weekly.regularHours.asHours()}", CurrencyUtils.formatCurrency(weekly.regularPay, currency))
                KeyValueRow("Overtime · ${weekly.overtimeHours.asHours()}", CurrencyUtils.formatCurrency(weekly.overtimePay, currency), valueColor = status.overtime)
                KeyValueRow("Est. tax (${settings.defaultTaxRate.asPlain()}%)", "−${CurrencyUtils.formatCurrency(tax, currency)}")
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                KeyValueRow("Net pay", CurrencyUtils.formatCurrency(weekly.totalEarnings - tax, currency), bold = true)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    LegendDot(status.success, "Paid ${CurrencyUtils.formatCurrency(weekly.paidEarnings, currency)}")
                    LegendDot(status.warning, "Unpaid ${CurrencyUtils.formatCurrency(weekly.unpaidEarnings, currency)}")
                }
                Spacer(Modifier.height(Spacing.lg))
                Button(
                    onClick = onExport,
                    enabled = weekly.logs.isNotEmpty(),
                    shape = ControlShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.IosShare, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Share pay statement")
                }
            }
        }

        item { SectionHeader("Shifts this week") }
        if (weekly.logs.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        icon = Icons.Outlined.BarChart,
                        title = "Nothing logged",
                        message = "Shifts you add for this week will show up here.",
                        actionLabel = "Log a shift",
                        onAction = onAddShift
                    )
                }
            }
        } else {
            item {
                AppCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    weekly.logs.sortedByDescending { it.date }.forEachIndexed { i, log ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ShiftRow(
                            log = log, currency = currency,
                            onClick = { onEditShift(log) },
                            onTogglePaid = { onTogglePaid(log) },
                            onDelete = { onDeleteShift(log.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Seven bars (Mon–Sun) showing hours per day; overtime stacked on top. */
@Composable
private fun WeekBarChart(weekly: WeeklyPayrollSummary) {
    val days = DateUtils.getDaysInWeek(weekly.startOfWeek)
    val byDay = days.map { d ->
        val logs = weekly.logs.filter { it.date == DateUtils.formatDate(d) }
        logs.sumOf { it.regularHours } to logs.sumOf { it.overtimeHours }
    }
    val maxHours = (byDay.maxOfOrNull { it.first + it.second } ?: 0.0).coerceAtLeast(8.0)
    val primary = MaterialTheme.colorScheme.primary
    val overtime = AppTheme.status.overtime
    val track = MaterialTheme.colorScheme.surfaceVariant
    val today = LocalDate.now()

    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / 7f
            val barW = slot * 0.46f
            val radius = CornerRadius(barW / 2.4f)
            byDay.forEachIndexed { i, (reg, ot) ->
                val x = i * slot + (slot - barW) / 2f
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
                val regH = (reg / maxHours * size.height).toFloat()
                val otH = (ot / maxHours * size.height).toFloat()
                if (regH + otH > 0f) {
                    drawRoundRect(primary, Offset(x, size.height - regH - otH), Size(barW, regH + otH), radius)
                }
                if (otH > 0f) {
                    drawRoundRect(overtime, Offset(x, size.height - regH - otH), Size(barW, otH), radius)
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                val (reg, ot) = byDay[i]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DateUtils.getDayOfWeekShort(d).take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (reg + ot > 0) (reg + ot).asHours() else "–",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
