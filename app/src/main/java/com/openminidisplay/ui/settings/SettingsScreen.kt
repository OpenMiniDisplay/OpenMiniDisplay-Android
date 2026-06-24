package com.openminidisplay.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.R
import com.openminidisplay.settings.AppColorScheme
import com.openminidisplay.settings.AppPreferences

private data class DelayOption(val labelRes: Int, val ms: Long)

private val lowPowerOptions = listOf(
    DelayOption(R.string.settings_delay_30s, 30_000L),
    DelayOption(R.string.settings_delay_60s, 60_000L),
    DelayOption(R.string.settings_delay_90s, 90_000L),
    DelayOption(R.string.settings_delay_2m, 120_000L),
    DelayOption(R.string.settings_delay_5m, 300_000L),
)

private val dimOptions = listOf(
    DelayOption(R.string.settings_delay_5s, 5_000L),
    DelayOption(R.string.settings_delay_10s, 10_000L),
    DelayOption(R.string.settings_delay_20s, 20_000L),
    DelayOption(R.string.settings_delay_30s, 30_000L),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme by AppPreferences.colorScheme.collectAsStateWithLifecycle()
    val lowPowerDelay by AppPreferences.lowPowerDelayMs.collectAsStateWithLifecycle()
    val gradualDim by AppPreferences.gradualDimMs.collectAsStateWithLifecycle()
    val keepScreenOnPlugged by AppPreferences.keepScreenOnWhenPlugged.collectAsStateWithLifecycle()
    val listenPort by AppPreferences.listenPort.collectAsStateWithLifecycle()
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    SettingsBackButton(onClick = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_entry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                Text(
                    text = stringResource(R.string.settings_color_scheme),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsChoiceChip(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = colorScheme == AppColorScheme.DARK,
                        onClick = { AppPreferences.setColorScheme(AppColorScheme.DARK) },
                    )
                    SettingsChoiceChip(
                        label = stringResource(R.string.settings_theme_light),
                        selected = colorScheme == AppColorScheme.LIGHT,
                        onClick = { AppPreferences.setColorScheme(AppColorScheme.LIGHT) },
                    )
                    SettingsChoiceChip(
                        label = stringResource(R.string.settings_theme_oled),
                        selected = colorScheme == AppColorScheme.OLED,
                        onClick = { AppPreferences.setColorScheme(AppColorScheme.OLED) },
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_power)) {
                Text(
                    text = stringResource(R.string.settings_low_power_delay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    lowPowerOptions.forEach { option ->
                        SettingsChoiceChip(
                            label = stringResource(option.labelRes),
                            selected = lowPowerDelay == option.ms,
                            onClick = { AppPreferences.setLowPowerDelayMs(option.ms) },
                        )
                    }
                }

                SettingsDivider()

                Text(
                    text = stringResource(R.string.settings_gradual_dim),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dimOptions.forEach { option ->
                        SettingsChoiceChip(
                            label = stringResource(option.labelRes),
                            selected = gradualDim == option.ms,
                            onClick = { AppPreferences.setGradualDimMs(option.ms) },
                        )
                    }
                }

                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_keep_screen_plugged),
                    subtitle = stringResource(R.string.settings_keep_screen_plugged_summary),
                    trailing = {
                        Switch(
                            checked = keepScreenOnPlugged,
                            onCheckedChange = AppPreferences::setKeepScreenOnWhenPlugged,
                        )
                    },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_system)) {
                SettingsRow(
                    title = stringResource(R.string.settings_brightness_permission),
                    subtitle = stringResource(R.string.settings_brightness_permission_summary),
                    onClick = {
                        if (!Settings.System.canWrite(context)) {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    trailing = {
                        Text(
                            text = if (Settings.System.canWrite(context)) {
                                stringResource(R.string.settings_granted)
                            } else {
                                stringResource(R.string.settings_not_granted)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_battery_optimization),
                    subtitle = stringResource(R.string.settings_battery_optimization_summary),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val powerManager =
                                context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }
                    },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    trailing = {
                        Text(
                            text = versionName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_listen_port),
                    subtitle = stringResource(R.string.settings_listen_port_summary),
                    trailing = {
                        Text(
                            text = listenPort.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            SettingsCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_advanced),
                    subtitle = stringResource(R.string.settings_advanced_summary),
                    onClick = onOpenAdvanced,
                    trailing = {
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
