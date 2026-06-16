package com.openminidisplay.display.repo

import com.openminidisplay.display.model.DisplayLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DisplayNavigationRepository {
    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

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

    fun clampToLayout(layout: DisplayLayout) {
        if (layout.pages.isEmpty()) {
            _pageIndex.value = 0
        } else if (_pageIndex.value > layout.pages.lastIndex) {
            _pageIndex.value = layout.pages.lastIndex
        }
    }
}
