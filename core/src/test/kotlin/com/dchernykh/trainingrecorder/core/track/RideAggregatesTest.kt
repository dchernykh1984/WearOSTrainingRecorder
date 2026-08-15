package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideAggregatesTest {
    @Test
    fun anAverageNeedsSomethingToAverage() {
        // What the rider saw before this existed: the field on the screen for
        // the whole ride, permanently empty, because nothing computed it.
        assertNull(RideAggregates().average("hr"))
        assertNull(RideAggregates().maximum("hr"))
    }

    @Test
    fun heartRateAveragesAndPeaks() {
        val aggregates = RideAggregates()
        listOf(100.0, 120.0, 140.0).forEach { aggregates.record(mapOf("hr" to it)) }
        assertEquals(120.0, aggregates.average("hr"))
        assertEquals(140.0, aggregates.maximum("hr"))
    }

    @Test
    fun aRestingSensorIsNotALowReading() {
        // A stopped bicycle reports zero cadence and zero power. Averaging those
        // in produces an average the rider has never seen on the screen.
        val aggregates = RideAggregates()
        listOf(0.0, 200.0, 0.0, 100.0, 0.0).forEach { aggregates.record(mapOf("power" to it)) }
        assertEquals(150.0, aggregates.average("power"), "the zeros are coasting, not effort")
        assertEquals(200.0, aggregates.maximum("power"), "the peak is unaffected either way")
    }

    @Test
    fun aSampleWithoutTheFieldLowersNothing() {
        // A strap that drops out for a minute must not drag the average down;
        // it simply contributes nothing while it is away.
        val aggregates = RideAggregates()
        aggregates.record(mapOf("hr" to 150.0))
        repeat(60) { aggregates.record(mapOf("cadence" to 90.0)) }
        aggregates.record(mapOf("hr" to 150.0))
        assertEquals(150.0, aggregates.average("hr"))
    }

    @Test
    fun averageSpeedIsDistanceOverMovingTimeAndNotTheMeanOfTheReadings() {
        // Which is what every head unit means by it, and the reason the two
        // cannot be the same number: the mean of the readings counts every
        // second at a traffic light.
        val snapshot = RideAggregates().snapshot(distanceMeters = 10_000.0, movingSeconds = 1000.0, maxSpeedMps = 0.0)
        assertEquals(10.0, snapshot["speed_avg"])
    }

    @Test
    fun aRideThatHasNotMovedOffersNoAverageSpeed() {
        val snapshot = RideAggregates().snapshot(distanceMeters = 0.0, movingSeconds = 0.0, maxSpeedMps = 0.0)
        assertNull(snapshot["speed_avg"])
        assertNull(snapshot["speed_max"])
    }

    @Test
    fun theSnapshotUsesTheIdsTheScreensAskFor() {
        // The names are the contract between this and the catalogue: a typo here
        // is a field that stays empty and says nothing about why.
        val aggregates = RideAggregates()
        aggregates.record(mapOf("hr" to 150.0, "cadence" to 90.0, "power" to 200.0))
        val snapshot = aggregates.snapshot(distanceMeters = 1000.0, movingSeconds = 100.0, maxSpeedMps = 15.0)
        listOf("hr_avg", "hr_max", "cadence_avg", "cadence_max", "power_avg", "power_max", "speed_avg", "speed_max")
            .forEach { assertTrue(snapshot.containsKey(it), "$it is missing from the snapshot") }
    }

    @Test
    fun clearingLeavesNothingOfTheLastRide() {
        val aggregates = RideAggregates()
        aggregates.record(mapOf("hr" to 190.0))
        aggregates.clear()
        aggregates.record(mapOf("hr" to 100.0))
        assertTrue(abs(assertNotNullValue(aggregates.average("hr")) - 100.0) < 0.001)
        assertEquals(100.0, aggregates.maximum("hr"))
    }

    private fun assertNotNullValue(value: Double?): Double = value ?: error("expected a value")
}
