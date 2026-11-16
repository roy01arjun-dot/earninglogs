package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.WorkLogEntity
import com.example.ui.theme.AppTheme
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.format.TextStyle
import java.util.Locale

/** One shift in a list. Tap = edit, menu = mark paid / delete. */
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
    val status = AppTheme.status
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.lg, end = Spacing.xs, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date block
        Column(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.width(Spacing.md))

        Column(Modifier.weight(1f)) {
            Text(
                log.workplace,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val details = buildString {
                append(log.netHoursWorked.asHours())
                if (log.overtimeHours > 0) append(" · ${log.overtimeHours.asHours()} OT")
                if (log.breakMinutes > 0) append(" · ${log.breakMinutes}m break")
                if (log.note.isNotBlank()) append(" · ${log.note}")
            }
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                CurrencyUtils.formatCurrency(log.totalPay, currency),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR)
            )
            Spacer(Modifier.height(4.dp))
            if (log.isPaid) {
                StatusPill("Paid", status.success, status.successContainer)
            } else {
                StatusPill("Unpaid", status.warning, status.warningContainer)
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Shift options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuOpen = false; onClick() }
                )
                DropdownMenuItem(
                    text = { Text(if (log.isPaid) "Mark as unpaid" else "Mark as paid") },
                    leadingIcon = {
                        Icon(if (log.isPaid) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle, null)
                    },
                    onClick = { menuOpen = false; onTogglePaid() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; confirmDelete = true }
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete shift?",
            message = "${DateUtils.formatDisplayDate(log.date)} at ${log.workplace} will be removed from all your devices.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}
