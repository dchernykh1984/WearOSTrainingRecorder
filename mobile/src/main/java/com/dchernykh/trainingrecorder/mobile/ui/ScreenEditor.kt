package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.config.ConfigLevel
import com.dchernykh.trainingrecorder.core.config.ConfigTarget
import com.dchernykh.trainingrecorder.core.config.Screen
import com.dchernykh.trainingrecorder.core.config.ScreenSet
import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.field.FieldCategory
import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R

/**
 * Edits the screens for one tier: the default, a discipline, or one sport.
 *
 * The banner at the top is the important part. Until the rider changes something
 * this tier is reading the one above it, and editing here forks it - after which
 * changing the parent no longer reaches it. That is easy to do by accident and
 * impossible to see afterwards, so it is stated before the first edit rather
 * than explained after. The default has no parent, which is the one case where
 * the banner has nothing to warn about.
 */
@Composable
fun ScreenEditor(
    target: ConfigTarget,
    screens: ScreenSet,
    level: ConfigLevel,
    canReset: Boolean,
    onScreensChanged: (ScreenSet) -> Unit,
    onResetToInherited: () -> Unit,
    onPickField: (screenIndex: Int, slotIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(titleOf(target)),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // The default inherits from nothing, so there is no sentence to show
        // and nothing a Reset could put back.
        if (target !is ConfigTarget.Default) {
            InheritanceBanner(level = level, canReset = canReset, onResetToInherited = onResetToInherited)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(screens.screens.withIndex().toList(), key = { it.index }) { (index, screen) ->
                ScreenCard(
                    index = index,
                    screen = screen,
                    canRemove = screens.screens.size > 1,
                    onSlotCountChanged = { onScreensChanged(screens.withScreen(index, screen.resized(it))) },
                    onSlotClicked = { slot -> onPickField(index, slot) },
                    onRemove = { onScreensChanged(screens.minusScreen(index)) },
                )
                HorizontalDivider()
            }
            item {
                AddScreenButton(
                    enabled = screens.screens.size < ScreenSet.MAX_SCREENS,
                    onClick = { onScreensChanged(screens.plusScreen(Screen.empty(DEFAULT_NEW_SCREEN_SLOTS))) },
                )
            }
        }
    }
}

/** What the editor is called: the tier's own name. */
private fun titleOf(target: ConfigTarget): Int =
    when (target) {
        is ConfigTarget.Default -> R.string.config_target_default
        is ConfigTarget.OfDiscipline -> Labels.discipline(target.discipline.id)
        is ConfigTarget.OfSport -> Labels.sport(target.sport.id)
    }

@Composable
private fun InheritanceBanner(
    level: ConfigLevel,
    canReset: Boolean,
    onResetToInherited: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    when (level) {
                        ConfigLevel.DEFAULT -> R.string.editor_inherits_default
                        ConfigLevel.DISCIPLINE -> R.string.editor_inherits_discipline
                        ConfigLevel.SPORT_TYPE -> R.string.editor_own_copy
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            // Two lines' worth of room, always. The sentence changes when a sport
            // forks - and gets longer in German and Kazakh - so on a narrow phone
            // it can gain a line at the moment the rider is tapping the field
            // count below it, which moves the control out from under the tap.
            // Exactly two, not at least two: a floor alone still lets a longer
            // sentence take a third line in Kazakh or German, or at a large font
            // scale, and the banner grows again at the worst moment.
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        // Always here, disabled until there is something to reset.
        //
        // Showing it only after a sport forks made the banner change size at the
        // exact moment the rider was tapping the field count below it, so the
        // second tap of "three fields to six" landed on nothing. Reserving the
        // height alone was not enough: the button takes width too, and the text
        // beside it re-wraps in the longer languages. A control that is always
        // there cannot move anything, and a greyed Reset also says something
        // true - this sport has nothing of its own yet.
        TextButton(
            onClick = onResetToInherited,
            enabled = canReset,
        ) { Text(stringResource(R.string.editor_reset)) }
    }
}

