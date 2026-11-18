package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.UserSettings
import com.example.data.WageType
import com.example.data.WorkLogEntity
import com.example.ui.theme.TABULAR
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import com.example.util.NumberInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max

/**
 * Full-screen editor for creating or editing a shift.
 *
 * Simplified: you enter the total time you worked; overtime is worked out automatically from
 * "overtime after X hours". Pay details (rate, overtime) are pre-filled from settings and folded
 * away unless you need them.
 *
 * Bug fixed: the old editor derived the overtime threshold from whatever you typed as "regular
 * hours", so a daily-wage shift's overtime rate changed from shift to shift, and a long break could
 * turn the whole shift into overtime. Input also survives screen rotation now (rememberSaveable).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftEditor(
    initialLog: WorkLogEntity?,
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (WorkLogEntity) -> Unit,
    onDelete: ((Int) -> Unit)? = null
) {
    val isEdit = initialLog != null
    val currency = settings.currencySymbol

    var date by rememberSaveable { mutableStateOf(initialLog?.date ?: DateUtils.todayString()) }
    var workplace by rememberSaveable {
        mutableStateOf(initialLog?.workplace ?: settings.workplaces.firstOrNull() ?: "Main Workplace")
    }
    var hoursInput by rememberSaveable {
        mutableStateOf((initialLog?.hoursWorked ?: settings.defaultOvertimeThreshold).asPlain())
    }
    var breakMin by rememberSaveable { mutableIntStateOf(initialLog?.breakMinutes ?: 0) }
    var customBreak by rememberSaveable { mutableStateOf(breakMin !in BREAK_PRESETS) }
    var breakInput by rememberSaveable { mutableStateOf(breakMin.toString()) }
    var wageTypeName by rememberSaveable { mutableStateOf((initialLog?.wageType ?: settings.defaultWageType).name) }
    val wageType = WageType.valueOf(wageTypeName)
    var rateInput by rememberSaveable { mutableStateOf((initialLog?.wageRate ?: settings.defaultWageRate).asPlain()) }
    var thresholdInput by rememberSaveable {
        mutableStateOf((initialLog?.overtimeThresholdHours ?: settings.defaultOvertimeThreshold).asPlain())
    }
    var multiplierInput by rememberSaveable {
        mutableStateOf((initialLog?.overtimeMultiplier ?: settings.defaultOvertimeMultiplier).asPlain())
    }
    var note by rememberSaveable { mutableStateOf(initialLog?.note ?: "") }
    var isPaid by rememberSaveable { mutableStateOf(initialLog?.isPaid ?: false) }
    var showPayDetails by rememberSaveable { mutableStateOf(false) }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showAddWorkplace by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var attemptedSave by rememberSaveable { mutableStateOf(false) }

    // ---- parse & validate
    val hours = NumberInput.parse(hoursInput)
    val effectiveBreak = if (customBreak) breakInput.toIntOrNull() else breakMin
    val rate = NumberInput.parse(rateInput)
    val threshold = NumberInput.parse(thresholdInput)
    val multiplier = NumberInput.parse(multiplierInput)

    val hoursError = when {
        hours == null -> "Enter the hours you worked"
        hours <= 0 || hours > 24 -> "Enter between 0 and 24 hours"
        else -> null
    }
    val breakError = when {
        effectiveBreak == null -> "Enter minutes"
        effectiveBreak < 0 || effectiveBreak > 600 -> "0 to 600 minutes"
        hours != null && effectiveBreak / 60.0 >= hours -> "Break is longer than the shift"
        else -> null
    }
    val rateError = when {
        rate == null -> "Enter your pay rate"
        rate < 0 -> "Can't be negative"
        else -> null
    }
    val thresholdError = if (threshold == null || threshold < 0.5 || threshold > 24) "0.5 to 24 hours" else null
    val multiplierError = if (multiplier == null || multiplier < 1 || multiplier > 5) "1 to 5" else null
    val payDetailsInvalid = rateError != null || thresholdError != null || multiplierError != null
    val valid = hoursError == null && breakError == null && !payDetailsInvalid

    val draft = WorkLogEntity(
        id = initialLog?.id ?: 0,
        uuid = initialLog?.uuid ?: java.util.UUID.randomUUID().toString(),
        date = date,
        hoursWorked = hours ?: 0.0,
        breakMinutes = effectiveBreak ?: 0,
        wageType = wageType,
        wageRate = rate ?: 0.0,
        overtimeThresholdHours = threshold ?: settings.defaultOvertimeThreshold,
        overtimeMultiplier = multiplier ?: settings.defaultOvertimeMultiplier,
        note = note.trim(),
        isPaid = isPaid,
        workplace = workplace.trim().ifEmpty { "Main Workplace" },
        updatedAt = initialLog?.updatedAt ?: System.currentTimeMillis()
    )

    fun trySave() {
        attemptedSave = true
        if (payDetailsInvalid) showPayDetails = true // never hide the field that has the error
        if (valid) onSave(draft)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close without saving") }
                    },
                    title = { Text(if (isEdit) "Edit shift" else "Log a shift") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                            ) {
                                Text("You'll earn", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyUtils.formatCurrency(draft.totalPay, currency),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR)
                                )
                                if (draft.overtimeHours > 0) {
                                    Text(
                                        "Includes ${draft.overtimeHours.asHours()} overtime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = ::trySave,
                                shape = ControlShape,
                                modifier = Modifier.heightIn(min = ButtonMinHeight)
                            ) { Text(if (isEdit) "Save changes" else "Save shift") }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // ---- Date
                Field("Date") {
                    val today = DateUtils.todayString()
                    val yesterday = DateUtils.formatDate(LocalDate.now().minusDays(1))
                    val other = date != today && date != yesterday
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(selected = date == today, onClick = { date = today }, label = { Text("Today") })
                        FilterChip(selected = date == yesterday, onClick = { date = yesterday }, label = { Text("Yesterday") })
                        FilterChip(
                            selected = other,
                            onClick = { showDatePicker = true },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) },
                            label = { Text(if (other) DateUtils.formatDisplayDate(date) else "Other date") }
                        )
                    }
                }

                // ---- Workplace
                Field("Workplace") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        (settings.workplaces + workplace).distinct().forEach { wp ->
                            FilterChip(selected = workplace == wp, onClick = { workplace = wp }, label = { Text(wp) })
                        }
                        FilterChip(
                            selected = false,
                            onClick = { showAddWorkplace = true },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                            label = { Text("Add new") }
                        )
                    }
                }

                // ---- Hours (one field + big stepper buttons)
                Field("Hours worked") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilledTonalIconButton(
                            onClick = { hoursInput = max(0.0, (hours ?: 0.0) - 0.5).asPlain() },
                            modifier = Modifier.size(52.dp)
                        ) { Icon(Icons.Default.Remove, contentDescription = "Half an hour less") }
                        NumberField(
                            value = hoursInput, onChange = { hoursInput = it },
                            label = "Hours", suffix = "h",
                            error = if (attemptedSave || (hours != null && hoursError != null)) hoursError else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            onClick = { hoursInput = minOf(24.0, (hours ?: 0.0) + 0.5).asPlain() },
                            modifier = Modifier.size(52.dp)
                        ) { Icon(Icons.Default.Add, contentDescription = "Half an hour more") }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(4.0, 6.0, 8.0, 10.0).forEach { h ->
                            FilterChip(selected = hours == h, onClick = { hoursInput = h.asPlain() }, label = { Text(h.asHours()) })
                        }
                    }
                }

                // ---- Break
                Field("Unpaid break") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        BREAK_PRESETS.forEach { m ->
                            FilterChip(
                                selected = !customBreak && breakMin == m,
                                onClick = { customBreak = false; breakMin = m },
                                label = { Text(if (m == 0) "No break" else "$m min") }
                            )
                        }
                        FilterChip(selected = customBreak, onClick = { customBreak = true }, label = { Text("Other") })
                    }
                    if (customBreak) {
                        Spacer(Modifier.height(Spacing.sm))
                        NumberField(
                            value = breakInput, onChange = { breakInput = it.filter(Char::isDigit).take(3) },
                            label = "Break length", suffix = "min",
                            keyboardType = KeyboardType.Number,
                            error = breakError,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (breakError != null) {
                        Text(breakError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // ---- Paid
                AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    SwitchRow(
                        icon = Icons.Outlined.Payments,
                        title = "I've been paid for this",
                        subtitle = if (isPaid) "Marked as paid" else "Marked as unpaid",
                        checked = isPaid,
                        onCheckedChange = { isPaid = it }
                    )
                }

                // ---- Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Note (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = ControlShape,
                    modifier = Modifier.fillMaxWidth()
                )

                // ---- Pay details (collapsed)
                AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = if (showPayDetails) "Hide pay details" else "Show pay details"
                            ) { showPayDetails = !showPayDetails }
                            .semantics { stateDescription = if (showPayDetails) "Expanded" else "Collapsed" }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Pay details", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${CurrencyUtils.formatCurrency(rate ?: 0.0, currency)} per ${if (wageType == WageType.HOURLY) "hour" else "day"} · " +
                                    "overtime after ${(threshold ?: 0.0).asHours()} at ${(multiplier ?: 0.0).asPlain()}×",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (attemptedSave && payDetailsInvalid) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(if (showPayDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    AnimatedVisibility(visible = showPayDetails) {
                        Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = wageType == WageType.HOURLY,
                                    onClick = { wageTypeName = WageType.HOURLY.name },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                                ) { Text("Per hour") }
                                SegmentedButton(
                                    selected = wageType == WageType.DAILY,
                                    onClick = { wageTypeName = WageType.DAILY.name },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                                ) { Text("Per day") }
                            }
                            Spacer(Modifier.height(Spacing.md))
                            NumberField(
                                value = rateInput, onChange = { rateInput = it },
                                label = if (wageType == WageType.HOURLY) "Hourly rate" else "Daily rate",
                                prefix = currency, error = rateError,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            NumberField(
                                value = thresholdInput, onChange = { thresholdInput = it },
                                label = "Overtime starts after", suffix = "h", error = thresholdError,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            NumberField(
                                value = multiplierInput, onChange = { multiplierInput = it },
                                label = "Overtime pay rate", suffix = "×", error = multiplierError,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (isEdit && onDelete != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
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
            validate = { if (it.isBlank()) "Enter a name" else if (it.length > 40) "Use 40 characters or fewer" else null },
            onConfirm = { workplace = it },
            onDismiss = { showAddWorkplace = false }
        )
    }

    if (confirmDelete && initialLog != null && onDelete != null) {
        ConfirmDialog(
            title = "Delete this shift?",
            message = "It will be removed from all your devices.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(initialLog.id) },
            onDismiss = { confirmDelete = false }
        )
    }
}

private val BREAK_PRESETS = listOf(0, 15, 30, 60)

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Spacing.sm).semantics { heading() }
        )
        content()
    }
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
