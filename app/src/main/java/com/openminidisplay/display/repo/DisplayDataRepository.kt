package com.openminidisplay.display.repo

import com.openminidisplay.display.DefaultDisplayLayout
import com.openminidisplay.display.model.PieSlice
import com.openminidisplay.display.model.WidgetType
import com.openminidisplay.display.model.WidgetValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DisplayDataRepository {
    private val _data = MutableStateFlow<Map<String, WidgetValue>>(emptyMap())
    val data: StateFlow<Map<String, WidgetValue>> = _data.asStateFlow()

    init {
        seedDefaults()
    }

    fun seedDefaults() {
        val seeded = DefaultDisplayLayout.defaultData.mapNotNull { (id, raw) ->
            val type = DisplayLayoutRepository.findWidgetType(id)
                ?: inferTypeFromId(id)
            parseRaw(raw, type)?.let { id to it }
        }.toMap()
        _data.value = seeded
    }

    fun setRaw(widgetId: String, raw: String): Boolean {
        val type = DisplayLayoutRepository.findWidgetType(widgetId) ?: inferTypeFromId(widgetId)
        val value = parseRaw(raw, type) ?: return false
        _data.value = _data.value.toMutableMap().apply { put(widgetId, value) }
        return true
    }

    fun valueFor(widgetId: String, type: WidgetType): WidgetValue {
        return _data.value[widgetId] ?: defaultValue(type)
    }

    private fun defaultValue(type: WidgetType): WidgetValue {
        return when (type) {
            WidgetType.TEXT, WidgetType.METRIC -> WidgetValue.TextValue("--")
            WidgetType.PROGRESS, WidgetType.RING -> WidgetValue.Percent(0f)
            WidgetType.LINE, WidgetType.BAR -> WidgetValue.Series(emptyList())
            WidgetType.PIE -> WidgetValue.Pie(emptyList())
        }
    }

    private fun inferTypeFromId(id: String): WidgetType {
        return when (id.lowercase()) {
            "progress" -> WidgetType.PROGRESS
            "ring" -> WidgetType.RING
            "line" -> WidgetType.LINE
            "bar" -> WidgetType.BAR
            "pie" -> WidgetType.PIE
            "metric" -> WidgetType.METRIC
            else -> WidgetType.TEXT
        }
    }

    fun parseRaw(raw: String, type: WidgetType): WidgetValue? {
        if (raw.isBlank()) return null
        return when (type) {
            WidgetType.TEXT, WidgetType.METRIC -> WidgetValue.TextValue(raw)
            WidgetType.PROGRESS, WidgetType.RING -> WidgetValue.Percent(parsePercent(raw))
            WidgetType.LINE, WidgetType.BAR -> WidgetValue.Series(parseSeries(raw))
            WidgetType.PIE -> WidgetValue.Pie(parsePie(raw))
        }
    }

    private fun parsePercent(raw: String): Float {
        return raw.trim().removeSuffix("%").toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
    }

    private fun parseSeries(raw: String): List<Float> {
        return raw.split(',').mapNotNull { part -> part.trim().toFloatOrNull() }
    }

    private fun parsePie(raw: String): List<PieSlice> {
        val labeled = raw.split(',').mapNotNull { part ->
            val trimmed = part.trim()
            if (!trimmed.contains(':')) return@mapNotNull null
            val pieces = trimmed.split(':', limit = 2)
            val label = pieces[0].trim()
            val amount = pieces.getOrNull(1)?.trim()?.toFloatOrNull() ?: return@mapNotNull null
            if (label.isEmpty() || amount < 0f) return@mapNotNull null
            PieSlice(label, amount)
        }
        if (labeled.isNotEmpty()) return labeled

        return parseSeries(raw).mapIndexed { index, amount ->
            PieSlice("S${index + 1}", amount.coerceAtLeast(0f))
        }
    }
}
