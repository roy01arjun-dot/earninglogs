package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.ui.components.AppCard
import com.example.ui.components.ControlShape
import com.example.ui.components.EmptyState
import com.example.ui.components.ShiftRow
import com.example.ui.components.Spacing
import com.example.ui.components.asHours
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

private enum class PaidFilter(val label: String) { ALL("All"), UNPAID("Unpaid"), PAID("Paid") }

@Composable
fun ShiftsScreen(
    logs: List<WorkLogEntity>,
    settings: UserSettings,
    contentPadding: PaddingValues,
    onAddShift: () -> Unit,
    onEditShift: (WorkLogEntity) -> Unit,
    onDeleteShift: (Int) -> Unit,
    onTogglePaid: (WorkLogEntity) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PaidFilter.ALL) }
    val currency = settings.currencySymbol

    val visible = remember(logs, query, filter) {
        val q = query.trim().lowercase()
        logs.filter { log ->
            val matchesQuery = q.isEmpty() ||
                log.workplace.lowercase().contains(q) ||
                log.note.lowercase().contains(q) ||
                log.date.contains(q) ||
                DateUtils.formatDisplayDate(log.date).lowercase().contains(q)
            val matchesFilter = when (filter) {
                PaidFilter.ALL -> true
                PaidFilter.UNPAID -> !log.isPaid
                PaidFilter.PAID -> log.isPaid
            }
            matchesQuery && matchesFilter
        }
    }
    val grouped = remember(visible) { visible.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it }) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.screen, end = Spacing.screen,
            top = contentPadding.calculateTopPadding() + Spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search workplace, note or date") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") } }
                } else null,
                singleLine = true,
                shape = ControlShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PaidFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
                Text(
                    "${visible.size} shift${if (visible.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }

        if (grouped.isEmpty()) {
            item {
                AppCard {
                    if (logs.isEmpty()) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                            title = "No shifts yet",
                            message = "Every shift you log appears here, grouped by month.",
                            actionLabel = "Log a shift",
                            onAction = onAddShift
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = "No matches",
                            message = "Try a different search or filter."
                        )
                    }
                }
            }
        }

        grouped.forEach { (month, monthLogs) ->
            item(key = "header_$month") {
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(DateUtils.formatMonthYear("$month-01"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(
                            "${monthLogs.size} shifts · ${monthLogs.sumOf { it.netHoursWorked }.asHours()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        CurrencyUtils.formatCurrency(monthLogs.sumOf { it.totalPay }, currency),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR)
                    )
                }
            }
            item(key = "group_$month") {
                AppCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    monthLogs.forEachIndexed { i, log ->
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
