package com.openminidisplay.display

import com.openminidisplay.display.model.DisplayLayout
import com.openminidisplay.display.model.DisplayPage
import com.openminidisplay.display.model.GridSpec
import com.openminidisplay.display.model.TextStyleKind
import com.openminidisplay.display.model.WidgetSlot
import com.openminidisplay.display.model.WidgetType

object DefaultDisplayLayout {
    val layout: DisplayLayout = DisplayLayout(
        version = 1,
        pages = listOf(
            DisplayPage(
                id = "overview",
                grid = GridSpec(rows = 3, cols = 4, gap = 8, padding = 16),
                widgets = listOf(
                    WidgetSlot("title", WidgetType.TEXT, row = 0, col = 0, colSpan = 4, style = TextStyleKind.HEADLINE),
                    WidgetSlot("subtitle", WidgetType.TEXT, row = 1, col = 0, colSpan = 2, style = TextStyleKind.BODY),
                    WidgetSlot("status", WidgetType.TEXT, row = 1, col = 2, colSpan = 2, style = TextStyleKind.CAPTION),
                    WidgetSlot("progress", WidgetType.PROGRESS, row = 2, col = 0),
                    WidgetSlot("ring", WidgetType.RING, row = 2, col = 1),
                    WidgetSlot("line", WidgetType.LINE, row = 2, col = 2),
                    WidgetSlot("bar", WidgetType.BAR, row = 2, col = 3),
                ),
            ),
            DisplayPage(
                id = "focus",
                grid = GridSpec(rows = 1, cols = 1, gap = 0, padding = 0),
                widgets = listOf(
                    WidgetSlot(
                        id = "metric",
                        type = WidgetType.METRIC,
                        row = 0,
                        col = 0,
                        rowSpan = 1,
                        colSpan = 1,
                        style = TextStyleKind.METRIC,
                    ),
                ),
            ),
            DisplayPage(
                id = "charts",
                grid = GridSpec(rows = 1, cols = 2, gap = 8, padding = 16),
                widgets = listOf(
                    WidgetSlot("pie", WidgetType.PIE, row = 0, col = 0),
                    WidgetSlot("footer", WidgetType.TEXT, row = 0, col = 1, style = TextStyleKind.CAPTION),
                ),
            ),
        ),
    )

    val defaultData: Map<String, String> = mapOf(
        "title" to "OpenMiniDisplay",
        "subtitle" to "Remote smart display",
        "status" to "Waiting for connection",
        "metric" to "--",
        "progress" to "0",
        "ring" to "0",
        "line" to "10,20,15,30,25",
        "bar" to "6,14,10,22",
        "pie" to "CPU:30,MEM:25,IO:20,NET:25",
        "footer" to "Port 15180",
    )
}
