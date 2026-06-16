package com.openminidisplay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeState {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _brightness = MutableStateFlow(1f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    fun setConnectionState(state: ConnectionState) {
        if (_connectionState.value != state) {
            _connectionState.value = state
        }
    }

    internal fun setBrightness(level: Float) {
        _brightness.value = level.coerceIn(0f, 1f)
    }
}
