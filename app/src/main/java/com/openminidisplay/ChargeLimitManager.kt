package com.openminidisplay

import android.content.Context
import android.provider.Settings
import android.util.Log

object ChargeLimitManager {
    private const val TAG = "ChargeLimitManager"

    const val TARGET_PERCENT = 80

    private const val SAMSUNG_PROTECT_BATTERY = "protect_battery"
    private const val SYSTEM_CHARGING_CONTROL_MODE = "charging_control_mode"
    private const val MODE_LIMIT = 3

    private var limitApplied = false
    private var appliedStrategy: String? = null

    fun onPowerConnected(context: Context) {
        if (limitApplied) return

        val strategy = findApplicableStrategy(context) ?: run {
            Log.i(TAG, "Charge limit to $TARGET_PERCENT% is not supported on this device")
            return
        }

        if (strategy.apply(context)) {
            limitApplied = true
            appliedStrategy = strategy.name
            Log.i(TAG, "Charge limit enabled via ${strategy.name}")
        }
    }

    fun onPowerDisconnected(context: Context) {
        if (!limitApplied) return

        val strategyName = appliedStrategy
        appliedStrategy = null
        limitApplied = false

        if (strategyName == null) return
        val strategy = strategies.firstOrNull { it.name == strategyName } ?: return
        if (strategy.restore(context)) {
            Log.i(TAG, "Charge limit restored via ${strategy.name}")
        }
    }

    private fun findApplicableStrategy(context: Context): Strategy? {
        return strategies.firstOrNull { it.canApply(context) }
    }

    private interface Strategy {
        val name: String
        fun canApply(context: Context): Boolean
        fun apply(context: Context): Boolean
        fun restore(context: Context): Boolean
    }

    private val strategies = listOf(
        object : Strategy {
            override val name = "samsung_protect_battery"

            override fun canApply(context: Context): Boolean {
                return Settings.System.canWrite(context)
            }

            override fun apply(context: Context): Boolean {
                return putSystemInt(context, SAMSUNG_PROTECT_BATTERY, 1)
            }

            override fun restore(context: Context): Boolean {
                return putSystemInt(context, SAMSUNG_PROTECT_BATTERY, 0)
            }
        },
        object : Strategy {
            override val name = "system_charging_control_mode"

            override fun canApply(context: Context): Boolean {
                return Settings.System.canWrite(context)
            }

            override fun apply(context: Context): Boolean {
                return putSystemInt(context, SYSTEM_CHARGING_CONTROL_MODE, MODE_LIMIT)
            }

            override fun restore(context: Context): Boolean {
                return putSystemInt(context, SYSTEM_CHARGING_CONTROL_MODE, 0)
            }
        },
        object : Strategy {
            override val name = "secure_charge_optimization_mode"

            override fun canApply(context: Context): Boolean {
                return readSecureChargeOptimizationKey() != null
            }

            override fun apply(context: Context): Boolean {
                val key = readSecureChargeOptimizationKey() ?: return false
                return putSecureInt(context, key, 1)
            }

            override fun restore(context: Context): Boolean {
                val key = readSecureChargeOptimizationKey() ?: return false
                return putSecureInt(context, key, 0)
            }
        },
    )

    private fun readSecureChargeOptimizationKey(): String? {
        return try {
            Settings.Secure::class.java.getDeclaredField("CHARGE_OPTIMIZATION_MODE").get(null) as String
        } catch (_: Exception) {
            null
        }
    }

    private fun putSystemInt(context: Context, key: String, value: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(context.contentResolver, key, value)
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to write Settings.System.$key", exception)
            false
        }
    }

    private fun putSecureInt(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.Secure.putInt(context.contentResolver, key, value)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Missing permission for Settings.Secure.$key", exception)
            false
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to write Settings.Secure.$key", exception)
            false
        }
    }
}
