package com.openminidisplay.display.model

data class DisplayLayout(
    val version: Int = 1,
    val pages: List<DisplayPage> = emptyList(),
)

data class DisplayPage(
    val id: String,
    val grid: GridSpec = GridSpec(),
    val widgets: List<WidgetSlot> = emptyList(),
)

data class GridSpec(
    val rows: Int = 1,
    val cols: Int = 1,
    val gap: Int = 8,
    val padding: Int = 16,
)

data class WidgetSlot(
    val id: String,
    val type: WidgetType,
    val row: Int = 0,
    val col: Int = 0,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val style: TextStyleKind = TextStyleKind.BODY,
    val label: String? = null,
)

enum class WidgetType {
    TEXT,
    METRIC,
    PROGRESS,
    RING,
    LINE,
    BAR,
    PIE,
    ;

    companion object {
        fun fromRaw(raw: String): WidgetType? {
            return when (raw.lowercase()) {
                "text" -> TEXT
                "metric" -> METRIC
                "progress" -> PROGRESS
                "ring" -> RING
                "line" -> LINE
                "bar" -> BAR
                "pie" -> PIE
                else -> null
            }
        }
    }
}

enum class TextStyleKind {
    HEADLINE,
    BODY,
    CAPTION,
    METRIC,
    ;

    companion object {
        fun fromRaw(raw: String?): TextStyleKind {
            return when (raw?.lowercase()) {
                "headline" -> HEADLINE
                "caption" -> CAPTION
                "metric" -> METRIC
                else -> BODY
            }
        }
    }
}

data class PieSlice(
    val label: String,
    val value: Float,
)
