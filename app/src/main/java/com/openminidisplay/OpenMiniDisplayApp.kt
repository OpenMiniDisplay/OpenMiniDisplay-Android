package com.openminidisplay

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.openminidisplay.settings.AppPreferences
import java.lang.ref.WeakReference

class OpenMiniDisplayApp : Application() {

    val screenManager: ScreenManager by lazy { ScreenManager(this) }

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        registerActivityLifecycleCallbacks(MainActivityTracker)
    }

    companion object {
        @Volatile
        var resumedMainActivity: MainActivity? = null
            private set

        fun screenManagerOf(context: Context): ScreenManager {
            return (context.applicationContext as OpenMiniDisplayApp).screenManager
        }
    }

    private object MainActivityTracker : ActivityLifecycleCallbacks {
        private var ref = WeakReference<MainActivity>(null)

        override fun onActivityResumed(activity: Activity) {
            if (activity is MainActivity) {
                ref = WeakReference(activity)
                resumedMainActivity = activity
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity is MainActivity && ref.get() === activity) {
                resumedMainActivity = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            if (activity is MainActivity && ref.get() === activity) {
                resumedMainActivity = null
            }
        }
    }
}
