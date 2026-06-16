package com.openminidisplay.display.protocol

import com.openminidisplay.display.model.DisplayLayout
import com.openminidisplay.display.model.DisplayPage
import com.openminidisplay.display.model.GridSpec
import com.openminidisplay.display.model.TextStyleKind
import com.openminidisplay.display.model.WidgetSlot
import com.openminidisplay.display.model.WidgetType
import org.json.JSONArray
import org.json.JSONObject

object DisplayLayoutParser {
    fun parse(json: String): DisplayLayout? {
        return try {
            parseObject(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseObject(root: JSONObject): DisplayLayout {
        val version = root.optInt("version", 1)
        val pagesArray = root.optJSONArray("pages") ?: JSONArray()
        val pages = buildList {
            for (index in 0 until pagesArray.length()) {
                pagesArray.optJSONObject(index)?.let { add(parsePage(it)) }
            }
        }
        return DisplayLayout(version = version, pages = pages)
    }

    fun parsePages(json: String): List<DisplayPage> {
        return try {
            val root = JSONObject(json)
            val pagesArray = root.optJSONArray("pages") ?: return emptyList()
            buildList {
                for (index in 0 until pagesArray.length()) {
                    pagesArray.optJSONObject(index)?.let { add(parsePage(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePage(json: JSONObject): DisplayPage {
        val id = json.optString("id", "page")
        val gridJson = json.optJSONObject("grid")
        val grid = if (gridJson != null) {
            GridSpec(
                rows = gridJson.optInt("rows", 1).coerceAtLeast(1),
                cols = gridJson.optInt("cols", 1).coerceAtLeast(1),
                gap = gridJson.optInt("gap", 8).coerceAtLeast(0),
                padding = gridJson.optInt("padding", 16).coerceAtLeast(0),
            )
        } else {
            GridSpec()
        }

        val widgetsArray = json.optJSONArray("widgets") ?: JSONArray()
        val widgets = buildList {
            for (index in 0 until widgetsArray.length()) {
                val widgetJson = widgetsArray.optJSONObject(index) ?: continue
                parseWidget(widgetJson)?.let { add(it) }
            }
        }
        return DisplayPage(id = id, grid = grid, widgets = widgets)
    }

    private fun parseWidget(json: JSONObject): WidgetSlot? {
        val id = json.optString("id", "")
        val type = WidgetType.fromRaw(json.optString("type", "")) ?: return null
        if (id.isBlank()) return null

        return WidgetSlot(
            id = id,
            type = type,
            row = json.optInt("row", 0).coerceAtLeast(0),
            col = json.optInt("col", 0).coerceAtLeast(0),
            rowSpan = json.optInt("rowSpan", 1).coerceAtLeast(1),
            colSpan = json.optInt("colSpan", 1).coerceAtLeast(1),
            style = TextStyleKind.fromRaw(json.optString("style", "body")),
            label = json.optString("label", "").takeIf { it.isNotBlank() },
        )
    }
}
