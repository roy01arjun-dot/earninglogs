package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.ui.WeeklyPayrollSummary
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

fun buildPayrollReport(summary: WeeklyPayrollSummary, currency: String, taxRate: Double): String = buildString {
    val tax = summary.totalEarnings * taxRate / 100.0
    appendLine("PAY STATEMENT")
    appendLine("${DateUtils.formatDisplayDate(summary.startOfWeek.toString())} – ${DateUtils.formatDisplayDate(summary.endOfWeek.toString())}")
    appendLine()
    appendLine("Days worked     ${summary.daysWorked}")
    appendLine("Total hours     ${summary.totalHours.asHours()}")
    appendLine("  Regular       ${summary.regularHours.asHours()}")
    appendLine("  Overtime      ${summary.overtimeHours.asHours()}")
    appendLine()
    appendLine("Regular pay     ${CurrencyUtils.formatCurrency(summary.regularPay, currency)}")
    appendLine("Overtime pay    ${CurrencyUtils.formatCurrency(summary.overtimePay, currency)}")
    appendLine("Gross pay       ${CurrencyUtils.formatCurrency(summary.totalEarnings, currency)}")
    appendLine("Tax (${taxRate.asPlain()}%)     -${CurrencyUtils.formatCurrency(tax, currency)}")
    appendLine("Net pay         ${CurrencyUtils.formatCurrency(summary.totalEarnings - tax, currency)}")
    appendLine()
    appendLine("Paid            ${CurrencyUtils.formatCurrency(summary.paidEarnings, currency)}")
    appendLine("Outstanding     ${CurrencyUtils.formatCurrency(summary.unpaidEarnings, currency)}")
    if (summary.logs.isNotEmpty()) {
        appendLine()
        appendLine("SHIFTS")
        summary.logs.forEach { log ->
            val ot = if (log.overtimeHours > 0) " (+${log.overtimeHours.asHours()} OT)" else ""
            val note = if (log.note.isNotBlank()) " – ${log.note}" else ""
            appendLine("${DateUtils.formatShortDate(log.date)}  ${log.workplace}  ${log.netHoursWorked.asHours()}$ot  ${CurrencyUtils.formatCurrency(log.totalPay, currency)}${if (log.isPaid) "  ✓" else ""}$note")
        }
    }
    appendLine()
    append("Generated with Earninglogs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollExportSheet(
    summary: WeeklyPayrollSummary,
    currency: String,
    taxRate: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val report = remember(summary, currency, taxRate) { buildPayrollReport(summary, currency, taxRate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = Spacing.screen).navigationBarsPadding()) {
            Text("Pay statement", style = MaterialTheme.typography.titleLarge)
            Text(
                "Share it with your employer or keep it for your records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                report,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(report))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = ControlShape,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Copy")
                }
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Pay statement")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Share pay statement"))
                    },
                    shape = ControlShape,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Share")
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
