package com.dchernykh.trainingrecorder.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dchernykh.trainingrecorder.core.config.ConfigLevel
import com.dchernykh.trainingrecorder.core.config.ConfigTarget
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.config.isForked
import com.dchernykh.trainingrecorder.core.config.levelOf
import com.dchernykh.trainingrecorder.core.config.resolve
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R
import kotlinx.coroutines.delay

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
            val editing = editingId?.let(ConfigTarget::byKey)

            // Leaving the editor with the system back gesture, so the phone
            // behaves like every other phone rather than trapping the rider on
            // a screen whose only way out is a control they have to find.
            BackHandler(enabled = editing != null) {
                if (picking != null) picking = null else editingId = null
            }

            // The listener writes the watch's list to disk from a service the
            // screen knows nothing about, so the screen has to go and look. While
            // the history is open it keeps looking: a ride that lands while the
            // rider is watching the list is exactly the ride they are watching
            // for, and one that only appeared after navigating away and back
            // would read as the sync being broken. The effect ends with the
            // section, so nothing polls behind the other four tabs.
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(section, lifecycle) {
                if (section != Section.HISTORY) return@LaunchedEffect
                // Only while the app is actually in front of the rider. Keyed on
                // the section alone, a phone left on this tab in a pocket would
                // wake the Data Layer every ten seconds for a screen nobody is
                // looking at.
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        model.refreshWorkouts()
                        delay(HISTORY_REFRESH_MS)
                    }
                }
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
                    onEdit = { editingId = it?.key },
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
    editing: ConfigTarget?,
    picking: Pair<Int, Int>?,
    onEdit: (ConfigTarget?) -> Unit,
    onPick: (Pair<Int, Int>?) -> Unit,
    onLanguageChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration by model.configuration
    when {
        editing != null && picking != null ->
            FieldPicker(
                discipline = editing.fieldsOf,
                onFieldChosen = { fieldId ->
                    model.assignField(editing, picking.first, picking.second, fieldId)
                    onPick(null)
                },
                modifier = modifier,
            )
        editing != null ->
            ScreenEditor(
                target = editing,
                screens = configuration.resolve(editing),
                level = configuration.levelOf(editing),
                canReset = configuration.isForked(editing),
                onScreensChanged = { model.updateScreens(editing, it) },
                onResetToInherited = { model.resetTarget(editing) },
                onPickField = { screenIndex, slotIndex -> onPick(screenIndex to slotIndex) },
                modifier = modifier,
            )
        section == Section.SPORTS ->
            SportConfigurationList(configuration = configuration, onTargetSelected = onEdit, modifier = modifier)
        section == Section.RACE ->
            RaceSettings(config = model.race.value, onChanged = model::updateRace, modifier = modifier)
        section == Section.CONNECTIONS ->
            ConnectionList(model = model, modifier = modifier)
        section == Section.HISTORY ->
            WorkoutHistory(workouts = model.workouts.value, units = model.units.value, modifier = modifier)
        section == Section.SETTINGS ->
            Column(modifier = modifier.fillMaxSize()) {
                UnitSettings(current = model.units.value, onUnitsChosen = model::updateUnits)
                HorizontalDivider()
                // Weighted, so the fifteen-language list scrolls in what is left
                // rather than pushing the unit choice off the top of the screen.
                LanguageSettings(
                    current = model.language.value,
                    onLanguageChosen = {
                        model.updateLanguage(it)
                        // Recreated rather than recomposed: the strings come from
                        // the Activity's resources, fixed at attach time.
                        onLanguageChanged()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
    }
}

/** One setup screen per connector the build knows about. */
@Composable
private fun ConnectionList(
    model: CompanionViewModel,
    modifier: Modifier = Modifier,
) {
    val connections = model.connections
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(connections.connectors, key = { it.id }) { connector ->
            ConnectionSetup(
                connectorId = connector.id,
                fields = connector.credentialFields,
                values = connections.credentialsFor(connector.id),
                onValueChanged = { key, value -> model.updateCredential(connector.id, key, value) },
                onConnect = { model.connect(connector.id) },
                status = connections.statusFor(connector.id),
                codeRequested = connector.id == GarminProtocol.ID && connections.codeRequested.value,
                onCodeSubmitted = model::submitGarminCode,
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun SportConfigurationList(
    configuration: ScreenConfiguration,
    onTargetSelected: (ConfigTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One section open at a time, as in the field picker. Thirty-five sports
    // flat is a screen to scroll; four disciplines is a screen to read.
    var openDiscipline by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "default") {
            // First, and above the disciplines, because it is what they all
            // inherit. A rider who wants the same three fields everywhere sets
            // them here once instead of thirty-five times.
            TargetRow(
                title = stringResource(R.string.config_target_default),
                level = ConfigLevel.DEFAULT,
                screenCount = configuration.resolve(ConfigTarget.Default).screens.size,
                onClick = { onTargetSelected(ConfigTarget.Default) },
            )
            HorizontalDivider()
        }
        Discipline.entries.forEach { discipline ->
            val open = openDiscipline == discipline.id
            item(key = "discipline-${discipline.id}") {
                DisciplineHeader(
                    discipline = discipline,
                    sportCount = SportCatalogue.forDiscipline(discipline).size,
                    open = open,
                    onClick = { openDiscipline = if (open) null else discipline.id },
                )
            }
            if (!open) return@forEach
            item(key = "discipline-default-${discipline.id}") {
                // The section's own default, inside the section it applies to,
                // where "Default" needs no further explanation.
                TargetRow(
                    title = stringResource(R.string.config_target_section),
                    level = configuration.levelOf(ConfigTarget.OfDiscipline(discipline)),
                    screenCount = configuration.resolve(ConfigTarget.OfDiscipline(discipline)).screens.size,
                    onClick = { onTargetSelected(ConfigTarget.OfDiscipline(discipline)) },
                    indent = true,
                )
                HorizontalDivider()
            }
            items(SportCatalogue.forDiscipline(discipline), key = { it.id }) { sport ->
                TargetRow(
                    title = stringResource(Labels.sport(sport.id)),
                    level = configuration.levelOf(sport),
                    screenCount = configuration.resolve(sport).screens.size,
                    onClick = { onTargetSelected(ConfigTarget.OfSport(sport)) },
                    indent = true,
                )
                HorizontalDivider()
            }
        }
    }
}

/** One discipline, opened by tapping it. Sized above the rows it introduces. */
@Composable
private fun DisciplineHeader(
    discipline: Discipline,
    sportCount: Int,
    open: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Labels.discipline(discipline.id)),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (open) "-" else "+ $sportCount",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/**
 * Each row says which tier it currently reads from, because that is the thing
 * which is otherwise invisible: a sport following its discipline looks identical
 * to one holding its own copy, right up until the parent changes and only one of
 * them moves.
 */
@Composable
private fun TargetRow(
    title: String,
    level: ConfigLevel,
    screenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    indent: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = if (indent) 32.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
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

/**
 * How often the history looks for what the watch has sent while it is on screen.
 * Ten seconds is under the time it takes to wonder whether a ride arrived, and
 * far over the cost of reading one small file.
 */
private const val HISTORY_REFRESH_MS = 10_000L
