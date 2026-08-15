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
            "laps" to R.string.category_laps,
            "sensors" to R.string.category_sensors,
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
            "lap_time" to R.string.field_lap_time,
            "lap_time_last" to R.string.field_lap_time_last,
            "distance_total" to R.string.field_distance_total,
            "distance_lap" to R.string.field_distance_lap,
            "distance_lap_last" to R.string.field_distance_lap_last,
            "speed_current" to R.string.field_speed_current,
            "speed_avg" to R.string.field_speed_avg,
            "speed_max" to R.string.field_speed_max,
            "speed_lap" to R.string.field_speed_lap,
            "pace_current" to R.string.field_pace_current,
            "pace_avg" to R.string.field_pace_avg,
            "pace_lap" to R.string.field_pace_lap,
            "hr" to R.string.field_hr,
            "hr_avg" to R.string.field_hr_avg,
            "hr_max" to R.string.field_hr_max,
            "hr_lap" to R.string.field_hr_lap,
            "hr_zone" to R.string.field_hr_zone,
            "hr_pct_max" to R.string.field_hr_pct_max,
            "hr_pct_hrr" to R.string.field_hr_pct_hrr,
            "cadence" to R.string.field_cadence,
            "cadence_avg" to R.string.field_cadence_avg,
            "cadence_max" to R.string.field_cadence_max,
            "cadence_lap" to R.string.field_cadence_lap,
            "power" to R.string.field_power,
            "power_3s" to R.string.field_power_3s,
            "power_10s" to R.string.field_power_10s,
            "power_30s" to R.string.field_power_30s,
            "power_avg" to R.string.field_power_avg,
            "power_max" to R.string.field_power_max,
            "power_lap" to R.string.field_power_lap,
            "power_normalized" to R.string.field_power_normalized,
            "power_per_kg" to R.string.field_power_per_kg,
            "power_balance" to R.string.field_power_balance,
            "torque_effectiveness" to R.string.field_torque_effectiveness,
            "pedal_smoothness" to R.string.field_pedal_smoothness,
            "intensity_factor" to R.string.field_intensity_factor,
            "training_stress_score" to R.string.field_training_stress_score,
            "altitude" to R.string.field_altitude,
            "ascent_total" to R.string.field_ascent_total,
            "descent_total" to R.string.field_descent_total,
            "grade" to R.string.field_grade,
            "vertical_speed" to R.string.field_vertical_speed,
            "calories" to R.string.field_calories,
            "swolf" to R.string.field_swolf,
            "stroke_rate" to R.string.field_stroke_rate,
            "stroke_count" to R.string.field_stroke_count,
            "lengths" to R.string.field_lengths,
            "pace_100" to R.string.field_pace_100,
            "lap_number" to R.string.field_lap_number,
            "lap_count" to R.string.field_lap_count,
            "sensor_hr_battery" to R.string.field_sensor_hr_battery,
            "sensor_cadence_battery" to R.string.field_sensor_cadence_battery,
            "sensor_power_battery" to R.string.field_sensor_power_battery,
            "temperature" to R.string.field_temperature,
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
        )

    /** Zero when the id is unknown, which the caller renders as a blank slot. */
    fun sport(id: String): Int = sports[id] ?: 0

    fun discipline(id: String): Int = disciplines[id] ?: 0

    fun category(id: String): Int = categories[id] ?: 0

    fun field(id: String): Int = fields[id] ?: 0
}
