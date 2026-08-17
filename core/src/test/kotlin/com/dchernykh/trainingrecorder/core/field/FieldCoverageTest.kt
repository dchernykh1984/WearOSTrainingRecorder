package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.sensor.SensorOrigin
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import com.dchernykh.trainingrecorder.core.sensor.SensorSnapshot
import com.dchernykh.trainingrecorder.core.solar.SolarEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which fields the app can actually fill, held still.
 *
 * Two bugs of the same shape reached riders before this existed: average speed
 * and the power averages sat in the catalogue with nothing behind them, so a
 * field placed on a screen read empty for a whole ride and said nothing about
 * why. A field nobody feeds is indistinguishable from a sensor that is not
 * connected.
 *
 * So every field is accounted for here. Either the app produces it, or it is on
 * the list below with the reason - and adding a field to the catalogue without
 * doing one or the other fails this test.
 */
class FieldCoverageTest {
    /**
     * What the watch writes into a snapshot: Health Services, the Bluetooth
     * sensors, and everything the app works out for itself.
     *
     * Kept in step with `RecordingViewModel.derived`, `ExerciseRecorder.toSample`
     * and `SensorConnection.readingsFrom` by hand, because those live in the
     * Android modules where this cannot see them. That is exactly why the list
     * below is worth writing down.
     */
    private val produced =
        setOf(
            // Health Services
            "hr",
            "speed_current",
            "altitude",
            "cadence",
            "distance_total",
            "calories",
            // Bluetooth sensors
            "power",
            "power_balance",
            // worked out by the app
            "ascent_total",
            "descent_total",
            "grade",
            "vertical_speed",
            "hr_avg",
            "hr_max",
            "cadence_avg",
            "cadence_max",
            "power_avg",
            "power_max",
            "power_3s",
            "power_10s",
            "power_30s",
            "power_normalized",
            "speed_avg",
            "speed_max",
        )

    /**
     * Fields the app cannot fill yet, each with what it is waiting for.
     *
     * This is a list of work, not an excuse: every line is a field a rider can
     * put on a screen today and watch stay empty.
     */
    private val notYet =
        mapOf(
            "lap_number" to "laps are not implemented",
            "lap_count" to "laps are not implemented",
            "lap_time" to "laps are not implemented",
            "lap_time_last" to "laps are not implemented",
            "distance_lap" to "laps are not implemented",
            "distance_lap_last" to "laps are not implemented",
            "hr_lap" to "laps are not implemented",
            "cadence_lap" to "laps are not implemented",
            "power_lap" to "laps are not implemented",
            "speed_lap" to "laps are not implemented",
            "pace_lap" to "laps are not implemented",
            "hr_pct_max" to "needs the rider's maximum heart rate, which nothing asks for",
            "hr_pct_hrr" to "needs maximum and resting heart rate, which nothing asks for",
            "hr_zone" to "needs heart rate zones, which nothing asks for",
            "power_per_kg" to "needs the rider's weight, which nothing asks for",
            "intensity_factor" to "needs an FTP, which nothing asks for",
            "training_stress_score" to "needs an FTP, which nothing asks for",
            "pedal_smoothness" to "an optional field of the power characteristic that is not parsed",
            "torque_effectiveness" to "an optional field of the power characteristic that is not parsed",
            "sensor_hr_battery" to "needs the Bluetooth battery service, which is not read",
            "sensor_cadence_battery" to "needs the Bluetooth battery service, which is not read",
            "sensor_power_battery" to "needs the Bluetooth battery service, which is not read",
            "temperature" to "needs the environmental sensing service, which is not read",
            "stroke_count" to "needs the swimming data types, which are not requested",
            "stroke_rate" to "needs the swimming data types, which are not requested",
            "swolf" to "needs the swimming data types, which are not requested",
            "lengths" to "needs the swimming data types, which are not requested",
        )

    /** Everything a full snapshot can hand to the formatter. */
    private fun snapshot(): Map<String, String> {
        val readings =
            produced.associateWith { SensorReading(120.0, SensorOrigin.BUILT_IN, NOW) } +
                mapOf("speed_current" to SensorReading(8.0, SensorOrigin.BUILT_IN, NOW))
        return FieldValues.snapshot(
            state = RecordingState().prepare("cycling_road", NOW - 60_000).begin(NOW - 60_000),
            nowEpochMs = NOW,
            sensors = SensorSnapshot(readings),
            race = RaceStatsSnapshot(stats = RACE_FIELDS.associateWith { "1" }),
            surroundings = Surroundings(solar = SolarEvents(NOW - 3_600_000, NOW + 3_600_000)),
        )
    }

    @Test
    fun everyFieldIsEitherProducedOrOnTheListOfWorkOutstanding() {
        val accounted = produced + notYet.keys + DERIVED_FROM_PRODUCED + TIMERS + RACE_FIELDS
        val unaccounted = FieldCatalogue.all.map { it.id }.filterNot { it in accounted }
        assertTrue(
            unaccounted.isEmpty(),
            "these fields have no source and no entry saying why: $unaccounted",
        )
    }

    @Test
    fun everythingClaimedAsProducedActuallyRendersAValue() {
        val values = snapshot()
        val empty = (produced + DERIVED_FROM_PRODUCED + TIMERS).filter { values[it] == FieldCatalogue.EMPTY_VALUE }
        assertTrue(empty.isEmpty(), "claimed to be produced but rendered empty: $empty")
    }

    @Test
    fun theListOfWorkOutstandingNamesOnlyRealFields() {
        // A field that is renamed or removed must not leave a line here quietly
        // excusing something that no longer exists.
        val ids = FieldCatalogue.all.map { it.id }.toSet()
        val stale = notYet.keys.filterNot { it in ids }
        assertTrue(stale.isEmpty(), "no such fields any more: $stale")
    }

    @Test
    fun aFieldWithNothingBehindItReadsAsEmptyRatherThanAsZero() {
        // The distinction the whole screen depends on: nothing is not zero. A
        // rider seeing 0 W believes the meter; seeing a dash they look for it.
        val values =
            FieldValues.snapshot(state = RecordingState(), nowEpochMs = NOW, sensors = SensorSnapshot())
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["power"])
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["hr"])
    }

    private companion object {
        const val NOW = 1_782_000_000_000L

        /** Computed by the formatter out of fields that are produced. */
        val DERIVED_FROM_PRODUCED = setOf("pace_current", "pace_avg", "pace_100")

        val TIMERS = setOf("timer_elapsed", "timer_moving", "timer_paused", "time_of_day", "sunrise", "sunset")

        val RACE_FIELDS = FieldCatalogue.raceStats.map { it.id }.toSet()
    }
}
