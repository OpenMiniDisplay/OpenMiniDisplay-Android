package com.openminidisplay.display.protocol

import android.util.Log
import com.openminidisplay.display.DisplayStore
import com.openminidisplay.script.CardScriptManager

object DisplayCommandHandler {
    private const val TAG = "DisplayCommandHandler"

    fun handle(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false

        return when {
            trimmed.startsWith("SET ", ignoreCase = true) -> handleSet(trimmed)
            trimmed.startsWith("LAYOUT ", ignoreCase = true) -> handleLayout(trimmed)
            trimmed.startsWith("PATCH ", ignoreCase = true) -> handlePatch(trimmed)
            trimmed.startsWith("GOTO ", ignoreCase = true) -> handleGoto(trimmed)
            else -> false
        }
    }

    private fun handleSet(message: String): Boolean {
        val payload = message.drop(4).trim()
        val separatorIndex = payload.indexOf(' ')
        if (separatorIndex <= 0) return false

        val target = payload.substring(0, separatorIndex).trim()
        val content = payload.substring(separatorIndex + 1).trim()
        if (target.isEmpty() || content.isEmpty()) return false

        if (!target.contains('/')) {
            Log.w(TAG, "SET target must use card/component path: $target")
            return false
        }

        val updated = DisplayStore.setRawQualified(target, content)
        if (!updated) {
            Log.w(TAG, "Failed to update component '$target'")
        }
        return updated
    }

    private fun handleLayout(message: String): Boolean {
        val json = message.drop(7).trim()
        val layout = DisplayLayoutParser.parse(json)
        if (layout == null || layout.pages.isEmpty()) {
            Log.w(TAG, "Invalid LAYOUT payload")
            return false
        }
        DisplayStore.replaceLayout(layout)
        DisplayStore.clampPageToLayout(layout)
        CardScriptManager.syncLayout(layout)
        Log.i(TAG, "Layout replaced with ${layout.pages.size} page(s)")
        return true
    }

    private fun handlePatch(message: String): Boolean {
        val json = message.drop(6).trim()
        val pages = DisplayLayoutParser.parsePages(json)
        if (pages.isEmpty()) {
            Log.w(TAG, "Invalid PATCH payload")
            return false
        }
        DisplayStore.patchPages(pages)
        val layout = DisplayStore.layout.value
        DisplayStore.clampPageToLayout(layout)
        CardScriptManager.syncLayout(layout)
        Log.i(TAG, "Layout patched with ${pages.size} page(s)")
        return true
    }

    private fun handleGoto(message: String): Boolean {
        val target = message.drop(5).trim()
        if (target.isEmpty()) return false
        val layout = DisplayStore.layout.value
        target.toIntOrNull()?.let { index ->
            DisplayStore.goTo(index, layout)
            return true
        }
        DisplayStore.goToPageId(target, layout)
        return true
    }
}
