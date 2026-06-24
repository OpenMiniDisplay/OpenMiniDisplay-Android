package com.openminidisplay.ui.display

import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.openminidisplay.display.model.GridPlaced
import kotlin.math.roundToInt

internal fun gridMeasurePolicy(
    rows: Int,
    cols: Int,
    gap: Dp,
    items: List<GridPlaced>,
): MeasurePolicy {
    return MeasurePolicy { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val totalGapX = gapPx * (cols - 1).coerceAtLeast(0)
        val totalGapY = gapPx * (rows - 1).coerceAtLeast(0)
        val cellWidth = ((constraints.maxWidth - totalGapX) / cols.toFloat()).roundToInt().coerceAtLeast(0)
        val cellHeight = ((constraints.maxHeight - totalGapY) / rows.toFloat()).roundToInt().coerceAtLeast(0)

        val placeables = measurables.mapIndexed { index, measurable ->
            val slot = items[index]
            val width = (cellWidth * slot.colSpan + gapPx * (slot.colSpan - 1)).coerceAtLeast(0)
            val height = (cellHeight * slot.rowSpan + gapPx * (slot.rowSpan - 1)).coerceAtLeast(0)
            measurable.measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = height,
                    maxHeight = height,
                ),
            )
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            items.forEachIndexed { index, slot ->
                val x = slot.col * (cellWidth + gapPx)
                val y = slot.row * (cellHeight + gapPx)
                placeables[index].placeRelative(x, y)
            }
        }
    }
}
