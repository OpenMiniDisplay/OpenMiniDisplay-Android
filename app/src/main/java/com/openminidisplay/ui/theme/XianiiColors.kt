package com.openminidisplay.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.openminidisplay.settings.AppColorScheme

// Converted from https://github.com/Nigh/xianii-theme packages/design-system/theme.css
internal object XianiiColors {
    val DarkBase100 = Color(0xFF242424)
    val DarkBase200 = Color(0xFF161616)
    val DarkBase300 = Color(0xFF404040)
    val DarkContent = Color(0xFFF2F2F2)
    val DarkPrimary = Color(0xFFFFA1AD)
    val DarkOnPrimary = Color(0xFF242424)
    val DarkSecondary = Color(0xFFD8B0FF)
    val DarkOnSecondary = Color(0xFF242424)
    val DarkAccent = Color(0xFF7FCFC4)
    val DarkOnAccent = Color(0xFF242424)
    val DarkNeutral = Color(0xFF44403C)
    val DarkInfo = Color(0xFF8EB8FF)
    val DarkSuccess = Color(0xFF8FD9A8)
    val DarkWarning = Color(0xFFF5D08A)
    val DarkError = Color(0xFFFF8A80)

    val LightBase100 = Color(0xFFF5F5F5)
    val LightBase200 = Color(0xFFE8E8E8)
    val LightBase300 = Color(0xFFD4D4D4)
    val LightContent = Color(0xFF161616)
    val LightPrimary = Color(0xFFB3485C)
    val LightOnPrimary = Color(0xFFF5F5F5)
    val LightSecondary = Color(0xFF8658B1)
    val LightOnSecondary = Color(0xFFF5F5F5)
    val LightAccent = Color(0xFF087970)
    val LightOnAccent = Color(0xFFF5F5F5)

    val OledBlack = Color(0xFF000000)
    val OledBorder = Color(0xFF505050)
    val OledContent = Color(0xFFF0F0F0)
    val OledMuted = Color(0xFFADADAD)

    val XianiiDark = darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        tertiary = DarkAccent,
        onTertiary = DarkOnAccent,
        background = DarkBase200,
        onBackground = DarkContent,
        surface = DarkBase100,
        onSurface = DarkContent,
        surfaceVariant = DarkBase300,
        onSurfaceVariant = DarkContent.copy(alpha = 0.78f),
        outline = DarkNeutral,
        error = DarkError,
        onError = DarkBase200,
    )

    val XianiiLight = lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        secondary = LightSecondary,
        onSecondary = LightOnSecondary,
        tertiary = LightAccent,
        onTertiary = LightOnAccent,
        background = LightBase100,
        onBackground = LightContent,
        surface = LightBase200,
        onSurface = LightContent,
        surfaceVariant = LightBase300,
        onSurfaceVariant = LightContent.copy(alpha = 0.72f),
        outline = LightContent.copy(alpha = 0.24f),
        error = DarkError,
        onError = LightBase100,
    )

    val XianiiOled = darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        tertiary = DarkAccent,
        onTertiary = DarkOnAccent,
        background = OledBlack,
        onBackground = OledContent,
        surface = OledBlack,
        onSurface = OledContent,
        surfaceVariant = OledBorder,
        onSurfaceVariant = OledMuted,
        outline = OledBorder,
        outlineVariant = OledBorder.copy(alpha = 0.7f),
        error = DarkError,
        onError = OledContent,
        inverseSurface = OledContent,
        inverseOnSurface = OledBlack,
    )

    fun schemeFor(mode: AppColorScheme) = when (mode) {
        AppColorScheme.DARK -> XianiiDark
        AppColorScheme.LIGHT -> XianiiLight
        AppColorScheme.OLED -> XianiiOled
    }
}
