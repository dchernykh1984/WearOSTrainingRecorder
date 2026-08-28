package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AltitudeTrackerTest {
    private val start = 1_782_000_000_000L
    private val anHour = 3_600_000L

    @Test
    fun realClimbIsStillCounted() {
        val tracker = AltitudeTracker()
        // A quarter of a metre a second is nine hundred metres an hour: hard,
        // sustained, and human. A metre a second - which this test used to
        // assume - is nobody.
        (0..800).forEach { tracker.record(800.0 + it * 0.25, null, start + it * 1000L) }
        assertTrue(abs(tracker.ascentMeters - 200) < 12, "expected about 200 m, got ${tracker.ascentMeters}")
        assertEquals(0.0, tracker.descentMeters)
        assertTrue(abs((tracker.altitudeMeters ?: 0.0) - 1000) < 1, "the height follows the barometer")
    }

    @Test
    fun aRideThatOnlyGoesUpNeverShowsADescent() {
        val tracker = AltitudeTracker()
        (0..800).forEach { tracker.record(800.0 + it * 0.25, null, start + it * 1000L) }
        assertTrue(tracker.ascentMeters > 180)
        assertEquals(0.0, tracker.descentMeters)
    }

    @Test
    fun goingUpAndComingBackDownIsCountedBothWays() {
        val tracker = AltitudeTracker()
        // Ridden rather than teleported: a hundred metres in one reading is
        // refused as a sensor artefact, and rightly.
        (0..400).forEach { tracker.record(800.0 + it * 0.25, null, start + it * 1000L) }
        (0..400).forEach { tracker.record(900.0 - it * 0.25, null, start + (401 + it) * 1000L) }
        assertTrue(abs(tracker.ascentMeters - 100) < 15, "got ${tracker.ascentMeters}")
        assertTrue(abs(tracker.descentMeters - 100) < 15, "got ${tracker.descentMeters}")
    }

    @Test
    fun aWatchWithNoBarometerClimbsByItsFixes() {
        val tracker = AltitudeTracker()
        // Every reading at the same instant gives no rate to judge anything by,
        // and a hundred metres of climb inside one instant is not a ride. Spread
        // over an hour it is.
        (0..120).forEach {
            tracker.record(barometricMeters = null, gnssMeters = 800.0 + it * 0.85, nowEpochMs = start + it * 30_000L)
        }
        assertTrue(abs((tracker.altitudeMeters ?: 0.0) - 902) < 1)
        assertTrue(tracker.ascentMeters > 80, "got ${tracker.ascentMeters}")
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
        (0..400).forEach { tracker.record(800.0 + it * 0.25, null, start + it * 1000L) }
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
        // Ten seconds between fixes, which is what a receiver actually delivers,
        // climbing at a human two hundred and fifty metres an hour.
        (0..300).forEach { tracker.record(null, 800.0 + it * 0.7, start + it * 10_000L) }
        assertTrue(abs(tracker.ascentMeters - 210) < 25, "expected about 210 m, got ${tracker.ascentMeters}")
    }

    @Test
    fun aDescentOnlyRideCountsTheWholeDescentAndNoClimb() {
        val tracker = AltitudeTracker()
        (0..400).forEach { tracker.record(2000.0 - it, 2000.0, start + it * 1000L) }
        assertEquals(0.0, tracker.ascentMeters)
        // The first half-minute goes to settling, as it does on every ride.
        assertTrue(abs(tracker.descentMeters - 370) < 12, "expected about 370 m down, got ${tracker.descentMeters}")
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
        (0..20).forEach { tracker.record(850.0 + it * 0.5, gnssMeters = null, nowEpochMs = start + it * 10_000L) }
        assertEquals(860.0, tracker.altitudeMeters)
        assertTrue(tracker.measuring)
        assertTrue(tracker.ascentMeters > 5, "the barometer's climb is counted, got ${tracker.ascentMeters}")
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
        // And the ride carries on from where it really is, once the source has
        // had its half-minute to settle.
        (2..200).forEach { tracker.record(855.0 + (it - 2) * 0.25, null, start + it * 1000L) }
        assertTrue(tracker.ascentMeters > 30, "the real climb after it is counted, got ${tracker.ascentMeters}")
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
        // Half a minute apart, which is what the screen being off looks like.
        (0..10).forEach { tracker.record(800.0 + it * 6.0, null, start + it * 30_000L) }
        assertTrue(tracker.ascentMeters > 40, "climbing between deliveries is climbing, got ${tracker.ascentMeters}")
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

    @Test
    fun aReceiverConvergingFromZeroIsNotAnEightHundredMetreClimb() {
        // The ride that cost a whole ascent. A receiver with no vertical
        // solution reports zero and converges to the true height over the first
        // minutes. Every individual step looks like riding - only the sustained
        // rate gives it away, and the old per-step guard passed it straight
        // through: eight hundred and forty-two metres of hill that was never
        // ridden, on a ride whose only climbing was at the start.
        val tracker = AltitudeTracker()
        val overSeconds = 600
        (0..overSeconds).forEach { second ->
            tracker.record(
                barometricMeters = null,
                gnssMeters = 850.0 * (second.toDouble() / overSeconds),
                nowEpochMs = start + second * 1000L,
            )
        }
        assertEquals(0.0, tracker.ascentMeters, "a settling altimeter is not a hill")
        assertEquals(850.0, tracker.altitudeMeters, "the height itself is still shown")
    }

    @Test
    fun aRealClimbIsCountedOnceTheSourceHasSettled() {
        // Eight hundred metres an hour is a hard sustained climb and must
        // survive the settling rule intact.
        val tracker = AltitudeTracker()
        (0..1200).forEach { second ->
            tracker.record(850.0 + second * 0.22, null, start + second * 1000L)
        }
        // The first half-minute is given up to settling; the rest is counted.
        assertTrue(tracker.ascentMeters > 250, "expected most of 264 m, got ${tracker.ascentMeters}")
        assertTrue(tracker.ascentMeters <= 264, "cannot exceed what was climbed, got ${tracker.ascentMeters}")
    }

    @Test
    fun aFastDescentIsNotMistakenForASettlingSensor() {
        // The rule is one-sided on purpose. Eight hundred metres down in ten
        // minutes is an ordinary road descent, and a symmetric test would have
        // thrown it away.
        val tracker = AltitudeTracker()
        (0..600).forEach { second ->
            tracker.record(1700.0 - second * 1.4, null, start + second * 1000L)
        }
        assertTrue(tracker.descentMeters > 750, "expected about 840 m down, got ${tracker.descentMeters}")
        assertEquals(0.0, tracker.ascentMeters)
    }

    @Test
    fun theClimbGivenUpToSettlingIsSmall() {
        // What the rule costs: a ride that starts straight up a wall loses the
        // first half-minute of it. Under ten metres, against a whole ride's
        // ascent invented.
        val tracker = AltitudeTracker()
        (0..600).forEach { second ->
            tracker.record(850.0 + second * 0.28, null, start + second * 1000L)
        }
        val climbed = 600 * 0.28
        assertTrue(climbed - tracker.ascentMeters < 12, "gave up ${climbed - tracker.ascentMeters} m")
    }

    @Test
    fun aReceiverThatConvergesAgainMidRideCostsAWindowRatherThanTheWholeClimb() {
        // A solution lost in a tunnel and regained does exactly what it did at
        // the start of the ride. A rule that decided once that the source had
        // settled would count the whole of the second convergence as a hill.
        val tracker = AltitudeTracker()
        // Twenty minutes of honest riding first.
        (0..1200).forEach { tracker.record(850.0 + it * 0.05, null, start + it * 1000L) }
        val honest = tracker.ascentMeters
        assertTrue(honest > 40, "the real climb is counted, got $honest")
        // Then the receiver drops out and converges from zero again.
        (0..600).forEach {
            tracker.record(850.0 * (it.toDouble() / 600), null, start + (1200 + it) * 1000L)
        }
        // A window is what it takes to notice: while the window still straddles
        // the drop-out its net movement is downwards, and the convergence is
        // counted until it clears. That bounds the damage at half a minute of
        // it - forty-odd metres instead of the whole eight hundred and fifty.
        val invented = tracker.ascentMeters - honest
        assertTrue(invented < 50, "the second convergence added $invented m")
    }

    @Test
    fun aConvergingHeightIsNotWorthWritingDown() {
        // Refusing to count a convergence is only half of it. The recorded track
        // carries altitudes of its own, and a service computes its own ascent
        // from them - so a ramp from zero left in the file hands back the very
        // climb we refused.
        val tracker = AltitudeTracker()
        (0..120).forEach { second ->
            tracker.record(null, 850.0 * (second / 600.0), start + second * 1000L)
        }
        assertFalse(tracker.trustworthy, "a converging height must stay out of the track")
        // Once it settles, the height is written down again.
        (121..300).forEach { second ->
            tracker.record(null, 170.0 + (second - 120) * 0.2, start + second * 1000L)
        }
        assertTrue(tracker.trustworthy)
        assertNotNull(tracker.altitudeMeters)
    }

    @Test
    fun aSteadyHeightBecomesWorthWritingDownOnARealWatch() {
        // Every other test here spaces its readings exactly a thousand
        // milliseconds apart, and that regularity was hiding a bug: the window
        // was trimmed to readings *within* half a minute and then asked whether
        // it spanned half a minute, which only a reading landing on the
        // millisecond could ever satisfy. On a watch, where Health Services
        // delivers when it delivers, the answer was always no - and the altitude
        // field read empty for the whole ride.
        val tracker = AltitudeTracker()
        var at = start
        (0..90).forEach { second ->
            // A second, give or take, which is what arrives.
            at += 990 + (second * 7) % 40
            tracker.record(barometricMeters = 214.0 + (second % 3) * 0.4, gnssMeters = null, nowEpochMs = at)
        }

        assertTrue(tracker.trustworthy, "a barometer sitting still for a minute and a half has settled")
        assertNotNull(tracker.altitudeMeters, "and its height is what the rider should see")
    }

    @Test
    fun aRealClimbIsCountedWhenTheReadingsArriveLikeARealWatchs() {
        // The same bug, and the half of it that does not show on the screen:
        // nothing is counted until the source has settled, so a window that
        // could never close meant total ascent stayed at zero for the whole
        // ride. Every test that would have caught it spaced its readings
        // exactly a second apart.
        val tracker = AltitudeTracker()
        var at = start
        var height = 300.0
        repeat(600) { second ->
            at += 990 + (second * 7) % 40
            // Twenty metres a minute, which is an ordinary climb.
            height += 20.0 / 60.0
            tracker.record(barometricMeters = height, gnssMeters = null, nowEpochMs = at)
        }

        // Two hundred metres of climbing, less the half minute spent settling.
        assertTrue(
            tracker.ascentMeters > 180.0,
            "a ten minute climb of 200 m came back as ${tracker.ascentMeters} m",
        )
        assertTrue(tracker.descentMeters < 1.0, "and nothing went down")
    }
}
