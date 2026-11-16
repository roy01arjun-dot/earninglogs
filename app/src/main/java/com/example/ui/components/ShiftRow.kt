package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.WorkLogEntity
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.format.TextStyle
import java.util.Locale

/**
 * One shift in a list. Tap opens the editor (where you can also mark it paid or delete it).
 *
 * Simpler than before: no tiny 3-dot menu. Screen-reader users get one clear sentence per row
 * plus "Mark as paid" and "Delete" as accessibility actions.
 */
@Composable
fun ShiftRow(
    log: WorkLogEntity,
    currency: String,
    onClick: () -> Unit,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date = DateUtils.parseDate(log.date)
    var confirmDelete by remember { mutableStateOf(false) }
    val pay = CurrencyUtils.formatCurrency(log.totalPay, currency)

    val spoken = buildString {
        append(DateUtils.formatDisplayDate(log.date)).append(", ")
        append(log.workplace).append(", ")
        append(log.netHoursWorked.asSpokenHours())
        if (log.overtimeHours > 0) append(", including ${log.overtimeHours.asSpokenHours()} overtime")
        append(", $pay, ")
        append(if (log.isPaid) "paid" else "unpaid")
        if (log.note.isNotBlank()) append(". Note: ${log.note}")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Edit shift") { onClick(); true }
                customActions = listOf(
                    CustomAccessibilityAction(if (log.isPaid) "Mark as unpaid" else "Mark as paid") { onTogglePaid(); true },
                    CustomAccessibilityAction("Delete shift") { confirmDelete = true; true }
                )
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date block (grows with font size instead of clipping)
        Column(
            modifier = Modifier
                .sizeIn(minWidth = 52.dp, minHeight = 52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.width(Spacing.md))

        Column(Modifier.weight(1f)) {
            Text(log.workplace, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(log.netHoursWorked.asHours())
                    if (log.overtimeHours > 0) append(" · ${log.overtimeHours.asHours()} overtime")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (log.note.isNotBlank()) {
                Text(
                    log.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(horizontalAlignment = Alignment.End) {
            Text(pay, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR))
            Spacer(Modifier.height(4.dp))
            PaidBadge(log.isPaid)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this shift?",
            message = "${DateUtils.formatDisplayDate(log.date)} at ${log.workplace} will be removed from all your devices.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}
