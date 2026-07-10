package com.bodytempgage.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodytempgage.app.AppContainer
import com.bodytempgage.app.R
import com.bodytempgage.app.ble.BleEngine
import kotlinx.coroutines.launch

enum class Screen { Main, Picker, Settings }

@Composable
fun AppRoot(container: AppContainer) {
    val context = LocalContext.current
    val settingsOrNull by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var permissionsGranted by remember { mutableStateOf(BleEngine.hasScanPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = BleEngine.hasScanPermission(context)
        container.bleEngine.refresh()
    }

    // Scan while the UI is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> container.bleEngine.start(BleEngine.Client.UI)
                Lifecycle.Event.ON_STOP -> container.bleEngine.stop(BleEngine.Client.UI)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!permissionsGranted) {
        PermissionScreen(onRequest = { permissionLauncher.launch(BleEngine.scanPermissions()) })
        return
    }

    // Wait for the first DataStore emission so the device picker doesn't flash on launch.
    val settings = settingsOrNull ?: return

    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    val effectiveScreen = if (settings.selectedMac == null && screen == Screen.Main) {
        Screen.Picker
    } else {
        screen
    }

    when (effectiveScreen) {
        Screen.Picker -> {
            BackHandler(enabled = settings.selectedMac != null) { screen = Screen.Main }
            DevicePickerScreen(
                container = container,
                onSelected = { mac, name ->
                    scope.launch {
                        container.settings.setSelectedDevice(mac, name)
                        container.readings.resetLatest()
                    }
                    screen = Screen.Main
                },
            )
        }

        Screen.Settings -> {
            BackHandler { screen = Screen.Main }
            SettingsScreen(
                container = container,
                settings = settings,
                onChangeDevice = { screen = Screen.Picker },
                onBack = { screen = Screen.Main },
            )
        }

        Screen.Main -> MainScreen(
            container = container,
            settings = settings,
            onOpenSettings = { screen = Screen.Settings },
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.permissions_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant))
        }
        Button(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                ),
            )
        }) {
            Text(stringResource(R.string.open_app_settings))
        }
    }
}
