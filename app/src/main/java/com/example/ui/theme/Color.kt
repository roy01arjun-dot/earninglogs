package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand fallback palette
//
// Only used on Android 11 and below, or when the user turns dynamic color off.
// On Android 12+ the scheme is generated from the wallpaper instead, so nothing
// in the UI may reference these constants directly - see AppColors below.
// ---------------------------------------------------------------------------

internal val BrandTerracotta = Color(0xFF9E5326)
internal val BrandTerracottaLight = Color(0xFFD7733B)

// Light scheme
internal val LightPrimary = Color(0xFF9A4522)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFFFDBCD)
internal val LightOnPrimaryContainer = Color(0xFF380D00)
internal val LightSecondary = Color(0xFF8F4C33)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFFFDBCF)
internal val LightOnSecondaryContainer = Color(0xFF3A0B00)
internal val LightTertiary = Color(0xFF4C6B2F)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFCDF2A7)
internal val LightOnTertiaryContainer = Color(0xFF102000)
internal val LightError = Color(0xFFBA1A1A)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)
internal val LightBackground = Color(0xFFFFF8F6)
internal val LightOnBackground = Color(0xFF231A16)
internal val LightSurface = Color(0xFFFFF8F6)
internal val LightOnSurface = Color(0xFF231A16)
internal val LightSurfaceVariant = Color(0xFFF5DED6)
internal val LightOnSurfaceVariant = Color(0xFF53433E)
internal val LightOutline = Color(0xFF85736D)
internal val LightOutlineVariant = Color(0xFFD8C2BB)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFFFF1EC)
internal val LightSurfaceContainer = Color(0xFFFCEBE5)
internal val LightSurfaceContainerHigh = Color(0xFFF6E5E0)
internal val LightSurfaceContainerHighest = Color(0xFFF1E0DA)

// Dark scheme
internal val DarkPrimary = Color(0xFFFFB59B)
internal val DarkOnPrimary = Color(0xFF5B1A00)
internal val DarkPrimaryContainer = Color(0xFF7B2D0B)
internal val DarkOnPrimaryContainer = Color(0xFFFFDBCD)
internal val DarkSecondary = Color(0xFFFFB59B)
internal val DarkOnSecondary = Color(0xFF561F09)
internal val DarkSecondaryContainer = Color(0xFF72351E)
internal val DarkOnSecondaryContainer = Color(0xFFFFDBCF)
internal val DarkTertiary = Color(0xFFB2D68D)
internal val DarkOnTertiary = Color(0xFF203705)
internal val DarkTertiaryContainer = Color(0xFF354E19)
internal val DarkOnTertiaryContainer = Color(0xFFCDF2A7)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
internal val DarkBackground = Color(0xFF1A120E)
internal val DarkOnBackground = Color(0xFFF1DFD9)
internal val DarkSurface = Color(0xFF1A120E)
internal val DarkOnSurface = Color(0xFFF1DFD9)
internal val DarkSurfaceVariant = Color(0xFF53433E)
internal val DarkOnSurfaceVariant = Color(0xFFD8C2BB)
internal val DarkOutline = Color(0xFFA08D86)
internal val DarkOutlineVariant = Color(0xFF53433E)
internal val DarkSurfaceContainerLowest = Color(0xFF140C09)
internal val DarkSurfaceContainerLow = Color(0xFF231A16)
internal val DarkSurfaceContainer = Color(0xFF271E1A)
internal val DarkSurfaceContainerHigh = Color(0xFF322824)
internal val DarkSurfaceContainerHighest = Color(0xFF3D332E)

/**
 * Semantic colors for the app's own concepts.
 *
 * Screens reference these instead of literal colors so that a single theme decision -
 * wallpaper-derived dynamic color, light vs dark - reaches every surface at once. The previous
 * build hardcoded `WarmCreamBackground`, `TerracottaAccent` and friends at ~380 call sites,
 * which is why dark mode and dynamic color could never actually take effect.
 */
object AppColors {

    /** Page canvas. */
    val background: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

    /** Standard card / tile surface. */
    val card: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer

    val cardBorder: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

    /** Text field and chip background. */
    val input: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHighest

    /** The big gross-earnings hero card. Pair with [onHero]. */
    val hero: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    val onHero: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary

    /** FAB, active chips, primary actions. Pair with [onAccent]. */
    val accent: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    val onAccent: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary

    val textPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface

    val textSecondary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** "Paid" pills and positive figures. */
    val paidContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiaryContainer

    val onPaidContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onTertiaryContainer

    val paid: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

    /** "Unpaid" pills and overtime emphasis. */
    val unpaidContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondaryContainer

    val onUnpaidContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSecondaryContainer

    val unpaid: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

    /** Workplace tag pill. */
    val tagContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiaryContainer

    val onTagContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onTertiaryContainer
}
