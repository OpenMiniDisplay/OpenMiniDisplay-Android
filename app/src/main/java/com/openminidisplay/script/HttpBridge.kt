package com.openminidisplay.script

import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object HttpBridge {
    fun get(url: String): HttpResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()
            HttpResult(status, body, null)
        } catch (e: Exception) {
            HttpResult(-1, "", e.message ?: "request failed")
        } finally {
            connection.disconnect()
        }
    }

    fun bodyToLua(body: String): LuaValue {
        if (body.isBlank()) return LuaValue.NIL
        return try {
            jsonToLua(JSONObject(body))
        } catch (_: Exception) {
            try {
                jsonToLua(JSONArray(body))
            } catch (_: Exception) {
                LuaValue.valueOf(body)
            }
        }
    }

    private fun jsonToLua(value: Any?): LuaValue {
        return when (value) {
            null, JSONObject.NULL -> LuaValue.NIL
            is Boolean -> LuaValue.valueOf(value)
            is Number -> LuaValue.valueOf(value.toDouble())
            is String -> LuaValue.valueOf(value)
            is JSONObject -> {
                val table = LuaTable()
                value.keys().forEach { key ->
                    table.set(key, jsonToLua(value.get(key)))
                }
                table
            }
            is JSONArray -> {
                val table = LuaTable()
                for (index in 0 until value.length()) {
                    table.set(index + 1, jsonToLua(value.get(index)))
                }
                table
            }
            else -> LuaValue.valueOf(value.toString())
        }
    }
}

data class HttpResult(
    val status: Int,
    val body: String,
    val error: String?,
)
