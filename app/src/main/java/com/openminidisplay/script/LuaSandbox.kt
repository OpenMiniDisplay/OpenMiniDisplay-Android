package com.openminidisplay.script

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

object LuaSandbox {
    fun createGlobals(): Globals {
        val globals = JsePlatform.standardGlobals()
        globals.set("io", LuaValue.NIL)
        globals.set("os", LuaValue.NIL)
        globals.set("debug", LuaValue.NIL)
        globals.set("loadfile", LuaValue.NIL)
        globals.set("dofile", LuaValue.NIL)
        return globals
    }

    fun loadScript(globals: Globals, script: String, chunkName: String = "card") {
        globals.load(script, chunkName).call()
    }
}
