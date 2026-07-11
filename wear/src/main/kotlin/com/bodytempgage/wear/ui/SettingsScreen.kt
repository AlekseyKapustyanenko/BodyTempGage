package com.bodytempgage.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.bodytempgage.common.TempFormat
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.core.DisplayMode
import com.bodytempgage.wear.R
import com.bodytempgage.wear.WearContainer
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: WearContainer,
    settings: AppSettings,
    onChangeDevice: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.title3,
                )
            }

            item {
                ToggleChip(
                    checked = settings.alertEnabled,
                    onCheckedChange = { scope.launch { container.settings.setAlertEnabled(it) } },
                    label = { Text(stringResource(R.string.fever_alert)) },
                    toggleControl = {
                        androidx.wear.compose.material.Icon(
                            imageVector = ToggleChipDefaults.switchIcon(settings.alertEnabled),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                ToggleChip(
                    checked = settings.useFahrenheit,
                    onCheckedChange = { scope.launch { container.settings.setUseFahrenheit(it) } },
                    label = { Text(stringResource(R.string.unit_fahrenheit)) },
                    toggleControl = {
                        androidx.wear.compose.material.Icon(
                            imageVector = ToggleChipDefaults.switchIcon(settings.useFahrenheit),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Thresholds keep the order alertLow < warnLow < warnHigh < alertHigh.
            if (settings.alertEnabled) {
                item {
                    ThresholdRow(
                        label = stringResource(R.string.alert_threshold_high),
                        valueText = TempFormat.formatThreshold(settings.alertHighC, settings.useFahrenheit),
                        onStep = { delta ->
                            scope.launch {
                                container.settings.setAlertHighC(
                                    stepThreshold(settings.alertHighC, delta, settings.warnHighC + 0.1, 43.0),
                                )
                            }
                        },
                    )
                }
                item {
                    ThresholdRow(
                        label = stringResource(R.string.warn_threshold_high),
                        valueText = TempFormat.formatThreshold(settings.warnHighC, settings.useFahrenheit),
                        onStep = { delta ->
                            scope.launch {
                                container.settings.setWarnHighC(
                                    stepThreshold(settings.warnHighC, delta, settings.warnLowC + 0.1, settings.alertHighC - 0.1),
                                )
                            }
                        },
                    )
                }
                item {
                    ThresholdRow(
                        label = stringResource(R.string.warn_threshold_low),
                        valueText = TempFormat.formatThreshold(settings.warnLowC, settings.useFahrenheit),
                        onStep = { delta ->
                            scope.launch {
                                container.settings.setWarnLowC(
                                    stepThreshold(settings.warnLowC, delta, settings.alertLowC + 0.1, settings.warnHighC - 0.1),
                                )
                            }
                        },
                    )
                }
                item {
                    ThresholdRow(
                        label = stringResource(R.string.alert_threshold_low),
                        valueText = TempFormat.formatThreshold(settings.alertLowC, settings.useFahrenheit),
                        onStep = { delta ->
                            scope.launch {
                                container.settings.setAlertLowC(
                                    stepThreshold(settings.alertLowC, delta, 30.0, settings.warnLowC - 0.1),
                                )
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.display_mode),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            items(DisplayMode.entries.size) { index ->
                val mode = DisplayMode.entries[index]
                Chip(
                    onClick = { scope.launch { container.settings.setDisplayMode(mode) } },
                    label = {
                        Text(
                            when (mode) {
                                DisplayMode.GAUGE -> stringResource(R.string.mode_gauge)
                                DisplayMode.BODY -> stringResource(R.string.mode_body)
                                DisplayMode.BOTH -> stringResource(R.string.mode_both)
                            },
                        )
                    },
                    colors = if (mode == settings.displayMode) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Chip(
                    onClick = onChangeDevice,
                    label = { Text(stringResource(R.string.change_device)) },
                    secondaryLabel = settings.selectedName?.let { { Text(it) } },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One 0.1 °C step within [min]..[max], rounded so repeated steps don't drift. */
private fun stepThreshold(current: Double, delta: Double, min: Double, max: Double): Double =
    kotlin.math.round((current + delta).coerceIn(min, max) * 10) / 10.0

@Composable
private fun ThresholdRow(
    label: String,
    valueText: String,
    onStep: (Double) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onStep(-0.1) },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(36.dp),
            ) { Text("−") }
            Text(text = valueText, style = MaterialTheme.typography.title2)
            Button(
                onClick = { onStep(0.1) },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(36.dp),
            ) { Text("+") }
        }
    }
}
