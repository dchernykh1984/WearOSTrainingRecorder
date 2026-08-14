package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.core.config.ConfigLevel
import com.dchernykh.trainingrecorder.core.config.Screen
import com.dchernykh.trainingrecorder.core.config.ScreenSet
import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.field.FieldCategory
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R

/**
 * Edits the screens for one sport.
 *
 * The banner at the top is the important part. Until the rider changes something
 * this sport is reading its discipline's layout, or the default, and editing here
 * forks it - after which changing the parent no longer reaches it. That is easy
 * to do by accident and impossible to see afterwards, so it is stated before the
 * first edit rather than explained after.
 */
@Composable
fun ScreenEditor(
    sport: SportType,
    screens: ScreenSet,
    level: ConfigLevel,
    onScreensChanged: (ScreenSet) -> Unit,
    onResetToInherited: () -> Unit,
    onPickField: (screenIndex: Int, slotIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(Labels.sport(sport.id)),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        InheritanceBanner(level = level, onResetToInherited = onResetToInherited)
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

@Composable
private fun InheritanceBanner(
    level: ConfigLevel,
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
            enabled = level == ConfigLevel.SPORT_TYPE,
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
        Text(
            text = current.toString(),
            textAlign = TextAlign.Center,
            // Wide enough for two digits, so the buttons do not shuffle sideways
            // when the count crosses from nine to ten.
            modifier = Modifier.widthIn(min = COUNT_WIDTH),
        )
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
    sport: SportType,
    onFieldChosen: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = FieldCatalogue.forDisciplineByCategory(sport.discipline)
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
        Text(text = stringResource(Labels.category(category.id)))
        Text(text = if (open) "-" else "+ $fieldCount")
    }
    HorizontalDivider()
}

/** Room for two digits, so the count cannot nudge the buttons about. */
private val COUNT_WIDTH = 24.dp
