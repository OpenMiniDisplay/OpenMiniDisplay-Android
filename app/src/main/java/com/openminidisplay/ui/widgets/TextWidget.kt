package com.openminidisplay.ui.widgets

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.openminidisplay.display.model.ComponentAlign
import com.openminidisplay.display.model.TextStyleKind

@Composable
fun TextWidget(
    text: String,
    styleKind: TextStyleKind,
    expanded: Boolean = false,
    align: ComponentAlign = ComponentAlign.START,
    fit: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val typography = when (styleKind) {
        TextStyleKind.HEADLINE -> if (expanded) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall
        TextStyleKind.CAPTION -> MaterialTheme.typography.bodySmall
        TextStyleKind.METRIC -> if (expanded) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium
        TextStyleKind.BODY -> MaterialTheme.typography.bodyLarge
    }

    if (fit) {
        AutoFitText(
            text = text,
            baseStyle = typography,
            align = align,
            modifier = modifier,
        )
        return
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = align.toAlignment(),
    ) {
        Text(
            text = text,
            style = typography,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align.toTextAlign(),
            maxLines = if (expanded) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = if (align == ComponentAlign.CENTER) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

@Composable
private fun AutoFitText(
    text: String,
    baseStyle: TextStyle,
    align: ComponentAlign,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = align.toAlignment(),
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        // ponytail: key only on cell size — text updates must not reset font (clock tick jump)
        var fontSize by remember(maxW, maxH) {
            mutableStateOf(minOf(maxW.value, maxH.value).sp * 0.55f)
        }

        Text(
            text = text,
            style = baseStyle.copy(fontSize = fontSize),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align.toTextAlign(),
            maxLines = 1,
            softWrap = false,
            modifier = if (align == ComponentAlign.CENTER) Modifier.fillMaxWidth() else Modifier,
            onTextLayout = { result ->
                if ((result.didOverflowWidth || result.didOverflowHeight) && fontSize.value > 8f) {
                    fontSize *= 0.88f
                }
            },
        )
    }
}
