package com.openminidisplay.ui.display

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.settings.AppColorScheme
import com.openminidisplay.settings.AppPreferences
import com.openminidisplay.ui.theme.XianiiColors

@Composable
fun CardContainer(
    showChrome: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!showChrome) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
        return
    }

    val colorScheme by AppPreferences.colorScheme.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(12.dp)

    if (colorScheme == AppColorScheme.OLED) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .border(1.dp, XianiiColors.OledBorder, shape),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                content()
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxSize(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            content()
        }
    }
}
