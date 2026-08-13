package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.localization.R

/**
 * The race-stats parameters, which live on the phone because they are typed
 * once per event and typing on a watch is punishment.
 *
 * The refresh interval is a slider rather than a free field: the tradeoff is
 * battery against freshness, and the sensible range is narrow. A long race gets
 * a slow poll; a criterium is worth the drain.
 */
@Composable
fun RaceSettings(
    config: RaceStatsConfig,
    onChanged: (RaceStatsConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = config.siteUrl,
            onValueChange = { onChanged(config.copy(siteUrl = it)) },
            label = { Text(stringResource(R.string.race_site)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.competitionId,
            onValueChange = { onChanged(config.copy(competitionId = it)) },
            label = { Text(stringResource(R.string.race_competition)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = config.bib,
            onValueChange = { onChanged(config.copy(bib = it)) },
            label = { Text(stringResource(R.string.race_bib)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.race_refresh, config.refreshSeconds),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Slider(
            value = config.refreshSeconds.toFloat(),
            onValueChange = { onChanged(config.copy(refreshSeconds = it.toInt())) },
            valueRange =
                RaceStatsConfig.MIN_REFRESH_SECONDS.toFloat()..RaceStatsConfig.MAX_REFRESH_SECONDS.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.race_refresh_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
