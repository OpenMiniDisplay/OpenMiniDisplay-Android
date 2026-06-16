package com.openminidisplay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.ui.display.DisplayHost
import com.openminidisplay.ui.theme.OpenMiniDisplayTheme

class MainActivity : ComponentActivity() {

    private val screenManager get() = OpenMiniDisplayApp.screenManagerOf(this)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op; service still runs without notification visibility on older APIs */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        requestStartupPermissions()
        RemoteDisplayService.start(applicationContext)

        setContent {
            OpenMiniDisplayTheme {
                val brightness by RuntimeState.brightness.collectAsStateWithLifecycle()
                LaunchedEffect(brightness) {
                    screenManager.applyBrightnessToActivity(this@MainActivity, brightness)
                }
                DisplayHost(
                    onUserActivity = { RemoteDisplayService.notifyUserActivity(this@MainActivity) },
                )
            }
        }

        handleBeginLowPowerIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBeginLowPowerIfNeeded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun handleBeginLowPowerIfNeeded() {
        if (intent?.action != ScreenManager.ACTION_BEGIN_LOW_POWER) return
        intent.action = null

        screenManager.configurePreventLock(this)
        screenManager.startGradualDim(this) {
            screenManager.showPitchBlackScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        screenManager.configurePreventLock(this)
        applyConnectedUiState()
    }

    private fun applyConnectedUiState() {
        screenManager.keepScreenOn(this, enabled = true)
    }

    private fun requestStartupPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!Settings.System.canWrite(this)) {
            screenManager.openWriteSettingsScreen()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
