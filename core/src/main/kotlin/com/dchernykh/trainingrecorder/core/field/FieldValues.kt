package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.format.FieldFormatter
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsFormatter
import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.segment.SegmentState
import com.dchernykh.trainingrecorder.core.sensor.SensorSnapshot
import com.dchernykh.trainingrecorder.core.solar.SolarEvents

/**
 * Where and when the watch is, which two fields need and the rest ignore.
 *
 * Grouped rather than passed alongside the rest because they are the same fact
 * seen twice: the zone offset says what a clock time means here, and the sun's
 * timetable is that same "here" applied to the sky. Both are supplied by the
 * caller rather than read from the platform, so the whole of [FieldValues] stays
 * a pure function of what it is given.
 */
data class Surroundings(
    val clockOffsetMinutes: Int = 0,
    /** Null until the watch knows where it is; the sun's fields then read empty. */
    val solar: SolarEvents? = null,
)

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
    private const val MILLIS_PER_SECOND = 1000.0

    private val DISTANCE_FIELDS = setOf("distance_total")

    private val SPEED_FIELDS = setOf("speed_current", "speed_avg", "speed_max")

    /** Each pace field over the speed it is the inverse of. */
    private val PACE_SOURCES =
        mapOf(
            "pace_current" to "speed_current",
            "pace_avg" to "speed_avg",
        )

    private val ELEVATION_FIELDS = setOf("altitude", "ascent_total", "descent_total")

    private val PERCENT_FIELDS =
        setOf(
            "power_balance",
            "pedal_smoothness",
            "torque_effectiveness",
        )

    /**
     * Every field at once. The screens read a map rather than calling per field
     * because a Compose slot can observe a value but not a function call.
     *
     * [surroundings] is passed in rather than read from the default zone and a
     * location service, so this stays deterministic; the caller is what knows
     * where the watch is.
     */
    @Suppress("LongParameterList")
    fun snapshot(
        state: RecordingState,
        nowEpochMs: Long,
        sensors: SensorSnapshot = SensorSnapshot(),
        race: RaceStatsSnapshot = RaceStatsSnapshot.EMPTY,
        units: UnitSystem = UnitSystem.METRIC,
        surroundings: Surroundings = Surroundings(),
        segment: SegmentState? = null,
    ): Map<String, String> =
        FieldCatalogue.all.associate { definition ->
            definition.id to value(definition, state, sensors, race, units, nowEpochMs, surroundings, segment)
        }

    @Suppress("LongParameterList", "ReturnCount")
    private fun value(
        definition: DataFieldDef,
        state: RecordingState,
        sensors: SensorSnapshot,
        race: RaceStatsSnapshot,
        units: UnitSystem,
        now: Long,
        surroundings: Surroundings,
        segment: SegmentState?,
    ): String {
        val id = definition.id
        // Race stats arrive pre-formatted from the server; reformatting them
        // would only be a chance to disagree with the timing screen.
        if (definition.category == FieldCategory.RACE_STATS) return RaceStatsFormatter.displayValue(id, race)
        if (definition.category == FieldCategory.SEGMENT) return segmentValue(id, segment, units)
        timerValue(id, state, now, surroundings)?.let { return it }
        val reading = sensors.value(id)
        return when {
            id in DISTANCE_FIELDS -> FieldFormatter.distance(reading, units)
            id in SPEED_FIELDS -> FieldFormatter.speed(reading, units)
            id in PACE_SOURCES -> FieldFormatter.pace(sensors.value(PACE_SOURCES.getValue(id)), units)
            id == "pace_100" -> FieldFormatter.pacePer100m(sensors.value("speed_current"))
            id in ELEVATION_FIELDS -> FieldFormatter.elevation(reading, units)
            id in PERCENT_FIELDS -> FieldFormatter.percent(reading)
            id == "grade" -> FieldFormatter.grade(reading)
            else -> FieldFormatter.integer(reading)
        }
    }

    /**
     * What the live segment reads, or empty where there is nothing to say.
     *
     * Every one of these is empty most of the ride, and that is correct: there
     * is no segment under the wheels most of the time. The one exception is the
     * distance to the next segment, which is worth knowing precisely when the
     * rider is not on one yet.
     */
    private fun segmentValue(
        id: String,
        segment: SegmentState?,
        units: UnitSystem,
    ): String {
        if (segment == null) return FieldFormatter.empty
        return when {
            id == "segment_name" -> segment.segment.name
            id == "segment_best" -> FieldFormatter.duration(segment.segment.referenceSeconds?.let { millis(it) })
            // Zero rather than empty while the rider is on it: the way to the
            // nearest segment, when it is under the wheels, is no way at all.
            id == "segment_to_start" ->
                FieldFormatter.distance(if (segment.timing) 0.0 else segment.toStartMeters, units)
            segment.timing -> onTheSegment(id, segment, units)
            else -> FieldFormatter.empty
        }
    }

    /** The fields that only mean anything once the effort has begun. */
    private fun onTheSegment(
        id: String,
        segment: SegmentState,
        units: UnitSystem,
    ): String = clockOnTheSegment(id, segment) ?: groundOnTheSegment(id, segment, units)

    /** What the effort is doing to the clock. */
    private fun clockOnTheSegment(
        id: String,
        segment: SegmentState,
    ): String? =
        when (id) {
            "segment_time" -> FieldFormatter.duration(millis(segment.elapsedSeconds))
            "segment_time_left" -> FieldFormatter.duration(segment.estimatedRemainingSeconds?.let { millis(it) })
            "segment_projected" -> FieldFormatter.duration(segment.projectedSeconds?.let { millis(it) })
            "segment_ahead" -> FieldFormatter.signedDuration(segment.aheadSeconds?.let { millis(it) })
            else -> null
        }

    /** And what it is doing to the ground: distance covered, left, and climbed. */
    private fun groundOnTheSegment(
        id: String,
        segment: SegmentState,
        units: UnitSystem,
    ): String =
        when (id) {
            "segment_covered" -> FieldFormatter.distance(segment.coveredMeters, units)
            "segment_remaining" -> FieldFormatter.distance(segment.remainingMeters, units)
            "segment_ahead_distance" -> FieldFormatter.signedDistance(segment.aheadMeters, units)
            "segment_ascent" -> FieldFormatter.elevation(segment.ascentMeters, units)
            "segment_descent" -> FieldFormatter.elevation(segment.descentMeters, units)
            "segment_ascent_left" -> FieldFormatter.elevation(segment.remainingAscentMeters, units)
            "segment_descent_left" -> FieldFormatter.elevation(segment.remainingDescentMeters, units)
            "segment_grade_left" -> FieldFormatter.grade(segment.remainingGradePercent)
            else -> FieldFormatter.empty
        }

    private fun millis(seconds: Double): Long = (seconds * MILLIS_PER_SECOND).toLong()

    /** The fields the recording itself owns, rather than any sensor. */
    private fun timerValue(
        id: String,
        state: RecordingState,
        now: Long,
        surroundings: Surroundings,
    ): String? =
        when (id) {
            "timer_elapsed" -> FieldFormatter.duration(state.elapsedMillisAt(now))
            "timer_moving" -> FieldFormatter.duration(state.movingMillisAt(now))
            // Derived rather than tracked: paused time is whatever the clock ran
            // that the workout did not.
            "timer_paused" -> FieldFormatter.duration(state.elapsedMillisAt(now) - state.movingMillisAt(now))
            "time_of_day" -> FieldFormatter.clockTime(now, surroundings.clockOffsetMinutes)
            // Empty rather than absent when there is no position or the sun does
            // not rise at all that day: a blank field is the honest answer to
            // "when is sunset" north of the Arctic circle in June.
            "sunrise" ->
                FieldFormatter.clockTime(surroundings.solar?.sunriseEpochMs, surroundings.clockOffsetMinutes)

            "sunset" ->
                FieldFormatter.clockTime(surroundings.solar?.sunsetEpochMs, surroundings.clockOffsetMinutes)
            else -> null
        }
}
