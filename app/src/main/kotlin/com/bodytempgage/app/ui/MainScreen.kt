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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.bodytempgage.common.ble.BleEngine
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.common.TempFormat
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
    val rssi by container.readings.latestRssi.collectAsStateWithLifecycle()
    val scanState by container.bleEngine.state.collectAsStateWithLifecycle()
    val monitoring by MonitorService.isRunning.collectAsStateWithLifecycle()
    val history by container.readings.history.collectAsStateWithLifecycle()

    val now = rememberNowMillis()

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
                    val bodyTempC = r?.bodyTempC
                    if (r == null) {
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
                        val bodyColor = when {
                            !settings.alertEnabled || bodyTempC == null ->
                                MaterialTheme.colorScheme.onSurface

                            bodyTempC >= settings.alertHighC || bodyTempC <= settings.alertLowC ->
                                MaterialTheme.colorScheme.error

                            bodyTempC >= settings.warnHighC || bodyTempC <= settings.warnLowC ->
                                warningColor()

                            else -> MaterialTheme.colorScheme.onSurface
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
                                    label = stringResource(R.string.label_body_temp),
                                    big = true,
                                    valueColor = bodyColor,
                                )
                            }

                            DisplayMode.BOTH -> {
                                TempBlock(
                                    value = bodyTempC?.let {
                                        TempFormat.format(it, settings.useFahrenheit)
                                    } ?: "—",
                                    label = stringResource(R.string.label_body_temp),
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
                            batteryPercent = r.batteryPercent,
                            rssi = rssi,
                            lastDataMillis = r.timestampMillis,
                            now = now,
                        )

                        if (bodyTempC == null && settings.displayMode != DisplayMode.GAUGE) {
                            Text(
                                text = stringResource(R.string.not_worn_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Temperature history chart
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chart_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TempChart(
                        samples = history,
                        settings = settings,
                        nowMillis = now,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
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
                            reading?.bodyTempC?.let { TempFormat.format(it, settings.useFahrenheit) } ?: "—",
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
