package com.openminidisplay.script

import android.util.Log
import com.openminidisplay.display.model.DisplayCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luaj.vm2.LuaValue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LuaCardRuntime(
    private val card: DisplayCard,
    private val serviceScope: CoroutineScope,
    private val onWake: () -> Unit,
) {
    private val scriptExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "card-script-${card.id}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scriptScope = CoroutineScope(scriptExecutor + Job())

    private val globals = LuaSandbox.createGlobals()
    private val hostApi = LuaHostApi(
        cardId = card.id,
        onEvery = { seconds, name -> scheduleTimer(name, seconds) },
        onCancel = { name -> cancelTimer(name) },
        onHttpGet = { url, callback -> launchHttpGet(url, callback) },
        onWake = onWake,
    )
    private val timers = mutableMapOf<String, Job>()
    private val paused = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)

    fun start() {
        val script = card.script ?: return
        scriptScope.launch {
            try {
                hostApi.bind(globals)
                LuaSandbox.loadScript(globals, script, card.id)
                callLifecycle("on_init")
                Log.i(TAG, "Script started for card ${card.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start script for card ${card.id}", e)
            }
        }
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        scriptScope.launch {
            try {
                callLifecycle("on_destroy")
            } catch (_: Exception) {
            } finally {
                cancelAllTimers()
                hostApi.clearHttpCallbacks()
                scriptExecutor.close()
            }
        }
    }

    fun pause() {
        paused.set(true)
        cancelAllTimers()
        hostApi.clearHttpCallbacks()
    }

    fun resume() {
        if (destroyed.get()) return
        paused.set(false)
        scriptScope.launch {
            try {
                callLifecycle("on_init")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume script for card ${card.id}", e)
            }
        }
    }

    fun hasActiveTimers(): Boolean = timers.isNotEmpty() && !paused.get() && !destroyed.get()

    fun dispatchEvent(componentId: String, event: String, value: String? = null) {
        if (destroyed.get() || paused.get()) return
        scriptScope.launch {
            try {
                val fn = globals.get("on_event")
                if (fn.isnil() || !fn.isfunction()) return@launch
                if (value == null) {
                    fn.checkfunction().call(LuaValue.valueOf(componentId), LuaValue.valueOf(event))
                } else {
                    fn.checkfunction().call(
                        LuaValue.valueOf(componentId),
                        LuaValue.valueOf(event),
                        LuaValue.valueOf(value),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "on_event failed for card ${card.id}", e)
            }
        }
    }

    private fun scheduleTimer(name: String, seconds: Double) {
        cancelTimer(name)
        val intervalMs = (seconds.coerceAtLeast(0.1) * 1000).toLong()
        timers[name] = serviceScope.launch {
            while (isActive && !destroyed.get() && !paused.get()) {
                delay(intervalMs)
                if (destroyed.get() || paused.get()) break
                scriptScope.launch {
                    try {
                        callTimer(name)
                    } catch (e: Exception) {
                        Log.e(TAG, "on_timer failed for card ${card.id}", e)
                    }
                }
            }
        }
    }

    private fun cancelTimer(name: String) {
        timers.remove(name)?.cancel()
    }

    private fun cancelAllTimers() {
        timers.values.forEach { it.cancel() }
        timers.clear()
    }

    private fun launchHttpGet(url: String, callback: org.luaj.vm2.LuaFunction) {
        val requestId = hostApi.registerHttpCallback(callback)
        serviceScope.launch(Dispatchers.IO) {
            val result = HttpBridge.get(url)
            if (destroyed.get() || paused.get()) {
                hostApi.clearHttpCallbacks()
                return@launch
            }
            withContext(scriptExecutor) {
                val body = if (result.error == null) HttpBridge.bodyToLua(result.body) else LuaValue.NIL
                hostApi.dispatchHttpCallback(requestId, result.status, body, result.error)
            }
        }
    }

    private fun callLifecycle(functionName: String) {
        val fn = globals.get(functionName)
        if (fn.isnil() || !fn.isfunction()) return
        fn.checkfunction().call()
    }

    private fun callTimer(name: String) {
        val fn = globals.get("on_timer")
        if (fn.isnil() || !fn.isfunction()) return
        fn.checkfunction().call(LuaValue.valueOf(name))
    }

    companion object {
        private const val TAG = "CardScript"
    }
}
