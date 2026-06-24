package com.openminidisplay.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.openminidisplay.display.model.DisplayPage

@Composable
fun PageRenderer(
    page: DisplayPage,
    modifier: Modifier = Modifier,
) {
    val showPageChrome = page.cards.size > 1
    val padding = page.grid.padding.dp

    if (page.cards.size == 1) {
        val card = page.cards.first()
        CardContainer(showChrome = false, modifier = modifier.fillMaxSize()) {
            CardRenderer(
                card = card,
                showPageChrome = false,
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
            page.cards.forEach { card ->
                CardContainer(showChrome = showPageChrome, modifier = Modifier) {
                    CardRenderer(
                        card = card,
                        showPageChrome = showPageChrome,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        measurePolicy = gridMeasurePolicy(
            rows = page.grid.rows,
            cols = page.grid.cols,
            gap = page.grid.gap.dp,
            items = page.cards,
        ),
    )
}
