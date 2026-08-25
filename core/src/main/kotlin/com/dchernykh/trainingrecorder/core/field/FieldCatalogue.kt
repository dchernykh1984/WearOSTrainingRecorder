package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.field.FieldCategory.CADENCE
import com.dchernykh.trainingrecorder.core.field.FieldCategory.DISTANCE
import com.dchernykh.trainingrecorder.core.field.FieldCategory.ELEVATION
import com.dchernykh.trainingrecorder.core.field.FieldCategory.ENERGY
import com.dchernykh.trainingrecorder.core.field.FieldCategory.HEART_RATE
import com.dchernykh.trainingrecorder.core.field.FieldCategory.POWER
import com.dchernykh.trainingrecorder.core.field.FieldCategory.RACE_STATS
import com.dchernykh.trainingrecorder.core.field.FieldCategory.SEGMENT
import com.dchernykh.trainingrecorder.core.field.FieldCategory.SPEED
import com.dchernykh.trainingrecorder.core.field.FieldCategory.SWIMMING
import com.dchernykh.trainingrecorder.core.field.FieldCategory.TIME
import com.dchernykh.trainingrecorder.core.sport.Discipline

/**
 * Every value the rider can put on a screen.
 *
 * The race-stats ids are deliberately identical to the JSON keys the timing
 * server returns, so mapping a response onto fields is an identity lookup and a
 * key the server adds later needs no change here - the same "dumb watch"
 * contract the Garmin and Amazfit apps already rely on.
 */
object FieldCatalogue {
    private val cyclingOnly = setOf(Discipline.CYCLING)
    private val swimOnly = setOf(Discipline.SWIMMING)

    val time: List<DataFieldDef> =
        listOf(
            DataFieldDef("timer_elapsed", TIME),
            DataFieldDef("timer_moving", TIME),
            DataFieldDef("timer_paused", TIME),
            DataFieldDef("time_of_day", TIME),
            // Computed from the position and the date rather than read from
            // anywhere, so they are answerable on a mountain with no signal -
            // which is where the question gets asked.
            DataFieldDef("sunrise", TIME),
            DataFieldDef("sunset", TIME),
        )

    val distance: List<DataFieldDef> =
        listOf(
            DataFieldDef("distance_total", DISTANCE),
        )

    val speed: List<DataFieldDef> =
        listOf(
            DataFieldDef("speed_current", SPEED),
            DataFieldDef("speed_avg", SPEED),
            DataFieldDef("speed_max", SPEED),
            DataFieldDef("pace_current", SPEED),
            DataFieldDef("pace_avg", SPEED),
        )

    val heartRate: List<DataFieldDef> =
        listOf(
            // Every heart-rate field prefers a chest strap and falls back to the
            // watch's optical sensor, which is exactly the behaviour asked for.
            DataFieldDef("hr", HEART_RATE, SensorProfile.HEART_RATE),
            DataFieldDef("hr_avg", HEART_RATE, SensorProfile.HEART_RATE),
            DataFieldDef("hr_max", HEART_RATE, SensorProfile.HEART_RATE),
        )

    val cadence: List<DataFieldDef> =
        listOf(
            DataFieldDef("cadence", CADENCE, SensorProfile.CYCLING_SPEED_CADENCE),
            DataFieldDef("cadence_avg", CADENCE, SensorProfile.CYCLING_SPEED_CADENCE),
            DataFieldDef("cadence_max", CADENCE, SensorProfile.CYCLING_SPEED_CADENCE),
        )

    val power: List<DataFieldDef> =
        listOf(
            // A power meter has no substitute - these stay blank without one.
            powerField("power"),
            powerField("power_3s"),
            powerField("power_10s"),
            powerField("power_30s"),
            powerField("power_avg"),
            powerField("power_max"),
            powerField("power_normalized"),
            powerField("power_balance"),
            powerField("torque_effectiveness"),
            powerField("pedal_smoothness"),
        )

    val elevation: List<DataFieldDef> =
        listOf(
            DataFieldDef("altitude", ELEVATION),
            DataFieldDef("ascent_total", ELEVATION),
            DataFieldDef("descent_total", ELEVATION),
            DataFieldDef("grade", ELEVATION),
            DataFieldDef("vertical_speed", ELEVATION),
        )

