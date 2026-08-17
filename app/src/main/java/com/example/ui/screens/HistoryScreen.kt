package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppColors
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@Composable
fun HistoryScreen(
    allLogs: List<WorkLogEntity>,
    searchQuery: String,
    userSettings: UserSettings,
    onSearchQueryChange: (String) -> Unit,
    onEditLogClick: (WorkLogEntity) -> Unit,
    onDeleteLogClick: (Int) -> Unit,
    onTogglePaidClick: (WorkLogEntity) -> Unit = {},
    onAddLogClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currency = userSettings.currencySymbol
    var selectedWorkplaceFilter by remember { mutableStateOf("ALL") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "UNPAID", "PAID"

    val filteredLogs = remember(allLogs, searchQuery, selectedWorkplaceFilter, selectedStatusFilter) {
        allLogs.filter { log ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                val q = searchQuery.lowercase().trim()
                log.date.contains(q) ||
                log.note.lowercase().contains(q) ||
                log.workplace.lowercase().contains(q) ||
                DateUtils.formatDisplayDate(log.date).lowercase().contains(q)
            }
            val matchesWorkplace = selectedWorkplaceFilter == "ALL" || log.workplace == selectedWorkplaceFilter
            val matchesStatus = when (selectedStatusFilter) {
                "PAID" -> log.isPaid
                "UNPAID" -> !log.isPaid
                else -> true
            }
            matchesQuery && matchesWorkplace && matchesStatus
        }.sortedByDescending { it.date }
    }

    val totalFilteredEarnings = remember(filteredLogs) {
        filteredLogs.sumOf { it.totalPay }
    }
    val totalFilteredHours = remember(filteredLogs) {
        filteredLogs.sumOf { it.netHoursWorked }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Title & Quick Total Summary Bar
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shift History",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        color = AppColors.textPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.card,
                        border = BorderStroke(1.dp, AppColors.cardBorder)
                    ) {
                        Text(
                            text = "${filteredLogs.size} Shifts",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 2. Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_shifts_input"),
                placeholder = { Text("Search by date, workplace, note...", color = AppColors.textSecondary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppColors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.accent,
                    unfocusedBorderColor = AppColors.cardBorder,
                    focusedContainerColor = AppColors.card.copy(alpha = 0.5f),
                    unfocusedContainerColor = AppColors.card.copy(alpha = 0.5f),
                    focusedTextColor = AppColors.textPrimary,
                    unfocusedTextColor = AppColors.textPrimary
                )
            )
        }

        // 3. Status Filter Pills (All, Unpaid, Paid) & Workplace Pills
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusFilterPill(
                        label = "All Status",
                        selected = selectedStatusFilter == "ALL",
                        onClick = { selectedStatusFilter = "ALL" },
                        modifier = Modifier.weight(1f)
                    )
                    StatusFilterPill(
                        label = "Unpaid",
                        selected = selectedStatusFilter == "UNPAID",
                        onClick = { selectedStatusFilter = "UNPAID" },
                        badgeColor = AppColors.unpaid,
                        modifier = Modifier.weight(1f)
                    )
                    StatusFilterPill(
                        label = "Paid",
                        selected = selectedStatusFilter == "PAID",
                        onClick = { selectedStatusFilter = "PAID" },
                        badgeColor = AppColors.onPaidContainer,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Workplace Row
                if (userSettings.workplaces.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedWorkplaceFilter == "ALL",
                                onClick = { selectedWorkplaceFilter = "ALL" },
                                label = { Text("All Workplaces", fontSize = 11.sp) },
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
                                onClick = { selectedWorkplaceFilter = wp },
                                label = { Text(wp, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
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
                    }
                }
            }
        }

        // 4. Quick Totals Summary Bar for Current Filter
        if (filteredLogs.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.card.copy(alpha = 0.6f),
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
                        Text(
                            text = "${String.format("%.1f", totalFilteredHours)} hours total",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.textSecondary
                        )
                        Text(
                            text = "Total: ${CurrencyUtils.formatCurrency(totalFilteredEarnings, currency)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppColors.hero
                        )
                    }
                }
            }
        }

        // 5. Shift Cards List
        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, AppColors.cardBorder),
                    colors = CardDefaults.cardColors(containerColor = AppColors.card)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AppColors.accent.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (allLogs.isEmpty()) "No Shift Logs Yet" else "No Matching Shifts",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (allLogs.isEmpty())
                                "Start tracking your daily shifts and earnings."
                            else
                                "Try adjusting your search query or filters.",
                            fontSize = 12.sp,
                            color = AppColors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (allLogs.isEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onAddLogClick,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log New Shift")
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                HistoryShiftCard(
                    log = log,
                    currency = currency,
                    onTogglePaidClick = { onTogglePaidClick(log) },
                    onEditClick = { onEditLogClick(log) },
                    onDeleteClick = { onDeleteLogClick(log.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun StatusFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badgeColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AppColors.accent else AppColors.card,
        border = BorderStroke(1.dp, if (selected) AppColors.accent else AppColors.cardBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (badgeColor != null && !selected) {
                Surface(
                    shape = CircleShape,
                    color = badgeColor,
                    modifier = Modifier.size(6.dp)
                ) {}
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else AppColors.textPrimary
            )
        }
    }
}

@Composable
private fun HistoryShiftCard(
    log: WorkLogEntity,
    currency: String,
    onTogglePaidClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppColors.cardBorder),
        colors = CardDefaults.cardColors(containerColor = AppColors.card)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top: Date & Total Pay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateUtils.formatDisplayDate(log.date),
                        fontSize = 15.sp,
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

                Text(
                    text = CurrencyUtils.formatCurrency(log.totalPay, currency),
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.hero
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Details: Hours & OT breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format("%.1f", log.netHoursWorked)}h worked" +
                            if (log.overtimeHours > 0) " (${String.format("%.1f", log.overtimeHours)}h OT)" else "" +
                            if (log.breakMinutes > 0) " â€¢ ${log.breakMinutes}m break" else "",
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )

                Text(
                    text = "${CurrencyUtils.formatCurrency(log.wageRate, currency)}/hr",
                    fontSize = 11.sp,
                    color = AppColors.textSecondary
                )
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

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1-Tap Toggle Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (log.isPaid) AppColors.paidContainer else AppColors.unpaidContainer,
                    modifier = Modifier.clickable { onTogglePaidClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

                // Edit & Delete Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = AppColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
