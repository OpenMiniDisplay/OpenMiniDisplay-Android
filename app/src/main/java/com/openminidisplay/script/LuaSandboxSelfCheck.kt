package com.openminidisplay.script

import android.util.Log
import org.luaj.vm2.LuaValue

object LuaSandboxSelfCheck {
    fun run() {
        try {
            val globals = LuaSandbox.createGlobals()
            val hostApi = LuaHostApi(
                cardId = "selfcheck",
                onEvery = { _, _ -> },
                onCancel = { _ -> },
                onHttpGet = { _, _ -> },
            )
            hostApi.bind(globals)
            LuaSandbox.loadScript(
                globals,
                """
                function on_init()
                  set("title", "ok")
                  set_prop("btn", "label", "Go")
                end
                """.trimIndent(),
                "selfcheck",
            )
            val onInit = globals.get("on_init")
            check(onInit.isfunction()) { "on_init missing" }
            onInit.checkfunction().call()
            Log.i("CardScript", "LuaSandboxSelfCheck passed")
        } catch (e: Exception) {
            Log.e("CardScript", "LuaSandboxSelfCheck failed", e)
        }
    }
}
