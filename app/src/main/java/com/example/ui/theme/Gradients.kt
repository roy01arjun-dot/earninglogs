package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AppGradients {
    val PrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF9E5326), Color(0xFFB8622E))
    )

    val PrimaryHeaderGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF9E5326), Color(0xFFA6592B))
    )

    val EarningsGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFD7733B), Color(0xFFE2844D))
    )

    val OvertimeGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFC05822), Color(0xFFD7733B))
    )

    val PaidGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF425C2B), Color(0xFF5A7B3C))
    )

    val UnpaidGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFC05822), Color(0xFFD7733B))
    )

    val AccentGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFD7733B), Color(0xFFDB7B4B))
    )

    val CardSurfaceGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFEFE3D0), Color(0xFFEADDC7))
    )

    val FABGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFD7733B), Color(0xFFDB7B4B))
    )
}

