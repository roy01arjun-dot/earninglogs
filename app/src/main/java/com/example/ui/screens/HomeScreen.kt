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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ShiftTimerState
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.ui.MonthlyPayrollSummary
import com.example.ui.components.AppCard
import com.example.ui.components.ButtonMinHeight
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ControlShape
import com.example.ui.components.EmptyState
import com.example.ui.components.KeyValueRow
import com.example.ui.components.PeriodSwitcher
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShiftRow
import com.example.ui.components.Spacing
import com.example.ui.components.asHours
import com.example.ui.components.asPlain
import com.example.ui.components.asSpokenHours
import com.example.ui.theme.AppTheme
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.YearMonth

/**
 * Home: one summary card, the shift timer and the latest shifts. That's it.
 * (Stats grid, tax editor and per-workplace breakdown moved to Reports / Settings.)
 */
@Composable
fun HomeScreen(
    monthly: MonthlyPayrollSummary,
    settings: UserSettings,
    timer: ShiftTimerState,
    contentPadding: PaddingValues,
    onNavigateMonth: (Long) -> Unit,
    onCurrentMonth: () -> Unit,
    onMarkMonthPaid: () -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (WorkLogEntity) -> Unit,
    onDeleteShift: (Int) -> Unit,
    onTogglePaid: (WorkLogEntity) -> Unit,
    onSeeAllShifts: () -> Unit,
    onStartTimer: (String) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onFinishTimer: () -> Unit,
    onDiscardTimer: () -> Unit
) {
    val currency = settings.currencySymbol
    val isCurrentMonth = monthly.yearMonth == YearMonth.now()
    val recent = remember(monthly.logs) {
        monthly.logs.sortedWith(compareByDescending<WorkLogEntity> { it.date }.thenByDescending { it.id }).take(5)
    }

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
                unit = "month",
                onPrevious = { onNavigateMonth(-1) },
                onNext = { onNavigateMonth(1) },
                showReset = !isCurrentMonth,
                resetLabel = "Back to this month",
                onReset = onCurrentMonth
            )
        }

        item { MonthSummaryCard(monthly, settings, onMarkMonthPaid) }

        if (isCurrentMonth || timer.isRunning) {
            item {
                ShiftTimerCard(
                    timer = timer,
                    workplaces = settings.workplaces,
                    onStart = onStartTimer, onPause = onPauseTimer, onResume = onResumeTimer,
                    onFinish = onFinishTimer, onDiscard = onDiscardTimer
                )
            }
        }

        item {
            SectionHeader(
                "Recent shifts",
                actionLabel = if (monthly.logs.size > recent.size) "See all" else null,
                onAction = onSeeAllShifts
            )
        }
        if (recent.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "No shifts in ${DateUtils.formatMonthYear(monthly.yearMonth)}",
                        message = "Tap \"Log shift\" after work, or use the timer to clock in.",
                        actionLabel = "Log a shift",
                        onAction = onAddShift
                    )
                }
            }
        } else {
            item {
                AppCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    recent.forEachIndexed { i, log ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun MonthSummaryCard(monthly: MonthlyPayrollSummary, settings: UserSettings, onMarkMonthPaid: () -> Unit) {
    val currency = settings.currencySymbol
    val status = AppTheme.status
    val target = settings.monthlyTargetEarnings
    val progress = if (target > 0) (monthly.totalEarnings / target).toFloat().coerceIn(0f, 1f) else 0f
    val earned = CurrencyUtils.formatCurrency(monthly.totalEarnings, currency)
    var confirmPaid by remember { mutableStateOf(false) }

    AppCard {
        Column(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Earned $earned from ${monthly.logs.size} shifts, ${monthly.totalHours.asSpokenHours()}"
            }
        ) {
            Text("Earned", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(earned, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${monthly.totalHours.asHours()} · ${monthly.logs.size} shift${if (monthly.logs.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (target > 0) {
            Spacer(Modifier.height(Spacing.lg))
            val pct = (progress * 100).toInt()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .semantics { contentDescription = "$pct percent of your monthly goal" },
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "$pct% of your ${CurrencyUtils.formatCurrency(target, currency)} goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.xs))
        KeyValueRow("Paid", CurrencyUtils.formatCurrency(monthly.paidEarnings, currency), valueColor = status.success)
        KeyValueRow("Not paid yet", CurrencyUtils.formatCurrency(monthly.unpaidEarnings, currency), valueColor = status.warning)
        if (settings.defaultTaxRate > 0) {
            KeyValueRow(
                "After ${settings.defaultTaxRate.asPlain()}% tax",
                CurrencyUtils.formatCurrency(monthly.totalEarnings * (1 - settings.defaultTaxRate / 100.0), currency)
            )
        }
        if (monthly.unpaidEarnings > 0) {
            Spacer(Modifier.height(Spacing.sm))
            FilledTonalButton(
                onClick = { confirmPaid = true },
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("I got paid for this month")
            }
        }
    }

    if (confirmPaid) {
        ConfirmDialog(
            title = "Mark month as paid?",
            message = "All unpaid shifts in ${DateUtils.formatMonthYear(monthly.yearMonth)} " +
                "(${CurrencyUtils.formatCurrency(monthly.unpaidEarnings, currency)}) will be marked as paid.",
            confirmLabel = "Mark as paid",
            onConfirm = onMarkMonthPaid,
            onDismiss = { confirmPaid = false }
        )
    }
}

@Composable
private fun ShiftTimerCard(
    timer: ShiftTimerState,
    workplaces: List<String>,
    onStart: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit
) {
    val status = AppTheme.status
    var selected by rememberSaveable(workplaces) { mutableStateOf(workplaces.firstOrNull() ?: "Main Workplace") }
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
                    else -> "Working · ${timer.workplace}"
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        if (timer.isRunning) {
            val h = timer.totalElapsedMs / 3_600_000
            val m = (timer.totalElapsedMs / 60_000) % 60
            Text(
                timer.formattedDuration,
                style = MaterialTheme.typography.displaySmall,
                // Read as "2 hours 5 minutes", not "zero two colon zero five ..."
                modifier = Modifier
                    .padding(vertical = Spacing.sm)
                    .semantics { contentDescription = "$h hours $m minutes, started at ${timer.formattedStartTime}" }
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
                    modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)
                ) {
                    Icon(if (timer.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(if (timer.isPaused) "Resume" else "Pause")
                }
                Button(onClick = onFinish, shape = ControlShape, modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)) {
                    Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Finish")
                }
            }
            TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Discard timer", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text(
                "Clock in when you start work. We'll fill in the hours when you finish.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                if (workplaces.size > 1) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { menu = true },
                            shape = ControlShape,
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                                .semantics { contentDescription = "Workplace: $selected. Tap to change" }
                        ) { Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            workplaces.forEach { wp ->
                                DropdownMenuItem(text = { Text(wp) }, onClick = { selected = wp; menu = false })
                            }
                        }
                    }
                }
                Button(
                    onClick = { onStart(selected) },
                    shape = ControlShape,
                    modifier = Modifier.then(if (workplaces.size > 1) Modifier else Modifier.fillMaxWidth())
                        .heightIn(min = ButtonMinHeight)
                ) {
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
            message = "The ${timer.formattedDuration} on the timer won't be logged.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = onDiscard,
            onDismiss = { confirmDiscard = false }
        )
    }
}
