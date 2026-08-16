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
    /** Wear OS 6. Named rather than referenced so the app still builds on 36. */
    const val API_36 = 36

    const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

    val required: List<String> get() = requiredFor(Build.VERSION.SDK_INT)

    /**
     * Taken as an argument rather than read from [Build] so the rules can be
     * tested at every release they change in. What a watch asks for is not the
     * kind of thing to find out about from a rider.
     */
    fun requiredFor(sdkInt: Int): List<String> =
        buildList {
            // Both, always. Asking for the fine permission alone is ignored
            // outright from API 31, which leaves the app with no location at
            // all rather than with an approximate one.
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            // Wear OS 6 removed BODY_SENSORS in favour of the granular health
            // permissions. Asking for it there leaves it permanently ungranted,
            // and the prompt would reappear on every launch.
            if (sdkInt < API_36) {
                add(Manifest.permission.BODY_SENSORS)
            }
            if (sdkInt >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Wear OS 6 replaces BODY_SENSORS with the granular health
            // permissions. Both are requested: the old one is still what
            // older watches understand, and the new one is what API 36 will
            // actually check before handing over a heart rate.
            if (sdkInt >= API_36) {
                add(READ_HEART_RATE)
            }
        }

    /**
     * The ones that only mean anything once the ride is out of sight, and that
     * have to be asked for on their own.
     *
     * Android refuses a request for background access bundled with anything
     * else - it is silently dropped, not refused - and it refuses one made
     * before the foreground permission is held. So these come after [required],
     * one at a time, and the order here is the order they are asked in.
     *
     * This is not a nicety. Without background location the watch stops handing
     * over positions the moment the screen goes off, which on a ride is almost
     * immediately: the timer runs, the satellite indicator stays green because
     * availability is still reported, and the distance simply never moves again.
     * A rider covered two hundred metres and the ride recorded ten.
     */
    val background: List<String> get() = backgroundFor(Build.VERSION.SDK_INT)

    fun backgroundFor(sdkInt: Int): List<String> =
        buildList {
            if (sdkInt >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            // The same rule for the watch's own heart rate sensor, from the
            // release that introduced the distinction.
            if (sdkInt in Build.VERSION_CODES.TIRAMISU until API_36) {
                add(BODY_SENSORS_BACKGROUND)
            }
        }

    /**
     * The next background permission to ask for, or null when there is none
     * left. One at a time, because the platform drops a background request that
     * arrives alongside anything else.
     */
    fun nextBackground(
        granted: Set<String>,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): String? = backgroundFor(sdkInt).firstOrNull { it !in granted }

    /** Named rather than referenced: the constant does not exist below API 33. */
    const val BODY_SENSORS_BACKGROUND = "android.permission.BODY_SENSORS_BACKGROUND"
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

    fun held(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var granted by remember {
        mutableStateOf((RecordingPermissions.required + RecordingPermissions.background).filter(::held).toSet())
    }
    var asked by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            granted = granted + result.filterValues { it }.keys
            asked = true
        }
    val single =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            RecordingPermissions.nextBackground(granted)?.let { if (allowed) granted = granted + it }
            asked = true
        }
    val missing = RecordingPermissions.required.filterNot { it in granted }

    LaunchedEffect(Unit) {
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    // Only once the foreground ones are in hand, and only one at a time: a
    // background request bundled with anything else, or made before its
    // foreground counterpart is held, is dropped by the platform without a word.
    LaunchedEffect(granted) {
        if (missing.isEmpty()) RecordingPermissions.nextBackground(granted)?.let(single::launch)
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
