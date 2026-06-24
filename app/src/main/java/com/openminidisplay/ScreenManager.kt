package com.openminidisplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.openminidisplay.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var dimJob: Job? = null
    private var lastSystemBrightness = -1
    private var savedSystemBrightness: Int? = null
    private var displayPowerSavingActive = false

    fun onConnected() {
        cancelDimming()
        restoreBrightnessLevel()
        acquireScreenWakeLock()
        navigateToDashboard()
    }

    fun onDisconnected() {
        releaseScreenWakeLock()
    }

    fun shouldKeepScreenOn(): Boolean {
        val connected = RuntimeState.connectionState.value == ConnectionState.CONNECTED
        val pluggedIn = RuntimeState.isPluggedIn.value
        val keepWhenPlugged = AppPreferences.keepScreenOnWhenPlugged()
        return connected || (pluggedIn && keepWhenPlugged)
    }

    fun isDisplayPowerSavingActive(): Boolean = displayPowerSavingActive

    /** Full display brightness on resume — not during plugged idle dim / pitch-black transition. */
    fun shouldRestoreDisplayBrightness(): Boolean {
        if (displayPowerSavingActive) return false
        if (RuntimeState.connectionState.value == ConnectionState.CONNECTED) return true
        return shouldKeepScreenOn()
    }

    fun beginLowPowerTransition() {
        displayPowerSavingActive = true
        releaseScreenWakeLock()
        if (PowerState.isPluggedIn(context)) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_BEGIN_LOW_POWER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        } else {
            enterBatteryLowPower()
        }
    }

    fun enterBatteryLowPower() {
        cancelDimming()
        releaseScreenWakeLock()
        val intent = Intent(context, RemoteDisplayService::class.java).apply {
            action = RemoteDisplayService.ACTION_BATTERY_DEEP_IDLE
        }
        context.startService(intent)
    }

    fun showPitchBlackScreen() {
        if (!PowerState.isPluggedIn(context)) return
        navigateToPitchBlack()
    }

    fun startGradualDim(activity: Activity, onComplete: () -> Unit = {}) {
        if (!shouldKeepScreenOn()) {
            onComplete()
            return
        }
        configurePreventLock(activity)
        dimJob?.cancel()
        dimJob = mainScope.launch {
            val startBrightness = RuntimeState.brightness.value.coerceIn(0f, 1f)
            val startTimeMs = System.currentTimeMillis()
            val durationMs = AppPreferences.gradualDimMs()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTimeMs
                if (elapsed >= durationMs) {
                    applyBrightness(activity, 0f, forceSystemUpdate = true)
                    break
                }
                val progress = elapsed.toFloat() / durationMs
                val fraction = startBrightness * (1f - progress)
                applyBrightness(activity, fraction)
                delay(FRAME_DELAY_MS)
            }
            onComplete()
        }
    }

    fun restoreBrightnessLevel(activity: Activity? = null) {
        cancelDimming()
        displayPowerSavingActive = false
        captureSystemBrightnessIfNeeded()
        lastSystemBrightness = -1
        applyBrightness(activity, 1f, forceSystemUpdate = true)
    }

    /** Restore pre-display system brightness when leaving the dashboard (settings, home, etc.). */
    fun restoreUserBrightness(activity: Activity? = null) {
        if (displayPowerSavingActive) return
        cancelDimming()
        activity?.let { clearWindowBrightnessOverride(it) }
        val saved = savedSystemBrightness
        if (saved != null && Settings.System.canWrite(context)) {
            lastSystemBrightness = saved
            setSystemBrightness(saved)
        }
    }

    fun cancelDimming() {
        dimJob?.cancel()
        dimJob = null
    }

    fun releaseWakeLocks() {
        cancelDimming()
        releaseScreenWakeLock()
    }

    fun configurePreventLock(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
    }

    fun configureAllowLock(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(false)
            activity.setTurnScreenOn(false)
        }
    }

    fun applyScreenPolicy(activity: Activity) {
        if (shouldKeepScreenOn()) {
            configurePreventLock(activity)
            keepScreenOn(activity, enabled = true)
        } else {
            configureAllowLock(activity)
            keepScreenOn(activity, enabled = false)
        }
    }

    fun applyBrightnessToActivity(activity: Activity, fraction: Float) {
        setWindowBrightness(activity, fraction)
    }

    private fun applyBrightness(
        activity: Activity?,
        fraction: Float,
        forceSystemUpdate: Boolean = false,
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        RuntimeState.setBrightness(clamped)
        activity?.let { setWindowBrightness(it, clamped) }
        val systemLevel = (MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * clamped).toInt()
        if (forceSystemUpdate || systemLevel != lastSystemBrightness) {
            lastSystemBrightness = systemLevel
            setSystemBrightness(systemLevel)
        }
    }

    private fun acquireScreenWakeLock() {
        if (screenWakeLock?.isHeld == true) return

        @Suppress("DEPRECATION")
        screenWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "$TAG:ScreenWakeLock",
        ).apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseScreenWakeLock() {
        screenWakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        screenWakeLock = null
    }

    fun setSystemBrightness(brightness: Int) {
        if (!Settings.System.canWrite(context)) return

        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
        )
    }

    fun setWindowBrightness(activity: Activity, brightness: Float) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = brightness.coerceIn(0f, 1f)
        activity.window.attributes = layoutParams
    }

    private fun captureSystemBrightnessIfNeeded() {
        if (savedSystemBrightness != null || !Settings.System.canWrite(context)) return
        try {
            savedSystemBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
        } catch (exception: Settings.SettingNotFoundException) {
            Log.w(TAG, "Could not read system brightness", exception)
        }
    }

    private fun clearWindowBrightnessOverride(activity: Activity) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = layoutParams
    }

    fun keepScreenOn(activity: Activity, enabled: Boolean) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun navigateToDashboard() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    private fun navigateToPitchBlack() {
        val intent = Intent(context, PitchBlackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    fun openWriteSettingsScreen() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    companion object {
        private const val TAG = "ScreenManager"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 60 * 1000L
        const val GRADUAL_DIM_DURATION_MS = 10_000L
        private const val FRAME_DELAY_MS = 16L

        const val MIN_BRIGHTNESS = 0
        const val MAX_BRIGHTNESS = 255

        const val ACTION_BEGIN_LOW_POWER = "com.openminidisplay.action.BEGIN_LOW_POWER"
    }
}
