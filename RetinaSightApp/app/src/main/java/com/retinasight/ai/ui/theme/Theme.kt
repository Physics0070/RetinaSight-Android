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
 * Dynamic colour is deliberately NOT used - the severity scale must mean the
 * same thing on every phone, and a wallpaper-derived palette would break that.
 */
private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    secondary = Teal500,
    onSecondary = Color.White,
    background = Surface,
    onBackground = OnSurface,
    surface = Color.White,
    onSurface = OnSurface,
    outline = Outline
)

private val DarkColors = darkColorScheme(
    primary = Teal100,
    onPrimary = Teal900,
    primaryContainer = Teal900,
    onPrimaryContainer = Teal100,
    secondary = Teal500,
    onSecondary = Color.Black,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = Color(0xFF1C1B1F),
    onSurface = OnSurfaceDark,
    outline = Outline
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
