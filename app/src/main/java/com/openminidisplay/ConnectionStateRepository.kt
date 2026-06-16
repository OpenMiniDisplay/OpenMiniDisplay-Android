package com.openminidisplay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConnectionStateRepository {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun updateState(newState: ConnectionState) {
        if (_state.value != newState) {
            _state.value = newState
        }
    }
}