    val energy: List<DataFieldDef> =
        listOf(
            DataFieldDef("calories", ENERGY),
        )

    val swimming: List<DataFieldDef> =
        listOf(
            DataFieldDef("pace_100", SWIMMING, disciplines = swimOnly),
        )

    /**
     * Live segments, against the rider's own best effort.
     *
     * Strava retired public leaderboards for third-party apps in 2020, so the
     * rival here is the rider's previous self rather than the KOM - the same
     * comparison Garmin calls "PR" and by some distance the one most riders
     * actually chase. The field names follow Garmin's, because a rider who has
     * used live segments before should not have to learn a second vocabulary.
     */
    val segment: List<DataFieldDef> =
        listOf(
            DataFieldDef("segment_name", SEGMENT),
            DataFieldDef("segment_to_start", SEGMENT),
            DataFieldDef("segment_time", SEGMENT),
            DataFieldDef("segment_time_left", SEGMENT),
            DataFieldDef("segment_covered", SEGMENT),
            DataFieldDef("segment_remaining", SEGMENT),
            DataFieldDef("segment_ahead", SEGMENT),
            DataFieldDef("segment_ahead_distance", SEGMENT),
            DataFieldDef("segment_best", SEGMENT),
            DataFieldDef("segment_projected", SEGMENT),
            DataFieldDef("segment_ascent", SEGMENT),
            DataFieldDef("segment_descent", SEGMENT),
            DataFieldDef("segment_ascent_left", SEGMENT),
            DataFieldDef("segment_descent_left", SEGMENT),
            DataFieldDef("segment_grade_left", SEGMENT),
        )

    /**
     * Ids match the timing server's JSON keys one for one. `qty_abs` and
     * `qty_group` are intentionally absent: the server sends them, but they are
     * rendered as the denominator of the place fields rather than on their own,
     * exactly as the Garmin and Amazfit apps do.
     */
    val raceStats: List<DataFieldDef> =
        listOf(
            raceField("place_abs"),
            raceField("place_group"),
            raceField("gap_prev_abs"),
            raceField("gap_next_abs"),
            raceField("gap_leader_abs"),
            raceField("gap_prev_abs_delta"),
            raceField("gap_next_abs_delta"),
            raceField("gap_leader_abs_delta"),
            raceField("gap_prev_group"),
            raceField("gap_next_group"),
            raceField("gap_leader_group"),
            raceField("gap_prev_group_delta"),
            raceField("gap_next_group_delta"),
            raceField("gap_leader_group_delta"),
            raceField("laps"),
        )

    val all: List<DataFieldDef> =
        time + distance + speed + heartRate + cadence + power +
            elevation + energy + swimming + segment + raceStats

    /** Shown in an empty slot and wherever a value is not available yet. */
    const val EMPTY_VALUE = "--"

    /** Keys the server sends that feed a field without being one themselves. */
    val raceStatsSupportingKeys: Set<String> = setOf("qty_abs", "qty_group")

    fun byId(id: String): DataFieldDef? = all.firstOrNull { it.id == id }

    fun forCategory(category: FieldCategory): List<DataFieldDef> = all.filter { it.category == category }

    fun forDiscipline(discipline: Discipline): List<DataFieldDef> = all.filter { it.availableFor(discipline) }

    /**
     * The same fields, gathered under their headings and in catalogue order.
     *
     * The picker shows these headings closed, so a category that filters down to
     * nothing for a given sport would be a heading that opens onto blank space.
     * Grouping after filtering is what keeps that from happening - and doing it
     * here rather than in the screen is what lets a test say so.
     */
    fun forDisciplineByCategory(discipline: Discipline): Map<FieldCategory, List<DataFieldDef>> =
        forDiscipline(discipline).groupBy { it.category }

    private fun powerField(id: String) =
        DataFieldDef(
            id = id,
            category = POWER,
            preferredProfile = SensorProfile.CYCLING_POWER,
            fallsBackToBuiltIn = false,
            disciplines = cyclingOnly,
        )

    private fun raceField(id: String) = DataFieldDef(id, RACE_STATS)
}
