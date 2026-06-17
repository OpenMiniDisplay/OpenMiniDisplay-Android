package com.openminidisplay.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.openminidisplay.display.model.TextStyleKind

@Composable
fun TextWidget(
    text: String,
    styleKind: TextStyleKind,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val typography = when (styleKind) {
        TextStyleKind.HEADLINE -> if (expanded) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall
        TextStyleKind.CAPTION -> MaterialTheme.typography.bodySmall
        TextStyleKind.METRIC -> if (expanded) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium
        TextStyleKind.BODY -> MaterialTheme.typography.bodyLarge
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (expanded) Alignment.Center else Alignment.TopStart,
    ) {
        Text(
            text = text,
            style = typography,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (expanded) TextAlign.Center else TextAlign.Start,
            maxLines = if (expanded) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
