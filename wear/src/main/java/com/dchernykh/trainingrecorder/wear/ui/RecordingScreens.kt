package com.dchernykh.trainingrecorder.wear.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** Named on the controls page, so the rider can see what they are stopping. */
    @StringRes sportLabelRes: Int = 0,
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
            ControlsPage(sportLabelRes = sportLabelRes, actions = actions, onAction = onAction)
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
    // Measured, because the text has to be sized to the room it actually gets.
    // Ten fields is six bands on a face that fits one comfortably, and drawing
    // them all at one size is how a screen the app offers came out unreadable.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val type = slotTypeFor((maxHeight - ROUND_VERTICAL_INSET * 2) / bands.size)
        var consumed = 0
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = ROUND_INSET, vertical = ROUND_VERTICAL_INSET),
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
                        Slot(
                            fieldId = fieldId,
                            values = values,
                            type = type,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
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
    val rows = slots.chunked(columns)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val type = slotTypeFor((maxHeight - SQUARE_INSET * 2) / rows.size)
        Column(
            modifier = Modifier.fillMaxSize().padding(SQUARE_INSET),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { fieldId ->
                        Slot(
                            fieldId = fieldId,
                            values = values,
                            type = type,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * How large a slot's caption and value may be drawn.
 *
 * Derived from the height the slot actually has rather than fixed by the theme.
 * A watch face is a fixed thing that has to hold between one and ten fields, so
 * the only quantity that can give is the type.
 */
private data class SlotType(
    val value: TextUnit,
    val label: TextUnit,
)

/**
 * The value takes a bit under half the row and the caption a fifth, which leaves
 * room between them at every count. The floors are what stays readable at arm's
 * length; the ceilings stop a single field from filling the screen edge to edge.
 */
private fun slotTypeFor(slotHeight: Dp): SlotType {
    val available = slotHeight.value
    return SlotType(
        value = (available * VALUE_SHARE).coerceIn(MIN_VALUE_SP, MAX_VALUE_SP).sp,
        label = (available * LABEL_SHARE).coerceIn(MIN_LABEL_SP, MAX_LABEL_SP).sp,
    )
}

/** A caption above its value, or nothing at all when the slot is unassigned. */
@Composable
private fun Slot(
    fieldId: String?,
    values: (String) -> String,
    type: SlotType,
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
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = type.label,
                        // Set with the size: a line box left at the theme's height
                        // keeps the spacing of type three times as large, which is
                        // most of what made ten fields overlap.
                        lineHeight = type.label * LINE_HEIGHT_RATIO,
                    ),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = values(fieldId),
            style =
                MaterialTheme.typography.numeralSmall.copy(
                    fontSize = type.value,
                    lineHeight = type.value * LINE_HEIGHT_RATIO,
                ),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The controls, laid out like the watch's own workout screen: what is being
 * recorded, then one large disc per action with its word underneath.
 *
 * Centred as a column rather than pinned to the middle of the screen, because the
 * pair of buttons and their captions is taller than it is deep - and on a round
 * face, anything that grows downwards from the centre runs into the rim.
 */
@Composable
private fun ControlsPage(
    sportLabelRes: Int,
    actions: List<RecordingAction>,
    onAction: (RecordingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = CONTROLS_INSET, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (sportLabelRes != 0) {
            Text(
                text = stringResource(sportLabelRes),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            )
        }
        Row(
            // Shared out rather than fixed. Two captions wide enough for German
            // at a fixed width overflow a 192 dp watch - the small round faces
            // this app still supports - and the rim then eats the words that were
            // added to make the buttons explain themselves. Capped as well as
            // shared, so a large face does not push the pair out to its edges.
            // The cap comes first. Modifiers apply left to right, and a
            // fillMaxWidth ahead of it hands down an exact width that widthIn can
            // only agree with - the row looked capped and spread to the rim on
            // every face wider than the cap.
            modifier = Modifier.widthIn(max = CONTROLS_MAX_WIDTH).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Top, not centre: a caption that wraps to two lines would otherwise
            // push its own button out of line with the one beside it.
            verticalAlignment = Alignment.Top,
        ) {
            actions.sortedBy { it.controlOrder() }.forEach {
                ControlButton(action = it, onAction = onAction, modifier = Modifier.weight(1f))
            }
        }
    }
}

private const val DATA_PAGE = 0
private const val CONTROLS_PAGE = 1
private const val PAGE_COUNT_WITH_CONTROLS = 2
private val ROUND_INSET = 16.dp
private val ROUND_VERTICAL_INSET = 12.dp
private val SQUARE_INSET = 8.dp

private const val VALUE_SHARE = 0.42f
private const val LABEL_SHARE = 0.20f
private const val MIN_VALUE_SP = 14f
private const val MAX_VALUE_SP = 44f
private const val MIN_LABEL_SP = 9f
private const val MAX_LABEL_SP = 16f
private const val LINE_HEIGHT_RATIO = 1.08f
private val CONTROLS_INSET = 16.dp

/** Two comfortable slots on a large face; the row shares the width below that. */
private val CONTROLS_MAX_WIDTH = 200.dp
