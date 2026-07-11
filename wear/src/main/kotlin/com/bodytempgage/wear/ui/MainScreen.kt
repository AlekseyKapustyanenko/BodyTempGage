package com.bodytempgage.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.bodytempgage.common.TempFormat
import com.bodytempgage.common.ble.BleEngine
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.core.DisplayMode
import com.bodytempgage.wear.R
import com.bodytempgage.wear.WearContainer
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.sample

/** Amber used for the temperature warning band (matches the phone app's intent). */
private val WarningColor = Color(0xFFFFB300)

/** Minimum spacing between UI refreshes from the reading flow. */
private const val UI_SAMPLE_MILLIS = 1_000L

@Composable
fun MainScreen(
    container: WearContainer,
    settings: AppSettings,
    onOpenSettings: () -> Unit,
) {
    // Advertisements repeat the same measurement several times a second and every reading
    // carries a fresh timestamp, so collecting the raw flow recomposes the list on every scan
    // callback — visible as scroll stutter on the watch. Only recompose when the displayed
    // values change, at most once a second.
    val reading by remember(container) {
        container.readings.latest
            .sample(UI_SAMPLE_MILLIS)
            .distinctUntilChangedBy { r ->
                r?.let { Triple(it.gaugeTempC, it.bodyTempC, it.batteryPercent) }
            }
    }.collectAsStateWithLifecycle(initialValue = container.readings.latest.value)
    val scanState by container.bleEngine.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val r = reading
            if (r == null) {
                item { Text("—", style = MaterialTheme.typography.display1) }
                item {
                    Text(
                        text = when (scanState) {
                            BleEngine.ScanState.BLUETOOTH_OFF -> stringResource(R.string.bluetooth_off)
                            BleEngine.ScanState.FAILED -> stringResource(R.string.scan_failed)
                            else -> stringResource(R.string.searching)
                        },
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val bodyC = r.bodyTempC
                when (settings.displayMode) {
                    DisplayMode.GAUGE -> item {
                        TempBlock(
                            value = TempFormat.format(r.gaugeTempC, settings.useFahrenheit),
                            label = stringResource(R.string.label_gauge_temp),
                            big = true,
                        )
                    }

                    DisplayMode.BODY -> item {
                        TempBlock(
                            value = bodyC?.let { TempFormat.format(it, settings.useFahrenheit) } ?: "—",
                            label = stringResource(R.string.label_body_temp),
                            big = true,
                            valueColor = bodyColorFor(bodyC, settings),
                        )
                    }

                    DisplayMode.BOTH -> {
                        item {
                            TempBlock(
                                value = bodyC?.let { TempFormat.format(it, settings.useFahrenheit) } ?: "—",
                                label = stringResource(R.string.label_body_temp),
                                big = true,
                                valueColor = bodyColorFor(bodyC, settings),
                            )
                        }
                        item {
                            TempBlock(
                                value = TempFormat.format(r.gaugeTempC, settings.useFahrenheit),
                                label = stringResource(R.string.label_gauge_temp),
                                big = false,
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.battery, r.batteryPercent),
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }

                if (bodyC == null && settings.displayMode != DisplayMode.GAUGE) {
                    item {
                        Text(
                            text = stringResource(R.string.not_worn_hint),
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                Chip(
                    onClick = onOpenSettings,
                    label = { Text(stringResource(R.string.settings)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Colours the body value: error in the alert bands, amber in the warning bands. */
@Composable
private fun bodyColorFor(bodyC: Double?, settings: AppSettings): Color = when {
    !settings.alertEnabled || bodyC == null -> MaterialTheme.colors.onBackground
    bodyC >= settings.alertHighC || bodyC <= settings.alertLowC -> MaterialTheme.colors.error
    bodyC >= settings.warnHighC || bodyC <= settings.warnLowC -> WarningColor
    else -> MaterialTheme.colors.onBackground
}

@Composable
private fun TempBlock(
    value: String,
    label: String,
    big: Boolean,
    valueColor: Color = MaterialTheme.colors.onBackground,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = if (big) MaterialTheme.typography.display1 else MaterialTheme.typography.title2,
            color = valueColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}
