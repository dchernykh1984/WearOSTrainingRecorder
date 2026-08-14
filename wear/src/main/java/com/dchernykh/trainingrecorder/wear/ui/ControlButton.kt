package com.dchernykh.trainingrecorder.wear.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.core.recording.RecordingAction
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.wear.R as WearR

/**
 * One control: a filled circle with its icon, and a word underneath saying what
 * it does.
 *
 * The first version drew bare glyphs on black. They were correct and almost
 * invisible - a pale outline on an unlit OLED, at arm's length, in daylight, is
 * a control the rider hunts for while still moving. This follows the shape the
 * watch's own workout app uses, because that is the one their thumb already
 * knows: a large coloured disc, red to stop and green to go, with a label rather
 * than an icon left to explain itself.
 *
 * Finishing and discarding are bound to a long press, which
 * [RecordingAction.requiresLongPress] decides - a stop that a pocket brush can
 * trigger is worse than one that takes a moment. Wear's own button carries
 * `onLongClick` in its signature, so this is the platform's gesture rather than
 * one assembled by hand, and it keeps the accessibility behaviour and the
 * haptics. The label says so out loud: "Hold to end", not "End".
 */
@Composable
fun ControlButton(
    action: RecordingAction,
    onAction: (RecordingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val longPress = action.requiresLongPress
    val caption = stringResource(action.captionRes())
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = { if (!longPress) onAction(action) },
            onLongClick = if (longPress) ({ onAction(action) }) else null,
            // No label: the caption is already the button's description, and
            // repeating it here has the screen reader say "Hold to end, button,
            // double tap and hold to Hold to end". Without one the platform
            // supplies its own "double tap and hold to activate", which says the
            // same thing once.
            onLongClickLabel = null,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = action.containerColour(),
                    // White on both discs, as the platform's own controls do -
                    // and both discs are dark enough for it to clear the 3:1 a
                    // graphical object needs. The first green was the brighter one
                    // the watch itself uses, at 2.2:1, which washes out in exactly
                    // the daylight this change was made for.
                    contentColor = Color.White,
                ),
            modifier = Modifier.size(BUTTON_SIZE),
        ) {
            Icon(
                painter = painterResource(action.iconRes()),
                contentDescription = caption,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    // Hidden from the screen reader: the button above already
                    // carries these exact words as its description, and leaving
                    // both would have TalkBack say every control twice.
                    .clearAndSetSemantics {},
        )
    }
}

/**
 * Red for the two that end the ride, green for the three the ride survives.
 *
 * Not "stop" against "go": pausing stops the timer and is still green, because
 * what the colour divides is what the rider can undo. Red is the side there is
 * no coming back from, which is why it is also the side that takes a long press.
 * The distinction reads before the icon does - a rider glancing down knows which
 * half is dangerous before they have recognised a shape.
 */
private fun RecordingAction.containerColour(): Color =
    when (this) {
        RecordingAction.FINISH, RecordingAction.DISCARD -> STOP
        RecordingAction.START, RecordingAction.RESUME, RecordingAction.PAUSE -> GO
    }

/**
 * Which side of the screen each control sits on: the one that ends the ride goes
 * left, matching the watch's own workout controls, so the thumb lands where it
 * already expects to.
 */
internal fun RecordingAction.controlOrder(): Int =
    when (this) {
        RecordingAction.FINISH, RecordingAction.DISCARD -> 0
        RecordingAction.START, RecordingAction.RESUME, RecordingAction.PAUSE -> 1
    }

@DrawableRes
private fun RecordingAction.iconRes(): Int =
    when (this) {
        RecordingAction.START, RecordingAction.RESUME -> WearR.drawable.ic_start
        RecordingAction.PAUSE -> WearR.drawable.ic_pause
        RecordingAction.FINISH -> WearR.drawable.ic_finish
        RecordingAction.DISCARD -> WearR.drawable.ic_discard
    }

/**
 * The word under the button.
 *
 * Both long-press controls say how they are done rather than what they are. That
 * matters most while a ride is starting up, when discarding is the *only* control
 * on screen: a tap does nothing there, and a button captioned "Discard" that
 * ignores being pressed is indistinguishable from a broken one.
 */
@StringRes
private fun RecordingAction.captionRes(): Int =
    when (this) {
        RecordingAction.START -> R.string.action_start
        RecordingAction.RESUME -> R.string.action_resume
        RecordingAction.PAUSE -> R.string.action_pause
        RecordingAction.FINISH -> R.string.action_hold_to_end
        RecordingAction.DISCARD -> R.string.action_hold_to_discard
    }

/**
 * Large enough to hit without looking: a quarter of a 233 dp watch face, which
 * is the proportion the platform's own workout controls use and comfortably over
 * the 48 dp a touch target needs.
 */
private val BUTTON_SIZE = 64.dp
private val ICON_SIZE = 30.dp

/**
 * Taken from the watch's own workout screen rather than from the theme: these two
 * mean stop and go before they mean anything about this app, and a rider reads
 * them without reading them.
 */
private val STOP = Color(0xFFE5393E)
private val GO = Color(0xFF12A163)
