package com.pengnini.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PurplePrimary = Color(0xFF9C7CFF)
private val PurpleOnPrimary = Color(0xFF1B1033)
private val PurpleContainer = Color(0xFF3F2D7A)
private val CyanSecondary = Color(0xFF5EDFC1)
private val CyanOnSecondary = Color(0xFF003830)
private val DarkBg = Color(0xFF0F0F14)
private val DarkSurface = Color(0xFF181820)
private val DarkSurfaceVariant = Color(0xFF26262E)

private val DarkColors = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleOnPrimary,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    background = DarkBg,
    onBackground = Color(0xFFE6E1E9),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC9C5D0),
    outline = Color(0xFF4A4754),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4DD2),
    onPrimary = Color.White,
    secondary = Color(0xFF1DA98D),
    onSecondary = Color.White,
)

@Composable
fun PengniniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
