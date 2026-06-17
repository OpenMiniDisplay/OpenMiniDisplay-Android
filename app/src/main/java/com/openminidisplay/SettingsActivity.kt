package com.openminidisplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.openminidisplay.ui.settings.AdvancedSettingsScreen
import com.openminidisplay.ui.settings.SettingsScreen
import com.openminidisplay.ui.theme.OpenMiniDisplayTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OpenMiniDisplayTheme {
                var showAdvanced by rememberSaveable { mutableStateOf(false) }
                if (showAdvanced) {
                    AdvancedSettingsScreen(
                        onBack = { showAdvanced = false },
                    )
                } else {
                    SettingsScreen(
                        onBack = { finish() },
                        onOpenAdvanced = { showAdvanced = true },
                    )
                }
            }
        }
    }
}
