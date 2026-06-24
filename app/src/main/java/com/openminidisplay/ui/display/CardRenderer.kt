package com.openminidisplay.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.openminidisplay.display.model.DisplayCard

@Composable
fun CardRenderer(
    card: DisplayCard,
    showPageChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderless = !showPageChrome && card.components.size == 1
    val padding = if (borderless) 0.dp else card.grid.padding.dp

    if (card.components.size == 1) {
        val component = card.components.first()
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RenderComponent(
                cardId = card.id,
                slot = component,
                showLabel = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        content = {
            card.components.forEach { component ->
                RenderComponent(
                    cardId = card.id,
                    slot = component,
                    showLabel = true,
                    modifier = Modifier,
                )
            }
        },
        measurePolicy = gridMeasurePolicy(
            rows = card.grid.rows,
            cols = card.grid.cols,
            gap = card.grid.gap.dp,
            items = card.components,
        ),
    )
}
