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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bodytempgage.app.R

/** Published from docs/privacy-policy.md via GitHub Pages. */
internal const val PRIVACY_POLICY_URL =
    "https://alekseykapustyanenko.github.io/BodyTempGage/privacy-policy.html"

/**
 * First-run gate: the app info, the medical disclaimer, and the privacy policy, each with
 * its own checkbox. [onAccepted] fires only after all three are ticked and Continue pressed.
 */
@Composable
fun ConsentScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    var aboutRead by rememberSaveable { mutableStateOf(false) }
    var disclaimerAgreed by rememberSaveable { mutableStateOf(false) }
    var privacyAgreed by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.consent_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        ConsentCard(
            title = stringResource(R.string.about_title),
            body = stringResource(R.string.about_text),
            checkLabel = stringResource(R.string.consent_about_check),
            checked = aboutRead,
            onCheckedChange = { aboutRead = it },
        )

        ConsentCard(
            title = stringResource(R.string.disclaimer_title),
            body = stringResource(R.string.disclaimer_text),
            checkLabel = stringResource(R.string.consent_disclaimer_check),
            checked = disclaimerAgreed,
            onCheckedChange = { disclaimerAgreed = it },
        )

        ConsentCard(
            title = stringResource(R.string.privacy_policy),
            body = null,
            checkLabel = stringResource(R.string.consent_privacy_check),
            checked = privacyAgreed,
            onCheckedChange = { privacyAgreed = it },
        ) {
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                },
            ) {
                Text(stringResource(R.string.consent_read_policy))
            }
        }

        Button(
            onClick = onAccepted,
            enabled = aboutRead && disclaimerAgreed && privacyAgreed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.consent_continue))
        }
    }
}

@Composable
private fun ConsentCard(
    title: String,
    body: String?,
    checkLabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extraContent()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(
                    text = checkLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
