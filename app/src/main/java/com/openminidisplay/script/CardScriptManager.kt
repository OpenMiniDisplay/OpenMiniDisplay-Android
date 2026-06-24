package com.openminidisplay.script

import android.util.Log
import com.openminidisplay.display.DisplayStore
import com.openminidisplay.display.model.DisplayLayout
import kotlinx.coroutines.CoroutineScope

object CardScriptManager {
    private val runtimes = mutableMapOf<String, LuaCardRuntime>()
    private var serviceScope: CoroutineScope? = null
    private var started = false
    private var paused = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        paused = false
        serviceScope = scope
        // ponytail: explicit syncLayout from LAYOUT/PATCH/resume — StateFlow skips equal layouts
        syncLayout(DisplayStore.layout.value)
        LuaSandboxSelfCheck.run()
    }

    fun stopAll() {
        runtimes.values.forEach { it.destroy() }
        runtimes.clear()
        started = false
        paused = false
        serviceScope = null
    }

    fun syncLayout(layout: DisplayLayout = DisplayStore.layout.value) {
        val scope = serviceScope ?: return
        val scriptedCards = layout.pages
            .flatMap { it.cards }
            .filter { !it.script.isNullOrBlank() }
            .associateBy { it.id }

        val toRemove = runtimes.keys.filter { it !in scriptedCards.keys }
        toRemove.forEach { cardId ->
            runtimes.remove(cardId)?.destroy()
        }

        scriptedCards.forEach { (cardId, card) ->
            runtimes.remove(cardId)?.destroy()
            val runtime = LuaCardRuntime(card, scope)
            runtimes[cardId] = runtime
            if (!paused) {
                runtime.start()
            } else {
                Log.i(TAG, "Script for card $cardId loaded while paused; will start on resume")
            }
        }
    }

    fun dispatchEvent(cardId: String, componentId: String, event: String, value: String? = null) {
        val runtime = runtimes[cardId]
        if (runtime == null) {
            Log.w(TAG, "dispatchEvent($cardId/$componentId/$event): no script runtime")
            return
        }
        runtime.dispatchEvent(componentId, event, value)
    }

    fun pauseAll() {
        paused = true
        runtimes.values.forEach { it.pause() }
    }

    fun resumeAll() {
        paused = false
        syncLayout(DisplayStore.layout.value)
    }

    private const val TAG = "CardScript"
}
