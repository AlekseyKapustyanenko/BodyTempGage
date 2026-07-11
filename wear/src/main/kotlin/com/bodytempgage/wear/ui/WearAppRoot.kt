package com.bodytempgage.wear.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.bodytempgage.wear.WearContainer
import com.bodytempgage.wear.R
import com.bodytempgage.common.ble.BleEngine
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.wear.service.MonitorService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val PICKER = "picker"
}

@Composable
fun WearAppRoot(container: WearContainer) {
    val context = LocalContext.current

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
        PermissionScreen(onRequest = { permissionLauncher.launch(requiredPermissions()) })
        return
    }

    // Keep monitoring (and alerting) alive in the background while enabled; stop the scan when
    // the user turns monitoring off. Observed live so the tile/settings toggle takes effect at once.
    val monitoringEnabled by container.settings.flow
        .map { it.monitoringEnabled }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(monitoringEnabled) {
        if (monitoringEnabled) MonitorService.start(context) else MonitorService.stop(context)
    }

    // Wait once for the first DataStore emission (avoids an empty first frame). Each screen
    // observes the live settings flow itself: nav destination lambdas hold on to their captured
    // values, so passing the current settings down from here would leave the on-screen
    // destination rendering a stale snapshot until it is revisited.
    val initialSettings by produceState<AppSettings?>(initialValue = null) {
        value = container.settings.flow.first()
    }
    val settings = initialSettings ?: return

    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                container = container,
                initialSettings = settings,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                initialSettings = settings,
                onChangeDevice = { navController.navigate(Routes.PICKER) },
            )
        }
        composable(Routes.PICKER) {
            DevicePickerScreen(
                container = container,
                onSelected = { navController.popBackStack(Routes.MAIN, inclusive = false) },
            )
        }
    }
}

/** BLE scan permissions, plus notifications on API 33+ so alerts can be shown. */
private fun requiredPermissions(): Array<String> {
    val perms = BleEngine.scanPermissions().toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
    }
    return perms.toTypedArray()
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.permissions_text),
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
        )
        Chip(
            onClick = onRequest,
            label = {
                Text(
                    text = stringResource(R.string.grant),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
