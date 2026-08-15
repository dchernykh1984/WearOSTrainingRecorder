package com.dchernykh.trainingrecorder.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.localization.R

/**
 * The first thing the rider sees: what they ride, then everything else.
 *
 * Two tiers rather than one long list. The favourites are the sports this rider
 * actually uses, newest kind first, and they are the whole point - a rider who
 * only rides gravel taps once, every time. Underneath them the catalogue is
 * browsed by discipline, which is where a sport tried once a year lives without
 * costing anything to scroll past.
 *
 * Forgetting a favourite is a long press, and it asks first. A tap here starts a
 * workout, so a control that removed one on a tap would sit next to eleven
 * controls that begin recording - and the same finger uses both.
 */
@Composable
fun SportPicker(
    favourites: List<SportType>,
    onSportChosen: (SportType) -> Unit,
    onForgetFavourite: (SportType) -> Unit,
    onPairSensors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openDiscipline by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }

    val removing = pendingRemoval?.let { id -> SportCatalogue.all.firstOrNull { it.id == id } }
    if (removing != null) {
        // Back answers "no". Without this the question is the one screen whose
        // back gesture leaves the app - and a rider who held a sport by accident
        // reaches for back first.
        BackHandler { pendingRemoval = null }
        Confirmation(
            question = stringResource(R.string.favourite_remove_question),
            subject = stringResource(Labels.sport(removing.id)),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                onForgetFavourite(removing)
                pendingRemoval = null
            },
            onCancel = { pendingRemoval = null },
        )
        return
    }

    val discipline = openDiscipline?.let(Discipline::byId)
    if (discipline != null) {
        BackHandler { openDiscipline = null }
        SportList(
            titleRes = Labels.discipline(discipline.id),
            sports = SportCatalogue.forDiscipline(discipline),
            onSportChosen = onSportChosen,
            modifier = modifier,
        )
        return
    }

    WatchList(modifier = modifier) {
        ListHeader { Text(stringResource(R.string.screen_title_start)) }
        if (favourites.isNotEmpty()) {
            favourites.forEach { sport ->
                Button(
                    onClick = { onSportChosen(sport) },
                    onLongClick = { pendingRemoval = sport.id },
                    onLongClickLabel = stringResource(R.string.action_remove),
                    modifier = Modifier.fillMaxWidth(),
                    label = { CentredLabel(stringResource(Labels.sport(sport.id))) },
                )
            }
            ListHeader { Text(stringResource(R.string.sports_all)) }
        }
        Discipline.entries.forEach { entry ->
            Button(
                onClick = { openDiscipline = entry.id },
                modifier = Modifier.fillMaxWidth(),
                label = { CentredLabel(stringResource(Labels.discipline(entry.id))) },
            )
        }
        // Last, below every sport: pairing is something a rider does once and
        // then never again, while starting a workout is what they came for.
        // Coloured because it is the one entry here that does not start a ride,
        // and a rider reaching for it in a hurry finds it by not being a sport.
        Button(
            onClick = onPairSensors,
            colors = ButtonDefaults.buttonColors(containerColor = LINK, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
            label = { CentredLabel(stringResource(R.string.sensors_title)) },
        )
    }
}

/** One discipline's sports, reached by tapping the discipline. */
@Composable
private fun SportList(
    titleRes: Int,
    sports: List<SportType>,
    onSportChosen: (SportType) -> Unit,
    modifier: Modifier = Modifier,
) {
    WatchList(modifier = modifier) {
        ListHeader { Text(stringResource(titleRes)) }
        sports.forEach { sport ->
            Button(
                onClick = { onSportChosen(sport) },
                modifier = Modifier.fillMaxWidth(),
                label = { CentredLabel(stringResource(Labels.sport(sport.id))) },
            )
        }
    }
}

/**
 * The colour of a wireless link, near enough to be recognised and far enough
 * from the Bluetooth mark to owe nobody anything.
 *
 * There is deliberately no Bluetooth rune here. The glyph and the word are
 * registered marks of the Bluetooth SIG and using them means qualifying the
 * product with them; the colour is not a mark, and it is what the eye finds
 * anyway.
 */
private val LINK = Color(0xFF1A6FD4)
