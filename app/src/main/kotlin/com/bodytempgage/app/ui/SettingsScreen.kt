package com.bodytempgage.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bodytempgage.app.AppContainer
import com.bodytempgage.app.R
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.common.TempFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: AppSettings,
    onChangeDevice: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.unit_fahrenheit),
                        checked = settings.useFahrenheit,
                        onCheckedChange = {
                            scope.launch { container.settings.setUseFahrenheit(it) }
                        },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingSwitchRow(
                        title = stringResource(R.string.fever_alert),
                        subtitle = stringResource(R.string.fever_alert_desc),
                        checked = settings.alertEnabled,
                        onCheckedChange = {
                            scope.launch { container.settings.setAlertEnabled(it) }
                        },
                    )
                    if (settings.alertEnabled) {
                        HorizontalDivider()
                        // Thresholds keep the order alertLow < warnLow < warnHigh < alertHigh.
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
                        ThresholdRow(
                            label = stringResource(R.string.warn_threshold_high),
                            valueText = TempFormat.formatThreshold(settings.warnHighC, settings.useFahrenheit),
                            onStep = { delta ->
                                scope.launch {
                                    container.settings.setWarnHighC(
                                        stepThreshold(
                                            settings.warnHighC,
                                            delta,
                                            settings.warnLowC + 0.1,
                                            settings.alertHighC - 0.1,
                                        ),
                                    )
                                }
                            },
                        )
                        ThresholdRow(
                            label = stringResource(R.string.warn_threshold_low),
                            valueText = TempFormat.formatThreshold(settings.warnLowC, settings.useFahrenheit),
                            onStep = { delta ->
                                scope.launch {
                                    container.settings.setWarnLowC(
                                        stepThreshold(
                                            settings.warnLowC,
                                            delta,
                                            settings.alertLowC + 0.1,
                                            settings.warnHighC - 0.1,
                                        ),
                                    )
                                }
                            },
                        )
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
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    settings.selectedName?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    settings.selectedMac?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onChangeDevice) {
                        Text(stringResource(R.string.change_device))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.disclaimer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.disclaimer_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.privacy_policy))
                    }
                }
            }
        }
    }
}

/** Published from docs/privacy-policy.md via GitHub Pages. */
private const val PRIVACY_POLICY_URL =
    "https://alekseykapustyanenko.github.io/BodyTempGage/privacy-policy.html"

/** One 0.1 °C step within [min]..[max], rounded so repeated steps don't drift. */
private fun stepThreshold(current: Double, delta: Double, min: Double, max: Double): Double =
    kotlin.math.round((current + delta).coerceIn(min, max) * 10) / 10.0

@Composable
private fun ThresholdRow(
    label: String,
    valueText: String,
    onStep: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onStep(-0.1) }) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onStep(0.1) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
