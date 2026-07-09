package com.openminidisplay.display.model

import android.util.Log

object ComponentPresentationSelfCheck {
    fun run() {
        try {
            val slot = ComponentSlot(
                id = "t",
                type = ComponentType.METRIC,
                fit = true,
                align = ComponentAlign.CENTER,
            )
            val props = ComponentProps()
            val multi = ComponentPresentation.resolve(slot, props, cardMultiComponent = true)
            check(multi.fit && multi.align == ComponentAlign.CENTER && multi.fill) {
                "multi fit/center/fill: $multi"
            }
            val single = ComponentPresentation.resolve(slot, props, cardMultiComponent = false)
            check(single.fill) { "borderless defaults fill: $single" }
            Log.i("CardScript", "ComponentPresentationSelfCheck passed")
        } catch (e: Exception) {
            Log.e("CardScript", "ComponentPresentationSelfCheck failed", e)
        }
    }
}
