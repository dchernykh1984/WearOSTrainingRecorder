package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.config.ConfigLevel
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R

/**
 * The phone owns the configuration, so this is where a sport's screens are
 * edited.
 *
 * Each row says which tier it currently reads from, because that is the thing
 * which is otherwise invisible: a sport following its discipline looks identical
 * to one holding its own copy, right up until the parent changes and only one of
 * them moves.
 */
@Composable
fun CompanionApp(
    configuration: ScreenConfiguration = ScreenConfiguration.initial(),
    onSportSelected: (SportType) -> Unit = {},
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SportConfigurationList(configuration = configuration, onSportSelected = onSportSelected)
        }
    }
}

@Composable
fun SportConfigurationList(
    configuration: ScreenConfiguration,
    onSportSelected: (SportType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(SportCatalogue.all, key = { it.id }) { sport ->
            SportRow(
                sport = sport,
                level = configuration.levelOf(sport),
                screenCount = configuration.resolve(sport).screens.size,
                onClick = { onSportSelected(sport) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SportRow(
    sport: SportType,
    level: ConfigLevel,
    screenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = stringResource(Labels.sport(sport.id)), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(level.labelRes()) + " - " + stringResource(R.string.screen_count, screenCount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun ConfigLevel.labelRes(): Int =
    when (this) {
        ConfigLevel.DEFAULT -> R.string.config_level_default
        ConfigLevel.DISCIPLINE -> R.string.config_level_discipline
        ConfigLevel.SPORT_TYPE -> R.string.config_level_sport_type
    }
