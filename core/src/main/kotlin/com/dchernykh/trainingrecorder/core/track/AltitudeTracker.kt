package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs

/**
 * The altitude to show and the climb to record.
 *
 * One source, not two reconciled. The platform's own elevation is already a
 * height above sea level - it calibrates the barometer itself - so the offset
 * machinery that used to sit here was solving a problem that had been solved
 * upstream, and it broke the moment a watch produced no altitude with its
 * position: with nothing to calibrate against, there was no altitude at all,
 * which is a worse answer than the perfectly good one the barometer was already
 * giving. A fix's altitude is the fallback and nothing more; it is the weakest
 * thing GNSS produces.
 *
 * The climb is measured from that same series, past a threshold, from the last
 * accepted reading rather than the last one seen - so a flat road totals nothing
 * while a long steady drag is not thrown away a metre at a time.
 *
 * One step is refused outright: a change faster than anyone climbs. Total ascent
 * once came back equal to the height of the ground the ride started on, because
 * the series began at zero before the sensor had settled and the jump to eight
 * hundred metres was counted as a climb.
 *
 * Judged as a rate rather than a distance, which matters more than it looks:
 * readings arrive whenever the platform delivers them, and with the screen off
 * that can be a minute apart. Eight hundred metres in a second is a sensor
 * settling; sixty metres in a minute is a hill, and a fixed cap in metres would
 * have thrown the hill away.
 *
 * A per-step rate is still not enough on its own, and this cost a rider a whole
 * ride's ascent. A receiver with no vertical solution reports zero and then
 * *converges* to the true height over the first minutes. Every individual step
 * of that convergence looks like riding; only the sustained rate gives it away.
 * Eight hundred and fifty metres over ten minutes is one and a half metres a
 * second held for ten minutes - five thousand metres an hour, where the best
 * climber alive manages under two thousand.
 *
 * So nothing is counted until the source has settled, which is judged over a
 * window: a sustained *climb* faster than any human is a sensor converging, not
 * a hill. The test is deliberately one-sided. Ascent has a physiological
 * ceiling and descent does not - eight hundred metres down in ten minutes is an
 * ordinary road descent, and a symmetric rule would have thrown it away.
 *
 * The cost is the first half-minute of climb on a ride that starts up a wall:
 * under ten metres, against a whole ride's ascent invented.
 */
