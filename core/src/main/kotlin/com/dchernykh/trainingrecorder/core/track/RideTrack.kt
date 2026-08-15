package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One position fix, as the track consumes it. */
data class Fix(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val atEpochMs: Long,
)

/** Great-circle distance, which over a ride is indistinguishable from the truth. */
object Haversine {
    /** Metres between two positions. */
    fun metresBetween(
        from: Fix,
        to: Fix,
    ): Double {
        val lat1 = radians(from.latitudeDeg)
        val lat2 = radians(to.latitudeDeg)
        val halfDeltaLat = radians(to.latitudeDeg - from.latitudeDeg) / 2
        val halfDeltaLon = radians(to.longitudeDeg - from.longitudeDeg) / 2
        val a =
            sin(halfDeltaLat) * sin(halfDeltaLat) +
                cos(lat1) * cos(lat2) * sin(halfDeltaLon) * sin(halfDeltaLon)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    private fun radians(degrees: Double): Double = degrees * Math.PI / 180.0

    /** The mean radius, which is what every distance formula of this kind uses. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8
}

/**
 * How far the ride has gone, and how fast, worked out from the positions it
 * recorded.
 *
 * This exists because the platform's own distance cannot be relied on. A ride
 * that plainly happened - an hour of it, with a heart rate trace and a green
 * satellite indicator - came back from Health Services reporting zero metres
 * from beginning to end, and a zero it reports confidently is indistinguishable
 * from a rider who did not move. Positions are the one thing the app can see for
 * itself, and a distance computed from them is by construction the same distance
 * the saved track draws.
 *
 * Two filters, both of which matter more than they look:
 *
 * A fix that implies an impossible speed is dropped. GNSS occasionally emits a
 * position a kilometre away and comes straight back, and unfiltered that is two
 * kilometres added to a ride that never moved.
 *
 * Movement below a few metres is ignored - but measured from an *anchor* that is
 * held until the threshold is crossed, not from the previous fix. Measured
 * pairwise it was worse than useless: fixes arrive about once a second, a walker
 * covers a metre and a half in that time, so every single step fell under the
 * threshold and was thrown away. A two hundred metre walk recorded three metres,
 * which is the one number worse than none - it looks like the feature works.
 *
 * Holding the anchor keeps both properties. A rider moving steadily gets further
 * from it every second and crosses the threshold within a few, and every metre
 * they covered is counted from where they last were. A watch on a table wanders
 * around its anchor without ever getting far from it, because the noise is
 * bounded and the anchor does not follow it.
 */
class RideTrack(
    private val jitterMeters: Double = JITTER_METERS,
    private val speedCeilingMps: Double = MAX_PLAUSIBLE_SPEED_MPS,
    private val stationaryAfterSeconds: Double = STATIONARY_AFTER_SECONDS,
    private val crawlingSpeedMps: Double = CRAWLING_SPEED_MPS,
) {
    /** Where the last counted metre ended, and what the next fix is measured from. */
    private var anchor: Fix? = null

    var distanceMeters: Double = 0.0
        private set

    /** Metres per second over the last accepted pair, or null before there is one. */
    var speedMps: Double? = null
        private set

    var maxSpeedMps: Double = 0.0
        private set

    /**
     * True when the fix moved the ride on.
     *
     * Four ways it does not. The first fix has nothing to measure from. A fix
     * out of order has no interval. An impossible one is refused *and* replaces
     * the anchor, because keeping the old one would measure everything after it
     * from a position the rider left long ago, turning a single bad fix into a
     * wrong distance for the rest of the ride. And a fix still inside the noise
     * leaves the anchor exactly where it is, so the next one is measured from
     * the same place and the rider's slow progress accumulates instead of being
     * discarded a metre at a time.
     */
    @Suppress("ReturnCount")
    fun record(fix: Fix): Boolean {
        val from = anchor
        if (from == null) {
            anchor = fix
            return false
        }
        val seconds = (fix.atEpochMs - from.atEpochMs) / MILLIS_PER_SECOND
        if (seconds <= 0) return false
        val metres = Haversine.metresBetween(from, fix)
        if (metres / seconds > speedCeilingMps) {
            anchor = fix
            return false
        }
        // Over the whole held interval, which is the average across the stretch
        // actually measured rather than of one arbitrary pair - and the reason
        // the two tests below can tell a walker from a drifting receiver at all.
        val speed = metres / seconds
        if (metres < jitterMeters || speed < crawlingSpeedMps) {
            // The anchor is held, not advanced, so the rider's slow progress
            // accumulates towards the threshold instead of being discarded a
            // metre at a time. That is what makes the speed floor safe: a
            // stationary receiver drifts a few metres and stops, so as the
            // anchor is held its apparent speed falls towards zero, while a
            // walker's stays at walking pace however long the anchor is held.
            //
            // Someone who has not got clear of the anchor in several seconds is
            // standing still, and should be shown that rather than the speed of
            // whatever they last did. The anchor moves to them at the same
            // moment, which matters more than it looks: held across a five
            // minute stop at a cafe, the speed floor is measured over those five
            // minutes, and the rider would have to cover a hundred metres before
            // a single one of them counted. Once they are known to be standing,
            // the next stretch is measured from where they stood.
            if (seconds >= stationaryAfterSeconds) {
                speedMps = 0.0
                anchor = fix
            }
            return false
        }
        anchor = fix
        distanceMeters += metres
        speedMps = speed
        maxSpeedMps = max(maxSpeedMps, speed)
        return true
    }

    /** Forgets the ride, keeping the settings it was built with. */
    fun clear() {
        anchor = null
        distanceMeters = 0.0
        speedMps = null
        maxSpeedMps = 0.0
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0

        /**
         * Displacement from the anchor under this is receiver noise rather than
         * travel. A walker crosses it in about two seconds and a rider in less
         * than one; a watch on a table never does, because its wander is bounded
         * and the anchor does not follow it.
         */
        const val JITTER_METERS = 3.0

        /**
         * Failing to get clear of the anchor for this long is standing still.
         * Under half a metre a second, which is slower than a walk.
         */
        const val STATIONARY_AFTER_SECONDS = 6.0

        /**
         * Slower than this, averaged over the whole held interval, is drift
         * rather than travel. A receiver left alone wanders a few metres and
         * stays there, so the longer the anchor is held the slower it appears;
         * a walker is four times this and stays there.
         */
        const val CRAWLING_SPEED_MPS = 0.35

        /**
         * Faster than this between two fixes did not happen on a bicycle: 250
         * km/h leaves room for a descent, a train, and a bad fix that is merely
         * bad rather than absurd.
         */
        const val MAX_PLAUSIBLE_SPEED_MPS = 70.0
    }
}

/**
 * Altitude from the two sources a watch may have, preferring the one that
 * measures rather than infers.
 *
 * A barometer resolves a metre of climb; a satellite fix is lucky to be within
 * fifteen, and its error wanders. Over a ride that is the difference between a
 * climb total worth reading and one that invents a hundred metres on a flat
 * road.
 *
 * What a barometer cannot do is know how high it started, and it drifts as the
 * weather moves - which is what the calibration is for. The offset between the
 * barometric reading and the satellite's is taken at the start and refreshed
 * occasionally, so the shape of the climb comes from the barometer and its
 * datum from the sky.
 *
 * A watch with no barometer falls back to the satellite reading alone, which is
 * what it always was.
 */
class AltitudeFusion(
    private val recalibrateAfterMs: Long = RECALIBRATE_AFTER_MS,
) {
    private var offsetMeters: Double? = null
    private var calibratedAtEpochMs = 0L

    /**
     * The altitude to record, or null when neither source has anything to say.
     *
     * [barometricMeters] is the watch's own pressure-derived altitude and
     * [gnssMeters] the one that came with the fix; either may be absent.
     */
    fun altitude(
        barometricMeters: Double?,
        gnssMeters: Double?,
        nowEpochMs: Long,
    ): Double? {
        if (barometricMeters == null) return gnssMeters
        val due = offsetMeters == null || nowEpochMs - calibratedAtEpochMs >= recalibrateAfterMs
        // Only ever against a fix. Without one there is nothing to calibrate to,
        // and an uncalibrated barometer is still the better *shape* - so it is
        // used with whatever offset it already had, including none.
        if (due && gnssMeters != null) {
            offsetMeters = gnssMeters - barometricMeters
            calibratedAtEpochMs = nowEpochMs
        }
        return barometricMeters + (offsetMeters ?: 0.0)
    }

    fun clear() {
        offsetMeters = null
        calibratedAtEpochMs = 0
    }

    private companion object {
        /**
         * Weather moves a barometer by a few metres over an hour, which is worth
         * correcting and nowhere near worth chasing more often than that.
         */
        const val RECALIBRATE_AFTER_MS = 60 * 60 * 1000L
    }
}

/**
 * How much of a ride was upwards, from a series of altitudes.
 *
 * Climb is the one figure that punishes a noisy altimeter hardest: every wobble
 * upwards is counted and none of the ones downwards cancel it, so an unfiltered
 * total on a flat road can reach hundreds of metres. Only a change past the
 * threshold is taken, and it is taken from the last accepted altitude rather
 * than the last reading - so a long steady climb is not thrown away one
 * sub-threshold step at a time.
 */
class ClimbTotal(
    private val thresholdMeters: Double = THRESHOLD_METERS,
) {
    private var reference: Double? = null

    var ascentMeters: Double = 0.0
        private set

    var descentMeters: Double = 0.0
        private set

    fun record(altitudeMeters: Double) {
        val from = reference
        if (from == null) {
            reference = altitudeMeters
            return
        }
        val change = altitudeMeters - from
        if (abs(change) < thresholdMeters) return
        if (change > 0) ascentMeters += change else descentMeters -= change
        reference = altitudeMeters
    }

    fun clear() {
        reference = null
        ascentMeters = 0.0
        descentMeters = 0.0
    }

    private companion object {
        /** Below this is the altimeter breathing, not the road going up. */
        const val THRESHOLD_METERS = 3.0
    }
}
