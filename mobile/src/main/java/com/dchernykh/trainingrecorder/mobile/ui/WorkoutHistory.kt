package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.format.FieldFormatter
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R

/**
 * The list of recorded workouts, on the phone only.
 *
 * Each row says where the ride has got to, because that is the question the
 * history is actually asked: not "what did I do" - the rider remembers - but
 * "did it reach Garmin". A ride still waiting is worth seeing at a glance.
 */
@Composable
fun WorkoutHistory(
    workouts: List<WorkoutSummary>,
    units: UnitSystem,
    modifier: Modifier = Modifier,
) {
    if (workouts.isEmpty()) {
        Text(
            text = stringResource(R.string.history_empty),
            modifier = modifier.fillMaxWidth().padding(24.dp),
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(workouts, key = { it.id }) { workout ->
            WorkoutRow(workout = workout, units = units)
            HorizontalDivider()
        }
    }
}

@Composable
private fun WorkoutRow(
    workout: WorkoutSummary,
    units: UnitSystem,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = Labels.sport(workout.sportTypeId)
            Text(
                text = if (label != 0) stringResource(label) else workout.sportTypeId,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = FieldFormatter.distance(workout.distanceMeters, units),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text =
                FieldFormatter.duration(workout.durationSeconds * MILLIS_PER_SECOND) +
                    " - " + stringResource(uploadLabel(workout)),
            style = MaterialTheme.typography.bodySmall,
        )
        // Why it has not arrived, for every service that has something to say.
        // "Waiting to upload" on its own is unactionable - it looks identical
        // whether the watch has no signal, the token has expired, or the service
        // is refusing outright - and the rider cannot read the watch's log.
        workout.uploadReasons.toSortedMap().forEach { (connectorId, reason) ->
            Text(
                text =
                    connectorId + ": " + reason + " - " +
                        stringResource(R.string.history_attempts, workout.uploadAttempts[connectorId] ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One line for the whole upload state. Anything still pending outranks a
 * success, because that is the row the rider needs to notice.
 */
private fun uploadLabel(workout: WorkoutSummary): Int =
    when {
        workout.uploads.isEmpty() -> R.string.history_not_sent
        workout.uploads.values.any { it == UploadState.PENDING } -> R.string.history_pending
        workout.uploads.values.any { it == UploadState.FAILED } -> R.string.history_failed
        else -> R.string.history_uploaded
    }

private const val MILLIS_PER_SECOND = 1000L
