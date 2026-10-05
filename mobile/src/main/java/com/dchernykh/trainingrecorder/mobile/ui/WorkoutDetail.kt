package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.format.FieldFormatter
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R
import java.text.DateFormat
import java.util.Date

/**
 * One ride, in full.
 *
 * The list answers "did it arrive"; this answers "why not". Everything the
 * watch recorded about the attempt is here rather than summarised, because the
 * rider cannot read the watch's log and this is the only place the explanation
 * can reach them. A reason is shown verbatim, in a monospaced face: it is text
 * from a service or an exception, not prose the app wrote, and dressing it up
 * as prose would invite reading it as one.
 */
@Composable
fun WorkoutDetail(
    workout: WorkoutSummary,
    units: UnitSystem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // A visible way back, which the sport editor does without. This screen
        // is read rather than operated, and a rider who opened it to find out
        // why a ride is stuck should not have to know the system gesture to
        // leave. The gesture works too.
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
            Text(stringResource(R.string.history_detail_back))
        }
        val sport = Labels.sport(workout.sportTypeId)
        Text(
            text = if (sport != 0) stringResource(sport) else workout.sportTypeId,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Fact(stringResource(R.string.history_detail_started), startedAt(workout))
        Fact(
            stringResource(R.string.history_detail_duration),
            FieldFormatter.duration(workout.durationSeconds * MILLIS_PER_SECOND),
        )
        Fact(
            stringResource(R.string.history_detail_distance),
            FieldFormatter.distance(workout.distanceMeters, units),
        )
        Fact(stringResource(R.string.history_detail_avg_speed), FieldFormatter.speed(averageSpeed(workout), units))
        Fact(stringResource(R.string.history_detail_size), FieldFormatter.fileSize(workout.fileSizeBytes))

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Text(
            text = stringResource(R.string.history_detail_uploads),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (workout.uploads.isEmpty()) {
            // The same words the list row uses, and for the same condition. A
            // ride with no entries has not been *offered* to anything yet,
            // which is not the same as having no services set up - saying the
            // latter would tell a rider whose services are fine to go and check
            // them.
            Text(
                text = stringResource(R.string.history_not_sent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        workout.uploads.toSortedMap().forEach { (connectorId, state) ->
            ServiceStatus(
                connectorId = connectorId,
                state = state,
                attempts = workout.uploadAttempts[connectorId] ?: 0,
                reason = workout.uploadReasons[connectorId],
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ServiceStatus(
    connectorId: String,
    state: UploadState,
    attempts: Int,
    reason: String?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = connectorId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(stateLabel(state)),
                style = MaterialTheme.typography.bodyMedium,
                // Red only for refused. A ride still waiting is not a problem
                // the rider has to act on, and colouring it as one would teach
                // them to ignore the colour.
                color =
                    if (state == UploadState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
        if (attempts > 0) {
            Text(
                text = stringResource(R.string.history_attempts, attempts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reason?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun stateLabel(state: UploadState): Int =
    when (state) {
        UploadState.PENDING -> R.string.history_pending
        UploadState.UPLOADED -> R.string.history_uploaded
        UploadState.FAILED -> R.string.history_failed
    }

/**
 * In the phone's own locale and zone, because that is where the rider is
 * reading it - the watch recorded an instant, not a wall clock.
 */
private fun startedAt(workout: WorkoutSummary): String =
    DateFormat
        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(workout.startedAtEpochMs))

/** Over the whole ride, which is the only duration the summary carries. */
private fun averageSpeed(workout: WorkoutSummary): Double? =
    if (workout.durationSeconds > 0) workout.distanceMeters / workout.durationSeconds else null

private const val MILLIS_PER_SECOND = 1000L
