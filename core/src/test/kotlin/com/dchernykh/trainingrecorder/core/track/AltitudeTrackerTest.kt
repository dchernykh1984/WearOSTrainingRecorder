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
    fun realClimbIsStillCounted() {
        val tracker = AltitudeTracker()
        tracker.record(800.0, null, start)
        (1..100).forEach { tracker.record(800.0 + it, null, start + it * 1000L) }
        assertTrue(abs(tracker.ascentMeters - 100) < 4, "expected about 100 m, got ${tracker.ascentMeters}")
        assertEquals(0.0, tracker.descentMeters)
        assertTrue(abs((tracker.altitudeMeters ?: 0.0) - 900) < 1, "the height follows the barometer")
    }

    @Test
    fun aRideThatOnlyGoesUpNeverShowsADescent() {
        val tracker = AltitudeTracker()
        (0..100).forEach { tracker.record(800.0 + it, null, start + it * 1000L) }
        assertTrue(tracker.ascentMeters > 90)
        assertEquals(0.0, tracker.descentMeters)
    }

    @Test
    fun goingUpAndComingBackDownIsCountedBothWays() {
        val tracker = AltitudeTracker()
        // Ridden rather than teleported: a hundred metres in one reading is
        // refused as a sensor artefact, and rightly.
        (0..100).forEach { tracker.record(800.0 + it, null, start + it * 1000L) }
        (0..100).forEach { tracker.record(900.0 - it, null, start + (101 + it) * 1000L) }
        assertTrue(abs(tracker.ascentMeters - 100) < 4, "got ${tracker.ascentMeters}")
        assertTrue(abs(tracker.descentMeters - 100) < 4, "got ${tracker.descentMeters}")
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
        (0..40).forEach { tracker.record(800.0 + it, null, start + it * 1000L) }
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

    @Test
    fun aBarometerIsUsedWhenTheFixCarriesNoAltitudeAtAll() {
        // The regression this replaced. Many receivers report no altitude with a
        // position, and requiring one before trusting the barometer left the
        // watch with no altitude, no climb rate and no gradient - while the
        // barometer sat there giving a perfectly good height above the sea,
        // which is what the platform's elevation already is.
        val tracker = AltitudeTracker()
        listOf(850.0, 855.0, 860.0).forEachIndexed { index, metres ->
            tracker.record(metres, gnssMeters = null, nowEpochMs = start + index * 10_000L)
        }
        assertEquals(860.0, tracker.altitudeMeters)
        assertTrue(tracker.measuring)
        assertEquals(10.0, tracker.ascentMeters)
    }

    @Test
    fun aSensorSettlingFromZeroIsNotAnEightHundredMetreClimb() {
        // What produced a total ascent equal to the height of the ground: the
        // series began at zero before the sensor had settled, and the step to
        // the real height was counted as a hill.
        val tracker = AltitudeTracker()
        tracker.record(0.0, null, start)
        tracker.record(855.0, null, start + 1000)
        assertEquals(855.0, tracker.altitudeMeters, "the height itself is the new one")
        assertEquals(0.0, tracker.ascentMeters, "nobody climbs eight hundred metres in a second")
        // And the ride carries on from where it really is.
        tracker.record(865.0, null, start + 11_000)
        assertEquals(10.0, tracker.ascentMeters)
    }

    @Test
    fun theFixIsUsedOnlyWhenThereIsNoBarometer() {
        val tracker = AltitudeTracker()
        tracker.record(barometricMeters = 850.0, gnssMeters = 900.0, nowEpochMs = start)
        assertEquals(850.0, tracker.altitudeMeters, "the barometer is the better source")
    }

    @Test
    fun aHillClimbedBetweenTwoDeliveriesIsStillAHill() {
        // Readings arrive when the platform delivers them, and with the screen
        // off that can be a minute apart. A cap measured in metres rather than
        // in metres per second would have thrown away the sixty metres of climb
        // that happened while nobody was looking at the watch.
        val tracker = AltitudeTracker()
        tracker.record(800.0, null, start)
        tracker.record(860.0, null, start + 60_000)
        assertEquals(60.0, tracker.ascentMeters, "a minute of climbing is a climb")
    }

    @Test
    fun aSensorSettlingIsStillRefusedHoweverLongTheGap() {
        // Eight hundred metres in a second is nobody; the guard is a rate, so it
        // still catches it.
        val tracker = AltitudeTracker()
        tracker.record(0.0, null, start)
        tracker.record(855.0, null, start + 1000)
        assertEquals(0.0, tracker.ascentMeters)
    }
}
