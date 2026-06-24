package com.openminidisplay.display

import com.openminidisplay.display.model.ComponentSlot
import com.openminidisplay.display.model.ComponentType
import com.openminidisplay.display.model.DisplayCard
import com.openminidisplay.display.model.DisplayLayout
import com.openminidisplay.display.model.DisplayPage
import com.openminidisplay.display.model.GridSpec
import com.openminidisplay.display.model.TextStyleKind

object DefaultDisplayLayout {
    private fun singleComponentCard(
        id: String,
        component: ComponentSlot,
        row: Int,
        col: Int,
        rowSpan: Int = 1,
        colSpan: Int = 1,
    ): DisplayCard = DisplayCard(
        id = id,
        row = row,
        col = col,
        rowSpan = rowSpan,
        colSpan = colSpan,
        grid = GridSpec(rows = 1, cols = 1, gap = 0, padding = 0),
        components = listOf(component.copy(row = 0, col = 0)),
    )

    val layout: DisplayLayout = DisplayLayout(
        version = 2,
        pages = listOf(
            DisplayPage(
                id = "overview",
                grid = GridSpec(rows = 3, cols = 4, gap = 8, padding = 16),
                cards = listOf(
                    singleComponentCard(
                        "title",
                        ComponentSlot("title", ComponentType.TEXT, style = TextStyleKind.HEADLINE),
                        row = 0,
                        col = 0,
                        colSpan = 4,
                    ),
                    singleComponentCard(
                        "subtitle",
                        ComponentSlot("subtitle", ComponentType.TEXT, style = TextStyleKind.BODY),
                        row = 1,
                        col = 0,
                        colSpan = 2,
                    ),
                    singleComponentCard(
                        "status",
                        ComponentSlot("status", ComponentType.TEXT, style = TextStyleKind.CAPTION),
                        row = 1,
                        col = 2,
                        colSpan = 2,
                    ),
                    singleComponentCard(
                        "progress",
                        ComponentSlot("progress", ComponentType.PROGRESS),
                        row = 2,
                        col = 0,
                    ),
                    singleComponentCard(
                        "ring",
                        ComponentSlot("ring", ComponentType.RING),
                        row = 2,
                        col = 1,
                    ),
                    singleComponentCard(
                        "line",
                        ComponentSlot("line", ComponentType.LINE),
                        row = 2,
                        col = 2,
                    ),
                    singleComponentCard(
                        "bar",
                        ComponentSlot("bar", ComponentType.BAR),
                        row = 2,
                        col = 3,
                    ),
                ),
            ),
            DisplayPage(
                id = "focus",
                grid = GridSpec(rows = 1, cols = 1, gap = 0, padding = 0),
                cards = listOf(
                    singleComponentCard(
                        "metric",
                        ComponentSlot(
                            id = "metric",
                            type = ComponentType.METRIC,
                            style = TextStyleKind.METRIC,
                        ),
                        row = 0,
                        col = 0,
                    ),
                ),
            ),
            DisplayPage(
                id = "charts",
                grid = GridSpec(rows = 1, cols = 2, gap = 8, padding = 16),
                cards = listOf(
                    singleComponentCard(
                        "pie",
                        ComponentSlot("pie", ComponentType.PIE),
                        row = 0,
                        col = 0,
                    ),
                    singleComponentCard(
                        "footer",
                        ComponentSlot("footer", ComponentType.TEXT, style = TextStyleKind.CAPTION),
                        row = 0,
                        col = 1,
                    ),
                ),
            ),
        ),
    )

    val defaultData: Map<String, String> = mapOf(
        "title/title" to "OpenMiniDisplay",
        "subtitle/subtitle" to "Remote smart display",
        "status/status" to "Waiting for connection",
        "metric/metric" to "--",
        "progress/progress" to "0",
        "ring/ring" to "0",
        "line/line" to "10,20,15,30,25",
        "bar/bar" to "6,14,10,22",
        "pie/pie" to "CPU:30,MEM:25,IO:20,NET:25",
        "footer/footer" to "Port 15180",
    )
}
