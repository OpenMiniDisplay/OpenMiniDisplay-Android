package com.openminidisplay.display

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File

object DisplayAssetStore {
    private lateinit var cacheDir: File

    fun init(context: Context) {
        if (::cacheDir.isInitialized) return
        cacheDir = File(context.cacheDir, "pushed-assets").apply { mkdirs() }
    }

    fun push(assetId: String, base64: String): Boolean {
        if (!::cacheDir.isInitialized) {
            Log.w(TAG, "push($assetId): not initialized")
            return false
        }
        val safeId = sanitizeId(assetId)
        if (safeId.isEmpty()) return false
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.isEmpty()) return false
            File(cacheDir, safeId).writeBytes(bytes)
            true
        } catch (e: Exception) {
            Log.w(TAG, "push($assetId) failed", e)
            false
        }
    }

    fun resolvePath(ref: String): String? {
        if (!ref.startsWith("asset:", ignoreCase = true)) return null
        if (!::cacheDir.isInitialized) return null
        val id = sanitizeId(ref.removePrefix("asset:"))
        val file = File(cacheDir, id)
        return file.takeIf { it.isFile }?.absolutePath
    }

    private fun sanitizeId(raw: String): String =
        raw.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private const val TAG = "DisplayAssetStore"
}
