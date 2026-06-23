package com.openminidisplay.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppPreferences {
    private const val PREFS_NAME = "openminidisplay_settings"

    private const val KEY_COLOR_SCHEME = "color_scheme"
    private const val KEY_LOW_POWER_DELAY_MS = "low_power_delay_ms"
    private const val KEY_GRADUAL_DIM_MS = "gradual_dim_ms"
    private const val KEY_KEEP_SCREEN_ON_PLUGGED = "keep_screen_on_plugged"
    private const val KEY_LISTEN_PORT = "listen_port"
    private const val KEY_HEARTBEAT_TIMEOUT_MS = "heartbeat_timeout_ms"

    const val DEFAULT_LOW_POWER_DELAY_MS = 60_000L
    const val DEFAULT_GRADUAL_DIM_MS = 10_000L
    const val DEFAULT_LISTEN_PORT = 15_180
    const val DEFAULT_HEARTBEAT_TIMEOUT_MS = 5_000L

    private lateinit var prefs: SharedPreferences

    private val _colorScheme = MutableStateFlow(AppColorScheme.DARK)
    val colorScheme: StateFlow<AppColorScheme> = _colorScheme.asStateFlow()

    private val _lowPowerDelayMs = MutableStateFlow(DEFAULT_LOW_POWER_DELAY_MS)
    val lowPowerDelayMs: StateFlow<Long> = _lowPowerDelayMs.asStateFlow()

    private val _gradualDimMs = MutableStateFlow(DEFAULT_GRADUAL_DIM_MS)
    val gradualDimMs: StateFlow<Long> = _gradualDimMs.asStateFlow()

    private val _keepScreenOnWhenPlugged = MutableStateFlow(true)
    val keepScreenOnWhenPlugged: StateFlow<Boolean> = _keepScreenOnWhenPlugged.asStateFlow()

    private val _listenPort = MutableStateFlow(DEFAULT_LISTEN_PORT)
    val listenPort: StateFlow<Int> = _listenPort.asStateFlow()

    private val _heartbeatTimeoutMs = MutableStateFlow(DEFAULT_HEARTBEAT_TIMEOUT_MS)
    val heartbeatTimeoutMs: StateFlow<Long> = _heartbeatTimeoutMs.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_COLOR_SCHEME -> _colorScheme.value = AppColorScheme.fromKey(prefs.getString(key, null))
            KEY_LOW_POWER_DELAY_MS -> _lowPowerDelayMs.value = prefs.getLong(key, DEFAULT_LOW_POWER_DELAY_MS)
            KEY_GRADUAL_DIM_MS -> _gradualDimMs.value = prefs.getLong(key, DEFAULT_GRADUAL_DIM_MS)
            KEY_KEEP_SCREEN_ON_PLUGGED -> _keepScreenOnWhenPlugged.value = prefs.getBoolean(key, true)
            KEY_LISTEN_PORT -> _listenPort.value = prefs.getInt(key, DEFAULT_LISTEN_PORT)
            KEY_HEARTBEAT_TIMEOUT_MS -> _heartbeatTimeoutMs.value =
                prefs.getLong(key, DEFAULT_HEARTBEAT_TIMEOUT_MS)
        }
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reloadAll()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setColorScheme(scheme: AppColorScheme) {
        prefs.edit().putString(KEY_COLOR_SCHEME, scheme.storageKey).apply()
        _colorScheme.value = scheme
    }

    fun setLowPowerDelayMs(value: Long) {
        prefs.edit().putLong(KEY_LOW_POWER_DELAY_MS, value.coerceIn(15_000L, 600_000L)).apply()
    }

    fun setGradualDimMs(value: Long) {
        prefs.edit().putLong(KEY_GRADUAL_DIM_MS, value.coerceIn(3_000L, 60_000L)).apply()
    }

    fun setKeepScreenOnWhenPlugged(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON_PLUGGED, enabled).apply()
    }

    fun setListenPort(port: Int) {
        prefs.edit().putInt(KEY_LISTEN_PORT, port.coerceIn(1024, 65_535)).apply()
    }

    fun setHeartbeatTimeoutMs(value: Long) {
        prefs.edit().putLong(KEY_HEARTBEAT_TIMEOUT_MS, value.coerceIn(2_000L, 60_000L)).apply()
    }

    fun listenPort(): Int = _listenPort.value

    fun lowPowerDelayMs(): Long = _lowPowerDelayMs.value

    fun gradualDimMs(): Long = _gradualDimMs.value

    fun heartbeatTimeoutMs(): Long = _heartbeatTimeoutMs.value

    fun keepScreenOnWhenPlugged(): Boolean = _keepScreenOnWhenPlugged.value

    private fun reloadAll() {
        _colorScheme.value = AppColorScheme.fromKey(prefs.getString(KEY_COLOR_SCHEME, null))
        _lowPowerDelayMs.value = prefs.getLong(KEY_LOW_POWER_DELAY_MS, DEFAULT_LOW_POWER_DELAY_MS)
        _gradualDimMs.value = prefs.getLong(KEY_GRADUAL_DIM_MS, DEFAULT_GRADUAL_DIM_MS)
        _keepScreenOnWhenPlugged.value = prefs.getBoolean(KEY_KEEP_SCREEN_ON_PLUGGED, true)
        _listenPort.value = prefs.getInt(KEY_LISTEN_PORT, DEFAULT_LISTEN_PORT)
        _heartbeatTimeoutMs.value = prefs.getLong(KEY_HEARTBEAT_TIMEOUT_MS, DEFAULT_HEARTBEAT_TIMEOUT_MS)
    }
}
