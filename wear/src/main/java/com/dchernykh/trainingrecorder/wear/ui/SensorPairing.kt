package com.dchernykh.trainingrecorder.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.core.field.SensorProfile
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.wear.ble.DiscoveredSensor
import com.dchernykh.trainingrecorder.wear.ble.PairedSensor

/**
 * Pairing sensors, which happens on the watch because that is what has to be
 * next to the strap.
 *
 * Paired sensors are listed first and discovered ones below. A rider opening
 * this screen mid-warm-up almost always wants to confirm what is already
 * connected, not to add something new, and making them scroll past a scanning
 * list to find that out is the wrong way round.
 */
@Composable
fun SensorPairing(
    paired: List<PairedSensor>,
    discovered: List<DiscoveredSensor>,
    connected: Set<String>,
    scanning: Boolean,
    onPair: (DiscoveredSensor) -> Unit,
    onForget: (PairedSensor) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingForget by rememberSaveable { mutableStateOf<String?>(null) }
    val forgetting = pendingForget?.let { address -> paired.firstOrNull { it.address == address } }
    if (forgetting != null) {
        // Answers "no", rather than falling through to the screen's own back
        // handler and leaving the sensors list altogether.
        BackHandler { pendingForget = null }
        Confirmation(
            question = stringResource(R.string.sensors_forget_question),
            subject = forgetting.name ?: forgetting.address,
            confirmLabel = stringResource(R.string.sensors_forget),
            onConfirm = {
                onForget(forgetting)
                pendingForget = null
            },
            onCancel = { pendingForget = null },
        )
        return
    }

    WatchList(modifier = modifier) {
        ListHeader { Text(stringResource(R.string.sensors_title)) }
        paired.forEach { sensor ->
            val linked = sensor.address in connected
            Button(
                // Tapping does nothing destructive: this row is here to be read.
                // Forgetting is held, and asks - a rider checking whether their
                // strap is talking should not be able to unpair it by tapping
                // the row that tells them.
                onClick = {},
                onLongClick = { pendingForget = sensor.address },
                onLongClickLabel = stringResource(R.string.sensors_forget),
                colors =
                    if (linked) {
                        ButtonDefaults.filledTonalButtonColors()
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                modifier = Modifier.fillMaxWidth(),
                label = { CentredLabel(sensor.name ?: sensor.address) },
                secondaryLabel = {
                    // The question this screen is opened to answer. Without it a
                    // paired sensor and a working one look identical, and the
                    // only way to tell them apart is to start a ride and see
                    // whether the number appears.
                    CentredLabel(
                        stringResource(
                            if (linked) R.string.sensors_connected else R.string.sensors_connecting,
                        ) + SEPARATOR + describe(sensor.profiles),
                    )
                },
            )
        }

        // Only what is not already paired: a strap that is connected and working
        // still advertises, and showing it again as if it were new invites the
        // rider to pair it twice and wonder which row is the real one.
        val fresh = discovered.filterNot { found -> paired.any { it.address == found.address } }
        if (fresh.isEmpty()) {
            Text(
                text = stringResource(if (scanning) R.string.sensors_searching else R.string.sensors_none),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        fresh.forEach { sensor ->
            Button(
                onClick = { onPair(sensor) },
                modifier = Modifier.fillMaxWidth(),
                label = { CentredLabel(sensor.name ?: sensor.address) },
                secondaryLabel = { CentredLabel(describe(sensor.profiles)) },
            )
        }
    }
}

/**
 * Everything the sensor can report, not just the first thing it mentioned.
 *
 * A strap that speaks heart rate and running cadence used to be listed as
 * whichever profile came first, which read as a flat statement that it could not
 * do the other one.
 */
@Composable
private fun describe(profiles: Set<SensorProfile>): String =
    profiles.map { stringResource(labelFor(it)) }.joinToString(SEPARATOR)

private const val SEPARATOR = ", "

/**
 * What the sensor is, rather than the raw profile id. A rider knows they own a
 * heart-rate strap; they do not know they own a "180D".
 */
private fun labelFor(profile: SensorProfile): Int =
    when (profile) {
        SensorProfile.HEART_RATE -> R.string.profile_heart_rate
        SensorProfile.CYCLING_SPEED_CADENCE -> R.string.profile_csc
        SensorProfile.CYCLING_POWER -> R.string.profile_cps
        SensorProfile.RUNNING_SPEED_CADENCE -> R.string.profile_rsc
        else -> R.string.sensors_title
    }
