package com.dchernykh.trainingrecorder.wear.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.fit.RecordedWorkout
import com.dchernykh.trainingrecorder.core.fit.TrackPoint
import com.dchernykh.trainingrecorder.core.format.FieldFormatter
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsFormatter
import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.recording.RecordingAction
import com.dchernykh.trainingrecorder.core.recording.RecordingPhase
import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import com.dchernykh.trainingrecorder.core.sensor.SensorSnapshot
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.core.workout.SportOrdering
import com.dchernykh.trainingrecorder.wear.health.ExerciseRecorder
import com.dchernykh.trainingrecorder.wear.service.RecordingService
import com.dchernykh.trainingrecorder.wear.storage.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ties the recording together: the state machine, Health Services, the samples
 * collected for the FIT file, and the values the screens read.
 *
 * The pieces below it are all testable on their own, which is why this layer is
 * kept thin - it wires them and owns nothing but the current state.
 */
class RecordingViewModel(
    application: Application,
    private val recorder: ExerciseRecorder = ExerciseRecorder(application),
    private val repository: WorkoutRepository = WorkoutRepository(application),
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _sensors = MutableStateFlow(SensorSnapshot())
    val sensors: StateFlow<SensorSnapshot> = _sensors.asStateFlow()

    private val history = MutableStateFlow<List<String>>(emptyList())

    /** The sport picker's order: recency by kind. */
    val sports: List<SportType> get() = SportOrdering.order(history.value)

    var screens: ScreenConfiguration = ScreenConfiguration.initial()
        private set

    var units: UnitSystem = UnitSystem.METRIC
        private set

    var raceStats: RaceStatsSnapshot = RaceStatsSnapshot.EMPTY
        private set

    private val samples = mutableListOf<TrackPoint>()
    private val external = mutableMapOf<String, SensorReading>()

    fun start(sport: SportType) {
        _state.update { it.prepare(sport.id, now()) }
        RecordingService.start(getApplication())
        viewModelScope.launch {
            runCatching { recorder.start(sport.id) }
                .onSuccess {
                    _state.update { current ->
                        if (current.phase == RecordingPhase.PREPARING) current.begin(now()) else current
                    }
                    collectSamples()
                }.onFailure { discard() }
        }
    }

    private suspend fun collectSamples() {
        recorder.samples().collect { sample ->
            val timestamp = now()
            _sensors.value =
                SensorSnapshot.merge(
                    external = external.toMap(),
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
                        heartRateBpm = _sensors.value.value("hr")?.toInt(),
                        cadenceRpm = _sensors.value.value("cadence")?.toInt(),
                        speedMps = _sensors.value.value("speed_current"),
                        powerWatts = _sensors.value.value("power")?.toInt(),
                        distanceMeters = _sensors.value.value("distance_total"),
                    )
            }
        }
    }

    fun onAction(action: RecordingAction) {
        when (action) {
            RecordingAction.PAUSE -> viewModelScope.launch { pause() }
            RecordingAction.RESUME -> viewModelScope.launch { resume() }
            RecordingAction.FINISH -> viewModelScope.launch { finish() }
            RecordingAction.DISCARD -> discard()
            RecordingAction.START -> Unit
        }
    }

    private suspend fun pause() {
        runCatching { recorder.pause() }
        _state.update { it.pause(now()) }
    }

    private suspend fun resume() {
        runCatching { recorder.resume() }
        _state.update { it.resume(now()) }
    }

    private suspend fun finish() {
        // Flushed before ending: Health Services batches samples, and without
        // this the last minutes of the ride are still in the buffer.
        runCatching { recorder.flush() }
        runCatching { recorder.end() }
        val finished = _state.updateAndGet { it.finish(now()) }
        save(finished)
        RecordingService.stop(getApplication())
    }

    private fun save(finished: RecordingState) {
        val sportId = finished.sportTypeId ?: return
        val start = finished.startedAtEpochMs ?: return
        val recorded =
            RecordedWorkout(
                sportTypeId = sportId,
                startedAtEpochMs = start,
                totalTimerSeconds = finished.movingMillisAt(now()) / MILLIS_PER_SECOND,
                totalElapsedSeconds = finished.elapsedMillisAt(now()) / MILLIS_PER_SECOND,
                totalDistanceMeters = _sensors.value.value("distance_total") ?: 0.0,
                points = samples.toList(),
            )
        runCatching { repository.save(workoutId(start), recorded, enabledConnectors = emptySet()) }
        samples.clear()
        history.update { SportOrdering.record(it, sportId) }
    }

    fun discard() {
        viewModelScope.launch { runCatching { recorder.end() } }
        _state.value = RecordingState()
        samples.clear()
        RecordingService.stop(getApplication())
    }

    /** What a slot shows, whatever kind of field it holds. */
    fun display(fieldId: String): String {
        val definition = FieldCatalogue.byId(fieldId) ?: return FieldCatalogue.EMPTY_VALUE
        if (definition.category == com.dchernykh.trainingrecorder.core.field.FieldCategory.RACE_STATS) {
            return RaceStatsFormatter.displayValue(fieldId, raceStats)
        }
        val now = now()
        return when (fieldId) {
            "timer_elapsed" -> FieldFormatter.duration(_state.value.elapsedMillisAt(now))
            "timer_moving" -> FieldFormatter.duration(_state.value.movingMillisAt(now))
            "time_of_day" -> FieldFormatter.clockTime(now)
            "distance_total" -> FieldFormatter.distance(_sensors.value.value(fieldId), units)
            "speed_current", "speed_avg", "speed_max" -> FieldFormatter.speed(_sensors.value.value(fieldId), units)
            "pace_current", "pace_avg" -> FieldFormatter.pace(_sensors.value.value("speed_current"), units)
            "altitude", "ascent_total", "descent_total" ->
                FieldFormatter.elevation(_sensors.value.value(fieldId), units)
            "grade" -> FieldFormatter.grade(_sensors.value.value(fieldId))
            else -> FieldFormatter.integer(_sensors.value.value(fieldId))
        }
    }

    private fun workoutId(startedAt: Long): String = "workout-$startedAt"

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}

private fun <T> MutableStateFlow<T>.updateAndGet(transform: (T) -> T): T {
    update(transform)
    return value
}
