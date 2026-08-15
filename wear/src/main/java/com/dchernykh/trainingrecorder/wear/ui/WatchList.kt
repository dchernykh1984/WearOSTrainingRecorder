package com.dchernykh.trainingrecorder.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.localization.R

/**
 * A scrolling list of full-width entries, which is what every list on this watch
 * turns out to be.
 *
 * The padding is not decoration: the top and bottom of a round face are where
 * the circle takes the corners away, and a list that starts at the very edge
 * puts its first row where it cannot be read or reliably hit.
 */
@Composable
fun WatchList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/**
 * Centred rather than left-aligned, which is what a round screen asks for.
 *
 * The list scrolls, so every row passes through the top and bottom of the face
 * on its way past - and that is exactly where the circle takes the corners away.
 * A line that starts at the left edge loses its first letters there; a centred
 * one loses the same amount from both ends and stays readable the whole way.
 */
@Composable
fun CentredLabel(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A question with two answers, taking the whole screen.
 *
 * Full screen rather than a dialog over the list, because the two things it
 * guards - forgetting a favourite and forgetting a sensor - are reached by
 * holding a control whose *tap* does something quite different. A rider who
 * held one by accident should meet an obvious question, not a small box over a
 * list of controls they were already aiming at.
 *
 * Cancel comes second and is the plain one: the destructive answer should never
 * be the one a thumb finds by habit.
 */
@Composable
fun Confirmation(
    question: String,
    subject: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WatchList(modifier = modifier) {
        Text(
            text = question,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = subject,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth(),
            label = { CentredLabel(confirmLabel) },
        )
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            label = { CentredLabel(stringResource(R.string.action_cancel)) },
        )
    }
}
