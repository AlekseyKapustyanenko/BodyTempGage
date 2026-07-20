package com.bodytempgage.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.bodytempgage.wear.R

/**
 * First-run gate: app info, medical disclaimer, and privacy policy, each acknowledged with
 * its own checkbox. [onAccepted] fires only after all three are ticked and Continue pressed.
 */
@Composable
fun ConsentScreen(onAccepted: () -> Unit) {
    var aboutRead by rememberSaveable { mutableStateOf(false) }
    var disclaimerAgreed by rememberSaveable { mutableStateOf(false) }
    var privacyAgreed by rememberSaveable { mutableStateOf(false) }
    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = stringResource(R.string.consent_title),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                )
            }
            item { ConsentText(stringResource(R.string.consent_about_text)) }
            item {
                ConsentCheck(
                    label = stringResource(R.string.consent_about_check),
                    checked = aboutRead,
                    onCheckedChange = { aboutRead = it },
                )
            }
            item { ConsentText(stringResource(R.string.about_disclaimer)) }
            item {
                ConsentCheck(
                    label = stringResource(R.string.consent_disclaimer_check),
                    checked = disclaimerAgreed,
                    onCheckedChange = { disclaimerAgreed = it },
                )
            }
            item { ConsentText(stringResource(R.string.consent_privacy_text)) }
            item {
                ConsentCheck(
                    label = stringResource(R.string.consent_privacy_check),
                    checked = privacyAgreed,
                    onCheckedChange = { privacyAgreed = it },
                )
            }
            item {
                Chip(
                    onClick = onAccepted,
                    enabled = aboutRead && disclaimerAgreed && privacyAgreed,
                    label = {
                        Text(
                            text = stringResource(R.string.consent_continue),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ConsentText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ConsentCheck(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(label) },
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.checkboxIcon(checked),
                contentDescription = null,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
