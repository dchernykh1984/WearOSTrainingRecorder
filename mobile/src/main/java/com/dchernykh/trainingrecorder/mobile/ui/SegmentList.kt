package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.format.FieldFormatter
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.localization.R

/**
 * The rider's starred segments, as the phone last saw them.
 *
 * Shown at all because a live segment field that stays empty is unactionable:
 * with this the rider can see whether the climb they starred this morning has
 * actually arrived, and whether the app has a time of theirs to race against.
 */
@Composable
fun SegmentPanel(
    settings: SegmentSettings,
    units: UnitSystem,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = settings.segments.value
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = stringResource(R.string.segments_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.segments_explain),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSyncNow, enabled = !settings.syncing.value) {
                Text(stringResource(R.string.segments_sync_now))
            }
            settings.status.value?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        if (segments.isEmpty()) {
            Text(
                text = stringResource(R.string.segments_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            return@Column
        }
        segments.forEach { segment ->
            SegmentRow(segment = segment, units = units)
        }
    }
}

@Composable
private fun SegmentRow(
    segment: Segment,
    units: UnitSystem,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(text = segment.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            // The distance and the time to beat, which together are the whole
            // of what the watch will be racing the rider against.
            text = summary(segment, units),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun summary(
    segment: Segment,
    units: UnitSystem,
): String {
    val distance = FieldFormatter.distance(segment.distanceMeters, units)
    val best = segment.referenceSeconds
    return if (best == null) {
        stringResource(R.string.segments_no_effort, distance)
    } else {
        stringResource(R.string.segments_best, distance, FieldFormatter.duration((best * 1000).toLong()))
    }
}
