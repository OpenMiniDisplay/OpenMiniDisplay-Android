package com.openminidisplay.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.R
import com.openminidisplay.RemoteDisplayService
import com.openminidisplay.settings.AppPreferences

private data class TimeoutOption(val labelRes: Int, val ms: Long)

private val heartbeatOptions = listOf(
    TimeoutOption(R.string.settings_delay_3s, 3_000L),
    TimeoutOption(R.string.settings_delay_5s, 5_000L),
    TimeoutOption(R.string.settings_delay_10s, 10_000L),
    TimeoutOption(R.string.settings_delay_30s, 30_000L),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val listenPort by AppPreferences.listenPort.collectAsStateWithLifecycle()
    val heartbeatTimeout by AppPreferences.heartbeatTimeoutMs.collectAsStateWithLifecycle()
    var portText by rememberSaveable(listenPort) { mutableStateOf(listenPort.toString()) }
    var portError by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_advanced_title)) },
                navigationIcon = { SettingsBackButton(onClick = onBack) },
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
                text = stringResource(R.string.settings_advanced_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsSection(title = stringResource(R.string.settings_section_network)) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it.filter { ch -> ch.isDigit() }.take(5)
                        portError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    label = { Text(stringResource(R.string.settings_listen_port)) },
                    supportingText = {
                        if (portError) {
                            Text(stringResource(R.string.settings_port_invalid))
                        } else {
                            Text(stringResource(R.string.settings_port_range))
                        }
                    },
                    isError = portError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (port == null || port !in 1024..65_535) {
                            portError = true
                            return@Button
                        }
                        if (port != listenPort) {
                            AppPreferences.setListenPort(port)
                            RemoteDisplayService.restartListener(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_apply_port))
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_protocol)) {
                Text(
                    text = stringResource(R.string.settings_heartbeat_timeout),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    heartbeatOptions.forEach { option ->
                        SettingsChoiceChip(
                            label = stringResource(option.labelRes),
                            selected = heartbeatTimeout == option.ms,
                            onClick = { AppPreferences.setHeartbeatTimeoutMs(option.ms) },
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_service)) {
                SettingsRow(
                    title = stringResource(R.string.settings_restart_listener),
                    subtitle = stringResource(R.string.settings_restart_listener_summary),
                    onClick = { RemoteDisplayService.restartListener(context) },
                )
            }
        }
    }
}
