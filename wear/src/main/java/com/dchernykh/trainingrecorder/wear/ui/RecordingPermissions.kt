package com.dchernykh.trainingrecorder.wear.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.localization.R

/**
 * What the app must be granted before it can record anything.
 *
 * Declaring a permission in the manifest is not the same as holding it: from API
 * 23 every one of these has to be asked for at runtime, and calling
 * `connectGatt` or starting a health foreground service without them throws a
 * SecurityException rather than failing quietly.
 */
object RecordingPermissions {
    val required: List<String>
        get() =
            buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.BODY_SENSORS)
                add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
}

/**
 * Asks once, then gets out of the way.
 *
 * A refusal is not fatal: the rider still gets a workout, just without whatever
 * they declined, so [content] is shown either way rather than trapping them on a
 * wall of explanation.
 */
@Composable
fun WithRecordingPermissions(content: @Composable (granted: Set<String>) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            RecordingPermissions.required
                .filter {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }.toSet(),
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            granted = granted + result.filterValues { it }.keys
            asked = true
        }
    val missing = RecordingPermissions.required.filterNot { it in granted }

    LaunchedEffect(Unit) {
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    if (missing.isNotEmpty() && !asked) {
        PermissionPrompt(onGrant = { launcher.launch(missing.toTypedArray()) })
    } else {
        content(granted)
    }
}

@Composable
private fun PermissionPrompt(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.permissions_explanation))
        Button(onClick = onGrant, label = { Text(stringResource(R.string.permissions_grant)) })
    }
}
