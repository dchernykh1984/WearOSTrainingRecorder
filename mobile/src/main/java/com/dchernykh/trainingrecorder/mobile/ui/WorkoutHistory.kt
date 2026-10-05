package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
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
 *
 * The row opens [WorkoutDetail], which is where the full explanation lives. The
 * reason stays on the row as well, truncated or not: the rider who has eight
 * refused rides wants to see at a glance whether they all failed for the same
 * thing, and making them open each one to find out would hide the pattern.
 */
@Composable
fun WorkoutHistory(
    workouts: List<WorkoutSummary>,
    units: UnitSystem,
    onSelect: (WorkoutSummary) -> Unit,
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
            WorkoutRow(workout = workout, units = units, onSelect = { onSelect(workout) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun WorkoutRow(
    workout: WorkoutSummary,
    units: UnitSystem,
    onSelect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
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
                // Clipped here, whole in the detail. A reason now carries the
                // exception's own message and runs to a hundred-odd characters,
                // and letting that wrap would turn a scannable list of rides
                // into a wall of the same stack-trace line repeated.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
