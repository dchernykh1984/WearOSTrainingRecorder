package com.dchernykh.trainingrecorder.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.MaterialTheme
import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.layout.ScreenShape
import com.dchernykh.trainingrecorder.core.recording.RecordingPhase
import com.dchernykh.trainingrecorder.core.sensor.FixStatus
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.wear.recording.RecordingViewModel
import com.dchernykh.trainingrecorder.wear.recording.SensorPairingViewModel

/**
 * The watch app: pick a sport, then the recording screens.
 *
 * There is no navigation graph because there are only two places to be, and
 * which one is decided by whether a recording is running - so it survives the
 * process being restarted mid-ride without any saved back stack.
 */
@Composable
fun TrainingRecorderApp(
    model: RecordingViewModel = viewModel(),
    sensorModel: SensorPairingViewModel = viewModel(),
) {
    MaterialTheme {
        WithRecordingPermissions {
            // Once per launch: what the phone published before this watch existed
            // arrives no other way, since the listener only hears changes.
            LaunchedEffect(Unit) { model.syncFromPhone() }
            val state by model.state.collectAsStateWithLifecycle()
            var pairing by remember { mutableStateOf(false) }
            if (state.phase == RecordingPhase.IDLE || state.phase == RecordingPhase.FINISHED) {
                if (pairing) {
                    SensorPairingScreen(model = sensorModel, onLeave = { pairing = false })
                } else {
                    val favourites by model.favourites.collectAsStateWithLifecycle()
                    SportPicker(
                        favourites = favourites,
                        onSportChosen = model::start,
                        onForgetFavourite = model::forgetFavourite,
                        onPairSensors = { pairing = true },
                    )
                }
            } else {
                // Read as observed state, not called as a function: a lambda that
                // asked the model for each value would be invoked once and never
                // again, freezing every field for the whole ride.
                val values by model.values.collectAsStateWithLifecycle()
                // Observed for the same reason the values are: a layout the
                // rider changes on the phone should reach the ride they are on,
                // not the one after it.
                val configuration by model.configuration.collectAsStateWithLifecycle()
                val sport = sportOf(model, state.sportTypeId)
                // Only where there is a position to have: an indoor ride is not
                // failing to find satellites, it never asked for any, and a red
                // GPS on a turbo trainer is a bug report waiting to be filed.
                val fix by model.fix.collectAsStateWithLifecycle(FixStatus.NONE)
                RecordingPager(
                    screens = configuration.resolve(requireNotNull(sport)),
                    shape = currentShape(),
                    values = { values[it] ?: FieldCatalogue.EMPTY_VALUE },
                    actions = state.availableActions,
                    onAction = model::onAction,
                    sportLabelRes = Labels.sport(sport.id),
                    fix = fix.takeIf { model.tracksPosition(requireNotNull(sport)) },
                )
            }
        }
    }
}

/**
 * The pairing screen, scanning only while it is on screen.
 *
 * Tied to composition rather than to a button, because a BLE scan the rider
 * forgot to stop is the fastest way to flatten a watch battery there is.
 */
@Composable
private fun SensorPairingScreen(
    model: SensorPairingViewModel,
    onLeave: () -> Unit,
) {
    val paired by model.paired.collectAsStateWithLifecycle()
    val discovered by model.discovered.collectAsStateWithLifecycle()
    val scanning by model.scanning.collectAsStateWithLifecycle()
    val connected by model.connected.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        model.startScan()
        onDispose { model.stopScan() }
    }
    BackHandler { onLeave() }
    SensorPairing(
        paired = paired,
        discovered = discovered,
        connected = connected,
        scanning = scanning,
        onPair = model::pair,
        onForget = model::forget,
    )
}

private fun sportOf(
    model: RecordingViewModel,
    sportTypeId: String?,
): SportType? = model.sports.value.firstOrNull { it.id == sportTypeId } ?: model.sports.value.firstOrNull()

/** Round and square watches want different layouts, and the system knows which. */
@Composable
private fun currentShape(): ScreenShape =
    if (LocalConfiguration.current.isScreenRound) ScreenShape.ROUND else ScreenShape.SQUARE
