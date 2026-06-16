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
    val showChrome = page.widgets.size > 1
    val padding = page.grid.padding.dp

    if (page.widgets.size == 1) {
        val slot = page.widgets.first()
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WidgetSlotContainer(showChrome = false, modifier = Modifier.fillMaxSize()) {
                RenderWidget(slot = slot, showChrome = false, modifier = Modifier.fillMaxSize())
            }
        }
        return
    }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        content = {
            page.widgets.forEach { slot ->
                WidgetSlotContainer(showChrome = showChrome, modifier = Modifier) {
                    RenderWidget(slot = slot, showChrome = showChrome, modifier = Modifier.fillMaxSize())
                }
            }
        },
        measurePolicy = gridMeasurePolicy(
            rows = page.grid.rows,
            cols = page.grid.cols,
            gap = page.grid.gap.dp,
            widgets = page.widgets,
        ),
    )
}
