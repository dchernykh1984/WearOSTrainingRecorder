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
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.core.workout.SportOrdering
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R

@Composable
fun TrainingRecorderApp(
    history: List<String> = emptyList(),
    onSportChosen: (SportType) -> Unit = {},
) {
    MaterialTheme {
        SportPicker(history = history, onSportChosen = onSportChosen)
    }
}

/**
 * The first thing the rider sees. The order is recency by kind, so the sport of
 * the last workout is at the top and the one before that - of a different kind -
 * is right below it.
 */
@Composable
fun SportPicker(
    history: List<String>,
    onSportChosen: (SportType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sports = SportOrdering.order(history)
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
