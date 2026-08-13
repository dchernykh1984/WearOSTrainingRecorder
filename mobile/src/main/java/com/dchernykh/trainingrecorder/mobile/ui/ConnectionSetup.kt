package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(explanationFor(connectorId)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (connectorId == StravaProtocol.ID) {
            Text(
                text = stringResource(R.string.connect_strava_callback),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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
    }
}

private fun explanationFor(connectorId: String): Int =
    if (connectorId == StravaProtocol.ID) R.string.connect_strava_explanation else R.string.connect_garmin_explanation
