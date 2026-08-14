package com.dchernykh.trainingrecorder.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dchernykh.trainingrecorder.core.config.ConfigLevel
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R

/**
 * The phone companion.
 *
 * Everything the watch cannot reasonably ask for is configured here: screen
 * layouts, race identifiers, service credentials and the language. Sections
 * rather than a navigation graph, because they are five siblings with one level
 * of depth below one of them - a graph would be more machinery than the shape
 * deserves, and the editor's back gesture is the only transition worth handling.
 */
@Composable
fun CompanionApp(
    onLanguageChanged: () -> Unit = {},
    model: CompanionViewModel = viewModel(),
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Saveable, so rotating the phone does not throw the rider out of
            // the editor they were halfway through. The sport is held by id
            // because SportType is not itself saveable.
            var section by rememberSaveable { mutableStateOf(Section.SPORTS) }
            var editingId by rememberSaveable { mutableStateOf<String?>(null) }
            var picking by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
            val editing = editingId?.let { id -> SportCatalogue.byId(id) }

            // Leaving the editor with the system back gesture, so the phone
            // behaves like every other phone rather than trapping the rider on
            // a screen whose only way out is a control they have to find.
            BackHandler(enabled = editing != null) {
                if (picking != null) picking = null else editingId = null
            }

            // Re-read when the rider opens the history, because the listener
            // writes the watch's list to disk from a service the screen knows
            // nothing about - without this, a ride that arrived while the app was
            // open would only appear on the next launch.
            LaunchedEffect(section) {
                if (section == Section.HISTORY) model.refreshWorkouts()
            }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        Section.entries.forEach { entry ->
                            NavigationBarItem(
                                selected = section == entry && editing == null,
                                onClick = {
                                    section = entry
                                    editingId = null
                                    picking = null
                                },
                                icon = {},
                                label = { Text(stringResource(entry.labelRes)) },
                            )
                        }
                    }
                },
            ) { padding ->
                SectionContent(
                    model = model,
                    section = section,
                    editing = editing,
                    picking = picking,
                    onEdit = { editingId = it?.id },
                    onPick = { picking = it },
                    onLanguageChanged = onLanguageChanged,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun SectionContent(
    model: CompanionViewModel,
    section: Section,
    editing: SportType?,
    picking: Pair<Int, Int>?,
    onEdit: (SportType?) -> Unit,
    onPick: (Pair<Int, Int>?) -> Unit,
    onLanguageChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration by model.configuration
    when {
        editing != null && picking != null ->
            FieldPicker(
                sport = editing,
                onFieldChosen = { fieldId ->
                    model.assignField(editing, picking.first, picking.second, fieldId)
                    onPick(null)
                },
                modifier = modifier,
            )
        editing != null ->
            ScreenEditor(
                sport = editing,
                screens = configuration.resolve(editing),
                level = configuration.levelOf(editing),
                onScreensChanged = { model.updateScreens(editing, it) },
                onResetToInherited = { model.resetSport(editing) },
                onPickField = { screenIndex, slotIndex -> onPick(screenIndex to slotIndex) },
                modifier = modifier,
            )
        section == Section.SPORTS ->
            SportConfigurationList(configuration = configuration, onSportSelected = onEdit, modifier = modifier)
        section == Section.RACE ->
            RaceSettings(config = model.race.value, onChanged = model::updateRace, modifier = modifier)
        section == Section.CONNECTIONS ->
            ConnectionList(model = model, modifier = modifier)
        section == Section.HISTORY ->
            WorkoutHistory(workouts = model.workouts.value, units = model.units.value, modifier = modifier)
        section == Section.SETTINGS ->
            LanguageSettings(
                current = model.language.value,
                onLanguageChosen = {
                    model.updateLanguage(it)
                    // Recreated rather than recomposed: the strings come from the
                    // Activity's resources, which are fixed at attach time.
                    onLanguageChanged()
                },
                modifier = modifier,
            )
    }
}

/** One setup screen per connector the build knows about. */
@Composable
private fun ConnectionList(
    model: CompanionViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(model.connectors, key = { it.id }) { connector ->
            ConnectionSetup(
                connectorId = connector.id,
                fields = connector.credentialFields,
                values = model.credentialsFor(connector.id),
                onValueChanged = { key, value -> model.updateCredential(connector.id, key, value) },
                onConnect = { model.connect(connector.id) },
                status = model.statusFor(connector.id),
            )
            HorizontalDivider()
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

/**
 * Each row says which tier it currently reads from, because that is the thing
 * which is otherwise invisible: a sport following its discipline looks identical
 * to one holding its own copy, right up until the parent changes and only one of
 * them moves.
 */
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
