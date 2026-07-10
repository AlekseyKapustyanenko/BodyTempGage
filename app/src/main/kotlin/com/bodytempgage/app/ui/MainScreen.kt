package com.bodytempgage.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodytempgage.app.AppContainer
import com.bodytempgage.app.R
import com.bodytempgage.app.ble.BleEngine
import com.bodytempgage.app.ble.GattClient
import com.bodytempgage.app.data.AppSettings
import com.bodytempgage.app.service.MonitorService
import com.bodytempgage.core.DisplayMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    container: AppContainer,
    settings: AppSettings,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reading by container.readings.latest.collectAsStateWithLifecycle()
    val liveRaw by container.readings.liveBodyTemp.collectAsStateWithLifecycle()
    val rssi by container.readings.latestRssi.collectAsStateWithLifecycle()
    val scanState by container.bleEngine.state.collectAsStateWithLifecycle()
    val gattState by container.gattClient.state.collectAsStateWithLifecycle()
    val monitoring by MonitorService.isRunning.collectAsStateWithLifecycle()

    val now = rememberNowMillis()
    // The gauge's own reading (GATT) wins over the advertisement estimate while fresh.
    val live = liveRaw?.takeIf { now - it.timestampMillis < MonitorService.LIVE_FRESH_MILLIS }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        MonitorService.start(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(settings.selectedName ?: stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Display mode selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DisplayMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.displayMode == mode,
                        onClick = { scope.launch { container.settings.setDisplayMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DisplayMode.entries.size,
                        ),
                    ) {
                        Text(
                            when (mode) {
                                DisplayMode.GAUGE -> stringResource(R.string.mode_gauge)
                                DisplayMode.BODY -> stringResource(R.string.mode_body)
                                DisplayMode.BOTH -> stringResource(R.string.mode_both)
                            },
                        )
                    }
                }
            }

            // Temperature card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val r = reading
                    val bodyTempC = live?.tempC ?: r?.bodyTempC
                    if (r == null && bodyTempC == null) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.displayLarge,
                        )
                        Text(
                            text = when (scanState) {
                                BleEngine.ScanState.BLUETOOTH_OFF -> stringResource(R.string.bluetooth_off)
                                BleEngine.ScanState.FAILED -> stringResource(R.string.scan_failed)
                                else -> stringResource(R.string.searching)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val fever = settings.alertEnabled && bodyTempC != null &&
                            bodyTempC >= settings.alertThresholdC
                        val bodyColor =
                            if (fever) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        val bodySuffix = if (live != null) {
                            " · " + stringResource(R.string.source_device)
                        } else {
                            ""
                        }

                        when (settings.displayMode) {
                            DisplayMode.GAUGE -> {
                                TempBlock(
                                    value = r?.let {
                                        TempFormat.format(it.gaugeTempC, settings.useFahrenheit)
                                    } ?: "—",
                                    label = stringResource(R.string.label_gauge_temp),
                                    big = true,
                                )
                            }

                            DisplayMode.BODY -> {
                                TempBlock(
                                    value = bodyTempC?.let {
                                        TempFormat.format(it, settings.useFahrenheit)
                                    } ?: "—",
                                    label = stringResource(R.string.label_body_temp) + bodySuffix,
                                    big = true,
                                    valueColor = bodyColor,
                                )
                            }

                            DisplayMode.BOTH -> {
                                TempBlock(
                                    value = bodyTempC?.let {
                                        TempFormat.format(it, settings.useFahrenheit)
                                    } ?: "—",
                                    label = stringResource(R.string.label_body_temp) + bodySuffix,
                                    big = false,
                                    valueColor = bodyColor,
                                )
                                TempBlock(
                                    value = r?.let {
                                        TempFormat.format(it.gaugeTempC, settings.useFahrenheit)
                                    } ?: "—",
                                    label = stringResource(R.string.label_gauge_temp),
                                    big = false,
                                )
                            }
                        }

                        StatusRow(
                            batteryPercent = r?.batteryPercent,
                            rssi = if (r != null) rssi else null,
                            lastDataMillis = maxOf(
                                r?.timestampMillis ?: 0L,
                                live?.timestampMillis ?: 0L,
                            ),
                            now = now,
                        )
                    }
                }
            }

            // GATT "exact reading" connection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.connect_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when (gattState) {
                            GattClient.State.DISCONNECTED -> stringResource(R.string.connect_desc)
                            GattClient.State.CONNECTING -> stringResource(R.string.connect_connecting)
                            GattClient.State.CONNECTED -> stringResource(R.string.connect_connected)
                            GattClient.State.FAILED -> stringResource(R.string.connect_failed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (gattState == GattClient.State.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (gattState) {
                            GattClient.State.DISCONNECTED, GattClient.State.FAILED -> Button(
                                onClick = {
                                    val mac = settings.selectedMac ?: return@Button
                                    scope.launch { container.settings.setGattRequested(true) }
                                    container.gattClient.connect(mac)
                                },
                                enabled = settings.selectedMac != null,
                            ) {
                                Text(stringResource(R.string.connect))
                            }

                            GattClient.State.CONNECTING -> {
                                CircularProgressIndicator(modifier = Modifier.width(24.dp))
                                Spacer(Modifier.width(12.dp))
                                OutlinedButton(onClick = {
                                    scope.launch { container.settings.setGattRequested(false) }
                                    container.gattClient.disconnect()
                                }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }

                            GattClient.State.CONNECTED -> OutlinedButton(onClick = {
                                scope.launch { container.settings.setGattRequested(false) }
                                container.gattClient.disconnect()
                            }) {
                                Text(stringResource(R.string.disconnect))
                            }
                        }
                    }
                }
            }

            // Background monitoring switch
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.monitoring),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.monitoring_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = monitoring,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    MonitorService.start(context)
                                }
                            } else {
                                MonitorService.stop(context)
                            }
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.warmup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Raw sensor details
            var detailsExpanded by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                Icon(
                    if (detailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.details))
            }
            if (detailsExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DetailRow(
                            stringResource(R.string.raw_skin),
                            reading?.let { TempFormat.format(it.gaugeTempC, settings.useFahrenheit) } ?: "—",
                        )
                        DetailRow(
                            stringResource(R.string.raw_ambient),
                            reading?.let { TempFormat.format(it.ambientTempC, settings.useFahrenheit) } ?: "—",
                        )
                        DetailRow(
                            stringResource(R.string.detail_estimate),
                            reading?.let { TempFormat.format(it.bodyTempC, settings.useFahrenheit) } ?: "—",
                        )
                        DetailRow(
                            stringResource(R.string.device_mac),
                            settings.selectedMac ?: "—",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TempBlock(
    value: String,
    label: String,
    big: Boolean,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = value,
        style = if (big) {
            MaterialTheme.typography.displayLarge
        } else {
            MaterialTheme.typography.displayMedium
        },
        color = valueColor,
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusRow(batteryPercent: Int?, rssi: Int?, lastDataMillis: Long, now: Long) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (batteryPercent != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    batteryIcon(batteryPercent),
                    contentDescription = stringResource(R.string.battery),
                    tint = if (batteryPercent <= 15) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = "$batteryPercent%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (rssi != null) {
            Text(
                text = "$rssi dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = ageText(now - lastDataMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun ageText(ageMillis: Long): String {
    val seconds = (ageMillis / 1000).coerceAtLeast(0)
    return when {
        seconds < 5 -> stringResource(R.string.just_now)
        seconds < 60 -> stringResource(R.string.seconds_ago, seconds)
        else -> stringResource(R.string.minutes_ago, seconds / 60)
    }
}

fun batteryIcon(percent: Int): ImageVector = when {
    percent >= 90 -> Icons.Filled.BatteryFull
    percent >= 65 -> Icons.Filled.Battery6Bar
    percent >= 40 -> Icons.Filled.Battery4Bar
    percent >= 15 -> Icons.Filled.Battery2Bar
    else -> Icons.Filled.Battery0Bar
}
