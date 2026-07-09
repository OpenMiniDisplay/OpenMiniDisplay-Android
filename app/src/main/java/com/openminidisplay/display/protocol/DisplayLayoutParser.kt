package com.openminidisplay.display.protocol

import com.openminidisplay.display.model.ComponentAlign
import com.openminidisplay.display.model.ComponentSlot
import com.openminidisplay.display.model.ComponentType
import com.openminidisplay.display.model.DisplayCard
import com.openminidisplay.display.model.DisplayLayout
import com.openminidisplay.display.model.DisplayPage
import com.openminidisplay.display.model.GridSpec
import com.openminidisplay.display.model.TextStyleKind
import org.json.JSONArray
import org.json.JSONObject

object DisplayLayoutParser {
    fun parse(json: String): DisplayLayout? {
        return try {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            if (version < 2) return null
            parseObject(root)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseObject(root: JSONObject): DisplayLayout {
        val version = root.optInt("version", 2)
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
            val version = root.optInt("version", 2)
            if (version < 2) return emptyList()
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
        val grid = parseGrid(gridJson)

        val cardsArray = json.optJSONArray("cards") ?: JSONArray()
        val cards = buildList {
            for (index in 0 until cardsArray.length()) {
                cardsArray.optJSONObject(index)?.let { parseCard(it) }?.let { add(it) }
            }
        }
        return DisplayPage(id = id, grid = grid, cards = cards)
    }

    private fun parseCard(json: JSONObject): DisplayCard? {
        val id = json.optString("id", "")
        if (id.isBlank()) return null

        val gridJson = json.optJSONObject("grid")
        val grid = parseGrid(gridJson)
        val script = json.optString("script", "").takeIf { it.isNotBlank() }

        val componentsArray = json.optJSONArray("components") ?: JSONArray()
        val components = buildList {
            for (index in 0 until componentsArray.length()) {
                componentsArray.optJSONObject(index)?.let { parseComponent(it) }?.let { add(it) }
            }
        }
        if (components.isEmpty()) return null

        return DisplayCard(
            id = id,
            row = json.optInt("row", 0).coerceAtLeast(0),
            col = json.optInt("col", 0).coerceAtLeast(0),
            rowSpan = json.optInt("rowSpan", 1).coerceAtLeast(1),
            colSpan = json.optInt("colSpan", 1).coerceAtLeast(1),
            grid = grid,
            script = script,
            components = components,
        )
    }

    private fun parseComponent(json: JSONObject): ComponentSlot? {
        val id = json.optString("id", "")
        val type = ComponentType.fromRaw(json.optString("type", "")) ?: return null
        if (id.isBlank()) return null

        return ComponentSlot(
            id = id,
            type = type,
            row = json.optInt("row", 0).coerceAtLeast(0),
            col = json.optInt("col", 0).coerceAtLeast(0),
            rowSpan = json.optInt("rowSpan", 1).coerceAtLeast(1),
            colSpan = json.optInt("colSpan", 1).coerceAtLeast(1),
            style = TextStyleKind.fromRaw(json.optString("style", "body")),
            label = json.optString("label", "").takeIf { it.isNotBlank() },
            defaultChecked = json.optBoolean("checked", false),
            align = ComponentAlign.fromRaw(json.optString("align", "").takeIf { it.isNotBlank() }),
            fill = if (json.has("fill")) json.optBoolean("fill") else null,
            fit = json.optBoolean("fit", false),
            scale = json.optDouble("scale", Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
            showLabel = if (json.has("showLabel")) json.optBoolean("showLabel") else null,
        )
    }

    private fun parseGrid(json: JSONObject?): GridSpec {
        if (json == null) return GridSpec()
        return GridSpec(
            rows = json.optInt("rows", 1).coerceAtLeast(1),
            cols = json.optInt("cols", 1).coerceAtLeast(1),
            gap = json.optInt("gap", 8).coerceAtLeast(0),
            padding = json.optInt("padding", 16).coerceAtLeast(0),
        )
    }
}
