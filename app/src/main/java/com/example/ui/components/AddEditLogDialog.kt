package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AppColors
import com.example.data.UserSettings
import com.example.data.WageType
import com.example.data.WorkLogEntity
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLogDialog(
    initialLog: WorkLogEntity?,
    userSettings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (WorkLogEntity) -> Unit
) {
    var dateStr by remember { mutableStateOf(initialLog?.date ?: DateUtils.todayString()) }
    var hoursInput by remember { mutableStateOf(initialLog?.hoursWorked?.toString() ?: "8.0") }
    var breakInput by remember { mutableStateOf(initialLog?.breakMinutes?.toString() ?: "0") }
    var wageType by remember { mutableStateOf(initialLog?.wageType ?: userSettings.defaultWageType) }
    var wageRateInput by remember { mutableStateOf(initialLog?.wageRate?.toString() ?: userSettings.defaultWageRate.toString()) }
    var overtimeThresholdInput by remember { mutableStateOf(initialLog?.overtimeThresholdHours?.toString() ?: userSettings.defaultOvertimeThreshold.toString()) }
    var overtimeMultiplierInput by remember { mutableStateOf(initialLog?.overtimeMultiplier?.toString() ?: userSettings.defaultOvertimeMultiplier.toString()) }
    var note by remember { mutableStateOf(initialLog?.note ?: "") }
    var isPaid by remember { mutableStateOf(initialLog?.isPaid ?: false) }
    var selectedWorkplace by remember { mutableStateOf(initialLog?.workplace ?: userSettings.workplaces.firstOrNull() ?: "Main Workplace") }
    
    var showNewWorkplaceDialog by remember { mutableStateOf(false) }
    var newWorkplaceInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Quick hour preset buttons
    val hourPresets = listOf("4.0", "6.0", "8.0", "10.0", "12.0")
    val breakPresets = listOf(0 to "0m", 30 to "30m", 60 to "60m")

    // Parsed values
    val hoursWorked = hoursInput.toDoubleOrNull() ?: 0.0
    val breakMinutes = breakInput.toIntOrNull() ?: 0
    val wageRate = wageRateInput.toDoubleOrNull() ?: 0.0
    val overtimeThreshold = overtimeThresholdInput.toDoubleOrNull() ?: 8.0
    val overtimeMultiplier = overtimeMultiplierInput.toDoubleOrNull() ?: 1.5

    // Calculated live preview
    val netHours = max(0.0, hoursWorked - (breakMinutes / 60.0))
    val regularHours = min(netHours, overtimeThreshold)
    val overtimeHours = max(0.0, netHours - overtimeThreshold)

    val effectiveHourlyRate = when (wageType) {
        WageType.HOURLY -> wageRate
        WageType.DAILY -> if (overtimeThreshold > 0) wageRate / overtimeThreshold else 0.0
    }
    val regularPay = when (wageType) {
        WageType.HOURLY -> regularHours * wageRate
        WageType.DAILY -> wageRate
    }
    val overtimePay = overtimeHours * (effectiveHourlyRate * overtimeMultiplier)
    val totalPay = regularPay + overtimePay

    val currency = userSettings.currencySymbol

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .imePadding()
                .clip(RoundedCornerShape(26.dp)),
            color = AppColors.background,
            border = BorderStroke(1.dp, AppColors.cardBorder),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (initialLog == null) "Log Shift Hours" else "Edit Shift Entry",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Text(
                            text = "Calculate daily wage and overtime",
                            fontSize = 12.sp,
                            color = AppColors.textSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. LIVE ESTIMATED PAY BANNER (Hero in dialog)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.hero)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Estimated Total Pay",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = CurrencyUtils.formatCurrency(totalPay, currency),
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${String.format("%.1f", netHours)} net hours",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (overtimeHours > 0) {
                                Text(
                                    text = "+${String.format("%.1f", overtimeHours)}h Overtime",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Date Selection (Today / Yesterday / Calendar)
                Text(
                    text = "Shift Date",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val todayStr = DateUtils.todayString()
                val yesterdayStr = DateUtils.formatDate(LocalDate.now().minusDays(1))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip(
                        label = "Today",
                        selected = dateStr == todayStr,
                        onClick = { dateStr = todayStr },
                        modifier = Modifier.weight(1f)
                    )
                    QuickChip(
                        label = "Yesterday",
                        selected = dateStr == yesterdayStr,
                        onClick = { dateStr = yesterdayStr },
                        modifier = Modifier.weight(1f)
                    )
                    QuickChip(
                        label = if (dateStr != todayStr && dateStr != yesterdayStr) DateUtils.formatDisplayDate(dateStr) else "Calendar",
                        selected = dateStr != todayStr && dateStr != yesterdayStr,
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Workplace Selector
                Text(
                    text = "Workplace",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val availableWorkplaces = remember(userSettings.workplaces, selectedWorkplace) {
                    (userSettings.workplaces + selectedWorkplace).distinct()
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableWorkplaces) { wp ->
                        FilterChip(
                            selected = selectedWorkplace == wp,
                            onClick = { selectedWorkplace = wp },
                            label = { Text(wp, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.accent,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = AppColors.card,
                                labelColor = AppColors.textPrimary
                            ),
                            border = BorderStroke(1.dp, AppColors.cardBorder)
                        )
                    }

                    item {
                        FilterChip(
                            selected = false,
                            onClick = { showNewWorkplaceDialog = true },
                            label = { Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = AppColors.card.copy(alpha = 0.5f),
                                labelColor = AppColors.accent
                            ),
                            border = BorderStroke(1.dp, AppColors.cardBorder)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Hours Worked & Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Working Hours",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )
                    Text(
                        text = "Tap preset or type below",
                        fontSize = 11.sp,
                        color = AppColors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Hour Presets Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hourPresets.forEach { preset ->
                        QuickChip(
                            label = "${preset.substringBefore(".")}h",
                            selected = hoursInput == preset,
                            onClick = { hoursInput = preset },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = hoursInput,
                        onValueChange = { hoursInput = it },
                        label = { Text("Total Hours", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.accent,
                            unfocusedBorderColor = AppColors.cardBorder,
                            focusedContainerColor = AppColors.card.copy(alpha = 0.4f),
                            unfocusedContainerColor = AppColors.card.copy(alpha = 0.4f)
                        )
                    )

                    OutlinedTextField(
                        value = breakInput,
                        onValueChange = { breakInput = it },
                        label = { Text("Break (mins)", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.accent,
                            unfocusedBorderColor = AppColors.cardBorder,
                            focusedContainerColor = AppColors.card.copy(alpha = 0.4f),
                            unfocusedContainerColor = AppColors.card.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Hourly Rate & Overtime Multiplier
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = wageRateInput,
                        onValueChange = { wageRateInput = it },
                        label = { Text("Hourly Rate ($currency)", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.accent,
                            unfocusedBorderColor = AppColors.cardBorder,
                            focusedContainerColor = AppColors.card.copy(alpha = 0.4f),
                            unfocusedContainerColor = AppColors.card.copy(alpha = 0.4f)
                        )
                    )

                    OutlinedTextField(
                        value = overtimeThresholdInput,
                        onValueChange = { overtimeThresholdInput = it },
                        label = { Text("OT Threshold (hrs)", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.accent,
                            unfocusedBorderColor = AppColors.cardBorder,
                            focusedContainerColor = AppColors.card.copy(alpha = 0.4f),
                            unfocusedContainerColor = AppColors.card.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Paid Status Toggle Switch
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.card.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, AppColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPaid) "Payment Status: PAID" else "Payment Status: UNPAID (Pending)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaid) AppColors.onPaidContainer else AppColors.unpaid
                            )
                            Text(
                                text = if (isPaid) "Shift has already been paid out" else "Shift is marked as pending payment",
                                fontSize = 11.sp,
                                color = AppColors.textSecondary
                            )
                        }

                        Switch(
                            checked = isPaid,
                            onCheckedChange = { isPaid = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppColors.onPaidContainer,
                                uncheckedTrackColor = AppColors.cardBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Optional Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes & Tasks (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Project meeting, night shift, bonus", fontSize = 12.sp) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.cardBorder,
                        focusedContainerColor = AppColors.card.copy(alpha = 0.4f),
                        unfocusedContainerColor = AppColors.card.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 8. Save / Cancel Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, AppColors.cardBorder)
                    ) {
                        Text("Cancel", color = AppColors.textSecondary)
                    }

                    Button(
                        onClick = {
                            val entity = WorkLogEntity(
                                id = initialLog?.id ?: 0,
                                date = dateStr,
                                hoursWorked = hoursWorked,
                                breakMinutes = breakMinutes,
                                wageType = wageType,
                                wageRate = wageRate,
                                overtimeThresholdHours = overtimeThreshold,
                                overtimeMultiplier = overtimeMultiplier,
                                note = note.trim(),
                                isPaid = isPaid,
                                workplace = selectedWorkplace
                            )
                            onSave(entity)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("dialog_save_shift_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (initialLog == null) "Save Shift" else "Update Shift",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedLocalDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            dateStr = DateUtils.formatDate(selectedLocalDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = AppColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Quick Add Workplace Sub-dialog
    if (showNewWorkplaceDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewWorkplaceDialog = false
                newWorkplaceInput = ""
            },
            title = { Text("Add Workplace", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newWorkplaceInput,
                    onValueChange = { newWorkplaceInput = it },
                    placeholder = { Text("e.g. Design Studio, Coffee Shop") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newWorkplaceInput.trim()
                        if (trimmed.isNotEmpty()) {
                            selectedWorkplace = trimmed
                        }
                        showNewWorkplaceDialog = false
                        newWorkplaceInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                ) {
                    Text("Select Workplace")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewWorkplaceDialog = false
                    newWorkplaceInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AppColors.accent else AppColors.card,
        border = BorderStroke(1.dp, if (selected) AppColors.accent else AppColors.cardBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else AppColors.textPrimary,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
