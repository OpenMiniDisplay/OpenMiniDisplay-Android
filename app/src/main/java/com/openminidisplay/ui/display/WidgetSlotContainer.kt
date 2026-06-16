package com.openminidisplay.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WidgetSlotContainer(
    showChrome: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (showChrome) {
        Card(
            modifier = modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                content()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}
