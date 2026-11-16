package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Status colours that Material's scheme doesn't cover. */
@Immutable
data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val overtime: Color,
    val overtimeContainer: Color,
    val danger: Color,
    val dangerContainer: Color
)

private val LightStatus = StatusColors(
    success = Emerald700, successContainer = Emerald50,
    warning = Amber700, warningContainer = Amber50,
    overtime = Violet600, overtimeContainer = Violet50,
    danger = Red600, dangerContainer = Red50
)

private val DarkStatus = StatusColors(
    success = Emerald300, successContainer = Emerald950,
    warning = Amber300, warningContainer = Amber950,
    overtime = Violet300, overtimeContainer = Violet950,
    danger = Red300, dangerContainer = Red950
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val LightScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo50,
    onPrimaryContainer = Indigo900,
    secondary = Ink700,
    onSecondary = Color.White,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink900,
    tertiary = Violet600,
    onTertiary = Color.White,
    tertiaryContainer = Violet50,
    onTertiaryContainer = Color(0xFF2E1470),
    background = Canvas,
    onBackground = Ink900,
    surface = Paper,
    onSurface = Ink900,
    surfaceVariant = Mist,
    onSurfaceVariant = Ink600,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Mist,
    outline = Line,
    outlineVariant = LineSoft,
    error = Red600,
    onError = Color.White,
    errorContainer = Red50,
    onErrorContainer = Color(0xFF7A1A12),
    inverseSurface = Ink900,
    inverseOnSurface = Snow
)

private val DarkScheme = darkColorScheme(
    primary = Indigo300,
    onPrimary = Color(0xFF0B1452),
    primaryContainer = Indigo800,
    onPrimaryContainer = Color(0xFFE0E6FF),
    secondary = Fog,
    onSecondary = NightCanvas,
    secondaryContainer = NightMist,
    onSecondaryContainer = Snow,
    tertiary = Violet300,
    onTertiary = Color(0xFF240C5E),
    tertiaryContainer = Violet950,
    onTertiaryContainer = Color(0xFFEDE4FF),
    background = NightCanvas,
    onBackground = Snow,
    surface = NightPaper,
    onSurface = Snow,
    surfaceVariant = NightMist,
    onSurfaceVariant = Fog,
    surfaceContainerLowest = NightCanvas,
    surfaceContainerLow = Color(0xFF12161D),
    surfaceContainer = NightPaper,
    surfaceContainerHigh = Color(0xFF1B2029),
    surfaceContainerHighest = NightMist,
    outline = NightLine,
    outlineVariant = NightLineSoft,
    error = Red300,
    onError = Color(0xFF450A06),
    errorContainer = Red950,
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Snow,
    inverseOnSurface = Ink900
)

object AppTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}

@Composable
fun WorkLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
