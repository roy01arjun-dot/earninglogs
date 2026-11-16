package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.ui.WeeklyPayrollSummary
import com.example.ui.components.AppCard
import com.example.ui.components.ButtonMinHeight
import com.example.ui.components.ControlShape
import com.example.ui.components.EmptyState
import com.example.ui.components.KeyValueRow
import com.example.ui.components.PeriodSwitcher
import com.example.ui.components.SectionHeader
import com.example.ui.components.Spacing
import com.example.ui.components.asHours
import com.example.ui.components.asPlain
import com.example.ui.components.asSpokenHours
import com.example.ui.theme.AppTheme
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReportsScreen(
    weekly: WeeklyPayrollSummary,
    settings: UserSettings,
    contentPadding: PaddingValues,
    onNavigateWeek: (Long) -> Unit,
    onCurrentWeek: () -> Unit,
    onExport: () -> Unit,
    onAddShift: () -> Unit
) {
    val currency = settings.currencySymbol
    val status = AppTheme.status
    val isThisWeek = weekly.startOfWeek == DateUtils.getStartOfWeek(LocalDate.now())
    val tax = weekly.totalEarnings * settings.defaultTaxRate / 100.0
    val byWorkplace = remember(weekly.logs) {
        weekly.logs.groupBy { it.workplace }
            .map { (wp, logs) -> Triple(wp, logs.sumOf { it.totalPay }, logs.sumOf { it.netHoursWorked }) }
            .sortedByDescending { it.second }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.screen, end = Spacing.screen,
            top = contentPadding.calculateTopPadding() + Spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            PeriodSwitcher(
                label = if (isThisWeek) "This week"
                else "${DateUtils.formatShortDate(weekly.startOfWeek.toString())} – ${DateUtils.formatShortDate(weekly.endOfWeek.toString())}",
                unit = "week",
                onPrevious = { onNavigateWeek(-1) },
                onNext = { onNavigateWeek(1) },
                showReset = !isThisWeek,
                resetLabel = "Back to this week",
                onReset = onCurrentWeek
            )
        }

        if (weekly.logs.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        icon = Icons.Outlined.BarChart,
                        title = "Nothing logged this week",
                        message = "Your weekly hours, pay and a shareable pay statement will appear here.",
                        actionLabel = "Log a shift",
                        onAction = onAddShift
                    )
                }
            }
            return@LazyColumn
        }

        item {
            AppCard {
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text("Week total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyUtils.formatCurrency(weekly.totalEarnings, currency), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "${weekly.totalHours.asHours()} over ${weekly.daysWorked} day${if (weekly.daysWorked == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                WeekBarChart(weekly)
            }
        }

        item {
            AppCard {
                SectionHeader("Breakdown", modifier = Modifier.padding(top = 0.dp))
                KeyValueRow("Regular (${weekly.regularHours.asHours()})", CurrencyUtils.formatCurrency(weekly.regularPay, currency))
                KeyValueRow("Overtime (${weekly.overtimeHours.asHours()})", CurrencyUtils.formatCurrency(weekly.overtimePay, currency))
                if (settings.defaultTaxRate > 0) {
                    KeyValueRow("Tax (${settings.defaultTaxRate.asPlain()}%)", "-${CurrencyUtils.formatCurrency(tax, currency)}")
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                KeyValueRow("Take-home", CurrencyUtils.formatCurrency(weekly.totalEarnings - tax, currency), bold = true)
                KeyValueRow("Paid", CurrencyUtils.formatCurrency(weekly.paidEarnings, currency), valueColor = status.success)
                KeyValueRow("Not paid yet", CurrencyUtils.formatCurrency(weekly.unpaidEarnings, currency), valueColor = status.warning)
                Spacer(Modifier.height(Spacing.md))
                Button(
                    onClick = onExport,
                    shape = ControlShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) {
                    Icon(Icons.Default.IosShare, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Share pay statement")
                }
            }
        }

        if (byWorkplace.size > 1) {
            item { SectionHeader("By workplace") }
            item {
                AppCard {
                    byWorkplace.forEachIndexed { i, (wp, pay, hours) ->
                        if (i > 0) Spacer(Modifier.height(Spacing.md))
                        val share = if (weekly.totalEarnings > 0) (pay / weekly.totalEarnings).toFloat() else 0f
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(wp, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(CurrencyUtils.formatCurrency(pay, currency), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR))
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { share },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                                drawStopIndicator = {}
                            )
                            Text(
                                "${hours.asHours()} · ${(share * 100).toInt()}% of the week",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Seven bars (Mon–Sun) of hours per day; overtime stacked on top. Fully described for TalkBack. */
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

    val description = days.mapIndexed { i, d ->
        val (reg, ot) = byDay[i]
        val name = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        if (reg + ot <= 0) "$name: no work" else "$name: ${(reg + ot).asSpokenHours()}" + if (ot > 0) ", ${ot.asSpokenHours()} overtime" else ""
    }.joinToString("; ")

    Column(Modifier.clearAndSetSemantics { contentDescription = "Hours per day. $description" }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / 7f
            val barW = slot * 0.5f
            val radius = CornerRadius(barW / 2.4f)
            byDay.forEachIndexed { i, (reg, ot) ->
                val x = i * slot + (slot - barW) / 2f
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
                val regH = (reg / maxHours * size.height).toFloat()
                val otH = (ot / maxHours * size.height).toFloat()
                if (regH + otH > 0f) drawRoundRect(primary, Offset(x, size.height - regH - otH), Size(barW, regH + otH), radius)
                if (otH > 0f) drawRoundRect(overtime, Offset(x, size.height - regH - otH), Size(barW, otH), radius)
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
                        if (reg + ot > 0) (reg + ot).asPlain(1) else "–",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (byDay.any { it.second > 0 }) {
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                LegendDot(primary, "Regular")
                LegendDot(overtime, "Overtime")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
