package com.openminidisplay.display

import android.util.Log

object DisplayAssetStoreSelfCheck {
    fun run() {
        try {
            val decoded = android.util.Base64.decode("R0lGODlh", android.util.Base64.DEFAULT)
            check(decoded.isNotEmpty()) { "base64 decode" }
            val bad = DisplayAssetStore.resolvePath("asset:missing-file.gif")
            check(bad == null) { "missing asset should be null" }
            Log.i("DisplayAssetStore", "DisplayAssetStoreSelfCheck passed")
        } catch (e: Exception) {
            Log.e("DisplayAssetStore", "DisplayAssetStoreSelfCheck failed", e)
        }
    }
}
