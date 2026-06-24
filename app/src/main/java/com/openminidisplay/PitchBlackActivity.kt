package com.openminidisplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class PitchBlackActivity : ComponentActivity() {

    private val screenManager get() = OpenMiniDisplayApp.screenManagerOf(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenManager.applyScreenPolicy(this)

        setContent {
            val brightness by RuntimeState.brightness.collectAsStateWithLifecycle()

            LaunchedEffect(brightness) {
                screenManager.applyBrightnessToActivity(this@PitchBlackActivity, brightness)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            RemoteDisplayService.notifyUserActivity(this@PitchBlackActivity)
                        }
                    },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (screenManager.shouldKeepScreenOn()) {
            screenManager.configurePreventLock(this)
        } else {
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            screenManager.restoreUserBrightness(this)
        }
    }
}
