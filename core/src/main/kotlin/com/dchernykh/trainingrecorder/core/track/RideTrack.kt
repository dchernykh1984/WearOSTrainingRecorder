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
 * Movement below a few metres between fixes is ignored. A watch left on a table
 * still produces fixes, each a metre or two from the last, and over an hour that
 * wander adds up to a ride the rider never took. The threshold is what a
 * consumer receiver's noise looks like, not a real slow crawl - a rider pushing
 * a bike still covers more than this between fixes.
 */
class RideTrack(
    private val jitterMeters: Double = JITTER_METERS,
    private val speedCeilingMps: Double = MAX_PLAUSIBLE_SPEED_MPS,
) {
    private var previous: Fix? = null

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
     * Four ways it does not, and each of them leaves the track in a different
     * place, which is what the branching is about: the first fix has nothing to
     * measure from, a fix out of order has no interval, an impossible one is
     * refused but still becomes the baseline - keeping the old one would measure
     * the next step from a position the rider left long ago, turning one bad fix
     * into a wrong distance for the rest of the ride - and a step inside the
     * noise means standing still, which is a real answer and the one a stopped
     * rider should see rather than a speed that never quite reaches zero.
     */
    @Suppress("ReturnCount")
    fun record(fix: Fix): Boolean {
        val last = previous
        if (last == null) {
            previous = fix
            return false
        }
        val seconds = (fix.atEpochMs - last.atEpochMs) / MILLIS_PER_SECOND
        if (seconds <= 0) return false
        val metres = Haversine.metresBetween(last, fix)
        val speed = metres / seconds
        previous = fix
        if (speed > speedCeilingMps) return false
        if (metres < jitterMeters) {
            speedMps = 0.0
            return false
        }
        distanceMeters += metres
        speedMps = speed
        maxSpeedMps = max(maxSpeedMps, speed)
        return true
    }

    /** Forgets the ride, keeping the settings it was built with. */
    fun clear() {
        previous = null
        distanceMeters = 0.0
        speedMps = null
        maxSpeedMps = 0.0
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0

        /**
         * Under this between two fixes is receiver noise rather than travel. A
         * rider pushing a bike covers more than this in a second.
         */
        const val JITTER_METERS = 3.0

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
