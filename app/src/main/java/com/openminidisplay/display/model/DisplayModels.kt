package com.openminidisplay.display.model

data class DisplayLayout(
    val version: Int = 2,
    val pages: List<DisplayPage> = emptyList(),
)

data class DisplayPage(
    val id: String,
    val grid: GridSpec = GridSpec(),
    val cards: List<DisplayCard> = emptyList(),
)

data class GridSpec(
    val rows: Int = 1,
    val cols: Int = 1,
    val gap: Int = 8,
    val padding: Int = 16,
)

interface GridPlaced {
    val row: Int
    val col: Int
    val rowSpan: Int
    val colSpan: Int
}

data class DisplayCard(
    val id: String,
    override val row: Int = 0,
    override val col: Int = 0,
    override val rowSpan: Int = 1,
    override val colSpan: Int = 1,
    val grid: GridSpec = GridSpec(),
    val script: String? = null,
    val components: List<ComponentSlot> = emptyList(),
) : GridPlaced

data class ComponentSlot(
    val id: String,
    val type: ComponentType,
    override val row: Int = 0,
    override val col: Int = 0,
    override val rowSpan: Int = 1,
    override val colSpan: Int = 1,
    val style: TextStyleKind = TextStyleKind.BODY,
    val label: String? = null,
    val defaultChecked: Boolean = false,
) : GridPlaced

enum class ComponentType {
    TEXT,
    METRIC,
    PROGRESS,
    RING,
    LINE,
    BAR,
    PIE,
    BUTTON,
    TOGGLE,
    ;

    val isDisplayType: Boolean
        get() = this != BUTTON && this != TOGGLE

    companion object {
        fun fromRaw(raw: String): ComponentType? {
            return when (raw.lowercase()) {
                "text" -> TEXT
                "metric" -> METRIC
                "progress" -> PROGRESS
                "ring" -> RING
                "line" -> LINE
                "bar" -> BAR
                "pie" -> PIE
                "button" -> BUTTON
                "toggle" -> TOGGLE
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

data class ComponentProps(
    val label: String? = null,
    val enabled: Boolean = true,
    val checked: Boolean = false,
)

object DisplayKeys {
    fun qualify(cardId: String, componentId: String): String = "$cardId:$componentId"

    fun splitQualified(qualified: String): Pair<String, String>? {
        val slash = qualified.indexOf('/')
        if (slash <= 0 || slash >= qualified.lastIndex) return null
        val cardId = qualified.substring(0, slash).trim()
        val componentId = qualified.substring(slash + 1).trim()
        if (cardId.isEmpty() || componentId.isEmpty()) return null
        return cardId to componentId
    }
}
