package com.openminidisplay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeState {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _brightness = MutableStateFlow(1f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _isPluggedIn = MutableStateFlow(false)
    val isPluggedIn: StateFlow<Boolean> = _isPluggedIn.asStateFlow()

    private val _batteryDeepIdle = MutableStateFlow(false)
    val batteryDeepIdle: StateFlow<Boolean> = _batteryDeepIdle.asStateFlow()

    fun setConnectionState(state: ConnectionState) {
        if (_connectionState.value != state) {
            _connectionState.value = state
        }
    }

    internal fun setBrightness(level: Float) {
        _brightness.value = level.coerceIn(0f, 1f)
    }

    fun setPluggedIn(pluggedIn: Boolean) {
        if (_isPluggedIn.value != pluggedIn) {
            _isPluggedIn.value = pluggedIn
        }
    }

    fun setBatteryDeepIdle(deepIdle: Boolean) {
        if (_batteryDeepIdle.value != deepIdle) {
            _batteryDeepIdle.value = deepIdle
        }
    }
}
