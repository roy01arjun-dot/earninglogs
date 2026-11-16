package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.data.WageType
import com.example.ui.components.AppCard
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.NumberField
import com.example.ui.components.SettingRow
import com.example.ui.components.Spacing
import com.example.ui.components.TextInputDialog
import com.example.ui.components.asPlain

private val CURRENCIES = listOf("₩", "$", "€", "£", "¥", "₹", "रू")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    currentMonthLabel: String,
    onBack: () -> Unit,
    onSave: (UserSettings) -> Unit,
    onAddWorkplace: (String) -> Unit,
    onRenameWorkplace: (String, String) -> Unit,
    onRemoveWorkplace: (String) -> Unit,
    onClearToday: () -> Unit,
    onClearMonth: () -> Unit,
    onClearAll: () -> Unit
) {
    var wageType by remember { mutableStateOf(settings.defaultWageType) }
    var rate by remember { mutableStateOf(settings.defaultWageRate.asPlain()) }
    var threshold by remember { mutableStateOf(settings.defaultOvertimeThreshold.asPlain()) }
    var multiplier by remember { mutableStateOf(settings.defaultOvertimeMultiplier.asPlain()) }
    var currency by remember { mutableStateOf(settings.currencySymbol) }
    var goal by remember { mutableStateOf(settings.monthlyTargetEarnings.asPlain(0)) }
    var tax by remember { mutableStateOf(settings.defaultTaxRate.asPlain()) }
    var payPeriod by remember { mutableStateOf(settings.payPeriodType) }

    var addWorkplace by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    var customCurrency by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    fun num(s: String) = s.replace(",", "").trim().toDoubleOrNull()

    val rateV = num(rate)
    val thresholdV = num(threshold)
    val multiplierV = num(multiplier)
    val goalV = num(goal)
    val taxV = num(tax)
    val valid = rateV != null && rateV >= 0 &&
        thresholdV != null && thresholdV in 0.5..24.0 &&
        multiplierV != null && multiplierV in 1.0..5.0 &&
        goalV != null && goalV >= 0 &&
        taxV != null && taxV in 0.0..100.0 &&
        currency.isNotBlank()

    val updated = if (valid) settings.copy(
        defaultWageType = wageType,
        defaultWageRate = rateV!!,
        defaultOvertimeThreshold = thresholdV!!,
        defaultOvertimeMultiplier = multiplierV!!,
        currencySymbol = currency.trim(),
        monthlyTargetEarnings = goalV!!,
        defaultTaxRate = taxV!!,
        payPeriodType = payPeriod
    ) else null
    val dirty = updated != null && !updated.payrollEquals(settings)

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Pay & workplaces") },
                actions = {
                    TextButton(onClick = { updated?.let { onSave(it); onBack() } }, enabled = dirty) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
            Label("Default pay")
            AppCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(wageType == WageType.HOURLY, { wageType = WageType.HOURLY }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Hourly") }
                    SegmentedButton(wageType == WageType.DAILY, { wageType = WageType.DAILY }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Daily") }
                }
                Spacer(Modifier.height(Spacing.md))
                NumberField(
                    rate, { rate = it },
                    label = if (wageType == WageType.HOURLY) "Hourly rate" else "Daily rate",
                    prefix = currency,
                    error = if (rateV == null || rateV < 0) "Enter a valid amount" else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    NumberField(
                        threshold, { threshold = it }, label = "Overtime after", suffix = "h",
                        error = if (thresholdV == null || thresholdV !in 0.5..24.0) "0.5 – 24" else null,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        multiplier, { multiplier = it }, label = "Overtime rate", suffix = "×",
                        error = if (multiplierV == null || multiplierV !in 1.0..5.0) "1 – 5" else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Label("Currency & goals")
            AppCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CURRENCIES.forEach { c ->
                        FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                    }
                    FilterChip(
                        selected = currency !in CURRENCIES,
                        onClick = { customCurrency = true },
                        label = { Text(if (currency !in CURRENCIES) currency else "Other…") }
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    NumberField(
                        goal, { goal = it }, label = "Monthly goal", prefix = currency,
                        error = if (goalV == null || goalV < 0) "Invalid" else null,
                        modifier = Modifier.weight(1.4f)
                    )
                    NumberField(
                        tax, { tax = it }, label = "Tax", suffix = "%",
                        error = if (taxV == null || taxV !in 0.0..100.0) "0 – 100" else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text("Pay period", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.sm))
                val periods = listOf("WEEKLY" to "Weekly", "BIWEEKLY" to "Bi-weekly", "MONTHLY" to "Monthly")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    periods.forEachIndexed { i, (key, label) ->
                        SegmentedButton(payPeriod == key, { payPeriod = key }, SegmentedButtonDefaults.itemShape(i, periods.size)) { Text(label) }
                    }
                }
            }

            Label("Workplaces")
            AppCard(contentPadding = PaddingValues(0.dp)) {
                settings.workplaces.forEachIndexed { i, wp ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(icon = Icons.Outlined.Storefront, title = wp) {
                        IconButton(onClick = { renameTarget = wp }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename $wp") }
                        if (settings.workplaces.size > 1) {
                            IconButton(onClick = { removeTarget = wp }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove $wp", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    icon = Icons.Outlined.Add,
                    title = "Add workplace",
                    tint = MaterialTheme.colorScheme.primary,
                    titleColor = MaterialTheme.colorScheme.primary,
                    onClick = { addWorkplace = true },
                    trailing = {}
                )
            }

            Label("Danger zone")
            AppCard(contentPadding = PaddingValues(0.dp)) {
                SettingRow(Icons.Outlined.Today, "Clear today's shifts", tint = MaterialTheme.colorScheme.error, onClick = { confirm = "today" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.EventBusy, "Clear shifts in $currentMonthLabel", tint = MaterialTheme.colorScheme.error, onClick = { confirm = "month" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    Icons.Outlined.DeleteForever, "Delete all shifts",
                    subtitle = "Also removes them from your synced devices",
                    tint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirm = "all" }
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (addWorkplace) {
        TextInputDialog(
            title = "Add workplace", label = "Name", confirmLabel = "Add",
            validate = { if (it.isBlank()) "Required" else if (it.trim() in settings.workplaces) "Already exists" else null },
            onConfirm = onAddWorkplace, onDismiss = { addWorkplace = false }
        )
    }
    renameTarget?.let { old ->
        TextInputDialog(
            title = "Rename workplace", label = "Name", initial = old,
            validate = { if (it.isBlank()) "Required" else if (it.trim() != old && it.trim() in settings.workplaces) "Already exists" else null },
            onConfirm = { onRenameWorkplace(old, it) }, onDismiss = { renameTarget = null }
        )
    }
    removeTarget?.let { wp ->
        ConfirmDialog(
            title = "Remove $wp?",
            message = "Existing shifts keep their workplace name. It just won't be offered for new shifts.",
            confirmLabel = "Remove", destructive = true,
            onConfirm = { onRemoveWorkplace(wp) }, onDismiss = { removeTarget = null }
        )
    }
    if (customCurrency) {
        TextInputDialog(
            title = "Currency symbol", label = "e.g. NPR, ¥, kr", initial = if (currency in CURRENCIES) "" else currency,
            validate = { if (it.isBlank() || it.length > 5) "1–5 characters" else null },
            onConfirm = { currency = it }, onDismiss = { customCurrency = false }
        )
    }
    when (confirm) {
        "today" -> ConfirmDialog("Clear today's shifts?", "All shifts logged for today will be deleted.", "Clear", onClearToday, { confirm = null }, destructive = true)
        "month" -> ConfirmDialog("Clear $currentMonthLabel?", "All shifts in $currentMonthLabel will be deleted.", "Clear", onClearMonth, { confirm = null }, destructive = true)
        "all" -> ConfirmDialog(
            "Delete all shifts?",
            "Every shift will be permanently deleted on this device and on your synced devices. Export a backup first if unsure.",
            "Delete all", onClearAll, { confirm = null }, destructive = true
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm)
    )
}
