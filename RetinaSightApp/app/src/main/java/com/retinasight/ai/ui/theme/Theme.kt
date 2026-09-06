package com.retinasight.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Light theme is the primary target: this app is used outdoors in daylight, so
 * a bright, high-contrast surface is the safe default. Dark theme is supported
 * for indoor and night use.
 *
 * The role mapping follows the design study's `ClinicalLightColorScheme`: navy
 * as primary, teal as secondary, cyan reserved for tertiary accents, and the
 * slate ramp across background / surface / outline.
 *
 * Dynamic colour is deliberately NOT used - the severity scale must mean the
 * same thing on every phone, and a wallpaper-derived palette would break that.
 */
private val LightColors = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = CalmingTeal.copy(alpha = 0.12f),
    onPrimaryContainer = DeepNavy,
    secondary = CalmingTeal,
    onSecondary = Color.White,
    secondaryContainer = CalmingTeal.copy(alpha = 0.20f),
    onSecondaryContainer = DeepNavy,
    tertiary = LaserCyan,
    onTertiary = DeepNavy,
    background = MedicalBg,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondary,
    outline = OutlineBorder
)

private val DarkColors = darkColorScheme(
    primary = LaserCyan,
    onPrimary = DeepNavy,
    primaryContainer = CalmingTeal,
    onPrimaryContainer = Color.White,
    secondary = CalmingTeal,
    onSecondary = Color.White,
    tertiary = LaserCyan,
    onTertiary = DeepNavy,
    background = DarkroomBg,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = DarkOutline
)

@Composable
fun RetinaSightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RetinaTypography,
        content = content
    )
}