class AltitudeTracker(
    private val barometricThresholdMeters: Double = BAROMETRIC_THRESHOLD_METERS,
    private val satelliteThresholdMeters: Double = SATELLITE_THRESHOLD_METERS,
    private val impossibleRateMps: Double = IMPOSSIBLE_RATE_MPS,
    private val settleWindowMs: Long = SETTLE_WINDOW_MS,
    private val sustainedClimbMps: Double = SUSTAINED_CLIMB_MPS,
) {
    private var reference: Double? = null
    private var referenceAtEpochMs = 0L

    /** Recent readings, kept only long enough to judge whether the source has settled. */
    private val recent = ArrayDeque<Reading>()

    private data class Reading(
        val meters: Double,
        val atEpochMs: Long,
    )

    /**
     * Chosen on the first reading, by whichever source gave it.
     *
     * A barometer resolves about a metre; a satellite fix wanders by ten and
     * more while the rider stands still. A total only ever adds the upward half
     * of that wander, and it cannot correct itself afterwards - so the
     * barometer's threshold applied to a fix invents climb by the hundred over
     * an hour. A watch has a barometer or it does not, so this is decided once.
     */
    private var thresholdMeters: Double = BAROMETRIC_THRESHOLD_METERS

    /** The height above the sea to show, or null before anything is known. */
    var altitudeMeters: Double? = null
        private set

    /** Climbed since this ride began, and nothing before it. */
    var ascentMeters: Double = 0.0
        private set

    var descentMeters: Double = 0.0
        private set

    /** True once some source has given a height, which is when the totals mean anything. */
    val measuring: Boolean get() = altitudeMeters != null

    /**
     * Whether the height is worth writing down.
     *
     * False while the source is still converging - and that matters beyond the
     * screen. Our own total can refuse a convergence, but the *series* we record
     * cannot: a service reading the file computes its own ascent from the
     * altitudes in it, so a ramp from zero left in the track hands the phantom
     * climb straight back however carefully we refused it ourselves.
     */
    var trustworthy: Boolean = false
        private set

    @Suppress("ReturnCount")
    fun record(
        barometricMeters: Double?,
        gnssMeters: Double?,
        nowEpochMs: Long,
    ) {
        val measured = barometricMeters ?: gnssMeters ?: return
        altitudeMeters = measured
        recent.addLast(Reading(measured, nowEpochMs))
        while (recent.size > 1 && nowEpochMs - recent.first().atEpochMs > settleWindowMs) {
            recent.removeFirst()
        }
        // Nothing is counted while the source is climbing faster than anybody
        // rides. The anchor follows, so counting resumes from where the ride
        // actually is.
        //
        // Asked of every reading rather than latched once at the start: a
        // receiver that loses its solution in a tunnel and converges again in
        // the middle of a ride does exactly what it did at the beginning, and a
        // rule that had already decided the source was settled would count the
        // whole of it as a hill.
        trustworthy = hasSettled(nowEpochMs)
        if (!trustworthy) {
            reference = measured
            referenceAtEpochMs = nowEpochMs
            return
        }
        val from = reference
        if (from == null) {
            thresholdMeters =
                if (barometricMeters != null) barometricThresholdMeters else satelliteThresholdMeters
            reference = measured
            referenceAtEpochMs = nowEpochMs
            return
        }
        val change = measured - from
        val seconds = (nowEpochMs - referenceAtEpochMs) / MILLIS_PER_SECOND
        // Two readings sharing an instant give no rate to judge by, so they fall
        // back to a plain cap. Refusing everything there would discard a real
        // change; allowing everything would let a settling sensor through.
        val allowed =
            if (seconds > 0) maxOf(thresholdMeters, impossibleRateMps * seconds) else SETTLING_CAP_METERS
        if (abs(change) > allowed) {
            // Not a hill: the sensor settling, or a datum moving under us. The
            // reference follows so the next reading is measured from where the
            // ride actually is.
            reference = measured
            referenceAtEpochMs = nowEpochMs
            return
        }
        if (abs(change) < thresholdMeters) return
        if (change > 0) ascentMeters += change else descentMeters -= change
        reference = measured
        referenceAtEpochMs = nowEpochMs
    }

    /**
     * True once the source has stopped converging.
     *
     * Judged over a window rather than between two readings, and only upwards:
     * a climb held faster than anybody can ride is a receiver settling. A
     * descent that fast is a road.
     */
    @Suppress("ReturnCount")
    private fun hasSettled(nowEpochMs: Long): Boolean {
        val first = recent.first()
        val seconds = (nowEpochMs - first.atEpochMs) / MILLIS_PER_SECOND
        if (seconds < settleWindowMs / MILLIS_PER_SECOND) return false
        val rise = recent.last().meters - first.meters
        return rise / seconds <= sustainedClimbMps
    }

    /** Forgets the ride. The next one starts from nothing, as it must. */
    fun clear() {
        recent.clear()
        trustworthy = false
        reference = null
        referenceAtEpochMs = 0
        altitudeMeters = null
        ascentMeters = 0.0
        descentMeters = 0.0
    }

    private companion object {
        /** Below this is a barometer breathing, not the road going up. */
        const val BAROMETRIC_THRESHOLD_METERS = 3.0

        /** A satellite fix wanders further than that while standing still. */
        const val SATELLITE_THRESHOLD_METERS = 12.0

        /**
         * Faster than this nobody climbs: two metres a second is seven thousand
         * metres an hour, several times what the best climber in the world
         * manages. Anything past it is the sensor settling or a datum moving.
         */
        const val IMPOSSIBLE_RATE_MPS = 2.0

        /** What a reading may change by when there is no interval to judge it over. */
        const val SETTLING_CAP_METERS = 100.0

        /**
         * Long enough for a convergence to show itself, short enough that the
         * climb it costs at the start of a ride is under ten metres.
         */
        const val SETTLE_WINDOW_MS = 30_000L

        /**
         * A metre a second held for half a minute is three and a half thousand
         * metres an hour. The best climber alive does under two thousand, so
         * anything past this is the altimeter finding itself rather than a road.
         */
        const val SUSTAINED_CLIMB_MPS = 1.0

        const val MILLIS_PER_SECOND = 1000.0
    }
}
