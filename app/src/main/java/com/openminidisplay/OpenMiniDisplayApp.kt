package com.openminidisplay

import android.app.Application
import android.content.Context

class OpenMiniDisplayApp : Application() {

    val screenManager: ScreenManager by lazy { ScreenManager(this) }

    companion object {
        fun screenManagerOf(context: Context): ScreenManager {
            return (context.applicationContext as OpenMiniDisplayApp).screenManager
        }
    }
}
