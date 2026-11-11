package ua.sprotyv.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Blue and yellow of the flag as accents; calm neutrals around them.
// Red is kept for real danger now; "error" here is a burnt orange, never red.
val FlagBlue = Color(0xFF0057B7)
val FlagYellow = Color(0xFFFFD700)

/** Only for danger that is happening now (an alert, a "call now" button). */
val DangerRed = Color(0xFFB3261E)

/** Red as text or an icon on the screen's own background: lighter on dark ones, so it reads (WCAG AA). */
val DangerInk: Color
    @androidx.compose.runtime.Composable get() =
        if (androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF8A80) else DangerRed

internal val Light = lightColorScheme(
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
    background = Color(0xFFF2F1EC),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFF1EFE8),
    onSurfaceVariant = Color(0xFF4A4945),
    outline = Color(0xFF8A887F),
    outlineVariant = Color(0xFFE6E4DC),
    error = Color(0xFF9A4A00),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3CF),
    onErrorContainer = Color(0xFF5C2C00),
)

internal val Dark = darkColorScheme(
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

// High contrast: pure black on white, or white and yellow on black. For low vision and bright sun.
private val ContrastLight = lightColorScheme(
    primary = Color(0xFF00316B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6F8),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF000000),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF00316B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF3B0),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF000000),
    outline = Color(0xFF000000),
    outlineVariant = Color(0xFF1A1A1A),
    error = Color(0xFF6E3300),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3CF),
    onErrorContainer = Color(0xFF000000),
)

private val ContrastDark = darkColorScheme(
    primary = FlagYellow,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2A2400),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    tertiary = FlagYellow,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2A2400),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFFFFFFF),
    outline = Color(0xFFFFFFFF),
    outlineVariant = Color(0xFFBDBDBD),
    error = Color(0xFFFFB68A),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF4A2000),
    onErrorContainer = Color(0xFFFFFFFF),
)

/** The look the owner chose. "As the phone" follows the phone's light or dark setting. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK, CONTRAST }

/** Dark and high-contrast as the app draws them now (not as the phone is set). */
val LocalDark = staticCompositionLocalOf { false }
val LocalContrast = staticCompositionLocalOf { false }

/**
 * [dynamic]: accent colours from the wallpaper (Material You). The "error" colour stays our burnt
 * orange either way, so red keeps meaning only danger now. High contrast ignores the wallpaper.
 */
@Composable
fun SprotyvTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val contrast = choice == ThemeChoice.CONTRAST
    val context = LocalContext.current
    val base = if (dark) Dark else Light
    val scheme = when {
        contrast -> if (dark) ContrastDark else ContrastLight
        dynamic -> (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).copy(
            error = base.error, onError = base.onError, errorContainer = base.errorContainer, onErrorContainer = base.onErrorContainer,
        )
        else -> base
    }
    // Status and navigation bar icons readable on the app's own background, whatever the phone's setting
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    if (window != null) {
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDark provides dark, LocalContrast provides contrast) {
        MaterialTheme(colorScheme = scheme, typography = AppType) {
            // The base every screen draws on: its own background and, with it, a readable text colour
            Surface(modifier = Modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onBackground) {
                content()
            }
        }
    }
}


/**
 * One type scale for the whole app, so every screen reads the same. Material's defaults spread the
 * letters apart (up to 0.5 sp) and go down to 11 sp: here the letters sit at their natural spacing,
 * nothing is smaller than 13 sp, lines get room in one ratio (about 1.4–1.5), and body text is
 * left-aligned with whole words. Sizes still grow with the phone's text size.
 */
private fun style(size: Int, line: Int, weight: androidx.compose.ui.text.font.FontWeight, body: Boolean = false) =
    androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.TextUnit(size.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
        lineHeight = androidx.compose.ui.unit.TextUnit(line.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(0f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = weight,
        textAlign = if (body) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Unspecified,
        hyphens = if (body) androidx.compose.ui.text.style.Hyphens.None else androidx.compose.ui.text.style.Hyphens.Unspecified,
        lineBreak = if (body) androidx.compose.ui.text.style.LineBreak.Paragraph else androidx.compose.ui.text.style.LineBreak.Unspecified,
    )

private val Regular = androidx.compose.ui.text.font.FontWeight.Normal
private val Medium = androidx.compose.ui.text.font.FontWeight.Medium

val AppType = androidx.compose.material3.Typography(
    displaySmall = style(34, 42, Regular),
    headlineLarge = style(30, 38, Regular),
    headlineMedium = style(26, 34, Regular),
    headlineSmall = style(22, 30, Regular),
    titleLarge = style(21, 28, Regular),
    titleMedium = style(17, 24, Medium),
    titleSmall = style(15, 22, Medium),
    bodyLarge = style(16, 24, Regular, body = true),
    bodyMedium = style(15, 22, Regular, body = true),
    bodySmall = style(14, 20, Regular, body = true),
    labelLarge = style(15, 20, Medium),
    labelMedium = style(14, 20, Medium),
    labelSmall = style(13, 18, Medium),
)
