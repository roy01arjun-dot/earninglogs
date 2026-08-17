package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.WeeklyPayrollSummary
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

data class ChartBarData(
    val label: String,
    val regularPay: Double,
    val overtimePay: Double,
    val totalPay: Double
)

@Composable
fun MonthlyChartCanvas(
    weeklySummaries: List<WeeklyPayrollSummary>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    if (weeklySummaries.isEmpty()) return

    val chartData = remember(weeklySummaries) {
        weeklySummaries.mapIndexed { index, summary ->
            ChartBarData(
                label = "Wk ${index + 1}",
                regularPay = summary.regularPay,
                overtimePay = summary.overtimePay,
                totalPay = summary.totalEarnings
            )
        }
    }

    val maxPay = remember(chartData) {
        val maxVal = chartData.maxOfOrNull { it.totalPay } ?: 100.0
        if (maxVal <= 0) 100.0 else maxVal * 1.15
    }

    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(weeklySummaries) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    val regularColor = MaterialTheme.colorScheme.primary
    val overtimeColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant

    val density = LocalDensity.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Chart Title & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Earnings Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(regularColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Base", style = MaterialTheme.typography.labelSmall)

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(overtimeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Overtime", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tooltip preview if bar tapped
            selectedBarIndex?.let { index ->
                if (index in chartData.indices) {
                    val data = chartData[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${data.label}: ${CurrencyUtils.formatCurrency(data.totalPay, currencySymbol)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "(Base: ${CurrencyUtils.formatCurrency(data.regularPay, currencySymbol)} + OT: ${CurrencyUtils.formatCurrency(data.overtimePay, currencySymbol)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(chartData) {
                            detectTapGestures { tapOffset ->
                                val width = size.width
                                val leftPadding = with(density) { 50.dp.toPx() }
                                val rightPadding = with(density) { 16.dp.toPx() }
                                val availableWidth = width - leftPadding - rightPadding
                                val barWidthSpace = availableWidth / chartData.size

                                val x = tapOffset.x - leftPadding
                                if (x >= 0 && x <= availableWidth) {
                                    val index = (x / barWidthSpace).toInt().coerceIn(0, chartData.size - 1)
                                    selectedBarIndex = if (selectedBarIndex == index) null else index
                                }
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    val leftPadding = 50.dp.toPx()
                    val bottomPadding = 24.dp.toPx()
                    val topPadding = 12.dp.toPx()
                    val rightPadding = 16.dp.toPx()

                    val chartHeight = height - bottomPadding - topPadding
                    val chartWidth = width - leftPadding - rightPadding

                    // Draw Horizontal Gridlines (4 lines)
                    val gridSteps = 3
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                    for (i in 0..gridSteps) {
                        val y = topPadding + (chartHeight / gridSteps) * i
                        val value = maxPay - (maxPay / gridSteps) * i

                        drawLine(
                            color = gridColor,
                            start = Offset(leftPadding, y),
                            end = Offset(width - rightPadding, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )

                        // Draw Y-axis text
                        val labelText = CurrencyUtils.formatCurrency(value, currencySymbol)
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            leftPadding - 8.dp.toPx(),
                            y + 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = textPaintColor.toArgb()
                                textSize = 9.sp.toPx()
                                textAlign = android.graphics.Paint.Align.RIGHT
                            }
                        )
                    }

                    // Draw Bars
                    val numBars = chartData.size
                    val barSpacing = chartWidth / numBars
                    val maxBarWidth = 28.dp.toPx()
                    val barWidth = minOf(barSpacing * 0.55f, maxBarWidth)

                    chartData.forEachIndexed { index, bar ->
                        val centerX = leftPadding + (index + 0.5f) * barSpacing
                        val barLeft = centerX - barWidth / 2

                        val totalPayAnimated = bar.totalPay * animationProgress.value
                        val regPayAnimated = bar.regularPay * animationProgress.value
                        val otPayAnimated = bar.overtimePay * animationProgress.value

                        val totalBarHeight = (totalPayAnimated / maxPay).toFloat() * chartHeight
                        val regBarHeight = (regPayAnimated / maxPay).toFloat() * chartHeight
                        val otBarHeight = (otPayAnimated / maxPay).toFloat() * chartHeight

                        val bottomY = topPadding + chartHeight

                        // Regular Pay Bar (Bottom part)
                        if (regBarHeight > 0) {
                            drawRoundRect(
                                color = if (selectedBarIndex == index) regularColor else regularColor.copy(alpha = 0.85f),
                                topLeft = Offset(barLeft, bottomY - regBarHeight),
                                size = Size(barWidth, regBarHeight),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }

                        // Overtime Pay Bar (Stacked on top)
                        if (otBarHeight > 0) {
                            drawRoundRect(
                                color = if (selectedBarIndex == index) overtimeColor else overtimeColor.copy(alpha = 0.9f),
                                topLeft = Offset(barLeft, bottomY - regBarHeight - otBarHeight),
                                size = Size(barWidth, otBarHeight),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }

                        // X Axis Label
                        drawContext.canvas.nativeCanvas.drawText(
                            bar.label,
                            centerX,
                            height - 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = textPaintColor.toArgb()
                                textSize = 11.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = (selectedBarIndex == index)
                            }
                        )
                    }
                }
            }
        }
    }
}
