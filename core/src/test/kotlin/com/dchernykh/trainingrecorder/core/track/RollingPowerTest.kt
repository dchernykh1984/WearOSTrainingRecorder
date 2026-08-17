package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RollingPowerTest {
    private val start = 1_782_000_000_000L

    private fun ride(
        power: RollingPower,
        watts: List<Double>,
    ) = watts.forEachIndexed { second, w -> power.record(w, start + second * 1000L) }

    @Test
    fun thereIsNothingToAverageBeforeThePedalsTurn() {
        assertNull(RollingPower().average(RollingPower.THREE_SECONDS_MS, start))
        assertNull(RollingPower().normalised())
    }

    @Test
    fun steadyPedallingAveragesToWhatWasPedalled() {
        val power = RollingPower()
        ride(power, List(60) { 250.0 })
        assertEquals(250.0, assertNotNull(power.average(RollingPower.THREE_SECONDS_MS, start + 59_000)))
        assertEquals(250.0, assertNotNull(power.average(RollingPower.THIRTY_SECONDS_MS, start + 59_000)))
    }

    @Test
    fun theThreeSecondAverageFollowsASurgeBeforeTheThirtySecondOneDoes() {
        // Which is the whole reason a rider puts both on the screen.
        val power = RollingPower()
        ride(power, List(30) { 200.0 } + List(3) { 500.0 })
        val now = start + 32_000
        val short = assertNotNull(power.average(RollingPower.THREE_SECONDS_MS, now))
        val long = assertNotNull(power.average(RollingPower.THIRTY_SECONDS_MS, now))
        assertTrue(short > 400, "the three second average should have caught the surge, was $short")
        assertTrue(long < 260, "the thirty second average should barely have moved, was $long")
    }

    @Test
    fun theWindowIsTimeAndNotACountOfSamples() {
        // A meter sends when it likes. Counting samples would let the window
        // stretch and shrink with the sensor's mood rather than the clock's.
        val power = RollingPower()
        power.record(400.0, start)
        power.record(400.0, start + 100)
        power.record(400.0, start + 200)
        power.record(100.0, start + 10_000)
        val recent = assertNotNull(power.average(RollingPower.THREE_SECONDS_MS, start + 10_000))
        assertEquals(100.0, recent, "only the last sample is inside three seconds")
    }

    @Test
    fun normalisedPowerPunishesIntervalsAsItIsMeantTo() {
        // Same average, ridden two ways. Minutes at four hundred alternating
        // with minutes soft-pedalling must cost more than sitting at two
        // hundred, which is the only reason the figure exists.
        val steady = RollingPower()
        ride(steady, List(1200) { 200.0 })
        val intervals = RollingPower()
        ride(intervals, (0 until 1200).map { if ((it / 60) % 2 == 0) 400.0 else 0.0 })
        val steadyNp = assertNotNull(steady.normalised())
        val intervalNp = assertNotNull(intervals.normalised())
        assertTrue(abs(steadyNp - 200) < 5, "a steady ride normalises to what it was, got $steadyNp")
        assertTrue(intervalNp > steadyNp * 1.3, "intervals must cost more, got $intervalNp against $steadyNp")
    }

    @Test
    fun secondToSecondWobbleIsSmoothedRatherThanPunished() {
        // The thirty second average is inside the definition on purpose: the
        // swing between the top and bottom of a pedal stroke is not an interval,
        // and normalised power must not read it as one.
        val wobbling = RollingPower()
        ride(wobbling, (0 until 600).map { if (it % 2 == 0) 100.0 else 300.0 })
        assertTrue(
            abs(assertNotNull(wobbling.normalised()) - 200) < 10,
            "got ${wobbling.normalised()}",
        )
    }

    @Test
    fun clearingLeavesNothingOfTheLastRide() {
        val power = RollingPower()
        ride(power, List(60) { 300.0 })
        power.clear()
        assertNull(power.average(RollingPower.THREE_SECONDS_MS, start + 60_000))
        assertNull(power.normalised())
    }
}

class GradientTest {
    private val start = 1_782_000_000_000L

    @Test
    fun thereIsNoGradientBeforeTheRideHasCoveredGround() {
        // Standing at a light, altitude noise over no distance is a gradient of
        // infinity, and showing it would be worse than showing nothing.
        val gradient = Gradient()
        gradient.record(altitudeMeters = 800.0, distanceMeters = 0.0, nowEpochMs = start)
        gradient.record(altitudeMeters = 801.0, distanceMeters = 1.0, nowEpochMs = start + 2000)
        assertNull(gradient.percent())
    }

    @Test
    fun aClimbReadsAsTheGradientItIs() {
        // Fifty metres up over a kilometre is five per cent.
        val gradient = Gradient()
        (0..10).forEach {
            gradient.record(
                altitudeMeters = 800.0 + it * 5.0,
                distanceMeters = it * 100.0,
                nowEpochMs = start + it * 1000L,
            )
        }
        assertTrue(abs(assertNotNull(gradient.percent()) - 5.0) < 0.2, "got ${gradient.percent()}")
    }

    @Test
    fun aDescentIsNegative() {
        val gradient = Gradient()
        (0..10).forEach {
            gradient.record(800.0 - it * 8.0, it * 100.0, start + it * 1000L)
        }
        assertTrue(assertNotNull(gradient.percent()) < -7, "got ${gradient.percent()}")
    }

    @Test
    fun verticalSpeedIsHeightPerHour() {
        // Ten metres in ten seconds is 3600 metres an hour, which is a lift
        // rather than a bicycle - but the arithmetic is the arithmetic.
        val gradient = Gradient()
        gradient.record(800.0, 0.0, start)
        gradient.record(810.0, 100.0, start + 10_000)
        assertTrue(abs(assertNotNull(gradient.verticalSpeedMetersPerHour()) - 3600) < 10)
    }

    @Test
    fun thereIsNoVerticalSpeedBeforeTheWindowHasFilled() {
        val gradient = Gradient()
        gradient.record(800.0, 0.0, start)
        gradient.record(802.0, 20.0, start + 1000)
        assertNull(gradient.verticalSpeedMetersPerHour())
    }

    @Test
    fun clearingLeavesNothingOfTheLastRide() {
        val gradient = Gradient()
        (0..10).forEach { gradient.record(800.0 + it * 5.0, it * 100.0, start + it * 1000L) }
        gradient.clear()
        assertNull(gradient.percent())
    }
}
