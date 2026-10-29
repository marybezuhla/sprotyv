package ua.sprotyv.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Blue and yellow of the flag as accents; calm neutrals around them.
// Red is kept for real danger now; "error" here is a burnt orange, never red.
val FlagBlue = Color(0xFF0057B7)
val FlagYellow = Color(0xFFFFD700)

/** Only for danger that is happening now (an alert, a "call now" button). */
val DangerRed = Color(0xFFB3261E)

private val Light = lightColorScheme(
    primary = FlagBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6F8),
    onPrimaryContainer = Color(0xFF00214A),
    secondary = Color(0xFF6B6A64),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = FlagYellow,
    onTertiary = Color(0xFF2E2500),
    tertiaryContainer = Color(0xFFFFF3B0),
    onTertiaryContainer = Color(0xFF3A2E00),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFF1EFE8),
    onSurfaceVariant = Color(0xFF4A4945),
    outline = Color(0xFFB9B7AE),
    outlineVariant = Color(0xFFE6E4DC),
    error = Color(0xFF9A4A00),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3CF),
    onErrorContainer = Color(0xFF5C2C00),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8EBBF2),
    onPrimary = Color(0xFF002C5E),
    primaryContainer = Color(0xFF00428C),
    onPrimaryContainer = Color(0xFFD6E6F8),
    secondary = Color(0xFFB4B2A9),
    onSecondary = Color(0xFF2C2C2A),
    tertiary = FlagYellow,
    onTertiary = Color(0xFF2E2500),
    tertiaryContainer = Color(0xFF4A3D00),
    onTertiaryContainer = Color(0xFFFFF3B0),
    background = Color(0xFF121418),
    onBackground = Color(0xFFE6E4DE),
    surface = Color(0xFF1B1E23),
    onSurface = Color(0xFFE6E4DE),
    surfaceVariant = Color(0xFF262A30),
    onSurfaceVariant = Color(0xFFC9C7C0),
    outline = Color(0xFF6E6C66),
    outlineVariant = Color(0xFF3A3D42),
    error = Color(0xFFFFB68A),
    onError = Color(0xFF4A2000),
    errorContainer = Color(0xFF6E3300),
    onErrorContainer = Color(0xFFFBE3CF),
)

@Composable
fun SprotyvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
