package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.localization.R

/**
 * Where the rider enters their own credentials for a service.
 *
 * The app deliberately ships none. For Strava that means each rider registers a
 * two-minute API application of their own, which is why the explanation comes
 * before the fields rather than in a help page nobody opens: without it the two
 * boxes look like an unreasonable demand instead of a one-off setup.
 */
@Composable
fun ConnectionSetup(
    connectorId: String,
    fields: List<CredentialField>,
    values: Map<String, String>,
    onValueChanged: (String, String) -> Unit,
    onConnect: () -> Unit,
    status: Int? = null,
    codeRequested: Boolean = false,
    onCodeSubmitted: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(explanationFor(connectorId)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                stringResource(
                    if (connectorId == StravaProtocol.ID) {
                        R.string.connect_strava_callback
                    } else {
                        R.string.connect_garmin_code_hint
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        fields.forEach { field ->
            key(field.key) {
                CredentialEntry(
                    field = field,
                    value = values[field.key].orEmpty(),
                    onValueChanged = { onValueChanged(field.key, it) },
                )
            }
        }
        Button(
            onClick = onConnect,
            enabled = fields.filter { it.required }.all { values[it.key]?.isNotBlank() == true },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.connect_action))
        }
        // Connecting Strava opens a browser and comes back minutes later, so the
        // screen has to say where it got to - silence reads as a dead button.
        if (status != null) {
            Text(
                text = stringResource(status),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // Garmin's second factor arrives by email or SMS a minute later. A field
        // on the screen rather than a dialog, so the rider can leave the app to
        // go and read the code and find it still waiting when they come back.
        if (codeRequested) VerificationCode(onCodeSubmitted = onCodeSubmitted)
    }
}

/**
 * One credential, with a way to read back what was typed.
 *
 * A masked field is the right default for a password over someone's shoulder,
 * and the wrong one for a forty-character client secret pasted from a browser:
 * dots tell the rider nothing about why the sign-in was refused. So the mask
 * comes off on request, per field - revealing the secret should not also reveal
 * the password next to it - and goes back on by itself when the rider leaves
 * the screen, because nothing here re-hides it for them.
 */
@Composable
private fun CredentialEntry(
    field: CredentialField,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(field.key) },
        singleLine = true,
        visualTransformation =
            if (field.secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (!field.secret) return
    Row(
        // The whole row toggles, not just the box: a 20 dp checkbox is a small
        // target, and the words beside it are what the thumb aims at anyway.
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = revealed,
                    role = Role.Checkbox,
                    onValueChange = { revealed = it },
                ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Null: the row above carries the toggle, and a checkbox with its own
        // handler would be a second control saying the same thing to TalkBack.
        Checkbox(checked = revealed, onCheckedChange = null)
        Text(
            text = stringResource(R.string.connect_show_secret),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun VerificationCode(onCodeSubmitted: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text(stringResource(R.string.connect_code_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = { onCodeSubmitted(code) },
        enabled = code.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.connect_submit_code))
    }
}

private fun explanationFor(connectorId: String): Int =
    if (connectorId == StravaProtocol.ID) R.string.connect_strava_explanation else R.string.connect_garmin_explanation
