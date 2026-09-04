package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShiftTimerState
import com.example.ui.theme.AppColors

@Composable
fun ShiftTimerCard(
    timerState: ShiftTimerState,
    currentTick: Long,
    workplaces: List<String>,
    onStartTimer: (String) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onEndTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedWorkplace by remember(timerState.workplace, workplaces) {
        mutableStateOf(
            if (timerState.workplace.isNotBlank()) timerState.workplace
            else workplaces.firstOrNull() ?: "Main Workplace"
        )
    }

    var showResetDialog by remember { mutableStateOf(false) }

    val isRunningOrPaused = timerState.isRunning
    val isPaused = timerState.isPaused
    val elapsedMillis = timerState.currentElapsedMillis(currentTick)
    val elapsedHours = timerState.currentElapsedHours(currentTick)
    val formattedDuration = timerState.formattedDuration(currentTick)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shift_timer_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        border = BorderStroke(
            width = if (isRunningOrPaused && !isPaused) 1.5.dp else 1.dp,
            color = if (isRunningOrPaused && !isPaused) AppColors.accent else AppColors.cardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Title, Icon Badge, and Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRunningOrPaused && !isPaused) {
                            AppColors.accent.copy(alpha = 0.18f)
                        } else {
                            AppColors.tagContainer
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isRunningOrPaused && !isPaused) Icons.Default.HourglassBottom else Icons.Default.Timer,
                                contentDescription = "Timer icon",
                                tint = if (isRunningOrPaused && !isPaused) AppColors.accent else AppColors.onTagContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Work Shift Timer",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppColors.textPrimary
                        )
                        Text(
                            text = if (isRunningOrPaused) {
                                if (isPaused) "Timer is paused" else "Active shift in progress"
                            } else {
                                "Track hours & log automatically"
                            },
                            fontSize = 11.sp,
                            color = AppColors.textSecondary
                        )
                    }
                }

                // Live status badge
                if (isRunningOrPaused) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPaused) AppColors.unpaidContainer else AppColors.paidContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isPaused) AppColors.unpaid else AppColors.onPaidContainer)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isPaused) "PAUSED" else "TRACKING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isPaused) AppColors.unpaid else AppColors.onPaidContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isRunningOrPaused) {
                // IDLE STATE: Choose workplace & Start Button
                Text(
                    text = "Select Workplace:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val availableWorkplaces = remember(workplaces) {
                    workplaces.ifEmpty { listOf("Main Workplace") }
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
                                containerColor = AppColors.card.copy(alpha = 0.6f),
                                labelColor = AppColors.textPrimary
                            ),
                            border = BorderStroke(1.dp, AppColors.cardBorder)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onStartTimer(selectedWorkplace) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_shift_timer_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Work Timer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // ACTIVE STATE: Big digital clock, calculated hours, control buttons
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.background.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, AppColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Digital Clock display
                        Text(
                            text = formattedDuration,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color = if (isPaused) AppColors.textSecondary else AppColors.textPrimary,
                            modifier = Modifier.testTag("timer_digital_display")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Calculated Hours
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Total: ${String.format(java.util.Locale.US, "%.2f", elapsedHours)} hrs",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.accent
                            )

                            if (timerState.startFormattedTime.isNotBlank()) {
                                Text(
                                    text = " â€¢ Started ${timerState.startFormattedTime}",
                                    fontSize = 12.sp,
                                    color = AppColors.textSecondary
                                )
                            }
                        }

                        if (timerState.workplace.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Workplace: ${timerState.workplace}",
                                fontSize = 11.sp,
                                color = AppColors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Controls Row: Pause/Resume, Reset, End & Log
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset / Discard button
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("timer_reset_btn"),
                        border = BorderStroke(1.dp, AppColors.cardBorder)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Timer",
                            tint = AppColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Pause / Resume button
                    OutlinedButton(
                        onClick = {
                            if (isPaused) onResumeTimer() else onPauseTimer()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("timer_pause_resume_btn"),
                        border = BorderStroke(1.dp, AppColors.cardBorder)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AppColors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPaused) "Resume" else "Pause",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.textPrimary
                        )
                    }

                    // End & Log Shift button (auto calculates total hours)
                    Button(
                        onClick = onEndTimer,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("timer_end_and_log_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End & Log",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset Timer?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will stop and clear the active shift timer without logging it.",
                    fontSize = 13.sp,
                    color = AppColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetTimer()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
