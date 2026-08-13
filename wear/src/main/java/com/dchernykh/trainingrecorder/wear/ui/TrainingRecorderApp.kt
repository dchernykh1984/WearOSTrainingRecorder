package com.dchernykh.trainingrecorder.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.layout.ScreenShape
import com.dchernykh.trainingrecorder.core.recording.RecordingPhase
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.wear.recording.RecordingViewModel

/**
 * The watch app: pick a sport, then the recording screens.
 *
 * There is no navigation graph because there are only two places to be, and
 * which one is decided by whether a recording is running - so it survives the
 * process being restarted mid-ride without any saved back stack.
 */
@Composable
fun TrainingRecorderApp(model: RecordingViewModel = viewModel()) {
    MaterialTheme {
        WithRecordingPermissions {
            val state by model.state.collectAsStateWithLifecycle()
            if (state.phase == RecordingPhase.IDLE || state.phase == RecordingPhase.FINISHED) {
                SportPicker(sports = model.sports, onSportChosen = model::start)
            } else {
                // Read as observed state, not called as a function: a lambda that
                // asked the model for each value would be invoked once and never
                // again, freezing every field for the whole ride.
                val values by model.values.collectAsStateWithLifecycle()
                val sport = sportOf(model, state.sportTypeId)
                RecordingPager(
                    screens = model.screensFor(requireNotNull(sport)),
                    shape = currentShape(),
                    values = { values[it] ?: FieldCatalogue.EMPTY_VALUE },
                    actions = state.availableActions,
                    onAction = model::onAction,
                )
            }
        }
    }
}

private fun sportOf(
    model: RecordingViewModel,
    sportTypeId: String?,
): SportType? = model.sports.firstOrNull { it.id == sportTypeId } ?: model.sports.firstOrNull()

/** Round and square watches want different layouts, and the system knows which. */
@Composable
private fun currentShape(): ScreenShape =
    if (LocalConfiguration.current.isScreenRound) ScreenShape.ROUND else ScreenShape.SQUARE

/**
 * The first thing the rider sees. The order is recency by kind, so the sport of
 * the last workout is at the top and the one before that - of a different kind -
 * is right below it.
 */
@Composable
fun SportPicker(
    sports: List<SportType>,
    onSportChosen: (SportType) -> Unit,
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
        ListHeader { Text(stringResource(R.string.screen_title_start)) }
        sports.forEach { sport ->
            Button(
                onClick = { onSportChosen(sport) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Labels.sport(sport.id))) },
            )
        }
    }
}
