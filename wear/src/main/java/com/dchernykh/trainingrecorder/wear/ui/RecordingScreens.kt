package com.dchernykh.trainingrecorder.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.core.config.Screen
import com.dchernykh.trainingrecorder.core.config.ScreenSet
import com.dchernykh.trainingrecorder.core.layout.Band
import com.dchernykh.trainingrecorder.core.layout.LayoutPlanner
import com.dchernykh.trainingrecorder.core.layout.ScreenShape
import com.dchernykh.trainingrecorder.core.recording.RecordingAction
import com.dchernykh.trainingrecorder.localization.Labels

/**
 * The screens the rider swipes through while recording.
 *
 * Vertical swipes cycle the configured screens and a horizontal swipe reveals
 * the controls, which is the arrangement asked for. Both pagers are infinite by
 * page count rather than wrapping state, so the cycle has no visible seam.
 */
@Composable
fun RecordingPager(
    screens: ScreenSet,
    shape: ScreenShape,
    values: (String) -> String,
    actions: List<RecordingAction>,
    onAction: (RecordingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The controls sit to the RIGHT of the data screens. Wear OS reserves the
    // left-to-right swipe for dismissing an app, so putting them on the left
    // would mean reaching for pause with the same gesture that closes the ride.
    val horizontal = rememberPagerState(initialPage = DATA_PAGE) { PAGE_COUNT_WITH_CONTROLS }
    // Hoisted out of the pager's page content on purpose: created inside, it
    // would be discarded whenever the data page leaves composition, and the
    // rider would come back from the controls to the first screen instead of the
    // one they were reading.
    val vertical = rememberPagerState(initialPage = 0) { screens.screens.size }
    HorizontalPager(state = horizontal, modifier = modifier.fillMaxSize()) { page ->
        if (page == CONTROLS_PAGE) {
            ControlsPage(actions = actions, onAction = onAction)
        } else {
            DataPages(screens = screens, shape = shape, values = values, state = vertical)
        }
    }
}

@Composable
private fun DataPages(
    screens: ScreenSet,
    shape: ScreenShape,
    values: (String) -> String,
    state: PagerState,
) {
    VerticalPager(state = state, modifier = Modifier.fillMaxSize()) { index ->
        DataScreen(screen = screens.screens[index], shape = shape, values = values)
    }
}

/**
 * One screen of slots, laid out as bands on a round watch and as a grid on a
 * square one. The plan comes from the shared module so the arrangement is the
 * same one its tests pin down.
 */
@Composable
fun DataScreen(
    screen: Screen,
    shape: ScreenShape,
    values: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val slots = screen.slots
    when (shape) {
        ScreenShape.ROUND ->
            RoundBands(
                bands = LayoutPlanner.planRound(slots.size),
                slots = slots,
                values = values,
                modifier = modifier,
            )
        ScreenShape.SQUARE -> SquareGrid(slots = slots, values = values, modifier = modifier)
    }
}

@Composable
private fun RoundBands(
    bands: List<Band>,
    slots: List<String?>,
    values: (String) -> String,
    modifier: Modifier = Modifier,
) {
    var consumed = 0
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = ROUND_INSET, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        bands.forEach { band ->
            val bandSlots = slots.drop(consumed).take(band.columns)
            consumed += band.columns
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bandSlots.forEach { fieldId ->
                    Slot(fieldId = fieldId, values = values, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun SquareGrid(
    slots: List<String?>,
    values: (String) -> String,
    modifier: Modifier = Modifier,
) {
    // Two columns once there are enough slots; the planner's pixel thresholds
    // belong to the drawing code, so the shape decision here is by count alone.
    val columns = if (slots.size >= LayoutPlanner.MIN_SLOTS_FOR_TWO_COLUMNS) 2 else 1
    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        slots.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { fieldId ->
                    Slot(fieldId = fieldId, values = values, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** A caption above its value, or nothing at all when the slot is unassigned. */
@Composable
private fun Slot(
    fieldId: String?,
    values: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (fieldId == null) {
            Text(text = "", textAlign = TextAlign.Center)
            return@Column
        }
        val label = Labels.field(fieldId)
        if (label != 0) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = values(fieldId),
            style = MaterialTheme.typography.numeralSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ControlsPage(
    actions: List<RecordingAction>,
    onAction: (RecordingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { ControlButton(action = it, onAction = onAction) }
        }
    }
}

private const val DATA_PAGE = 0
private const val CONTROLS_PAGE = 1
private const val PAGE_COUNT_WITH_CONTROLS = 2
private val ROUND_INSET = 16.dp
