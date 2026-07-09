package com.openminidisplay.display

import com.openminidisplay.display.model.ComponentAlign
import com.openminidisplay.display.model.ComponentProps
import com.openminidisplay.display.model.ComponentType
import com.openminidisplay.display.model.DisplayKeys
import com.openminidisplay.display.model.DisplayLayout
import com.openminidisplay.display.model.DisplayPage
import com.openminidisplay.display.model.PieSlice
import com.openminidisplay.display.model.WidgetValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DisplayStore {
    private val _layout = MutableStateFlow(DefaultDisplayLayout.layout)
    val layout: StateFlow<DisplayLayout> = _layout.asStateFlow()

    private val _data = MutableStateFlow<Map<String, WidgetValue>>(emptyMap())
    val data: StateFlow<Map<String, WidgetValue>> = _data.asStateFlow()

    private val _componentProps = MutableStateFlow<Map<String, ComponentProps>>(emptyMap())
    val componentProps: StateFlow<Map<String, ComponentProps>> = _componentProps.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    init {
        seedDefaults()
    }

    fun replaceLayout(layout: DisplayLayout) {
        _layout.value = layout
        seedPropsFromLayout(layout)
    }

    fun patchPages(pages: List<DisplayPage>) {
        if (pages.isEmpty()) return
        val current = _layout.value
        val merged = current.pages.toMutableList()
        pages.forEach { page ->
            val index = merged.indexOfFirst { it.id == page.id }
            if (index >= 0) {
                merged[index] = page
            } else {
                merged.add(page)
            }
        }
        val updated = current.copy(pages = merged)
        _layout.value = updated
        seedPropsFromLayout(updated)
    }

    fun setRawQualified(qualified: String, raw: String): Boolean {
        val parts = DisplayKeys.splitQualified(qualified) ?: return false
        return setRaw(parts.first, parts.second, raw)
    }

    fun setRaw(cardId: String, componentId: String, raw: String): Boolean {
        val type = findComponentType(cardId, componentId) ?: inferTypeFromId(componentId)
        val value = parseRaw(raw, type) ?: return false
        val key = DisplayKeys.qualify(cardId, componentId)
        _data.value = _data.value.toMutableMap().apply { put(key, value) }
        return true
    }

    fun setComponentProp(cardId: String, componentId: String, key: String, value: String): Boolean {
        val qualified = DisplayKeys.qualify(cardId, componentId)
        val current = _componentProps.value[qualified] ?: defaultProps(cardId, componentId)
        val updated = when (key.lowercase()) {
            "label" -> current.copy(label = value)
            "enabled" -> current.copy(enabled = value.equals("true", ignoreCase = true) || value == "1")
            "checked" -> current.copy(checked = value.equals("true", ignoreCase = true) || value == "1")
            "align" -> {
                val align = ComponentAlign.fromRaw(value) ?: return false
                current.copy(align = align)
            }
            "fill" -> current.copy(fill = value.equals("true", ignoreCase = true) || value == "1")
            "fit" -> current.copy(fit = value.equals("true", ignoreCase = true) || value == "1")
            "scale" -> {
                val scale = value.toFloatOrNull() ?: return false
                current.copy(scale = scale.coerceIn(0.2f, 1f))
            }
            "showlabel" -> current.copy(showLabel = value.equals("true", ignoreCase = true) || value == "1")
            else -> return false
        }
        _componentProps.value = _componentProps.value.toMutableMap().apply { put(qualified, updated) }
        return true
    }

    fun propsFor(
        cardId: String,
        componentId: String,
        slotLabel: String?,
        defaultChecked: Boolean = false,
    ): ComponentProps {
        val key = DisplayKeys.qualify(cardId, componentId)
        return _componentProps.value[key] ?: ComponentProps(
            label = slotLabel,
            checked = defaultChecked,
        )
    }

    fun valueFor(cardId: String, componentId: String, type: ComponentType): WidgetValue {
        val key = DisplayKeys.qualify(cardId, componentId)
        return _data.value[key] ?: defaultValue(type)
    }

    fun goTo(index: Int, layout: DisplayLayout) {
        if (layout.pages.isEmpty()) return
        _pageIndex.value = index.coerceIn(0, layout.pages.lastIndex)
    }

    fun goToPageId(pageId: String, layout: DisplayLayout) {
        val index = layout.pages.indexOfFirst { it.id.equals(pageId, ignoreCase = true) }
        if (index >= 0) {
            goTo(index, layout)
        }
    }

    fun clampPageToLayout(layout: DisplayLayout) {
        if (layout.pages.isEmpty()) {
            _pageIndex.value = 0
        } else if (_pageIndex.value > layout.pages.lastIndex) {
            _pageIndex.value = layout.pages.lastIndex
        }
    }

    fun findComponentType(cardId: String, componentId: String): ComponentType? {
        return _layout.value.pages
            .asSequence()
            .flatMap { it.cards.asSequence() }
            .firstOrNull { it.id == cardId }
            ?.components
            ?.firstOrNull { it.id == componentId }
            ?.type
    }

    fun findCard(cardId: String) =
        _layout.value.pages.asSequence().flatMap { it.cards.asSequence() }.firstOrNull { it.id == cardId }

    private fun seedDefaults() {
        val seeded = DefaultDisplayLayout.defaultData.mapNotNull { (qualified, raw) ->
            val parts = DisplayKeys.splitQualified(qualified) ?: return@mapNotNull null
            val type = findComponentType(parts.first, parts.second) ?: inferTypeFromId(parts.second)
            parseRaw(raw, type)?.let { DisplayKeys.qualify(parts.first, parts.second) to it }
        }.toMap()
        _data.value = seeded
        seedPropsFromLayout(_layout.value)
    }

    private fun seedPropsFromLayout(layout: DisplayLayout) {
        val seeded = buildMap {
            layout.pages.forEach { page ->
                page.cards.forEach { card ->
                    card.components.forEach { component ->
                        if (component.type == ComponentType.BUTTON || component.type == ComponentType.TOGGLE) {
                            val key = DisplayKeys.qualify(card.id, component.id)
                            put(
                                key,
                                defaultProps(
                                    card.id,
                                    component.id,
                                    component.label,
                                    component.type,
                                    component.defaultChecked,
                                ),
                            )
                        }
                    }
                }
            }
        }
        _componentProps.value = seeded
    }

    private fun defaultProps(
        cardId: String,
        componentId: String,
        slotLabel: String? = null,
        type: ComponentType? = findComponentType(cardId, componentId),
        defaultChecked: Boolean = false,
    ): ComponentProps {
        val label = slotLabel ?: findCard(cardId)?.components?.firstOrNull { it.id == componentId }?.label
        return ComponentProps(
            label = label ?: componentId,
            enabled = true,
            checked = type == ComponentType.TOGGLE && defaultChecked,
        )
    }

    private fun defaultValue(type: ComponentType): WidgetValue {
        return when (type) {
            ComponentType.TEXT, ComponentType.METRIC -> WidgetValue.TextValue("--")
            ComponentType.PROGRESS, ComponentType.RING -> WidgetValue.Percent(0f)
            ComponentType.LINE, ComponentType.BAR -> WidgetValue.Series(emptyList())
            ComponentType.PIE -> WidgetValue.Pie(emptyList())
            ComponentType.BUTTON, ComponentType.TOGGLE -> WidgetValue.TextValue("")
        }
    }

    private fun inferTypeFromId(id: String): ComponentType {
        return when (id.lowercase()) {
            "progress" -> ComponentType.PROGRESS
            "ring" -> ComponentType.RING
            "line" -> ComponentType.LINE
            "bar" -> ComponentType.BAR
            "pie" -> ComponentType.PIE
            "metric" -> ComponentType.METRIC
            "button" -> ComponentType.BUTTON
            "toggle" -> ComponentType.TOGGLE
            else -> ComponentType.TEXT
        }
    }

    fun parseRaw(raw: String, type: ComponentType): WidgetValue? {
        if (raw.isBlank() && type.isDisplayType) return null
        return when (type) {
            ComponentType.TEXT, ComponentType.METRIC -> WidgetValue.TextValue(raw)
            ComponentType.PROGRESS, ComponentType.RING -> WidgetValue.Percent(parsePercent(raw))
            ComponentType.LINE, ComponentType.BAR -> WidgetValue.Series(parseSeries(raw))
            ComponentType.PIE -> WidgetValue.Pie(parsePie(raw))
            ComponentType.BUTTON, ComponentType.TOGGLE -> WidgetValue.TextValue(raw)
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
