package com.dchernykh.trainingrecorder.core.track

import kotlin.test.Test
import kotlin.test.assertEquals

class CumulativeBaselineTest {
    @Test
    fun aCounterThatWasAlreadyRunningStartsThisRideAtNothing() {
        // What it was written for: an exercise session already under way hands
        // over its accumulated figures on the first update, so the ride opened
        // with nine hundred metres covered and then dropped back to nothing when
        // the app's own measurement took over.
        val baseline = CumulativeBaseline()
        assertEquals(0.0, baseline.sinceStart("distance_total", 935.4))
        assertEquals(64.6, baseline.sinceStart("distance_total", 1000.0), 0.001)
    }

    @Test
    fun aCounterThatBeganAtZeroIsLeftAlone() {
        val baseline = CumulativeBaseline()
        assertEquals(0.0, baseline.sinceStart("calories", 0.0))
        assertEquals(120.0, baseline.sinceStart("calories", 120.0))
    }

    @Test
    fun eachFieldIsCountedFromItsOwnStart() {
        val baseline = CumulativeBaseline()
        baseline.sinceStart("distance_total", 900.0)
        baseline.sinceStart("calories", 50.0)
        assertEquals(100.0, baseline.sinceStart("distance_total", 1000.0))
        assertEquals(10.0, baseline.sinceStart("calories", 60.0))
    }

    @Test
    fun aCounterThatGoesBackwardsStartsAgainRatherThanGoingNegative() {
        // Which is what a genuinely new session looks like from here. A negative
        // distance is not a number any field should ever show.
        val baseline = CumulativeBaseline()
        baseline.sinceStart("distance_total", 5000.0)
        assertEquals(0.0, baseline.sinceStart("distance_total", 10.0))
        assertEquals(90.0, baseline.sinceStart("distance_total", 100.0))
    }

    @Test
    fun clearingLeavesNothingOfTheLastRide() {
        val baseline = CumulativeBaseline()
        baseline.sinceStart("distance_total", 900.0)
        baseline.clear()
        assertEquals(0.0, baseline.sinceStart("distance_total", 2000.0))
    }
}
