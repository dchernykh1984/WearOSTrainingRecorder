package com.dchernykh.trainingrecorder.core.track

import kotlin.math.max

/**
 * The averages and maxima a ride screen offers, kept as the ride goes.
 *
 * These fields were in the catalogue from the start and had nothing behind
 * them: a rider could place "Avg Speed" or "Max HR" on a screen and watch it
 * read empty for the whole ride, because nothing in the app ever computed one.
 * They are cheap to keep - a sum and a count per field - and every input needed
 * is already passing through once a second.
 *
 * Averaged over the samples that carried a value rather than over the clock, so
 * a strap that drops out for a minute lowers the count rather than the average.
 * Zero is excluded from the averages the rider reads as effort - a stopped
 * bicycle reports zero cadence and zero power, and counting those turns the
 * average power of a ride with three descents into a number the rider has never
 * seen on the screen. Speed is the exception and is averaged over moving time
 * instead, which is what every head unit means by average speed.
 */
class RideAggregates {
    private val sums = mutableMapOf<String, Double>()
    private val counts = mutableMapOf<String, Int>()
    private val maxima = mutableMapOf<String, Double>()

    /**
     * Folds one sample in. Only the fields that have an average or a maximum are
     * kept; anything else is passed over.
     */
    fun record(readings: Map<String, Double>) {
        AVERAGED.forEach { field ->
            val value = readings[field] ?: return@forEach
            // A resting sensor is not a low reading, it is no reading. Averaging
            // its zeros in is how a ride's average power ends up below anything
            // the rider saw.
            if (value <= 0) return@forEach
            sums[field] = (sums[field] ?: 0.0) + value
            counts[field] = (counts[field] ?: 0) + 1
        }
        MAXIMA.forEach { field ->
            val value = readings[field] ?: return@forEach
            maxima[field] = max(maxima[field] ?: 0.0, value)
        }
    }

    /** Null until at least one sample carried the field. */
    fun average(field: String): Double? {
        val count = counts[field] ?: return null
        if (count == 0) return null
        return sums.getValue(field) / count
    }

    fun maximum(field: String): Double? = maxima[field]

    /**
     * Everything worked out so far, under the ids the screens ask for.
     *
     * [distanceMeters] and [movingSeconds] come from the ride rather than from
     * any sensor, because average speed is distance over moving time - which is
     * what a head unit means by it, and not the mean of the speed readings.
     */
    fun snapshot(
        distanceMeters: Double,
        movingSeconds: Double,
        maxSpeedMps: Double,
    ): Map<String, Double> =
        buildMap {
            average("hr")?.let { put("hr_avg", it) }
            maximum("hr")?.let { put("hr_max", it) }
            average("cadence")?.let { put("cadence_avg", it) }
            maximum("cadence")?.let { put("cadence_max", it) }
            average("power")?.let { put("power_avg", it) }
            maximum("power")?.let { put("power_max", it) }
            if (movingSeconds > 0) put("speed_avg", distanceMeters / movingSeconds)
            if (maxSpeedMps > 0) put("speed_max", maxSpeedMps)
        }

    fun clear() {
        sums.clear()
        counts.clear()
        maxima.clear()
    }

    private companion object {
        /** The fields with an average worth reading, by the id they arrive under. */
        val AVERAGED = setOf("hr", "cadence", "power")

        /**
         * Maxima include the zeros: the highest reading is the highest reading,
         * and nothing about a resting sensor can raise it.
         */
        val MAXIMA = setOf("hr", "cadence", "power")
    }
}
