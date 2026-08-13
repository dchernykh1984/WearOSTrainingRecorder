package com.dchernykh.trainingrecorder.wear.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.field.FieldValues
import com.dchernykh.trainingrecorder.core.fit.RecordedWorkout
import com.dchernykh.trainingrecorder.core.fit.TrackPoint
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.recording.RecordingAction
import com.dchernykh.trainingrecorder.core.recording.RecordingPhase
import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.sensor.SensorSnapshot
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.core.workout.SportOrdering
import com.dchernykh.trainingrecorder.wear.health.ExerciseRecorder
import com.dchernykh.trainingrecorder.wear.race.RaceStatsPoller
import com.dchernykh.trainingrecorder.wear.service.RecordingService
import com.dchernykh.trainingrecorder.wear.storage.WorkoutRepository
import com.dchernykh.trainingrecorder.wear.sync.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * Ties the recording together: the state machine, Health Services, the samples
 * collected for the FIT file, and the values the screens read.
 *
 * The screens read [values], a snapshot map recomputed on a ticker rather than
 * pulled on demand. Composables cannot observe a function call, so a display
 * that asked the model for each field would render once and then freeze - which
 * on a workout screen looks exactly like a stopped watch.
 */
class RecordingViewModel(
    application: Application,
    private val recorder: ExerciseRecorder = ExerciseRecorder(application),
    private val repository: WorkoutRepository = WorkoutRepository(application),
    private val settings: SettingsStore = SettingsStore(application),
    private val poller: RaceStatsPoller = RaceStatsPoller(),
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())

    /** What each field currently reads, recomputed once a second. */
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    private val sensors = MutableStateFlow(SensorSnapshot())
    private val raceStats = MutableStateFlow(RaceStatsSnapshot.EMPTY)
    private val history = MutableStateFlow<List<String>>(emptyList())

    /** The sport picker's order: recency by kind. */
    val sports: List<SportType> get() = SportOrdering.order(history.value)

    private var screens: ScreenConfiguration = ScreenConfiguration.initial()
    private var units: UnitSystem = UnitSystem.METRIC
    private var race: RaceStatsConfig = RaceStatsConfig()

    private val samples = mutableListOf<TrackPoint>()
    private var sampleJob: Job? = null
    private var raceJob: Job? = null
    private var tickJob: Job? = null

    /** Guards the transitions: each one awaits Health Services first. */
    private var busy = false

    init {
        applySettings()
    }

    /** Re-read whenever the screen comes back, so a phone push lands promptly. */
    fun applySettings() {
        settings.read()?.let {
            screens = it.screens
            units = it.units
            race = it.race
        }
    }

    fun screensFor(sport: SportType) = screens.resolve(sport)

    fun start(sport: SportType) {
        // PREPARING counts as started even though the state machine does not call
        // it active: between the tap and Health Services answering there is a
        // window where a second tap would start a second session.
        if (busy || _state.value.phase.isActive || _state.value.phase == RecordingPhase.PREPARING) return
        applySettings()
        _state.update { it.prepare(sport.id, now()) }
        RecordingService.start(getApplication())
        // A ticker rather than only recomputing when a sample lands: the timers
        // have to keep counting through a tunnel, where no sample lands at all.
        tickJob?.cancel()
        tickJob =
            viewModelScope.launch {
                while (true) {
                    recomputeValues()
                    delay(TICK_MS)
                }
            }
        sampleJob =
            viewModelScope.launch {
                runCatching { recorder.start(sport.id) }
                    .onSuccess {
                        _state.update { current ->
                            if (current.phase == RecordingPhase.PREPARING) current.begin(now()) else current
                        }
                        startRacePolling()
                        collectSamples()
                    }.onFailure { discard() }
            }
    }

    private fun startRacePolling() {
        if (!race.isComplete) return
        raceJob?.cancel()
        raceJob = viewModelScope.launch { poller.poll(race).collect { raceStats.value = it } }
    }

    private suspend fun collectSamples() {
        recorder.samples().collect { sample ->
            val timestamp = now()
            sensors.value =
                SensorSnapshot.merge(
                    external = emptyMap(),
                    builtIn = sample.readings,
                    nowEpochMs = timestamp,
                )
            // Only recorded while running: a paused ride must not lay down a
            // straight line between where the rider stopped and where they
            // started again.
            if (_state.value.phase == RecordingPhase.RECORDING) {
                samples +=
                    TrackPoint(
                        timestampEpochMs = timestamp,
                        latitudeDeg = sample.latitudeDeg,
                        longitudeDeg = sample.longitudeDeg,
                        altitudeMeters = sample.altitudeMeters,
                        heartRateBpm = sensors.value.value("hr")?.toInt(),
                        cadenceRpm = sensors.value.value("cadence")?.toInt(),
                        speedMps = sensors.value.value("speed_current"),
                        powerWatts = sensors.value.value("power")?.toInt(),
                        distanceMeters = sensors.value.value("distance_total"),
                    )
            }
            recomputeValues()
        }
    }

    fun onAction(action: RecordingAction) {
        // Every transition awaits Health Services, and a second tap during that
        // window would hit a state the machine refuses - which is a crash, not a
        // no-op. Ignoring the tap is the honest response.
        if (busy) return
        when (action) {
            RecordingAction.PAUSE -> transition({ recorder.pause() }) { it.pause(now()) }
            RecordingAction.RESUME -> transition({ recorder.resume() }) { it.resume(now()) }
            RecordingAction.FINISH -> {
                busy = true
                viewModelScope.launch { finish() }
            }
            RecordingAction.DISCARD -> discard()
            RecordingAction.START -> Unit
        }
    }

    private fun transition(
        call: suspend () -> Unit,
        apply: (RecordingState) -> RecordingState,
    ) {
        busy = true
        viewModelScope.launch {
            try {
                // The transition is only applied if the platform accepted it, so
                // a refused pause leaves the screens saying RECORDING - which is
                // what the watch is actually doing.
                if (runCatching { call() }.isSuccess) _state.update(apply)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun finish() {
        try {
            // Flushed before ending: Health Services batches samples, and without
            // this the last minutes of the ride are still in the buffer.
            runCatching { recorder.flush() }
            runCatching { recorder.end() }
            stopCollecting()
            _state.update { it.finish(now()) }
            save(_state.value)
            RecordingService.stop(getApplication())
        } finally {
            busy = false
        }
    }

    private suspend fun save(finished: RecordingState) {
        val sportId = finished.sportTypeId ?: return
        val start = finished.startedAtEpochMs ?: return
        val recorded =
            RecordedWorkout(
                sportTypeId = sportId,
                startedAtEpochMs = start,
                totalTimerSeconds = finished.movingMillisAt(now()) / MILLIS_PER_SECOND,
                totalElapsedSeconds = finished.elapsedMillisAt(now()) / MILLIS_PER_SECOND,
                totalDistanceMeters = sensors.value.value("distance_total") ?: 0.0,
                points = samples.toList(),
            )
        // Encoding a six-hour ride is thousands of messages plus a file write and
        // an index rewrite; on the main thread that is an ANR at exactly the
        // moment the rider is waiting to see their ride saved.
        val saved =
            withContext(Dispatchers.IO) {
                runCatching { repository.save("workout-$start", recorded, CONNECTORS) }
            }
        // The samples are the only copy of the ride until the file exists, so
        // they are kept if the write failed - a retry has something to write.
        if (saved.isSuccess) samples.clear()
        history.update { SportOrdering.record(it, sportId) }
    }

    fun discard() {
        viewModelScope.launch { runCatching { recorder.end() } }
        stopCollecting()
        _state.value = RecordingState()
        samples.clear()
        busy = false
        RecordingService.stop(getApplication())
    }

    private fun stopCollecting() {
        // Cancelled explicitly so the flow's awaitClose runs and Health Services
        // drops the callback; left running, every new workout stacks another
        // collector on top of the last.
        sampleJob?.cancel()
        sampleJob = null
        raceJob?.cancel()
        raceJob = null
        tickJob?.cancel()
        tickJob = null
        recomputeValues()
    }

    override fun onCleared() {
        stopCollecting()
        super.onCleared()
    }

    private fun recomputeValues() {
        val now = now()
        _values.value =
            FieldValues.snapshot(
                state = _state.value,
                nowEpochMs = now,
                sensors = sensors.value,
                race = raceStats.value,
                units = units,
                // The watch's own zone, so the clock field is not UTC.
                clockOffsetMinutes = TimeZone.getDefault().getOffset(now) / MILLIS_PER_MINUTE,
            )
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
        const val MILLIS_PER_MINUTE = 60_000
        const val TICK_MS = 1000L

        /**
         * The services a finished workout is owed to. Saving with an empty set
         * would make the retention policy consider every ride settled the moment
         * it was written, and evict rides that had never been uploaded.
         */
        val CONNECTORS = setOf("garmin", "strava")
    }
}