@Composable
private fun ScreenCard(
    index: Int,
    screen: Screen,
    canRemove: Boolean,
    onSlotCountChanged: (Int) -> Unit,
    onSlotClicked: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_screen_number, index + 1),
                style = MaterialTheme.typography.titleMedium,
            )
            if (canRemove) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.editor_remove_screen)) }
            }
        }
        SlotCountRow(current = screen.slotCount, onChange = onSlotCountChanged)
        screen.slots.forEachIndexed { slotIndex, fieldId ->
            SlotRow(slotIndex = slotIndex, fieldId = fieldId, onClick = { onSlotClicked(slotIndex) })
        }
    }
}

@Composable
private fun SlotCountRow(
    current: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Without this the row aligns to the top, and the count - a bare line of
        // text between two buttons that are three times its height - floats up
        // away from them.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.editor_field_count), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(current - 1) }, enabled = current > 1) { Text("-") }
        // The width of the widest count this can ever show, measured rather than
        // guessed at in dp: a fixed reservation is only wide enough at the font
        // size it was chosen for, and the buttons start shuffling again for a
        // rider who has turned the system text size up.
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = WIDEST_COUNT,
                // Present to the layout and to nothing else - drawn invisibly,
                // and hidden from the screen reader, which would otherwise
                // announce a number that is not the count.
                modifier = Modifier.alpha(0f).clearAndSetSemantics {},
            )
            Text(text = current.toString(), textAlign = TextAlign.Center)
        }
        OutlinedButton(onClick = { onChange(current + 1) }, enabled = current < Screen.MAX_SLOTS) { Text("+") }
    }
}

@Composable
private fun SlotRow(
    slotIndex: Int,
    fieldId: String?,
    onClick: () -> Unit,
) {
    val label = fieldId?.let(Labels::field)?.takeIf { it != 0 }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(R.string.editor_slot_number, slotIndex + 1))
        Text(
            text = if (label != null) stringResource(label) else stringResource(R.string.editor_empty_slot),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AddScreenButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(stringResource(R.string.editor_add_screen))
    }
}

/** Three fits every watch shape and is the commonest starting point. */
private const val DEFAULT_NEW_SCREEN_SLOTS = 3

/** Offered when a slot is tapped, grouped so sixty fields stay navigable. */
@Composable
fun FieldPicker(
    discipline: Discipline?,
    onFieldChosen: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null is the default layout, which belongs to no discipline and therefore
    // offers every field there is - it is inherited by all of them.
    val available =
        if (discipline == null) {
            FieldCatalogue.all.groupBy { it.category }
        } else {
            FieldCatalogue.forDisciplineByCategory(discipline)
        }
    // One category open at a time, and none to begin with. Sixty fields laid out
    // flat is a screen the rider scrolls through hunting for a heading; twelve
    // headings is a screen they read. An accordion rather than free toggling
    // because the complaint was the scrolling, and leaving every category a rider
    // ever opened expanded brings it straight back.
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(R.string.editor_empty_slot),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onFieldChosen(null) }
                        .padding(16.dp),
            )
            HorizontalDivider()
        }
        available.forEach { (category, fields) ->
            val open = openCategory == category.id
            item(key = "category-${category.id}") {
                CategoryHeader(
                    category = category,
                    fieldCount = fields.size,
                    open = open,
                    onClick = { openCategory = if (open) null else category.id },
                )
            }
            if (open) {
                items(fields, key = { it.id }) { field ->
                    Text(
                        text = stringResource(Labels.field(field.id)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onFieldChosen(field.id) }
                                .padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * One category, opened by tapping it.
 *
 * The marker is a character rather than an icon on purpose: the icon library is
 * only on the debug classpath here, so drawing one would build in development
 * and fail the release. The field count earns its place too - it tells the rider
 * whether opening this heading is worth the tap.
 */
@Composable
private fun CategoryHeader(
    category: FieldCategory,
    fieldCount: Int,
    open: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(if (open) R.string.editor_collapse else R.string.editor_expand),
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The heading outranks the fields under it. It read the other way round
        // before - a smaller, quieter heading over full-size field names - which
        // is exactly backwards for a list whose headings are now the thing the
        // rider navigates by.
        Text(
            text = stringResource(Labels.category(category.id)),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (open) "-" else "+ $fieldCount",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/** The widest the count can be: [Screen.MAX_SLOTS] is two digits. */
private const val WIDEST_COUNT = "00"
