package com.openminidisplay.script

import android.util.Log
import com.openminidisplay.display.DisplayStore
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LuaHostApi(
    private val cardId: String,
    private val onEvery: (seconds: Double, name: String) -> Unit,
    private val onCancel: (name: String) -> Unit,
    private val onHttpGet: (url: String, callback: LuaFunction) -> Unit,
) {
    private val nextRequestId = AtomicInteger(0)
    private val httpCallbacks = ConcurrentHashMap<Int, LuaFunction>()

    fun bind(globals: org.luaj.vm2.Globals) {
        globals.set("set", object : TwoArgFunction() {
            override fun call(id: LuaValue, value: LuaValue): LuaValue {
                DisplayStore.setRaw(cardId, id.checkjstring(), value.tojstring())
                return LuaValue.NIL
            }
        })
        globals.set("set_prop", object : ThreeArgFunction() {
            override fun call(id: LuaValue, key: LuaValue, value: LuaValue): LuaValue {
                DisplayStore.setComponentProp(cardId, id.checkjstring(), key.checkjstring(), value.tojstring())
                return LuaValue.NIL
            }
        })
        globals.set("every", object : TwoArgFunction() {
            override fun call(seconds: LuaValue, name: LuaValue): LuaValue {
                onEvery(seconds.todouble(), name.checkjstring())
                return LuaValue.NIL
            }
        })
        globals.set("cancel", object : OneArgFunction() {
            override fun call(name: LuaValue): LuaValue {
                onCancel(name.checkjstring())
                return LuaValue.NIL
            }
        })
        globals.set("http_get", object : TwoArgFunction() {
            override fun call(url: LuaValue, callback: LuaValue): LuaValue {
                if (!callback.isfunction()) return LuaValue.NIL
                onHttpGet(url.checkjstring(), callback.checkfunction())
                return LuaValue.NIL
            }
        })
        globals.set("log", object : OneArgFunction() {
            override fun call(message: LuaValue): LuaValue {
                Log.i(TAG, "[$cardId] ${message.tojstring()}")
                return LuaValue.NIL
            }
        })
    }

    fun dispatchHttpCallback(requestId: Int, status: Int, body: LuaValue, error: String?) {
        val callback = httpCallbacks.remove(requestId) ?: return
        callback.call(
            LuaValue.valueOf(status),
            body,
            LuaValue.valueOf(error ?: ""),
        )
    }

    fun registerHttpCallback(callback: LuaFunction): Int {
        val id = nextRequestId.incrementAndGet()
        httpCallbacks[id] = callback
        return id
    }

    fun clearHttpCallbacks() {
        httpCallbacks.clear()
    }

    companion object {
        private const val TAG = "CardScript"
    }
}
