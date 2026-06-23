package com.openminidisplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.settings.AppPreferences

@Composable
fun OpenMiniDisplayTheme(content: @Composable () -> Unit) {
    val colorSchemeMode by AppPreferences.colorScheme.collectAsStateWithLifecycle()
    MaterialTheme(
        colorScheme = XianiiColors.schemeFor(colorSchemeMode),
        content = content,
    )
}
