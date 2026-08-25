package com.dchernykh.trainingrecorder.localization

/**
 * Maps the stable ids the core module works in onto localized string resources.
 *
 * The maps are explicit rather than resolved by name at runtime: getIdentifier
 * defeats resource shrinking and fails silently when a key is missing, whereas a
 * missing entry here is caught by the completeness test.
 */
object Labels {
    private val sports =
        mapOf(
            "cycling_road" to R.string.sport_cycling_road,
            "cycling_gravel" to R.string.sport_cycling_gravel,
            "cycling_mtb" to R.string.sport_cycling_mtb,
            "cycling_cyclocross" to R.string.sport_cycling_cyclocross,
            "cycling_indoor" to R.string.sport_cycling_indoor,
            "cycling_virtual" to R.string.sport_cycling_virtual,
            "cycling_commute" to R.string.sport_cycling_commute,
            "cycling_ebike" to R.string.sport_cycling_ebike,
            "cycling_ebike_mtb" to R.string.sport_cycling_ebike_mtb,
            "cycling_track" to R.string.sport_cycling_track,
            "run_road" to R.string.sport_run_road,
            "run_trail" to R.string.sport_run_trail,
            "run_treadmill" to R.string.sport_run_treadmill,
            "run_track" to R.string.sport_run_track,
            "run_virtual" to R.string.sport_run_virtual,
            "run_indoor" to R.string.sport_run_indoor,
            "run_ultra" to R.string.sport_run_ultra,
            "walk" to R.string.sport_walk,
            "hike" to R.string.sport_hike,
            "ruck" to R.string.sport_ruck,
            "swim_pool" to R.string.sport_swim_pool,
            "swim_open_water" to R.string.sport_swim_open_water,
            "swim_generic" to R.string.sport_swim_generic,
            "xc_classic" to R.string.sport_xc_classic,
            "xc_skate" to R.string.sport_xc_skate,
            "xc_generic" to R.string.sport_xc_generic,
            "xc_backcountry" to R.string.sport_xc_backcountry,
            "xc_rollerski_classic" to R.string.sport_xc_rollerski_classic,
            "xc_rollerski_skate" to R.string.sport_xc_rollerski_skate,
            "snowshoe" to R.string.sport_snowshoe,
            "alpine_ski" to R.string.sport_alpine_ski,
        )

    private val disciplines =
        mapOf(
            "cycling" to R.string.discipline_cycling,
            "running" to R.string.discipline_running,
            "swimming" to R.string.discipline_swimming,
            "xc_skiing" to R.string.discipline_xc_skiing,
        )

    private val categories =
        mapOf(
            "time" to R.string.category_time,
            "distance" to R.string.category_distance,
            "speed" to R.string.category_speed,
            "heart_rate" to R.string.category_heart_rate,
            "cadence" to R.string.category_cadence,
            "power" to R.string.category_power,
            "elevation" to R.string.category_elevation,
            "energy" to R.string.category_energy,
            "swimming" to R.string.category_swimming,
            "segment" to R.string.category_segment,
            "race_stats" to R.string.category_race_stats,
        )

    private val fields =
        mapOf(
            "timer_elapsed" to R.string.field_timer_elapsed,
            "timer_moving" to R.string.field_timer_moving,
            "timer_paused" to R.string.field_timer_paused,
            "time_of_day" to R.string.field_time_of_day,
            "sunrise" to R.string.field_sunrise,
            "sunset" to R.string.field_sunset,
            "distance_total" to R.string.field_distance_total,
            "speed_current" to R.string.field_speed_current,
            "speed_avg" to R.string.field_speed_avg,
            "speed_max" to R.string.field_speed_max,
            "pace_current" to R.string.field_pace_current,
            "pace_avg" to R.string.field_pace_avg,
            "hr" to R.string.field_hr,
            "hr_avg" to R.string.field_hr_avg,
            "hr_max" to R.string.field_hr_max,
            "cadence" to R.string.field_cadence,
            "cadence_avg" to R.string.field_cadence_avg,
            "cadence_max" to R.string.field_cadence_max,
            "power" to R.string.field_power,
            "power_3s" to R.string.field_power_3s,
            "power_10s" to R.string.field_power_10s,
            "power_30s" to R.string.field_power_30s,
            "power_avg" to R.string.field_power_avg,
            "power_max" to R.string.field_power_max,
            "power_normalized" to R.string.field_power_normalized,
            "power_balance" to R.string.field_power_balance,
            "torque_effectiveness" to R.string.field_torque_effectiveness,
            "pedal_smoothness" to R.string.field_pedal_smoothness,
            "altitude" to R.string.field_altitude,
            "ascent_total" to R.string.field_ascent_total,
            "descent_total" to R.string.field_descent_total,
            "grade" to R.string.field_grade,
            "vertical_speed" to R.string.field_vertical_speed,
            "calories" to R.string.field_calories,
            "pace_100" to R.string.field_pace_100,
            "place_abs" to R.string.field_place_abs,
            "place_group" to R.string.field_place_group,
            "gap_prev_abs" to R.string.field_gap_prev_abs,
            "gap_next_abs" to R.string.field_gap_next_abs,
            "gap_leader_abs" to R.string.field_gap_leader_abs,
            "gap_prev_abs_delta" to R.string.field_gap_prev_abs_delta,
            "gap_next_abs_delta" to R.string.field_gap_next_abs_delta,
            "gap_leader_abs_delta" to R.string.field_gap_leader_abs_delta,
            "gap_prev_group" to R.string.field_gap_prev_group,
            "gap_next_group" to R.string.field_gap_next_group,
            "gap_leader_group" to R.string.field_gap_leader_group,
            "gap_prev_group_delta" to R.string.field_gap_prev_group_delta,
            "gap_next_group_delta" to R.string.field_gap_next_group_delta,
            "gap_leader_group_delta" to R.string.field_gap_leader_group_delta,
            "laps" to R.string.field_laps,
            "segment_name" to R.string.field_segment_name,
            "segment_to_start" to R.string.field_segment_to_start,
            "segment_time" to R.string.field_segment_time,
            "segment_remaining" to R.string.field_segment_remaining,
            "segment_ahead" to R.string.field_segment_ahead,
            "segment_ahead_distance" to R.string.field_segment_ahead_distance,
            "segment_best" to R.string.field_segment_best,
            "segment_projected" to R.string.field_segment_projected,
            "segment_ascent_left" to R.string.field_segment_ascent_left,
            "segment_grade_left" to R.string.field_segment_grade_left,
            "segment_time_left" to R.string.field_segment_time_left,
            "segment_covered" to R.string.field_segment_covered,
            "segment_ascent" to R.string.field_segment_ascent,
            "segment_descent" to R.string.field_segment_descent,
            "segment_descent_left" to R.string.field_segment_descent_left,
        )

    /** Zero when the id is unknown, which the caller renders as a blank slot. */
    fun sport(id: String): Int = sports[id] ?: 0

    fun discipline(id: String): Int = disciplines[id] ?: 0

    fun category(id: String): Int = categories[id] ?: 0

    fun field(id: String): Int = fields[id] ?: 0
}
