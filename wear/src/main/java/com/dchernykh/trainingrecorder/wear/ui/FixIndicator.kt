package com.dchernykh.trainingrecorder.wear.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.core.sensor.FixStatus

/**
 * Three letters at the foot of the screen saying whether the ride is being
 * tracked.
 *
 * Letters rather than a symbol: "GPS" is the same three characters in every
 * language this app speaks, needs no legend, and stays legible at a size no
 * glyph would survive. The colour carries the state and the word carries what
 * the state is about - neither alone would do, since a lone coloured dot at the
 * bottom of a watch face means nothing, and a lone "GPS" says only that the
 * feature exists.
 *
 * It sits over the data rather than taking a strip from it. The bottom of a
 * round face is the narrowest part of the circle, so a field there has already
 * given up most of its width - which makes it the one place on the screen where
 * a few points of height cost the rider nothing.
 */
@Composable
fun FixIndicator(
    fix: FixStatus,
    modifier: Modifier = Modifier,
) {
    Text(
        text = FIX_LABEL,
        style = MaterialTheme.typography.labelSmall,
        color =
            when (fix) {
                FixStatus.ACQUIRED -> FIX_GOOD
                FixStatus.ACQUIRING -> FIX_SEARCHING
                FixStatus.NONE -> FIX_NONE
            },
        // The inset belongs to the indicator, not to whoever places it: the
        // reason for it is that the rim of a round face clips anything sitting
        // on the very bottom edge, and that is true wherever this is used.
        modifier = modifier.padding(bottom = FIX_INSET),
    )
}

/**
 * Not localized on purpose: "GPS" is read as "GPS" in every language this app
 * speaks, and translating it would produce three letters nobody recognises.
 */
private const val FIX_LABEL = "GPS"

/** How far off the rim, so the circle does not clip the letters. */
private val FIX_INSET = 6.dp

/**
 * The same green and red as the controls, so the two places on this watch that
 * mean go and stop agree with each other. Amber sits between them and is the one
 * a rider only glances at while it lasts.
 */
private val FIX_GOOD = Color(0xFF12A163)
private val FIX_SEARCHING = Color(0xFFE0A116)
private val FIX_NONE = Color(0xFFE5393E)
