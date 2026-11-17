package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.math.abs

/** Status colours that Material's scheme doesn't name. `on*` = text/icon colour on the container. */
@Immutable
data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val overtime: Color,
    val overtimeContainer: Color,
    val onOvertimeContainer: Color,
    val danger: Color,
    val dangerContainer: Color
)

/** A colour pair for tinting things like workplaces: container background + readable content. */
@Immutable
data class Accent(val container: Color, val content: Color, val solid: Color)

private val LightStatus = StatusColors(
    success = Teal40, successContainer = Teal90, onSuccessContainer = Teal10,
    warning = Amber40, warningContainer = Amber90, onWarningContainer = Amber10,
    overtime = Coral40, overtimeContainer = Coral90, onOvertimeContainer = Coral10,
    danger = Red40, dangerContainer = Red90
)

private val DarkStatus = StatusColors(
    success = Teal80, successContainer = Teal30, onSuccessContainer = Teal90,
    warning = Amber80, warningContainer = Amber30, onWarningContainer = Amber90,
    overtime = Coral80, overtimeContainer = Coral30, onOvertimeContainer = Coral90,
    danger = Red80, dangerContainer = Red30
)

private val LightAccents = listOf(
    Accent(Violet90, Violet10, Violet40),
    Accent(Teal90, Teal10, Teal40),
    Accent(Coral90, Coral10, Coral40),
    Accent(Amber90, Amber10, Amber40),
    Accent(Color(0xFFD3E4FF), Color(0xFF001C3A), Color(0xFF1F5FA8)),
    Accent(Color(0xFFFFD8EC), Color(0xFF3A0726), Color(0xFFA23673))
)

private val DarkAccents = listOf(
    Accent(Violet30, Violet90, Violet80),
    Accent(Teal30, Teal90, Teal80),
    Accent(Coral30, Coral90, Coral80),
    Accent(Amber30, Amber90, Amber80),
    Accent(Color(0xFF00468A), Color(0xFFD3E4FF), Color(0xFFA3C9FF)),
    Accent(Color(0xFF7D1C57), Color(0xFFFFD8EC), Color(0xFFFFAFD6))
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }
val LocalAccents = staticCompositionLocalOf { LightAccents }

/** The user's workplaces, in order. Gives each one its own colour. */
val LocalWorkplaces = compositionLocalOf<List<String>> { emptyList() }

private val LightScheme = lightColorScheme(
    primary = Violet40, onPrimary = Color.White,
    primaryContainer = Violet90, onPrimaryContainer = Violet10,
    inversePrimary = Violet80,
    secondary = Teal40, onSecondary = Color.White,
    secondaryContainer = Teal90, onSecondaryContainer = Teal10,
    tertiary = Coral40, onTertiary = Color.White,
    tertiaryContainer = Coral90, onTertiaryContainer = Coral10,
    background = N98, onBackground = N10,
    surface = N98, onSurface = N10,
    surfaceVariant = N90, onSurfaceVariant = N30,
    surfaceTint = Violet40,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = N96,
    surfaceContainer = N94,
    surfaceContainerHigh = Color(0xFFEAE6F1),
    surfaceContainerHighest = N90,
    outline = N50, outlineVariant = N80,
    error = Red40, onError = Color.White,
    errorContainer = Red90, onErrorContainer = Red10,
    inverseSurface = N20, inverseOnSurface = N94
)

private val DarkScheme = darkColorScheme(
    primary = Violet80, onPrimary = Violet20,
    primaryContainer = Violet30, onPrimaryContainer = Violet90,
    inversePrimary = Violet40,
    secondary = Teal80, onSecondary = Teal20,
    secondaryContainer = Teal30, onSecondaryContainer = Teal90,
    tertiary = Coral80, onTertiary = Coral20,
    tertiaryContainer = Coral30, onTertiaryContainer = Coral90,
    background = D6, onBackground = N90,
    surface = D6, onSurface = N90,
    surfaceVariant = N30, onSurfaceVariant = N80,
    surfaceTint = Violet80,
    surfaceContainerLowest = D4,
    surfaceContainerLow = D10,
    surfaceContainer = D12,
    surfaceContainerHigh = D17,
    surfaceContainerHighest = D22,
    outline = N60, outlineVariant = N30,
    error = Red80, onError = Color(0xFF690005),
    errorContainer = Red30, onErrorContainer = Red90,
    inverseSurface = N90, inverseOnSurface = N20
)

/** Rounder, friendlier Material 3 shapes. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object AppTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current

    /** Stable colour for a workplace name, so the same workplace always has the same tint. */
    @Composable
    fun accentFor(key: String): Accent {
        val list = LocalAccents.current
        val index = LocalWorkplaces.current.indexOf(key)
        return list[(if (index >= 0) index else abs(key.trim().lowercase().hashCode())) % list.size]
    }

    /** Brand gradient used behind the dashboard hero, sign-in and lock screens. */
    val heroBrush: Brush = Brush.linearGradient(listOf(HeroStart, HeroMid, HeroEnd))
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
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
        LocalAccents provides if (darkTheme) DarkAccents else LightAccents
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography, shapes = AppShapes, content = content)
    }
}
