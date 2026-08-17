package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.WeeklyPayrollSummary
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@Composable
fun PayrollSummaryExportDialog(
    summary: WeeklyPayrollSummary,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val reportText = remember(summary, currencySymbol) {
        buildString {
            appendLine("=== WORK HOURS & PAYROLL REPORT ===")
            appendLine("Period: ${DateUtils.formatShortDate(summary.startOfWeek.toString())} - ${DateUtils.formatShortDate(summary.endOfWeek.toString())}")
            appendLine("Days Worked: ${summary.daysWorked}")
            appendLine("-----------------------------------")
            appendLine("Total Hours: ${String.format("%.1f", summary.totalHours)} hrs")
            appendLine("Regular Hours: ${String.format("%.1f", summary.regularHours)} hrs")
            appendLine("Overtime Hours: ${String.format("%.1f", summary.overtimeHours)} hrs")
            appendLine("-----------------------------------")
            appendLine("Base Regular Pay: ${CurrencyUtils.formatCurrency(summary.regularPay, currencySymbol)}")
            appendLine("Daily Overtime Pay: ${CurrencyUtils.formatCurrency(summary.overtimePay, currencySymbol)}")
            appendLine("GROSS TOTAL EARNINGS: ${CurrencyUtils.formatCurrency(summary.totalEarnings, currencySymbol)}")
            appendLine("===================================")
            appendLine()
            appendLine("Daily Shift Details:")
            summary.logs.forEach { log ->
                val netH = log.netHoursWorked
                val otH = log.overtimeHours
                val noteStr = if (log.note.isNotBlank()) " (${log.note})" else ""
                appendLine(" - ${log.date}: ${String.format("%.1f", netH)}h total [OT: ${String.format("%.1f", otH)}h] -> ${CurrencyUtils.formatCurrency(log.totalPay, currencySymbol)}$noteStr")
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Weekly Payroll Statement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = reportText,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(reportText))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Copy Text")
                    }

                    Button(
                        onClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, reportText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Payroll Report")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}
