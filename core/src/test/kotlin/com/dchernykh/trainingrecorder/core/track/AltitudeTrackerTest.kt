package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AltitudeTrackerTest {
    private val start = 1_782_000_000_000L
    private val anHour = 3_600_000L

    @Test
    fun theFirstFixMovesTheDisplayedHeightWithoutClimbingAnything() {
        // The bug this was written for. A ride began with the barometer on its
        // own raw reading; the first satellite fix moved the datum to the height
        // of the ground; and the step between them was recorded as a climb the
        // rider never made. Total ascent came out equal to the altitude above
        // the sea.
        val tracker = AltitudeTracker()
        tracker.record(barometricMeters = 120.0, gnssMeters = null, nowEpochMs = start)
        tracker.record(barometricMeters = 120.0, gnssMeters = 855.0, nowEpochMs = start + 20_000)
        assertEquals(855.0, tracker.altitudeMeters, "the height shown is above the sea")
        assertEquals(0.0, tracker.ascentMeters, "moving the datum is not climbing")
        assertEquals(0.0, tracker.descentMeters)
    }

    @Test
    fun theHourlyRecalibrationIsNotAHill() {
        // The same mistake, quieter: it repeated every hour for the length of
        // the ride.
        val tracker = AltitudeTracker()
        tracker.record(120.0, 800.0, start)
        tracker.record(120.0, 830.0, start + anHour)
        tracker.record(120.0, 830.0, start + anHour + 1000)
        assertEquals(0.0, tracker.ascentMeters, "the weather moved, the rider did not")
    }

    @Test
    fun realClimbIsStillCounted() {
        val tracker = AltitudeTracker()
        tracker.record(120.0, 800.0, start)
        (1..100).forEach { tracker.record(120.0 + it, 800.0, start + it * 1000L) }
        assertTrue(abs(tracker.ascentMeters - 100) < 4, "expected about 100 m, got ${tracker.ascentMeters}")
        assertEquals(0.0, tracker.descentMeters)
        assertTrue(abs((tracker.altitudeMeters ?: 0.0) - 900) < 1, "the height follows the barometer")
    }

    @Test
    fun aRideThatOnlyGoesUpNeverShowsADescent() {
        val tracker = AltitudeTracker()
        listOf(800.0, 820.0, 850.0, 900.0).forEach { tracker.record(it, null, start) }
        assertTrue(tracker.ascentMeters > 90)
        assertEquals(0.0, tracker.descentMeters)
    }

    @Test
    fun goingUpAndComingBackDownIsCountedBothWays() {
        val tracker = AltitudeTracker()
        listOf(800.0, 900.0, 800.0).forEach { tracker.record(it, null, start) }
        assertEquals(100.0, tracker.ascentMeters)
        assertEquals(100.0, tracker.descentMeters)
    }

    @Test
    fun aWatchWithNoBarometerClimbsByItsFixes() {
        val tracker = AltitudeTracker()
        listOf(
            800.0,
            850.0,
            900.0,
        ).forEach { tracker.record(barometricMeters = null, gnssMeters = it, nowEpochMs = start) }
        assertEquals(900.0, tracker.altitudeMeters)
        assertEquals(100.0, tracker.ascentMeters)
    }

    @Test
    fun nothingIsKnownBeforeAnySourceHasSpoken() {
        val tracker = AltitudeTracker()
        assertFalse(tracker.measuring)
        tracker.record(barometricMeters = null, gnssMeters = null, nowEpochMs = start)
        assertNull(tracker.altitudeMeters)
        assertFalse(tracker.measuring, "with no altitude at all there is no total to show")
        assertEquals(0.0, tracker.ascentMeters)
    }

    @Test
    fun everyRideStartsFromNothing() {
        // The totals are the ride's, not the day's. Anything left over from the
        // last one would be climb the rider is credited with twice.
        val tracker = AltitudeTracker()
        listOf(800.0, 900.0).forEach { tracker.record(it, null, start) }
        assertTrue(tracker.ascentMeters > 0)
        tracker.clear()
        assertEquals(0.0, tracker.ascentMeters)
        assertEquals(0.0, tracker.descentMeters)
        assertNull(tracker.altitudeMeters)
        assertFalse(tracker.measuring)
        // And the first reading of the next ride is a starting point, not a climb
        // from wherever the last one ended.
        tracker.record(300.0, null, start + anHour)
        assertEquals(0.0, tracker.ascentMeters)
    }

    @Test
    fun aFlatRoadTotalsNothingHoweverLongItIs() {
        val tracker = AltitudeTracker()
        (0..3600).forEach {
            val wobble = if (it % 2 == 0) 0.7 else -0.7
            tracker.record(800.0 + wobble, null, start + it * 1000L)
        }
        assertEquals(0.0, tracker.ascentMeters, "an hour of barometer noise is not a climb")
        assertEquals(0.0, tracker.descentMeters)
    }

    @Test
    fun aWatchWithNoBarometerIsNotCreditedWithTheWanderOfItsFixes() {
        // A satellite fix moves by ten metres and more while standing still, and
        // a total only ever adds the upward half of that. Given a barometer's
        // threshold it invents climb by the hundred over an hour.
        val tracker = AltitudeTracker()
        (0..3600).forEach {
            val wobble = if (it % 2 == 0) 5.0 else -5.0
            tracker.record(barometricMeters = null, gnssMeters = 800.0 + wobble, nowEpochMs = start + it * 1000L)
        }
        assertEquals(0.0, tracker.ascentMeters, "an hour of satellite wander is not a climb")
    }

    @Test
    fun aRealClimbIsStillCountedWithoutABarometer() {
        val tracker = AltitudeTracker()
        (0..100).forEach { tracker.record(null, 800.0 + it * 2.0, start + it * 1000L) }
        assertTrue(abs(tracker.ascentMeters - 200) < 15, "expected about 200 m, got ${tracker.ascentMeters}")
    }

    @Test
    fun aDescentThatCrossesTheHourlyRecalibrationIsStillJustTheDescent() {
        // The case most likely to be wrong and hardest to notice: the rider is
        // coming down a pass when the datum is refreshed. If the correction
        // leaked into the totals it would land in the descent, where a rider
        // already expects a large number and would not question it.
        val tracker = AltitudeTracker()
        tracker.record(barometricMeters = 1500.0, gnssMeters = 1500.0, nowEpochMs = start)
        // Up 300 m over the first hour.
        (1..300).forEach { tracker.record(1500.0 + it, 1500.0 + it, start + it * 1000L) }
        // The hour turns over and the weather has moved the pressure by 15 m.
        tracker.record(1800.0, 1815.0, start + anHour)
        // Then 500 m of descent.
        (1..500).forEach { tracker.record(1800.0 - it, null, start + anHour + it * 1000L) }

        assertTrue(abs(tracker.ascentMeters - 300) < 5, "expected 300 m up, got ${tracker.ascentMeters}")
        assertTrue(abs(tracker.descentMeters - 500) < 5, "expected 500 m down, got ${tracker.descentMeters}")
    }

    @Test
    fun aDescentOnlyRideCountsTheWholeDescentAndNoClimb() {
        val tracker = AltitudeTracker()
        (0..400).forEach { tracker.record(2000.0 - it, 2000.0, start + it * 1000L) }
        assertEquals(0.0, tracker.ascentMeters)
        assertTrue(abs(tracker.descentMeters - 400) < 5, "expected 400 m down, got ${tracker.descentMeters}")
    }

    @Test
    fun bothTotalsAreReadableFromTheFirstMomentThereIsAnAltitude() {
        // A dash where a zero belongs reads as "not working", and the rider has
        // no way to tell the two apart until they have climbed something.
        val tracker = AltitudeTracker()
        tracker.record(800.0, 800.0, start)
        assertTrue(tracker.measuring)
        assertEquals(0.0, tracker.ascentMeters)
        assertEquals(0.0, tracker.descentMeters)
    }
}
