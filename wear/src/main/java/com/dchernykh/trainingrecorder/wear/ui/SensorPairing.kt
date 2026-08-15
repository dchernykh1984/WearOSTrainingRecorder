package com.dchernykh.trainingrecorder.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
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
    scanning: Boolean,
    onPair: (DiscoveredSensor) -> Unit,
    onForget: (PairedSensor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ListHeader { Text(stringResource(R.string.sensors_title)) }
        paired.forEach { sensor ->
            Button(
                onClick = { onForget(sensor) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(sensor.name ?: sensor.address) },
                secondaryLabel = { Text(stringResource(R.string.sensors_forget)) },
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
                label = { Text(sensor.name ?: sensor.address) },
                secondaryLabel = { Text(describe(sensor.profiles)) },
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
