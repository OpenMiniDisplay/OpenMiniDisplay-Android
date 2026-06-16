package com.openminidisplay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenBrightnessState {
    private val _level = MutableStateFlow(1f)
    val level: StateFlow<Float> = _level.asStateFlow()

    internal fun update(level: Float) {
        _level.value = level.coerceIn(0f, 1f)
    }
}
