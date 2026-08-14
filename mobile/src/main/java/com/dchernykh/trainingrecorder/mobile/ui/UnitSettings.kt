package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.localization.R

/**
 * Metric or imperial, for everything at once.
 *
 * One choice rather than a unit per field on purpose. A rider who can set speed
 * in miles per hour while altitude stays in metres has been given a way to build
 * a display that contradicts itself, and the setting people actually want is the
 * one their country uses. The formatter already works this way - it takes a
 * [UnitSystem] and derives every field from it - so this is the switch that was
 * missing rather than a new idea.
 *
 * On the phone, like every other setting: the watch has the screen space for
 * numbers, not for lists to choose from.
 */
@Composable
fun UnitSettings(
    current: UnitSystem,
    onUnitsChosen: (UnitSystem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_units),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        UnitSystem.entries.forEach { system ->
            UnitRow(
                system = system,
                selected = system == current,
                onClick = { onUnitsChosen(system) },
            )
        }
    }
}

@Composable
private fun UnitRow(
    system: UnitSystem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The whole row is the target, matching the language picker below it.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(system.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/**
 * Named here rather than on the enum: [UnitSystem] lives in the plain Kotlin
 * module, which has no resources to point at.
 */
private fun UnitSystem.labelRes(): Int =
    when (this) {
        UnitSystem.METRIC -> R.string.units_metric
        UnitSystem.IMPERIAL -> R.string.units_imperial
    }
