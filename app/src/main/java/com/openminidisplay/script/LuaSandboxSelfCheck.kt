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
                onWake = {},
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
            val localTime = globals.get("local_time")
            check(localTime.isfunction()) { "local_time missing" }
            val time = localTime.checkfunction().call().tojstring()
            check(time.matches(Regex("\\d{2}:\\d{2}:\\d{2}"))) { "local_time format: $time" }
            Log.i("CardScript", "LuaSandboxSelfCheck passed (local_time=$time)")
        } catch (e: Exception) {
            Log.e("CardScript", "LuaSandboxSelfCheck failed", e)
        }
    }
}
