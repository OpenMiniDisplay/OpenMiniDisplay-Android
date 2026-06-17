package com.openminidisplay.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.settings.AppColorScheme
import com.openminidisplay.settings.AppPreferences

@Composable
fun OpenMiniDisplayTheme(content: @Composable () -> Unit) {
    val colorSchemeMode by AppPreferences.colorScheme.collectAsStateWithLifecycle()
    val colorScheme = XianiiColors.schemeFor(colorSchemeMode)
    val contentColor = when (colorSchemeMode) {
        AppColorScheme.OLED -> XianiiColors.OledContent
        else -> colorScheme.onBackground
    }
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
