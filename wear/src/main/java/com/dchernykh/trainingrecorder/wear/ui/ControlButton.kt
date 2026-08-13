package com.dchernykh.trainingrecorder.wear.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import com.dchernykh.trainingrecorder.core.recording.RecordingAction
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.wear.R as WearR

/**
 * One control, drawn as an icon rather than a word so it stays legible at a
 * glance and needs no translation.
 *
 * Finishing and discarding are bound to a long press, which
 * [RecordingAction.requiresLongPress] decides. Wear's own button carries
 * `onLongClick` in its signature, so this is the platform's gesture rather than
 * one assembled by hand - it keeps the accessibility behaviour and the haptics.
 */
@Composable
fun ControlButton(
    action: RecordingAction,
    onAction: (RecordingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val longPress = action.requiresLongPress
    IconButton(
        onClick = { if (!longPress) onAction(action) },
        onLongClick = if (longPress) ({ onAction(action) }) else null,
        onLongClickLabel = if (longPress) stringResource(action.labelRes()) else null,
        modifier = modifier.size(BUTTON_SIZE),
    ) {
        Icon(
            painter = painterResource(action.iconRes()),
            contentDescription = stringResource(action.labelRes()),
        )
    }
}

@DrawableRes
private fun RecordingAction.iconRes(): Int =
    when (this) {
        RecordingAction.START, RecordingAction.RESUME -> WearR.drawable.ic_start
        RecordingAction.PAUSE -> WearR.drawable.ic_pause
        RecordingAction.FINISH -> WearR.drawable.ic_finish
        RecordingAction.DISCARD -> WearR.drawable.ic_discard
    }

@StringRes
private fun RecordingAction.labelRes(): Int =
    when (this) {
        RecordingAction.START -> R.string.action_start
        RecordingAction.RESUME -> R.string.action_resume
        RecordingAction.PAUSE -> R.string.action_pause
        RecordingAction.FINISH -> R.string.action_finish
        RecordingAction.DISCARD -> R.string.action_discard
    }

private val BUTTON_SIZE = 52.dp
