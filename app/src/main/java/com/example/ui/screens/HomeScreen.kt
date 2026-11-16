package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ShiftTimerState
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.ui.ALL_WORKPLACES
import com.example.ui.MonthlyPayrollSummary
import com.example.ui.WorkplaceSummary
import com.example.ui.components.AppCard
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ControlShape
import com.example.ui.components.EmptyState
import com.example.ui.components.KeyValueRow
import com.example.ui.components.PeriodSwitcher
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShiftRow
import com.example.ui.components.Spacing
import com.example.ui.components.StatTile
import com.example.ui.components.TextInputDialog
import com.example.ui.components.asHours
import com.example.ui.components.asPlain
import com.example.ui.theme.AppTheme
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.YearMonth

@Composable
fun HomeScreen(
    monthly: MonthlyPayrollSummary,
    workplaceBreakdown: List<WorkplaceSummary>,
    selectedWorkplace: String,
    settings: UserSettings,
    timer: ShiftTimerState,
    contentPadding: PaddingValues,
    onSelectWorkplace: (String) -> Unit,
    onNavigateMonth: (Long) -> Unit,
    onCurrentMonth: () -> Unit,
    onUpdateTaxRate: (Double) -> Unit,
    onMarkMonthPaid: () -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (WorkLogEntity) -> Unit,
    onDeleteShift: (Int) -> Unit,
    onTogglePaid: (WorkLogEntity) -> Unit,
    onSeeAllShifts: () -> Unit,
    onStartTimer: (String) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onStopTimer: () -> Unit,
    onDiscardTimer: () -> Unit
) {
    val currency = settings.currencySymbol
    val isCurrentMonth = monthly.yearMonth == YearMonth.now()
    val recent = remember(monthly.logs) { monthly.logs.sortedWith(compareByDescending<WorkLogEntity> { it.date }.thenByDescending { it.id }).take(5) }

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
                label = DateUtils.formatMonthYear(monthly.yearMonth),
                sublabel = "${monthly.daysWorked} days worked",
                onPrevious = { onNavigateMonth(-1) },
                onNext = { onNavigateMonth(1) },
                showReset = !isCurrentMonth,
                resetLabel = "Back to this month",
                onReset = onCurrentMonth
            )
        }

        if (settings.workplaces.size > 1 || selectedWorkplace != ALL_WORKPLACES) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    item {
                        FilterChip(
                            selected = selectedWorkplace == ALL_WORKPLACES,
                            onClick = { onSelectWorkplace(ALL_WORKPLACES) },
                            label = { Text("All workplaces") }
                        )
                    }
                    items((settings.workplaces + selectedWorkplace).filter { it != ALL_WORKPLACES }.distinct()) { wp ->
                        FilterChip(selected = selectedWorkplace == wp, onClick = { onSelectWorkplace(wp) }, label = { Text(wp) })
                    }
                }
            }
        }

        item { EarningsHero(monthly, settings) }

        item {
            val status = AppTheme.status
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        label = "Hours worked", value = monthly.totalHours.asHours(),
                        icon = Icons.Outlined.AccessTime,
                        tint = MaterialTheme.colorScheme.primary, container = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Shifts", value = "${monthly.logs.size}",
                        icon = Icons.AutoMirrored.Outlined.EventNote,
                        tint = MaterialTheme.colorScheme.secondary, container = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f),
                        caption = if (monthly.daysWorked > 0) "Avg ${CurrencyUtils.formatCurrency(monthly.averageEarningsPerDay, currency)}/day" else null
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        label = "Overtime", value = monthly.overtimeHours.asHours(),
                        icon = Icons.Outlined.Bolt,
                        tint = status.overtime, container = status.overtimeContainer,
                        modifier = Modifier.weight(1f),
                        caption = if (monthly.overtimePay > 0) "+${CurrencyUtils.formatCurrency(monthly.overtimePay, currency)}" else null
                    )
                    StatTile(
                        label = "Awaiting payment", value = CurrencyUtils.formatCurrency(monthly.unpaidEarnings, currency),
                        icon = Icons.Outlined.HourglassEmpty,
                        tint = status.warning, container = status.warningContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            ShiftTimerCard(
                timer = timer,
                workplaces = settings.workplaces,
                onStart = onStartTimer, onPause = onPauseTimer, onResume = onResumeTimer,
                onStop = onStopTimer, onDiscard = onDiscardTimer
            )
        }

        item {
            PayoutCard(
                monthly = monthly, settings = settings,
                onUpdateTaxRate = onUpdateTaxRate, onMarkMonthPaid = onMarkMonthPaid
            )
        }

        val active = workplaceBreakdown.filter { it.totalEarnings > 0 }
        if (selectedWorkplace == ALL_WORKPLACES && active.size > 1) {
            item { SectionHeader("By workplace") }
            item { WorkplaceBreakdownCard(active, currency) }
        }

        item {
            SectionHeader(
                "Recent shifts",
                actionLabel = if (monthly.logs.isNotEmpty()) "See all" else null,
                onAction = onSeeAllShifts
            )
        }
        if (recent.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "No shifts this month",
                        message = "Log your first shift to see your earnings, hours and overtime here.",
                        actionLabel = "Log a shift",
                        onAction = onAddShift
                    )
                }
            }
        } else {
            item {
                AppCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    recent.forEachIndexed { i, log ->
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
private fun EarningsHero(monthly: MonthlyPayrollSummary, settings: UserSettings) {
    val currency = settings.currencySymbol
    val onHero = MaterialTheme.colorScheme.onPrimary
    val target = settings.monthlyTargetEarnings
    val progress = if (target > 0) (monthly.totalEarnings / target).toFloat().coerceIn(0f, 1f) else 0f
    val net = monthly.totalEarnings * (1 - settings.defaultTaxRate / 100.0)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(Spacing.xl)
    ) {
        Column {
            Text("Gross earnings", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.8f))
            Spacer(Modifier.height(Spacing.xs))
            Text(
                CurrencyUtils.formatCurrency(monthly.totalEarnings, currency),
                style = MaterialTheme.typography.displaySmall,
                color = onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "≈ ${CurrencyUtils.formatCurrency(net, currency)} after ${settings.defaultTaxRate.asPlain()}% tax",
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.8f)
            )
            if (target > 0) {
                Spacer(Modifier.height(Spacing.lg))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = onHero,
                    trackColor = onHero.copy(alpha = 0.22f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    Text(
                        "${(progress * 100).toInt()}% of monthly goal",
                        style = MaterialTheme.typography.labelMedium,
                        color = onHero.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        CurrencyUtils.formatCurrency(target, currency),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR),
                        color = onHero.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PayoutCard(
    monthly: MonthlyPayrollSummary,
    settings: UserSettings,
    onUpdateTaxRate: (Double) -> Unit,
    onMarkMonthPaid: () -> Unit
) {
    val status = AppTheme.status
    val currency = settings.currencySymbol
    var editTax by remember { mutableStateOf(false) }
    var confirmPaid by remember { mutableStateOf(false) }
    val tax = monthly.unpaidEarnings * settings.defaultTaxRate / 100.0

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Payout", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { editTax = true }) { Text("Tax ${settings.defaultTaxRate.asPlain()}%") }
        }
        KeyValueRow("Paid", CurrencyUtils.formatCurrency(monthly.paidEarnings, currency), valueColor = status.success)
        KeyValueRow("Awaiting payment", CurrencyUtils.formatCurrency(monthly.unpaidEarnings, currency), valueColor = status.warning)
        KeyValueRow("Est. tax on unpaid", "−${CurrencyUtils.formatCurrency(tax, currency)}")
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
        KeyValueRow("Expected take-home", CurrencyUtils.formatCurrency(monthly.unpaidEarnings - tax, currency), bold = true)
        if (monthly.unpaidEarnings > 0) {
            Spacer(Modifier.height(Spacing.md))
            FilledTonalButton(
                onClick = { confirmPaid = true },
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Mark month as paid")
            }
        }
    }

    if (editTax) {
        TextInputDialog(
            title = "Tax rate",
            label = "Percent withheld (e.g. 3.3)",
            initial = settings.defaultTaxRate.asPlain(),
            keyboardType = KeyboardType.Decimal,
            validate = { v ->
                val d = v.replace(',', '.').toDoubleOrNull()
                if (d == null || d < 0 || d > 100) "Enter a number between 0 and 100" else null
            },
            onConfirm = { onUpdateTaxRate(it.replace(',', '.').toDouble()) },
            onDismiss = { editTax = false }
        )
    }
    if (confirmPaid) {
        ConfirmDialog(
            title = "Mark as paid?",
            message = "All unpaid shifts in ${DateUtils.formatMonthYear(monthly.yearMonth)} " +
                "(${CurrencyUtils.formatCurrency(monthly.unpaidEarnings, currency)}) will be marked as paid.",
            confirmLabel = "Mark paid",
            onConfirm = onMarkMonthPaid,
            onDismiss = { confirmPaid = false }
        )
    }
}

@Composable
private fun WorkplaceBreakdownCard(items: List<WorkplaceSummary>, currency: String) {
    AppCard {
        items.forEachIndexed { i, wp ->
            if (i > 0) Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(wp.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    CurrencyUtils.formatCurrency(wp.totalEarnings, currency),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR)
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { wp.sharePercentage },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${wp.shiftCount} shifts · ${wp.totalHours.asHours()} · ${(wp.sharePercentage * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShiftTimerCard(
    timer: ShiftTimerState,
    workplaces: List<String>,
    onStart: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    val status = AppTheme.status
    var selected by remember(workplaces) { mutableStateOf(workplaces.firstOrNull() ?: "Main Workplace") }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    when {
                        !timer.isRunning -> MaterialTheme.colorScheme.outline
                        timer.isPaused -> status.warning
                        else -> status.success
                    }
                )
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                when {
                    !timer.isRunning -> "Shift timer"
                    timer.isPaused -> "Paused · ${timer.workplace}"
                    else -> "On shift · ${timer.workplace}"
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (timer.isRunning) {
                IconButton(onClick = { confirmDiscard = true }) {
                    Icon(Icons.Default.Close, contentDescription = "Discard timer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (timer.isRunning) {
            Text(
                timer.formattedDuration,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(vertical = Spacing.sm)
            )
            Text(
                "Started at ${timer.formattedStartTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedButton(
                    onClick = { if (timer.isPaused) onResume() else onPause() },
                    shape = ControlShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (timer.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(if (timer.isPaused) "Resume" else "Pause")
                }
                Button(onClick = onStop, shape = ControlShape, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("End & log")
                }
            }
        } else {
            Text(
                "Clock in when you start and we'll fill in the hours for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { menu = true }, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
                        Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        workplaces.forEach { wp ->
                            DropdownMenuItem(text = { Text(wp) }, onClick = { selected = wp; menu = false })
                        }
                    }
                }
                Button(onClick = { onStart(selected) }, shape = ControlShape) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Clock in")
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard timer?",
            message = "The current ${timer.formattedDuration} won't be logged.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = onDiscard,
            onDismiss = { confirmDiscard = false }
        )
    }
}
