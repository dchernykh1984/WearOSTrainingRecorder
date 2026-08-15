package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AltitudeFusionTest {
    private val start = 1_782_000_000_000L
    private val anHour = 3_600_000L

    @Test
    fun aWatchWithNoBarometerUsesTheSkyAsItAlwaysDid() {
        val fusion = AltitudeFusion()
        assertEquals(812.0, fusion.altitude(barometricMeters = null, gnssMeters = 812.0, nowEpochMs = start))
        assertNull(fusion.altitude(barometricMeters = null, gnssMeters = null, nowEpochMs = start))
    }

    @Test
    fun theBarometerIsBroughtToTheHeightTheSkySaysAtTheStart() {
        // A barometer knows how the ground changes, not where it began. The
        // first fix is what tells it.
        val fusion = AltitudeFusion()
        assertEquals(800.0, fusion.altitude(barometricMeters = 120.0, gnssMeters = 800.0, nowEpochMs = start))
    }

    @Test
    fun theShapeOfTheClimbComesFromTheBarometerNotTheSatellites() {
        // The point of the whole thing: GNSS altitude wanders by fifteen metres
        // while the rider is standing still, and the barometer does not.
        val fusion = AltitudeFusion()
        fusion.altitude(barometricMeters = 120.0, gnssMeters = 800.0, nowEpochMs = start)
        val climbed = fusion.altitude(barometricMeters = 140.0, gnssMeters = 785.0, nowEpochMs = start + 60_000)
        assertEquals(820.0, climbed, "twenty metres of barometer is twenty metres of climb")
    }

    @Test
    fun theOffsetIsRefreshedAsTheWeatherMoves() {
        // Pressure drifts over hours, so the datum is taken again. Not more
        // often: chasing it every minute would hand the satellites back the
        // wander this exists to keep out.
        val fusion = AltitudeFusion()
        fusion.altitude(barometricMeters = 100.0, gnssMeters = 800.0, nowEpochMs = start)
        val soon = fusion.altitude(barometricMeters = 100.0, gnssMeters = 830.0, nowEpochMs = start + 60_000)
        assertEquals(800.0, soon, "a fresh fix must not move the datum straight away")
        val later = fusion.altitude(barometricMeters = 100.0, gnssMeters = 830.0, nowEpochMs = start + anHour)
        assertEquals(830.0, later, "an hour on, the datum is taken again")
    }

    @Test
    fun theBarometerIsUsedEvenBeforeThereIsAFixToCalibrateAgainst() {
        // Indoors, or in the first minute of a ride. The absolute number is
        // whatever the sensor thought, but the climb is still right - and the
        // first fix corrects the datum without disturbing it.
        val fusion = AltitudeFusion()
        assertEquals(120.0, fusion.altitude(barometricMeters = 120.0, gnssMeters = null, nowEpochMs = start))
        assertEquals(140.0, fusion.altitude(barometricMeters = 140.0, gnssMeters = null, nowEpochMs = start + 1000))
        val corrected = fusion.altitude(barometricMeters = 140.0, gnssMeters = 800.0, nowEpochMs = start + 2000)
        assertEquals(800.0, corrected)
    }

    @Test
    fun clearingForgetsTheDatumWithTheRideItBelongedTo() {
        val fusion = AltitudeFusion()
        fusion.altitude(barometricMeters = 100.0, gnssMeters = 800.0, nowEpochMs = start)
        fusion.clear()
        assertEquals(50.0, fusion.altitude(barometricMeters = 100.0, gnssMeters = 50.0, nowEpochMs = start + 1000))
    }
}

class ClimbTotalTest {
    @Test
    fun aFlatRoadDoesNotClimb() {
        // The figure a noisy altimeter ruins first: every wobble upwards counts
        // and none of the ones downwards cancel it.
        val climb = ClimbTotal()
        listOf(800.0, 801.0, 799.5, 800.5, 799.0, 800.0).forEach(climb::record)
        assertEquals(0.0, climb.ascentMeters)
        assertEquals(0.0, climb.descentMeters)
    }

    @Test
    fun aSteadyClimbIsNotThrownAwayOneSmallStepAtATime() {
        // Measured from the last accepted altitude rather than the last reading,
        // so a metre at a time still adds up to the hill it was.
        val climb = ClimbTotal()
        (0..100).forEach { climb.record(800.0 + it) }
        assertTrue(abs(climb.ascentMeters - 100) < 4, "expected about 100 m, got ${climb.ascentMeters}")
    }

    @Test
    fun goingUpAndComingDownAreCountedApart() {
        val climb = ClimbTotal()
        listOf(800.0, 850.0, 900.0, 850.0, 800.0).forEach(climb::record)
        assertEquals(100.0, climb.ascentMeters)
        assertEquals(100.0, climb.descentMeters)
    }

    @Test
    fun clearingLeavesNothingOfTheLastRide() {
        val climb = ClimbTotal()
        listOf(800.0, 900.0).forEach(climb::record)
        climb.clear()
        climb.record(500.0)
        climb.record(510.0)
        assertEquals(10.0, climb.ascentMeters)
    }
}
