package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
            OutlinedTextField(
                value = values[field.key].orEmpty(),
                onValueChange = { onValueChanged(field.key, it) },
                label = { Text(field.key) },
                singleLine = true,
                visualTransformation =
                    if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
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
