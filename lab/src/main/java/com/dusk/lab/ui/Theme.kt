package com.dusk.lab.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFE39A2D)

private val Light = lightColorScheme(
    primary = Color(0xFF3B4A8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FA),
    onPrimaryContainer = Color(0xFF14204F),
    secondary = Color(0xFF8A5A1F),
    background = Color(0xFFF7F5F2),
    onBackground = Color(0xFF1B1C21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C21),
    surfaceVariant = Color(0xFFECE9E4),
    onSurfaceVariant = Color(0xFF55575F),
    outline = Color(0xFFB9B6AF),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAEB9F5),
    onPrimary = Color(0xFF14204F),
    primaryContainer = Color(0xFF2B3768),
    onPrimaryContainer = Color(0xFFDDE2FA),
    secondary = Color(0xFFFFB86B),
    background = Color(0xFF111319),
    onBackground = Color(0xFFE6E4E0),
    surface = Color(0xFF1A1D26),
    onSurface = Color(0xFFE6E4E0),
    surfaceVariant = Color(0xFF252935),
    onSurfaceVariant = Color(0xFFB4B6BF),
    outline = Color(0xFF4A4E5C),
    error = Color(0xFFF2B8B5),
)

@Composable
fun LabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
