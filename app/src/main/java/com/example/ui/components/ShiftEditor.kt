package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.UserSettings
import com.example.data.WageType
import com.example.data.WorkLogEntity
import com.example.ui.theme.AppTheme
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max

/**
 * Full-screen editor for creating or editing a shift.
 * @param prefillHours total hours from the shift timer, split into regular/overtime.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftEditor(
    initialLog: WorkLogEntity?,
    settings: UserSettings,
    prefillHours: Double? = null,
    prefillWorkplace: String? = null,
    onDismiss: () -> Unit,
    onSave: (WorkLogEntity) -> Unit,
    onDelete: ((Int) -> Unit)? = null
) {
    val status = AppTheme.status
    val isEdit = initialLog != null

    var date by remember { mutableStateOf(initialLog?.date ?: DateUtils.todayString()) }
    var workplace by remember {
        mutableStateOf(initialLog?.workplace ?: prefillWorkplace ?: settings.workplaces.firstOrNull() ?: "Main Workplace")
    }
    var regularInput by remember {
        mutableStateOf(
            when {
                initialLog != null -> (initialLog.regularHours + initialLog.breakMinutes / 60.0).asPlain()
                prefillHours != null -> minOf(prefillHours, settings.defaultOvertimeThreshold).asPlain()
                else -> settings.defaultOvertimeThreshold.asPlain()
            }
        )
    }
    var overtimeInput by remember {
        mutableStateOf(
            when {
                initialLog != null -> initialLog.overtimeHours.asPlain()
                prefillHours != null -> max(0.0, prefillHours - settings.defaultOvertimeThreshold).asPlain()
                else -> "0"
            }
        )
    }
    var breakInput by remember { mutableStateOf((initialLog?.breakMinutes ?: 0).toString()) }
    var wageType by remember { mutableStateOf(initialLog?.wageType ?: settings.defaultWageType) }
    var rateInput by remember { mutableStateOf((initialLog?.wageRate ?: settings.defaultWageRate).asPlain()) }
    var multiplierInput by remember {
        mutableStateOf((initialLog?.overtimeMultiplier ?: settings.defaultOvertimeMultiplier).asPlain())
    }
    var note by remember { mutableStateOf(initialLog?.note ?: "") }
    var isPaid by remember { mutableStateOf(initialLog?.isPaid ?: false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAddWorkplace by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }

    // ---- parsed values & live preview (same maths as WorkLogEntity) ----
    val regular = regularInput.replace(',', '.').toDoubleOrNull()
    val overtime = overtimeInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val breakMin = breakInput.toIntOrNull() ?: 0
    val rate = rateInput.replace(",", "").toDoubleOrNull()
    val multiplier = multiplierInput.replace(',', '.').toDoubleOrNull() ?: 1.5

    val regularError = when {
        regular == null -> "Enter hours"
        regular < 0 || regular > 24 -> "0 – 24 hours"
        else -> null
    }
    val rateError = when {
        rate == null -> "Enter a rate"
        rate < 0 -> "Must be positive"
        else -> null
    }
    val overtimeError = if (overtime < 0 || overtime > 24) "0 – 24 hours" else null
    val breakError = if (breakMin < 0 || breakMin > 600) "0 – 600 min" else null
    val valid = regularError == null && rateError == null && overtimeError == null && breakError == null

    val draft = WorkLogEntity(
        id = initialLog?.id ?: 0,
        uuid = initialLog?.uuid ?: java.util.UUID.randomUUID().toString(),
        date = date,
        hoursWorked = (regular ?: 0.0) + overtime,
        breakMinutes = breakMin,
        wageType = wageType,
        wageRate = rate ?: 0.0,
        overtimeThresholdHours = max(0.0, (regular ?: 0.0) - breakMin / 60.0),
        overtimeMultiplier = multiplier,
        note = note.trim(),
        isPaid = isPaid,
        workplace = workplace.trim().ifEmpty { "Main Workplace" },
        updatedAt = initialLog?.updatedAt ?: System.currentTimeMillis()
    )
    val currency = settings.currencySymbol

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    },
                    title = { Text(if (isEdit) "Edit shift" else "New shift") },
                    actions = {
                        TextButton(
                            onClick = {
                                attemptedSave = true
                                if (valid) onSave(draft)
                            }
                        ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Estimated pay", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyUtils.formatCurrency(draft.totalPay, currency),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR)
                                )
                                Text(
                                    buildString {
                                        append("${draft.netHoursWorked.asHours()} worked")
                                        if (draft.overtimeHours > 0) append(" · ${draft.overtimeHours.asHours()} overtime")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { attemptedSave = true; if (valid) onSave(draft) },
                                shape = ControlShape,
                                modifier = Modifier.height(48.dp)
                            ) { Text(if (isEdit) "Save changes" else "Add shift") }
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // ---- When & where
                AppCard {
                    FieldLabel("Date")
                    val today = DateUtils.todayString()
                    val yesterday = DateUtils.formatDate(LocalDate.now().minusDays(1))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(selected = date == today, onClick = { date = today }, label = { Text("Today") })
                        FilterChip(selected = date == yesterday, onClick = { date = yesterday }, label = { Text("Yesterday") })
                        FilterChip(
                            selected = date != today && date != yesterday,
                            onClick = { showDatePicker = true },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) },
                            label = { Text(if (date != today && date != yesterday) DateUtils.formatDisplayDate(date) else "Pick date") }
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    FieldLabel("Workplace")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        (settings.workplaces + workplace).distinct().forEach { wp ->
                            FilterChip(selected = workplace == wp, onClick = { workplace = wp }, label = { Text(wp) })
                        }
                        FilterChip(
                            selected = false,
                            onClick = { showAddWorkplace = true },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                            label = { Text("New") }
                        )
                    }
                }

                // ---- Hours
                AppCard {
                    FieldLabel("Hours")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        NumberField(
                            value = regularInput, onChange = { regularInput = it },
                            label = "Regular hours", suffix = "h",
                            error = if (attemptedSave) regularError else null,
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            value = overtimeInput, onChange = { overtimeInput = it },
                            label = "Overtime", suffix = "h",
                            error = overtimeError,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(4.0, 6.0, 8.0, 10.0, 12.0).forEach { h ->
                            FilterChip(
                                selected = regular == h,
                                onClick = { regularInput = h.asPlain() },
                                label = { Text(h.asHours()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    FieldLabel("Unpaid break")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.Center) {
                        listOf(0, 15, 30, 45, 60).forEach { m ->
                            FilterChip(
                                selected = breakMin == m,
                                onClick = { breakInput = m.toString() },
                                label = { Text(if (m == 0) "None" else "${m}m") }
                            )
                        }
                    }
                    NumberField(
                        value = breakInput, onChange = { breakInput = it.filter(Char::isDigit) },
                        label = "Break", suffix = "min", error = breakError,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---- Pay
                AppCard {
                    FieldLabel("Pay")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = wageType == WageType.HOURLY,
                            onClick = { wageType = WageType.HOURLY },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Hourly") }
                        SegmentedButton(
                            selected = wageType == WageType.DAILY,
                            onClick = { wageType = WageType.DAILY },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Daily") }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        NumberField(
                            value = rateInput, onChange = { rateInput = it },
                            label = if (wageType == WageType.HOURLY) "Hourly rate" else "Daily rate",
                            prefix = currency,
                            error = if (attemptedSave) rateError else null,
                            modifier = Modifier.weight(1.4f)
                        )
                        NumberField(
                            value = multiplierInput, onChange = { multiplierInput = it },
                            label = "OT rate", suffix = "×",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ---- Details
                AppCard {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(200) },
                        label = { Text("Note (optional)") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Paid", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (isPaid) "You've received this pay" else "Waiting for payment",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPaid) status.success else status.warning
                            )
                        }
                        Switch(checked = isPaid, onCheckedChange = { isPaid = it })
                    }
                }

                if (isEdit && onDelete != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Delete shift", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = DateUtils.parseDate(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = DateUtils.formatDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showAddWorkplace) {
        TextInputDialog(
            title = "New workplace",
            label = "Workplace name",
            confirmLabel = "Add",
            onConfirm = { workplace = it },
            onDismiss = { showAddWorkplace = false }
        )
    }

    if (confirmDelete && initialLog != null && onDelete != null) {
        ConfirmDialog(
            title = "Delete shift?",
            message = "This shift will be removed from all your devices.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(initialLog.id) },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.sm)
    )
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        prefix = if (prefix != null) { { Text(prefix) } } else null,
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR),
        shape = ControlShape,
        modifier = modifier
    )
}
