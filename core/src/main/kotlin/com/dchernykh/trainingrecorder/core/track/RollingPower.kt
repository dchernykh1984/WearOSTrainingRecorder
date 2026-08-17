package com.dchernykh.trainingrecorder.core.track

import kotlin.math.pow

/**
 * The smoothed power figures a rider actually pedals to.
 *
 * Instantaneous power is unreadable on a bike: it swings by a hundred watts
 * between the top and bottom of a pedal stroke, and a number that changes ten
 * times a second cannot be held to. Every head unit therefore shows an average
 * over the last few seconds, and riders talk in those terms - "I sat at 250 for
 * three minutes" means the three-second average.
 *
 * These fields were in the catalogue from the start with nothing behind them, so
 * a rider could put "Power 3s" on a screen and watch it read empty for the whole
 * ride.
 *
 * Averaged over a window of *time*, not of samples. Power arrives whenever the
 * meter sends it, which is about once a second but not exactly, and counting
 * samples would make the window stretch and shrink with the sensor's mood.
 */
class RollingPower {
    private val samples = ArrayDeque<Sample>()

    /** For normalised power, which needs the thirty-second average over the ride. */
    private var rollingFourthPowerSum = 0.0
    private var rollingCount = 0

    private data class Sample(
        val watts: Double,
        val atEpochMs: Long,
    )

    fun record(
        watts: Double,
        nowEpochMs: Long,
    ) {
        samples.addLast(Sample(watts, nowEpochMs))
        // The longest window is the only one that has to be kept; the shorter
        // ones are read out of the same tail.
        while (samples.size > 1 && nowEpochMs - samples.first().atEpochMs > LONGEST_WINDOW_MS) {
            samples.removeFirst()
        }
        average(THIRTY_SECONDS_MS, nowEpochMs)?.let {
            rollingFourthPowerSum += it.pow(NORMALISED_EXPONENT)
            rollingCount++
        }
    }

    /** Mean watts over the last [windowMs], or null when nothing is in it. */
    fun average(
        windowMs: Long,
        nowEpochMs: Long,
    ): Double? {
        val inWindow = samples.filter { nowEpochMs - it.atEpochMs <= windowMs }
        if (inWindow.isEmpty()) return null
        return inWindow.sumOf { it.watts } / inWindow.size
    }

    /**
     * Normalised power: the fourth root of the mean of the fourth power of the
     * thirty-second rolling average.
     *
     * The exponent is not decoration - it is what makes a ride of surges cost
     * more than the same average ridden steadily, which is the whole reason the
     * figure exists. Null until there is a full window to average, because a
     * normalised power computed over four seconds is a number that means
     * nothing and looks like it means something.
     */
    fun normalised(): Double? {
        if (rollingCount == 0) return null
        return (rollingFourthPowerSum / rollingCount).pow(1.0 / NORMALISED_EXPONENT)
    }

    fun clear() {
        samples.clear()
        rollingFourthPowerSum = 0.0
        rollingCount = 0
    }

    companion object {
        const val THREE_SECONDS_MS = 3_000L
        const val TEN_SECONDS_MS = 10_000L
        const val THIRTY_SECONDS_MS = 30_000L

        private const val LONGEST_WINDOW_MS = THIRTY_SECONDS_MS
        private const val NORMALISED_EXPONENT = 4.0
    }
}

/**
 * How steep the road is, and how fast the ride is gaining height.
 *
 * Both are differences over a distance or a time, and both are ruined by taking
 * them over too short an interval: a metre of altitude noise over five metres of
 * road is a twenty per cent gradient that nobody rode. They are measured over a
 * window long enough for the road to have changed more than the sensors have.
 */
class Gradient(
    private val windowMs: Long = WINDOW_MS,
    private val minimumRunMeters: Double = MINIMUM_RUN_METERS,
) {
    private val samples = ArrayDeque<Sample>()

    private data class Sample(
        val altitudeMeters: Double,
        val distanceMeters: Double,
        val atEpochMs: Long,
    )

    fun record(
        altitudeMeters: Double,
        distanceMeters: Double,
        nowEpochMs: Long,
    ) {
        samples.addLast(Sample(altitudeMeters, distanceMeters, nowEpochMs))
        while (samples.size > 1 && nowEpochMs - samples.first().atEpochMs > windowMs) {
            samples.removeFirst()
        }
    }

    /**
     * Percent, rising positive. Null until the ride has covered enough ground
     * for the answer to mean anything - on a bike stopped at a light, altitude
     * noise over no distance at all is a gradient of infinity.
     */
    fun percent(): Double? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val run = last.distanceMeters - first.distanceMeters
        if (run < minimumRunMeters) return null
        return (last.altitudeMeters - first.altitudeMeters) / run * PERCENT
    }

    /** Metres of height per hour, rising positive. Null before the window fills. */
    fun verticalSpeedMetersPerHour(): Double? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val seconds = (last.atEpochMs - first.atEpochMs) / MILLIS_PER_SECOND
        if (seconds < MINIMUM_SECONDS) return null
        return (last.altitudeMeters - first.altitudeMeters) / seconds * SECONDS_PER_HOUR
    }

    fun clear() = samples.clear()

    private companion object {
        /** Long enough for a road to change and short enough to feel live. */
        const val WINDOW_MS = 20_000L

        /** Under this the run is too short to divide by honestly. */
        const val MINIMUM_RUN_METERS = 20.0

        const val MINIMUM_SECONDS = 5.0
        const val MILLIS_PER_SECOND = 1000.0
        const val SECONDS_PER_HOUR = 3600.0
        const val PERCENT = 100.0
    }
}
