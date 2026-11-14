package com.dusk.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import com.dusk.app.data.ThemeMode

val Amber = Color(0xFFE39A2D)

private val Light = lightColorScheme(
    primary = Color(0xFF3B4A8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FA),
    onPrimaryContainer = Color(0xFF14204F),
    secondary = Color(0xFF8A5A1F),
    secondaryContainer = Color(0xFFFFE3C2),
    onSecondaryContainer = Color(0xFF3B2400),
    background = Color(0xFFF7F5F2),
    onBackground = Color(0xFF1B1C21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C21),
    surfaceVariant = Color(0xFFECE9E4),
    onSurfaceVariant = Color(0xFF55575F),
    surfaceContainerLow = Color(0xFFF1EEE9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFB9B6AF),
    outlineVariant = Color(0xFFDAD6CF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF5C1410),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAEB9F5),
    onPrimary = Color(0xFF14204F),
    primaryContainer = Color(0xFF2B3768),
    onPrimaryContainer = Color(0xFFDDE2FA),
    secondary = Color(0xFFFFB86B),
    secondaryContainer = Color(0xFF4A3214),
    onSecondaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF111319),
    onBackground = Color(0xFFE6E4E0),
    surface = Color(0xFF1A1D26),
    onSurface = Color(0xFFE6E4E0),
    surfaceVariant = Color(0xFF252935),
    onSurfaceVariant = Color(0xFFB4B6BF),
    surfaceContainerLow = Color(0xFF161922),
    surfaceContainer = Color(0xFF1A1D26),
    surfaceContainerHigh = Color(0xFF20242F),
    outline = Color(0xFF4A4E5C),
    outlineVariant = Color(0xFF333745),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFBE4E1),
)

/** Bar colours on the calendar, from a hard day (1) to a great one (5). */
val MoodColors = listOf(Color(0xFFC0563F), Color(0xFFDB8A3A), Color(0xFFC9A227), Color(0xFF6AA84F), Color(0xFF2E8B57))

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun DuskTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(mode)) Dark else Light) {
        // Surface sets the default text colour, so text stays readable in both themes.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
