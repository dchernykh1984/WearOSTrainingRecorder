package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsParser
import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.sensor.SensorOrigin
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import com.dchernykh.trainingrecorder.core.sensor.SensorSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldValuesTest {
    private val start = 1_770_000_000_000L
    private val now = start + 600_000

    private fun recording() = RecordingState().prepare("cycling_road", start).begin(start)

    private fun sensors(vararg readings: Pair<String, Double>) =
        SensorSnapshot(
            readings =
                readings.associate { (id, value) ->
                    id to SensorReading(value, SensorOrigin.BUILT_IN, now)
                },
        )

    @Test
    fun everyFieldInTheCatalogueRenders() {
        // The screens read this map by id; a field the snapshot skips would show
        // as blank on the watch with nothing to explain why.
        val values = FieldValues.snapshot(recording(), now)
        assertEquals(FieldCatalogue.all.map { it.id }.toSet(), values.keys)
    }

    @Test
    fun aFieldWithNoReadingIsThePlaceholderRatherThanZero() {
        // Zero is a claim - that the power meter says 0 W - and a missing sensor
        // has not made it.
        val values = FieldValues.snapshot(recording(), now)
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["power"])
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["hr"])
    }

    @Test
    fun theTimersComeFromTheRecordingNotTheSensors() {
        val values = FieldValues.snapshot(recording(), now)
        assertEquals("10:00", values["timer_elapsed"])
        assertEquals("10:00", values["timer_moving"])
    }

    @Test
    fun pausedTimeIsWhatTheClockRanAndTheWorkoutDidNot() {
        val paused = recording().pause(start + 60_000)
        val values = FieldValues.snapshot(paused, now)
        assertEquals("1:00", values["timer_moving"])
        assertEquals("10:00", values["timer_elapsed"])
        assertEquals("9:00", values["timer_paused"])
    }

    @Test
    fun theClockFieldFollowsTheOffsetItIsGiven() {
        val utc = FieldValues.snapshot(recording(), now, clockOffsetMinutes = 0)["time_of_day"]
        val moscow = FieldValues.snapshot(recording(), now, clockOffsetMinutes = 180)["time_of_day"]
        // Three hours apart, which is the whole point of passing an offset in.
        assertEquals("2:50", utc)
        assertEquals("5:50", moscow)
    }

    @Test
    fun speedAndDistanceFollowTheChosenUnits() {
        val readings = sensors("speed_current" to 10.0, "distance_total" to 5000.0)
        val metric = FieldValues.snapshot(recording(), now, sensors = readings)
        val imperial = FieldValues.snapshot(recording(), now, sensors = readings, units = UnitSystem.IMPERIAL)
        assertEquals("36 km/h", metric["speed_current"])
        assertEquals("5 km", metric["distance_total"])
        assertEquals("22.4 mph", imperial["speed_current"])
        assertEquals("3.11 mi", imperial["distance_total"])
    }

    @Test
    fun eachPaceFieldInvertsItsOwnSpeedRatherThanTheCurrentOne() {
        // Sharing one source is the easy mistake, and it makes average pace
        // jump around with the current speed - which is exactly what a rider
        // looks at average pace to avoid.
        val readings = sensors("speed_current" to 5.0, "speed_avg" to 4.0, "speed_lap" to 2.5)
        val values = FieldValues.snapshot(recording(), now, sensors = readings)
        assertEquals("3:20/km", values["pace_current"])
        assertEquals("4:10/km", values["pace_avg"])
        assertEquals("6:40/km", values["pace_lap"])
    }

    @Test
    fun swimmingPaceStaysPerHundredMetresEvenInImperial() {
        val readings = sensors("speed_current" to 1.0)
        val values = FieldValues.snapshot(recording(), now, sensors = readings, units = UnitSystem.IMPERIAL)
        assertEquals("1:40/100m", values["pace_100"])
    }

    @Test
    fun ratiosBelowOneKeepTheirDecimals() {
        // Rounded to a whole number an intensity factor of 0.85 reads "1", which
        // is not a rounding error but a different training session.
        val values = FieldValues.snapshot(recording(), now, sensors = sensors("intensity_factor" to 0.85))
        assertEquals("0.85", values["intensity_factor"])
    }

    @Test
    fun percentagesReadAsPercentages() {
        val readings = sensors("hr_pct_max" to 87.4, "sensor_hr_battery" to 60.0)
        val values = FieldValues.snapshot(recording(), now, sensors = readings)
        assertEquals("87%", values["hr_pct_max"])
        assertEquals("60%", values["sensor_hr_battery"])
    }

    @Test
    fun gradeKeepsItsSign() {
        val values = FieldValues.snapshot(recording(), now, sensors = sensors("grade" to 6.5))
        assertEquals("+6.5%", values["grade"])
        val down = FieldValues.snapshot(recording(), now, sensors = sensors("grade" to -6.5))
        assertEquals("-6.5%", down["grade"])
    }

    @Test
    fun lapTimesArriveInSecondsAndAreShownAsDurations() {
        val values = FieldValues.snapshot(recording(), now, sensors = sensors("lap_time" to 305.0))
        assertEquals("5:05", values["lap_time"])
    }

    @Test
    fun raceStatsAreShownExactlyAsTheServerSentThem() {
        val snapshot =
            RaceStatsParser.parse(
                """{"stats": {"place_abs": "3", "qty_abs": "40", "gap_leader_abs": "+0:35.7"}}""",
            )
        val values = FieldValues.snapshot(recording(), now, race = snapshot)
        assertEquals("3/40", values["place_abs"])
        assertEquals("+0:35.7", values["gap_leader_abs"])
    }

    @Test
    fun aRaceFieldWithoutAPollYetIsEmptyRatherThanZero() {
        val values = FieldValues.snapshot(recording(), now)
        assertTrue(
            FieldCatalogue.all
                .filter { it.category == FieldCategory.RACE_STATS }
                .all { values[it.id] == FieldCatalogue.EMPTY_VALUE },
        )
    }

    @Test
    fun anIdleRecordingStillRendersWithoutThrowing() {
        // The screens can compose before the first start, and a crash there
        // would take the app down before the rider ever picked a sport.
        val values = FieldValues.snapshot(RecordingState(), now)
        assertEquals("0:00", values["timer_elapsed"])
        assertEquals(FieldCatalogue.all.size, values.size)
    }
}
