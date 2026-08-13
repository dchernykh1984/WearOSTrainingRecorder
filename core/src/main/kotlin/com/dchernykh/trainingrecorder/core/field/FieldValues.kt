package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.format.FieldFormatter
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsFormatter
import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.sensor.SensorSnapshot

/**
 * Turns everything the watch currently knows into the strings the slots show.
 *
 * Kept out of the view model and out of Android entirely: what a field reads is
 * a pure function of the recording, the sensors and the chosen units, and the
 * only way to be confident that seventy-odd fields all render sensibly is to
 * test them - which needs them reachable without a watch.
 *
 * Fields are grouped by how they are formatted rather than handled one by one:
 * a new speed field should read correctly the moment it joins the catalogue,
 * not once someone remembers to add a branch for it.
 */
object FieldValues {
    /** Durations the sensors report in seconds rather than the timer itself. */
    private val DURATION_FIELDS = setOf("lap_time", "lap_time_last")

    private val DISTANCE_FIELDS = setOf("distance_total", "distance_lap", "distance_lap_last")

    private val SPEED_FIELDS = setOf("speed_current", "speed_avg", "speed_max", "speed_lap")

    /** Each pace field over the speed it is the inverse of. */
    private val PACE_SOURCES =
        mapOf(
            "pace_current" to "speed_current",
            "pace_avg" to "speed_avg",
            "pace_lap" to "speed_lap",
        )

    private val ELEVATION_FIELDS = setOf("altitude", "ascent_total", "descent_total")

    private val PERCENT_FIELDS =
        setOf(
            "hr_pct_max",
            "hr_pct_hrr",
            "power_balance",
            "pedal_smoothness",
            "torque_effectiveness",
            "sensor_hr_battery",
            "sensor_cadence_battery",
            "sensor_power_battery",
        )

    /** Ratios below one, which rounding to a whole number would erase. */
    private val DECIMAL_FIELDS = setOf("intensity_factor", "power_per_kg")

    /**
     * Every field at once. The screens read a map rather than calling per field
     * because a Compose slot can observe a value but not a function call.
     *
     * [clockOffsetMinutes] is passed in rather than read from the default zone,
     * so this stays deterministic; the caller knows the watch's zone.
     */
    fun snapshot(
        state: RecordingState,
        nowEpochMs: Long,
        sensors: SensorSnapshot = SensorSnapshot(),
        race: RaceStatsSnapshot = RaceStatsSnapshot.EMPTY,
        units: UnitSystem = UnitSystem.METRIC,
        clockOffsetMinutes: Int = 0,
    ): Map<String, String> =
        FieldCatalogue.all.associate { definition ->
            definition.id to value(definition, state, sensors, race, units, nowEpochMs, clockOffsetMinutes)
        }

    @Suppress("LongParameterList", "ReturnCount")
    private fun value(
        definition: DataFieldDef,
        state: RecordingState,
        sensors: SensorSnapshot,
        race: RaceStatsSnapshot,
        units: UnitSystem,
        now: Long,
        offsetMinutes: Int,
    ): String {
        val id = definition.id
        // Race stats arrive pre-formatted from the server; reformatting them
        // would only be a chance to disagree with the timing screen.
        if (definition.category == FieldCategory.RACE_STATS) return RaceStatsFormatter.displayValue(id, race)
        timerValue(id, state, now, offsetMinutes)?.let { return it }
        val reading = sensors.value(id)
        return when {
            id in DURATION_FIELDS -> FieldFormatter.duration(reading?.times(MILLIS_PER_SECOND)?.toLong())
            id in DISTANCE_FIELDS -> FieldFormatter.distance(reading, units)
            id in SPEED_FIELDS -> FieldFormatter.speed(reading, units)
            id in PACE_SOURCES -> FieldFormatter.pace(sensors.value(PACE_SOURCES.getValue(id)), units)
            id == "pace_100" -> FieldFormatter.pacePer100m(sensors.value("speed_current"))
            id in ELEVATION_FIELDS -> FieldFormatter.elevation(reading, units)
            id in PERCENT_FIELDS -> FieldFormatter.percent(reading)
            id in DECIMAL_FIELDS -> FieldFormatter.decimal(reading)
            id == "grade" -> FieldFormatter.grade(reading)
            else -> FieldFormatter.integer(reading)
        }
    }

    /** The fields the recording itself owns, rather than any sensor. */
    private fun timerValue(
        id: String,
        state: RecordingState,
        now: Long,
        offsetMinutes: Int,
    ): String? =
        when (id) {
            "timer_elapsed" -> FieldFormatter.duration(state.elapsedMillisAt(now))
            "timer_moving" -> FieldFormatter.duration(state.movingMillisAt(now))
            // Derived rather than tracked: paused time is whatever the clock ran
            // that the workout did not.
            "timer_paused" -> FieldFormatter.duration(state.elapsedMillisAt(now) - state.movingMillisAt(now))
            "time_of_day" -> FieldFormatter.clockTime(now, offsetMinutes)
            else -> null
        }

    private const val MILLIS_PER_SECOND = 1000.0
}
