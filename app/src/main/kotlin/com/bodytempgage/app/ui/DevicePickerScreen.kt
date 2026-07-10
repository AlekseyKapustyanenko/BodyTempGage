package com.bodytempgage.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodytempgage.app.AppContainer
import com.bodytempgage.app.R
import com.bodytempgage.app.ble.BleEngine
import com.bodytempgage.core.T201Decoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerScreen(
    container: AppContainer,
    onSelected: (mac: String, name: String?) -> Unit,
) {
    val devices by container.readings.devices.collectAsStateWithLifecycle()
    val scanState by container.bleEngine.state.collectAsStateWithLifecycle()

    val sorted = devices.values.sortedByDescending { it.rssi }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.select_device_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (scanState) {
                    BleEngine.ScanState.BLUETOOTH_OFF -> Text(
                        text = stringResource(R.string.bluetooth_off),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )

                    BleEngine.ScanState.FAILED -> Text(
                        text = stringResource(R.string.scan_failed),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )

                    else -> {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.scanning_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (sorted.isEmpty()) {
                Text(
                    text = stringResource(R.string.picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sorted, key = { it.mac }) { device ->
                        Card(onClick = { onSelected(device.mac, device.name) }) {
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                                },
                                headlineContent = {
                                    Text(device.name ?: T201Decoder.DEVICE_NAME_PREFIX)
                                },
                                supportingContent = {
                                    Text("${device.mac} · ${device.rssi} dBm")
                                },
                                trailingContent = {
                                    device.lastReading?.let {
                                        Text(
                                            TempFormat.format(it.bodyTempC, fahrenheit = false),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
