package com.dchernykh.trainingrecorder.wear.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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
    var consumed = 0
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = ROUND_INSET, vertical = ROUND_VERTICAL_INSET),
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
        modifier = modifier.fillMaxSize().padding(SQUARE_INSET),
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
    // An empty slot still occupies its cell. Emitting nothing drops the
    // caller's weight with it, and the field beside it then spreads across the
    // whole band, out of line with every other row on the screen.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (fieldId == null) return@Box
        Column(
            // Capped before it is filled, so a slot with the whole face to itself
            // keeps its caption next to its value instead of pushing the two to
            // opposite ends of the screen. The cap has to come first: a
            // fillMaxHeight ahead of it hands down an exact height that heightIn
            // can only agree with.
            modifier = Modifier.heightIn(max = MAX_SLOT_HEIGHT).fillMaxHeight().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val label = Labels.field(fieldId)
            // Weighted rather than sized: the caption gets a third of the slot and
            // the value the rest, whatever the slot turns out to be. That is what
            // makes one field and ten fields both work - the proportion is fixed and
            // the type follows.
            if (label != 0) {
                FittedText(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    minFontSize = MIN_LABEL,
                    maxFontSize = MAX_LABEL,
                    modifier = Modifier.fillMaxWidth().weight(LABEL_SHARE),
                )
            }
            FittedText(
                text = values(fieldId),
                style = MaterialTheme.typography.numeralSmall,
                minFontSize = MIN_VALUE,
                maxFontSize = MAX_VALUE,
                modifier = Modifier.fillMaxWidth().weight(VALUE_SHARE),
            )
        }
    }
}

/**
 * One line that shrinks until it fits the box it was given.
 *
 * The alternative - computing a size from the number of bands - was wrong in two
 * ways at once. It read a dp measurement as an sp one, so every size was off by
 * whatever font scale the rider had set, and it never looked at width at all: a
 * value like "1:23:45" in a two-column band was clipped to something that still
 * looked like a time and was not one. Measuring is the platform's job, and it
 * measures both dimensions and honours the font scale for free.
 *
 * The line height has to be released along with it. Auto-sizing substitutes the
 * font size and nothing else, so a style that carries the theme's 30 sp line box
 * keeps it at every candidate size - and in a 22 sp cell every candidate then
 * fails to fit, the search bottoms out at the smallest, and the digits are cut.
 * Unspecified lets the line box follow the size that is actually chosen.
 *
 * The step is coarse on purpose. Each candidate is a paragraph layout, the values
 * change once a second, and a tenth of a millimetre of extra precision is not
 * worth ten more layouts a second on a watch.
 *
 * The overflow stays Clip, which looks like the wrong choice and is not: auto-
 * sizing asks each candidate whether it fits, and text that is allowed to
 * ellipsise always says yes. Setting Ellipsis here therefore makes every size fit,
 * the search returns the largest, and a ten-field screen goes back to drawing
 * every row on top of the next - which is exactly what it did when this was
 * tried. Clipping only ever happens at the smallest size, when nothing fits.
 */
@Composable
private fun FittedText(
    text: String,
    style: TextStyle,
    minFontSize: TextUnit,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BasicText(
            text = text,
            style =
                style.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    lineHeight = TextUnit.Unspecified,
                ),
            maxLines = 1,
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize = minFontSize,
                    maxFontSize = maxFontSize,
                    stepSize = AUTO_SIZE_STEP,
                ),
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

/** As tall as a caption and value need together, and no taller. */
private val MAX_SLOT_HEIGHT = 104.dp

/** The caption takes a third of a slot, the value the rest. */
private const val LABEL_SHARE = 0.34f
private const val VALUE_SHARE = 0.66f

/** Floors keep a ten-field screen readable; ceilings stop one field filling the face. */
private val MIN_LABEL = 8.sp
private val MAX_LABEL = 16.sp
private val MIN_VALUE = 12.sp
private val MAX_VALUE = 44.sp

/** Coarse enough that the search is a handful of layouts, not a hundred. */
private val AUTO_SIZE_STEP = 2.sp
private val CONTROLS_INSET = 16.dp

/** Two comfortable slots on a large face; the row shares the width below that. */
private val CONTROLS_MAX_WIDTH = 200.dp
