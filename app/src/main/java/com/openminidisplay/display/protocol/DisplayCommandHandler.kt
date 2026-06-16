package com.openminidisplay.display.protocol

import android.util.Log
import com.openminidisplay.display.repo.DisplayDataRepository
import com.openminidisplay.display.repo.DisplayLayoutRepository
import com.openminidisplay.display.repo.DisplayNavigationRepository

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

        val widgetId = payload.substring(0, separatorIndex).trim()
        val content = payload.substring(separatorIndex + 1).trim()
        if (widgetId.isEmpty() || content.isEmpty()) return false

        val updated = DisplayDataRepository.setRaw(widgetId, content)
        if (!updated) {
            Log.w(TAG, "Failed to update widget '$widgetId'")
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
        DisplayLayoutRepository.replace(layout)
        DisplayNavigationRepository.clampToLayout(layout)
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
        DisplayLayoutRepository.patchPages(pages)
        DisplayNavigationRepository.clampToLayout(DisplayLayoutRepository.layout.value)
        Log.i(TAG, "Layout patched with ${pages.size} page(s)")
        return true
    }

    private fun handleGoto(message: String): Boolean {
        val target = message.drop(5).trim()
        if (target.isEmpty()) return false
        val layout = DisplayLayoutRepository.layout.value
        target.toIntOrNull()?.let { index ->
            DisplayNavigationRepository.goTo(index, layout)
            return true
        }
        DisplayNavigationRepository.goToPageId(target, layout)
        return true
    }
}
