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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppColors
import com.example.data.UserSettings
import com.example.data.WorkLogEntity
import com.example.ui.WeeklyPayrollSummary
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.time.LocalDate

@Composable
fun WeeklySummaryScreen(
    weeklySummary: WeeklyPayrollSummary,
    selectedWeekStart: LocalDate,
    userSettings: UserSettings,
    onNavigateWeek: (Long) -> Unit,
    onJumpToCurrentWeek: () -> Unit,
    onAddLogClick: () -> Unit,
    onEditLogClick: (WorkLogEntity) -> Unit,
    onDeleteLogClick: (Int) -> Unit,
    onExportPayrollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = userSettings.currencySymbol
    val weekDays = DateUtils.getDaysInWeek(selectedWeekStart)
    val today = LocalDate.now()
    val isCurrentWeek = selectedWeekStart == DateUtils.getStartOfWeek(today)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Week Navigation Bar
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = AppColors.card.copy(alpha = 0.8f),
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
                        onClick = { onNavigateWeek(-1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Week",
                            tint = AppColors.textPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${DateUtils.formatShortDate(weeklySummary.startOfWeek.toString())} - ${DateUtils.formatShortDate(weeklySummary.endOfWeek.toString())}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Text(
                            text = if (isCurrentWeek) "This Week" else "Selected Week",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.accent
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isCurrentWeek) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AppColors.accent,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable { onJumpToCurrentWeek() }
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
                            onClick = { onNavigateWeek(1) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Week",
                                tint = AppColors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // 2. HERO WEEKLY EARNINGS CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.hero),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                                text = "Weekly Total",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        // Export Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onExportPayrollClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Export",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Export Paystub",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = CurrencyUtils.formatCurrency(weeklySummary.totalEarnings, currency),
                        fontSize = 34.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Regular vs Overtime breakdown row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Base Pay (${String.format("%.1f", weeklySummary.regularHours)}h)",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = CurrencyUtils.formatCurrency(weeklySummary.regularPay, currency),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Overtime Pay (${String.format("%.1f", weeklySummary.overtimeHours)}h)",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = CurrencyUtils.formatCurrency(weeklySummary.overtimePay, currency),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Week at a Glance: Days strip (Mon - Sun)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AppColors.cardBorder),
                colors = CardDefaults.cardColors(containerColor = AppColors.card)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Days This Week (${weeklySummary.daysWorked} worked)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEach { day ->
                            val dayLogs = weeklySummary.logs.filter { it.date == day.toString() }
                            val hasShifts = dayLogs.isNotEmpty()
                            val isDayToday = day == today

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = day.dayOfWeek.name.take(3),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDayToday) AppColors.accent else AppColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        hasShifts -> AppColors.hero
                                        isDayToday -> AppColors.accent.copy(alpha = 0.2f)
                                        else -> AppColors.background
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = day.dayOfMonth.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (hasShifts || isDayToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (hasShifts) Color.White else if (isDayToday) AppColors.accent else AppColors.textSecondary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (hasShifts) "${String.format("%.0f", dayLogs.sumOf { it.netHoursWorked })}h" else "-",
                                    fontSize = 9.sp,
                                    color = if (hasShifts) AppColors.accent else AppColors.textSecondary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Daily Shift Entries for This Week
        if (weeklySummary.logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, AppColors.cardBorder),
                    colors = CardDefaults.cardColors(containerColor = AppColors.card)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Shifts Logged for This Week",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add working hours for this week to calculate weekly payroll.",
                            fontSize = 12.sp,
                            color = AppColors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
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
        } else {
            item {
                Text(
                    text = "Weekly Shift Log (${weeklySummary.logs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(weeklySummary.logs.sortedBy { it.date }, key = { it.id }) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditLogClick(log) },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppColors.cardBorder),
                    colors = CardDefaults.cardColors(containerColor = AppColors.card)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            Text(
                                text = CurrencyUtils.formatCurrency(log.totalPay, currency),
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppColors.hero
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${String.format("%.1f", log.netHoursWorked)}h" +
                                        if (log.overtimeHours > 0) " (+${String.format("%.1f", log.overtimeHours)}h OT)" else "",
                                fontSize = 11.sp,
                                color = AppColors.textSecondary
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onEditLogClick(log) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = AppColors.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteLogClick(log.id) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
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
}
