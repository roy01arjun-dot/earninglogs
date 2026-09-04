package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppColors
import com.example.data.ShiftTimerState
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.ui.MonthlyPayrollSummary
import com.example.ui.WeeklyPayrollSummary
import com.example.ui.WorkplaceSummary
import com.example.ui.components.MonthlyChartCanvas
import com.example.ui.components.ShiftTimerCard
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(
    monthlySummary: MonthlyPayrollSummary,
    weeklySummary: WeeklyPayrollSummary? = null,
    workplaceBreakdown: List<WorkplaceSummary>,
    selectedWorkplaceFilter: String,
    selectedMonth: YearMonth,
    userSettings: UserSettings,
    onSelectWorkplaceFilter: (String) -> Unit,
    onAddWorkplace: (String) -> Unit,
    onRenameWorkplace: (String, String) -> Unit = { _, _ -> },
    onNavigateMonth: (Long) -> Unit,
    onJumpToCurrentMonth: () -> Unit,
    onAddLogClick: () -> Unit,
    onEditLogClick: (WorkLogEntity) -> Unit,
    onDeleteLogClick: (Int) -> Unit,
    onTogglePaidClick: (WorkLogEntity) -> Unit,
    onPopulateSampleClick: () -> Unit,
    onViewAllShiftsClick: () -> Unit = {},
    onViewWeeklyTabClick: () -> Unit = {},
    timerState: ShiftTimerState? = null,
    currentTick: Long = System.currentTimeMillis(),
    onStartTimer: (String) -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onResumeTimer: () -> Unit = {},
    onResetTimer: () -> Unit = {},
    onEndTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddWorkplaceDialog by remember { mutableStateOf(false) }
    var newWorkplaceInput by remember { mutableStateOf("") }

    var workplaceToRename by remember { mutableStateOf<String?>(null) }
    var renameWorkplaceInput by remember { mutableStateOf("") }

    // Segmented tab for Analytics (0: Weekly Chart, 1: Workplace Share)
    var selectedInsightTab by remember { mutableIntStateOf(0) }

    val currency = userSettings.currencySymbol
    val target = userSettings.monthlyTargetEarnings
    val totalEarnings = monthlySummary.totalEarnings
    val progressRatio = if (target > 0) (totalEarnings / target).toFloat().coerceIn(0f, 1f) else 0f
    val progressPercent = (progressRatio * 100).toInt()

    val currentYearMonth = YearMonth.now()
    val isCurrentMonth = selectedMonth == currentYearMonth

    val recentLogs = remember(monthlySummary.logs) {
        monthlySummary.logs.sortedByDescending { it.date }.take(3)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sleek Month Selector
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = AppColors.card.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, AppColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onNavigateMonth(-1) },
                        modifier = Modifier.size(36.dp).testTag("prev_month_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Month",
                            tint = AppColors.textPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = DateUtils.formatMonthYear(selectedMonth),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isCurrentMonth) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AppColors.accent,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable { onJumpToCurrentMonth() }
                            ) {
                                Text(
                                    text = "Current",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onNavigateMonth(1) },
                            modifier = Modifier.size(36.dp).testTag("next_month_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Month",
                                tint = AppColors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // 2. Work Shift Timer Card (Live tracking & automatic total hour calculation)
        if (timerState != null) {
            item {
                ShiftTimerCard(
                    timerState = timerState,
                    currentTick = currentTick,
                    workplaces = userSettings.workplaces,
                    onStartTimer = onStartTimer,
                    onPauseTimer = onPauseTimer,
                    onResumeTimer = onResumeTimer,
                    onResetTimer = onResetTimer,
                    onEndTimer = onEndTimer
                )
            }
        }

        // 3. CURRENT WEEKLY EARNINGS CARD
        if (weeklySummary != null) {
            item {
                CurrentWeeklyEarningsCard(
                    weeklySummary = weeklySummary,
                    currency = currency,
                    onViewWeeklyDetails = onViewWeeklyTabClick
                )
            }
        }

        // 3. MONTHLY PAYROLL PROGRESS CARD
        item {
            MonthlyPayrollProgressCard(
                monthlySummary = monthlySummary,
                target = target,
                progressRatio = progressRatio,
                progressPercent = progressPercent,
                currency = currency,
                selectedWorkplaceFilter = selectedWorkplaceFilter,
                onClearFilter = { onSelectWorkplaceFilter("ALL") }
            )
        }

        // 4. THREE COMPACT METRIC CARDS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hours Card
                MiniMetricCard(
                    title = "Hours",
                    value = "${String.format("%.1f", monthlySummary.totalHours)}h",
                    subtitle = if (monthlySummary.overtimeHours > 0) "+${String.format("%.1f", monthlySummary.overtimeHours)}h OT" else "Regular",
                    icon = Icons.Default.AccessTime,
                    modifier = Modifier.weight(1f)
                )

                // Days Card
                MiniMetricCard(
                    title = "Shifts",
                    value = "${monthlySummary.daysWorked}",
                    subtitle = "${monthlySummary.logs.size} entries",
                    icon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f)
                )

                // Daily Average
                MiniMetricCard(
                    title = "Avg / Day",
                    value = CurrencyUtils.formatCurrency(monthlySummary.averageEarningsPerDay, currency),
                    subtitle = "Per worked day",
                    icon = Icons.Default.Paid,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. WORKPLACE FILTER CHIPS ROW (Quick 1-tap filtering)
        if (userSettings.workplaces.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedWorkplaceFilter == "ALL",
                            onClick = { onSelectWorkplaceFilter("ALL") },
                            label = { Text("All Workplaces", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.accent,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.card,
                                labelColor = AppColors.textPrimary
                            ),
                            border = BorderStroke(1.dp, AppColors.cardBorder)
                        )
                    }

                    items(userSettings.workplaces) { wp ->
                        FilterChip(
                            selected = selectedWorkplaceFilter == wp,
                            onClick = { onSelectWorkplaceFilter(wp) },
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
                            onClick = { showAddWorkplaceDialog = true },
                            label = { Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = AppColors.card.copy(alpha = 0.5f),
                                labelColor = AppColors.accent
                            ),
                            border = BorderStroke(1.dp, AppColors.cardBorder)
                        )
                    }
                }
            }
        }

        // 5. INSIGHTS SECTION: SEGMENTED TABS (Weekly Trend vs Workplace Breakdown)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, AppColors.cardBorder),
                colors = CardDefaults.cardColors(containerColor = AppColors.card)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Clean Segmented Tabs
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Tab 0: Weekly Trend
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedInsightTab = 0 },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedInsightTab == 0) AppColors.accent else Color.Transparent
                            ) {
                                Text(
                                    text = "Weekly Trend",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedInsightTab == 0) Color.White else AppColors.textSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            // Tab 1: Workplace Share
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedInsightTab = 1 },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedInsightTab == 1) AppColors.accent else Color.Transparent
                            ) {
                                Text(
                                    text = "Workplaces (${workplaceBreakdown.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedInsightTab == 1) Color.White else AppColors.textSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (selectedInsightTab == 0) {
                        // Weekly Chart Tab
                        if (monthlySummary.logs.isNotEmpty()) {
                            MonthlyChartCanvas(
                                weeklySummaries = monthlySummary.weeklyBreakdown,
                                currencySymbol = currency
                            )
                        } else {
                            EmptyLogsPrompt(
                                selectedMonth = selectedMonth,
                                onAddLogClick = onAddLogClick,
                                onPopulateSampleClick = onPopulateSampleClick
                            )
                        }
                    } else {
                        // Workplace Breakdown Tab
                        if (workplaceBreakdown.isEmpty()) {
                            Text(
                                text = "No shift data recorded for any workplace this month.",
                                fontSize = 13.sp,
                                color = AppColors.textSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                workplaceBreakdown.forEach { wp ->
                                    WorkplaceBreakdownItem(
                                        wp = wp,
                                        currency = currency,
                                        onRenameClick = {
                                            workplaceToRename = wp.name
                                            renameWorkplaceInput = wp.name
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. RECENT SHIFTS SECTION (Top 3 recent shifts + quick "View All" action)
        if (recentLogs.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Shifts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )

                    TextButton(
                        onClick = onViewAllShiftsClick,
                        modifier = Modifier.testTag("view_all_shifts_btn")
                    ) {
                        Text(
                            text = "View All (${monthlySummary.logs.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.accent
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            items(recentLogs, key = { it.id }) { log ->
                CleanShiftCard(
                    log = log,
                    currency = currency,
                    onTogglePaidClick = { onTogglePaidClick(log) },
                    onEditClick = { onEditLogClick(log) },
                    onDeleteClick = { onDeleteLogClick(log.id) }
                )
            }
        } else {
            // Friendly Empty State when no entries exist at all
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, AppColors.cardBorder),
                    colors = CardDefaults.cardColors(containerColor = AppColors.card)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AppColors.accent.copy(alpha = 0.15f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddChart,
                                    contentDescription = null,
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Shifts in ${DateUtils.formatMonthYear(selectedMonth)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Log your working hours to see real-time earnings, overtime pay, and weekly charts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onPopulateSampleClick,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AppColors.accent)
                            ) {
                                Text("Fill Sample Shifts", color = AppColors.accent, fontSize = 12.sp)
                            }
                            Button(
                                onClick = onAddLogClick,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Shift", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    // Add Workplace Dialog
    if (showAddWorkplaceDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddWorkplaceDialog = false
                newWorkplaceInput = ""
            },
            title = { Text("Add New Workplace", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter workplace or company name:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newWorkplaceInput,
                        onValueChange = { newWorkplaceInput = it },
                        placeholder = { Text("e.g. Design Studio, Store, Freelance") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newWorkplaceInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddWorkplace(trimmed)
                            onSelectWorkplaceFilter(trimmed)
                        }
                        showAddWorkplaceDialog = false
                        newWorkplaceInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                ) {
                    Text("Add & Select")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddWorkplaceDialog = false
                        newWorkplaceInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Workplace Dialog
    if (workplaceToRename != null) {
        AlertDialog(
            onDismissRequest = { workplaceToRename = null },
            title = { Text("Rename Workplace", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Update workplace label:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameWorkplaceInput,
                        onValueChange = { renameWorkplaceInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val oldName = workplaceToRename
                        val newName = renameWorkplaceInput.trim()
                        if (oldName != null && newName.isNotEmpty()) {
                            onRenameWorkplace(oldName, newName)
                        }
                        workplaceToRename = null
                    },
                    enabled = renameWorkplaceInput.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { workplaceToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MiniMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppColors.cardBorder),
        colors = CardDefaults.cardColors(containerColor = AppColors.card)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.textSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.accent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = AppColors.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WorkplaceBreakdownItem(
    wp: WorkplaceSummary,
    currency: String,
    onRenameClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = AppColors.tagContainer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = AppColors.onTagContainer,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = wp.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.textPrimary
                            )
                            IconButton(
                                onClick = onRenameClick,
                                modifier = Modifier.size(18.dp).padding(start = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    tint = AppColors.textSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Text(
                            text = "${wp.shiftCount} shifts â€¢ ${String.format("%.1f", wp.totalHours)}h",
                            fontSize = 11.sp,
                            color = AppColors.textSecondary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyUtils.formatCurrency(wp.totalEarnings, currency),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.hero
                    )
                    Text(
                        text = "${(wp.sharePercentage * 100).toInt()}% share",
                        fontSize = 10.sp,
                        color = AppColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { wp.sharePercentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = AppColors.accent,
                trackColor = AppColors.cardBorder
            )
        }
    }
}

@Composable
fun CleanShiftCard(
    log: WorkLogEntity,
    currency: String,
    onTogglePaidClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppColors.cardBorder),
        colors = CardDefaults.cardColors(containerColor = AppColors.card)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Date & Workplace tag
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = DateUtils.formatDisplayDate(log.date),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppColors.tagContainer
                        ) {
                            Text(
                                text = log.workplace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.onTagContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${String.format("%.1f", log.netHoursWorked)}h" +
                                if (log.overtimeHours > 0) " (+${String.format("%.1f", log.overtimeHours)}h OT)" else "" +
                                if (log.breakMinutes > 0) " â€¢ ${log.breakMinutes}m break" else "",
                        fontSize = 11.sp,
                        color = AppColors.textSecondary
                    )
                }

                // Right: Amount & 1-Tap Paid/Unpaid Pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyUtils.formatCurrency(log.totalPay, currency),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.hero
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // 1-Tap Toggle Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (log.isPaid) AppColors.paidContainer else AppColors.unpaidContainer,
                        modifier = Modifier.clickable { onTogglePaidClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (log.isPaid) Icons.Default.CheckCircle else Icons.Default.PendingActions,
                                contentDescription = null,
                                tint = if (log.isPaid) AppColors.onPaidContainer else AppColors.unpaid,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (log.isPaid) "PAID" else "UNPAID",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (log.isPaid) AppColors.onPaidContainer else AppColors.unpaid
                            )
                        }
                    }
                }
            }

            if (log.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.background.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log.note,
                        fontSize = 11.sp,
                        color = AppColors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsPrompt(
    selectedMonth: YearMonth,
    onAddLogClick: () -> Unit,
    onPopulateSampleClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No shifts logged for ${DateUtils.formatMonthYear(selectedMonth)} yet.",
            fontSize = 12.sp,
            color = AppColors.textSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPopulateSampleClick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.accent)
            ) {
                Text("Fill Sample", fontSize = 11.sp, color = AppColors.accent)
            }
            Button(
                onClick = onAddLogClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
            ) {
                Text("Log Shift", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun CurrentWeeklyEarningsCard(
    weeklySummary: WeeklyPayrollSummary,
    currency: String,
    onViewWeeklyDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_earnings_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        border = BorderStroke(1.dp, AppColors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header Row: Badge & Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.accent.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Current Week (${DateUtils.formatShortDate(DateUtils.formatDate(weeklySummary.startOfWeek))} â€“ ${DateUtils.formatShortDate(DateUtils.formatDate(weeklySummary.endOfWeek))})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.accent
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.background,
                    modifier = Modifier.clickable { onViewWeeklyDetails() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Details",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "View Weekly Tab",
                            tint = AppColors.textPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gross Earnings & Days Worked Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Weekly Earnings",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyUtils.formatCurrency(weeklySummary.totalEarnings, currency),
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.background
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${weeklySummary.daysWorked} days worked",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Text(
                            text = "${String.format("%.1f", weeklySummary.totalHours)} total hrs",
                            fontSize = 11.sp,
                            color = AppColors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Split metrics: Regular, Overtime, Paid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Regular Pay Pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.background
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "REGULAR",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textSecondary
                        )
                        Text(
                            text = CurrencyUtils.formatCurrency(weeklySummary.regularPay, currency),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Text(
                            text = "${String.format("%.1f", weeklySummary.regularHours)} hrs",
                            fontSize = 10.sp,
                            color = AppColors.textSecondary
                        )
                    }
                }

                // Overtime Pay Pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (weeklySummary.overtimeHours > 0) AppColors.unpaidContainer else AppColors.background
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "OVERTIME",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (weeklySummary.overtimeHours > 0) AppColors.onUnpaidContainer else AppColors.textSecondary
                        )
                        Text(
                            text = CurrencyUtils.formatCurrency(weeklySummary.overtimePay, currency),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (weeklySummary.overtimeHours > 0) AppColors.onUnpaidContainer else AppColors.textPrimary
                        )
                        Text(
                            text = "${String.format("%.1f", weeklySummary.overtimeHours)} hrs OT",
                            fontSize = 10.sp,
                            color = if (weeklySummary.overtimeHours > 0) AppColors.onUnpaidContainer.copy(alpha = 0.8f) else AppColors.textSecondary
                        )
                    }
                }

                // Paid Pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.paidContainer
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "PAID",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.onPaidContainer
                        )
                        Text(
                            text = CurrencyUtils.formatCurrency(weeklySummary.paidEarnings, currency),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.onPaidContainer
                        )
                        Text(
                            text = if (weeklySummary.unpaidEarnings > 0) "Pending: ${CurrencyUtils.formatCurrency(weeklySummary.unpaidEarnings, currency)}" else "Fully Settled",
                            fontSize = 10.sp,
                            color = AppColors.onPaidContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Day-of-week active shift dots (Mon - Sun)
            val weekDays = remember(weeklySummary.startOfWeek) {
                DateUtils.getDaysInWeek(weeklySummary.startOfWeek)
            }
            val logsByDate = remember(weeklySummary.logs) {
                weeklySummary.logs.groupBy { it.date }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEach { date ->
                    val dateStr = DateUtils.formatDate(date)
                    val dayLogs = logsByDate[dateStr] ?: emptyList()
                    val hasShift = dayLogs.isNotEmpty()
                    val isToday = date == LocalDate.now()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Text(
                            text = DateUtils.getDayOfWeekShort(date).take(1),
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isToday) AppColors.accent else AppColors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = when {
                                hasShift -> AppColors.accent
                                isToday -> AppColors.accent.copy(alpha = 0.15f)
                                else -> AppColors.background
                            },
                            border = if (isToday && !hasShift) BorderStroke(1.dp, AppColors.accent) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (hasShift) {
                                    Text(
                                        text = "${dayLogs.sumOf { it.netHoursWorked }.toInt()}h",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = "${date.dayOfMonth}",
                                        fontSize = 9.sp,
                                        color = if (isToday) AppColors.accent else AppColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyPayrollProgressCard(
    monthlySummary: MonthlyPayrollSummary,
    target: Double,
    progressRatio: Float,
    progressPercent: Int,
    currency: String,
    selectedWorkplaceFilter: String,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_earnings_card"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.hero),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Monthly Payroll Progress",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (selectedWorkplaceFilter != "ALL") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.clickable { onClearFilter() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedWorkplaceFilter,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear filter",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Large Total Amount
            Text(
                text = CurrencyUtils.formatCurrency(monthlySummary.totalEarnings, currency),
                fontSize = 34.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Paid vs Pending Side-by-Side Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Paid Badge
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "PAID",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = CurrencyUtils.formatCurrency(monthlySummary.paidEarnings, currency),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Pending Badge
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "PENDING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = CurrencyUtils.formatCurrency(monthlySummary.unpaidEarnings, currency),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PendingActions,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (target > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Goal: ${CurrencyUtils.formatCurrency(target, currency)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    val remaining = target - monthlySummary.totalEarnings
                    Text(
                        text = if (remaining > 0) "$progressPercent% (${CurrencyUtils.formatCurrency(remaining, currency)} left)" else "Goal Reached! ðŸŽ‰",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}
