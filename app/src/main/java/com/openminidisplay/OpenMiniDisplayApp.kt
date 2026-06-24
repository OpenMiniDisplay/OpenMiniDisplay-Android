package com.openminidisplay

import android.app.Application
import android.content.Context
import com.openminidisplay.settings.AppPreferences

class OpenMiniDisplayApp : Application() {

    val screenManager: ScreenManager by lazy { ScreenManager(this) }

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
    }

    companion object {
        fun screenManagerOf(context: Context): ScreenManager {
            return (context.applicationContext as OpenMiniDisplayApp).screenManager
        }
    }
}
