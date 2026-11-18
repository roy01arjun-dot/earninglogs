package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.ui.MonthlyPayrollSummary
import com.example.ui.components.AppCard
import com.example.ui.components.ButtonMinHeight
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ControlShape
import com.example.ui.components.EmptyState
import com.example.ui.components.HeroCard
import com.example.ui.components.PeriodSwitcher
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShiftRow
import com.example.ui.components.Spacing
import com.example.ui.components.StatTile
import com.example.ui.components.asHours
import com.example.ui.components.asPlain
import com.example.ui.components.asSpokenHours
import com.example.ui.theme.AppTheme
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.LocalTime
import java.time.YearMonth

/**
 * Dashboard: greeting, gradient earnings hero with a goal ring, three colourful stat tiles
 * and the latest shifts.
 */
@Composable
fun HomeScreen(
    userName: String,
    monthly: MonthlyPayrollSummary,
    settings: UserSettings,
    contentPadding: PaddingValues,
    onNavigateMonth: (Long) -> Unit,
    onCurrentMonth: () -> Unit,
    onMarkMonthPaid: () -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (WorkLogEntity) -> Unit,
    onDeleteShift: (Int) -> Unit,
    onTogglePaid: (WorkLogEntity) -> Unit,
    onSeeAllShifts: () -> Unit
) {
    val currency = settings.currencySymbol
    val scheme = MaterialTheme.colorScheme
    val status = AppTheme.status
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
            Column(Modifier.padding(horizontal = Spacing.xs)) {
                Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text(
                    userName.ifBlank { "Welcome" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
            }
        }

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

        item { EarningsHero(monthly, settings, onMarkMonthPaid) }

        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "Hours", value = monthly.totalHours.asHours(), icon = Icons.Outlined.AccessTime,
                    container = scheme.primaryContainer, content = scheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                StatTile(
                    label = "Overtime", value = monthly.overtimeHours.asHours(), icon = Icons.Outlined.Bolt,
                    container = status.overtimeContainer, content = status.onOvertimeContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                StatTile(
                    label = "Days worked", value = "${monthly.daysWorked}", icon = Icons.Outlined.EventAvailable,
                    container = status.successContainer, content = status.onSuccessContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight()
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
                        message = "Tap \"Log shift\" after work to add your hours.",
                        actionLabel = "Log a shift",
                        onAction = onAddShift
                    )
                }
            }
        } else {
            item {
                AppCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    recent.forEachIndexed { i, log ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = scheme.outlineVariant.copy(alpha = 0.5f))
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

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

// ---------------------------------------------------------------- hero

@Composable
private fun EarningsHero(monthly: MonthlyPayrollSummary, settings: UserSettings, onMarkMonthPaid: () -> Unit) {
    val currency = settings.currencySymbol
    val target = settings.monthlyTargetEarnings
    val progress = if (target > 0) (monthly.totalEarnings / target).toFloat().coerceIn(0f, 1f) else 0f
    val pct = (progress * 100).toInt()
    val earned = CurrencyUtils.formatCurrency(monthly.totalEarnings, currency)
    val soft = Color.White.copy(alpha = 0.86f)
    var confirmPaid by remember { mutableStateOf(false) }

    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = "Earned $earned from ${monthly.logs.size} shifts, ${monthly.totalHours.asSpokenHours()}" +
                        if (target > 0) ". $pct percent of your monthly goal" else ""
                }
            ) {
                Text("Earned this month", style = MaterialTheme.typography.labelLarge, color = soft)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    earned,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${monthly.totalHours.asHours()} · ${monthly.logs.size} shift${if (monthly.logs.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = soft
                )
                if (settings.defaultTaxRate > 0 && monthly.totalEarnings > 0) {
                    Text(
                        "≈ ${CurrencyUtils.formatCurrency(monthly.totalEarnings * (1 - settings.defaultTaxRate / 100.0), currency)} after ${settings.defaultTaxRate.asPlain()}% tax",
                        style = MaterialTheme.typography.bodySmall,
                        color = soft
                    )
                }
            }
            if (target > 0) {
                Spacer(Modifier.width(Spacing.md))
                GoalRing(progress, pct)
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            HeroChip(Icons.Filled.CheckCircle, "Paid", CurrencyUtils.formatCurrency(monthly.paidEarnings, currency), Modifier.weight(1f))
            HeroChip(Icons.Outlined.Schedule, "Not paid yet", CurrencyUtils.formatCurrency(monthly.unpaidEarnings, currency), Modifier.weight(1f))
        }

        if (monthly.unpaidEarnings > 0) {
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = { confirmPaid = true },
                shape = ControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2C1A8E)),
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

/** Circular goal progress. Decorative for TalkBack: the hero text already announces the percentage. */
@Composable
private fun GoalRing(progress: Float, pct: Int) {
    Box(Modifier.size(88.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(88.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.24f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(Color.White, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text("of goal", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.86f))
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.86f))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR),
                color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
