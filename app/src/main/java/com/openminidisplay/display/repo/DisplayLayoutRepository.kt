package com.openminidisplay.display.repo

import com.openminidisplay.display.DefaultDisplayLayout
import com.openminidisplay.display.model.DisplayLayout
import com.openminidisplay.display.model.DisplayPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DisplayLayoutRepository {
    private val _layout = MutableStateFlow(DefaultDisplayLayout.layout)
    val layout: StateFlow<DisplayLayout> = _layout.asStateFlow()

    fun replace(layout: DisplayLayout) {
        _layout.value = layout
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
        _layout.value = current.copy(pages = merged)
    }

    fun findWidgetType(widgetId: String): com.openminidisplay.display.model.WidgetType? {
        return _layout.value.pages
            .asSequence()
            .flatMap { it.widgets.asSequence() }
            .firstOrNull { it.id == widgetId }
            ?.type
    }
}
