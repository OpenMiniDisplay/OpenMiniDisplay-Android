package com.openminidisplay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object PowerState {
    fun isPluggedIn(context: Context): Boolean = isPluggedIn(batteryIntent(context))

    fun batteryLevelPercent(context: Context): Int {
        val intent = batteryIntent(context) ?: return -1
        return batteryLevelPercent(intent)
    }

    fun batteryLevelPercent(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private fun batteryIntent(context: Context): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun isPluggedIn(intent: Intent?): Boolean {
        if (intent == null) return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}
