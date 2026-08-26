package com.dchernykh.trainingrecorder.core.segment

import com.dchernykh.trainingrecorder.core.track.Fix
import com.dchernykh.trainingrecorder.core.track.Haversine
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Where the ride stands with respect to a segment. */
data class SegmentState(
    val segment: Segment,
    /** Metres to the start, and zero once the rider is on it. */
    val toStartMeters: Double? = null,
    /** True while the rider is on the segment. */
    val riding: Boolean = false,
    /** True for the moments after the finish, while the result is being shown. */
    val finished: Boolean = false,
    val coveredMeters: Double = 0.0,
    val elapsedSeconds: Double = 0.0,
) {
    /** True whenever there is an effort to report on, running or just done. */
    val timing: Boolean get() = riding || finished

    val remainingMeters: Double get() = (segment.distanceMeters - coveredMeters).coerceAtLeast(0.0)

    /**
     * Metres climbed and dropped so far, and still to come.
     *
     * All four are read off the segment's own profile rather than the watch's
     * altimeter, which is what makes them add up: what the rider has climbed
     * plus what is left is exactly what the segment climbs, every time. An
     * altimeter settling mid-effort would break that, and a rider watching two
     * fields that should agree and do not stops believing either.
     */
    val ascentMeters: Double? get() = ifTiming { segment.ascentBetween(0.0, coveredMeters) }

    val descentMeters: Double? get() = ifTiming { segment.descentBetween(0.0, coveredMeters) }

    val remainingAscentMeters: Double?
        get() = ifTiming { segment.ascentBetween(coveredMeters, segment.distanceMeters) }

    val remainingDescentMeters: Double?
        get() = ifTiming { segment.descentBetween(coveredMeters, segment.distanceMeters) }

    /** Average gradient of what is left, as a percentage. */
    val remainingGradePercent: Double? get() = ifTiming { segment.gradeAfter(coveredMeters) }

    /**
     * Seconds ahead of the reference effort, negative when behind it.
     *
     * Null without a reference, which is what a segment the rider has never
     * ridden looks like: there is a segment to time, just nobody to race.
     */
    val aheadSeconds: Double?
        get() =
            ifTiming {
                val reference = segment.referenceSecondsAt(coveredMeters) ?: return@ifTiming null
                reference - elapsedSeconds
            }

    /** Metres ahead of where the reference effort was at this point in the effort. */
    val aheadMeters: Double?
        get() =
            ifTiming {
                val reference = segment.referenceMetersAt(elapsedSeconds) ?: return@ifTiming null
                coveredMeters - reference
            }

    /**
     * How much longer this effort is likely to take.
     *
     * Worked out the way a rider would: the reference effort says how long the
     * rest of the segment took last time, and the part already ridden says how
     * today compares. Ride the first half five percent quicker and the rest is
     * expected five percent quicker too.
     *
     * Null until there is enough of an effort to scale by - at the start line
     * the honest answer is the reference time itself, and a moment later a
     * fraction of a second of riding would otherwise predict an hour or a
     * heartbeat depending on which way the first fix fell.
     */
    val estimatedRemainingSeconds: Double?
        get() =
            ifTiming {
                val total = segment.referenceSeconds ?: return@ifTiming null
                val soFar = segment.referenceSecondsAt(coveredMeters) ?: return@ifTiming null
                if (soFar < MINIMUM_SCALE_SECONDS) return@ifTiming null
                val pace = elapsedSeconds / soFar
                ((total - soFar) * pace).coerceAtLeast(0.0)
            }

    /**
     * What this effort is on course to come to.
     *
     * The number a rider actually wants at the foot of a climb, and the only one
     * that can be held against their best directly: put the projected finish
     * beside [Segment.referenceSeconds] and the whole comparison is two fields
     * and no arithmetic.
     */
    val projectedSeconds: Double?
        get() =
            ifTiming {
                if (finished) return@ifTiming elapsedSeconds
                val remaining = estimatedRemainingSeconds ?: return@ifTiming null
                elapsedSeconds + remaining
            }

    private fun ifTiming(value: () -> Double?): Double? = if (timing) value() else null

    private companion object {
        /**
         * Below this the part ridden is too short to say anything about the
         * rest, and scaling by it turns a rounding error into minutes.
         */
        const val MINIMUM_SCALE_SECONDS = 5.0
    }
}

/**
 * Follows the ride against the starred segments it passes through.
 *
 * Two jobs, and the second is harder than it looks. Deciding which segment is
 * relevant is cheap: the nearest start, and only once it is near enough to be
 * the one being ridden. Following the ride along the line afterwards is where
 * the care goes, because segments double back on themselves more often than
 * not - a lap of a park, an out-and-back climb - and "nearest point on the
 * line" jumps to the homeward leg the moment the two pass within a few metres
 * of each other, which reads on the watch as the rider finishing in half the
 * time they took.
 *
 * So the search only ever runs forwards from where the rider already was, and
 * stops at the first point the line starts leading away from them again. That
 * makes a place on the line a step from the last one rather than an answer
 * looked up afresh, which is what survives a segment crossing its own path.
 *
 * Even that is not quite enough on a narrow out-and-back, where the two legs
 * are a few metres apart and the receiver's ordinary error is of the same size:
 * at that scale the rider genuinely is nearer the other lane, and no reading of
 * a single position can say otherwise. What separates them is which way the
 * rider is going. A rider heading up the hill cannot be on the part of the line
 * that comes back down it, however close the two pass.
 */
class SegmentTracker(
    private val segments: List<Segment> = emptyList(),
    private val startRadiusMeters: Double = START_RADIUS_METERS,
    private val strayLimitMeters: Double = STRAY_LIMIT_METERS,
    private val searchAheadMeters: Double = SEARCH_AHEAD_METERS,
    private val strayGraceMs: Long = STRAY_GRACE_MS,
    private val resultLingerMs: Long = RESULT_LINGER_MS,
) {
    private var active: Segment? = null
    private var activeIndex = 0
    private var previousFix: Fix? = null
    private var headingDeg: Double? = null
    private var startedAtEpochMs = 0L
    private var strayingSinceEpochMs = 0L
    private var finishedAtEpochMs = 0L

    /** What to show: the segment being ridden, else the nearest one ahead. */
    var state: SegmentState? = null
        private set

    fun record(fix: Fix) {
        takeHeading(fix)
        val riding = active
        when {
            riding != null -> follow(riding, fix)
            holdingResult(fix) -> Unit
            else -> look(fix)
        }
    }

    /**
     * Which way the rider is going, over a baseline long enough to mean it.
     *
     * A bearing taken between two fixes half a metre apart is the receiver's
     * noise and nothing else, so the last position is held on to until the
     * rider has actually gone somewhere. At a standstill the heading simply
     * stays as it was, which is the honest answer.
     */
    private fun takeHeading(fix: Fix) {
        val from = previousFix
        if (from == null) {
            previousFix = fix
            return
        }
        if (Haversine.metresBetween(from, fix) < HEADING_BASELINE_METERS) return
        headingDeg = bearingDegrees(from, fix)
        previousFix = fix
    }

    /** Keeps the finished time on screen long enough to be read. */
    private fun holdingResult(fix: Fix): Boolean {
        if (finishedAtEpochMs == 0L) return false
        if (fix.atEpochMs - finishedAtEpochMs <= resultLingerMs) return true
        finishedAtEpochMs = 0L
        return false
    }

    /** Nothing is being ridden: report the nearest start worth knowing about. */
    private fun look(fix: Fix) {
        val nearest =
            segments
                .map { it to metresTo(fix, it.start) }
                .minByOrNull { it.second }
        if (nearest == null) {
            state = null
            return
        }
        val (segment, metres) = nearest
        if (metres <= startRadiusMeters) {
            begin(segment, fix)
            return
        }
        state = SegmentState(segment, toStartMeters = metres)
    }

    private fun begin(
        segment: Segment,
        fix: Fix,
    ) {
        active = segment
        activeIndex = 0
        startedAtEpochMs = fix.atEpochMs
        // Cleared with the effort. Left from the last segment, one stray fix
        // would be measured against a timestamp from an hour ago and abandon
        // this one on the spot.
        strayingSinceEpochMs = 0L
        finishedAtEpochMs = 0L
        state = SegmentState(segment, toStartMeters = 0.0, riding = true)
    }

    /** On a segment: advance along it, finish it, or leave it. */
    private fun follow(
        segment: Segment,
        fix: Fix,
    ) {
        val here = placeOnLine(segment, fix)
        if (here == null) {
            if (strayed(fix)) {
                // A wrong turn, or a segment that was never the one being
                // ridden. Better to let it go than to keep timing something the
                // rider left behind.
                abandon()
                look(fix)
            }
            return
        }
        strayingSinceEpochMs = 0L
        activeIndex = here
        val last = segment.points.lastIndex
        val done = here == last
        state =
            SegmentState(
                segment = segment,
                toStartMeters = 0.0,
                riding = !done,
                finished = done,
                coveredMeters = segment.points[here].distanceMeters,
                elapsedSeconds = (fix.atEpochMs - startedAtEpochMs) / MILLIS_PER_SECOND,
            )
        if (done) {
            active = null
            activeIndex = 0
            finishedAtEpochMs = fix.atEpochMs
        }
    }

    /**
     * True once the rider has been off the line long enough to mean it.
     *
     * One fix in the next street is a receiver having a moment, and throwing the
     * effort away over it would lose the segment for good - the start is behind
     * the rider by then and there is no way back onto it. Half a minute of them
     * is a rider who has gone somewhere else.
     */
    private fun strayed(fix: Fix): Boolean {
        if (strayingSinceEpochMs == 0L) {
            strayingSinceEpochMs = fix.atEpochMs
            return false
        }
        return fix.atEpochMs - strayingSinceEpochMs > strayGraceMs
    }

    /**
     * The rider's place on the line, or null if they are no longer on it.
     *
     * Forwards from where they were, and only as far as a lost minute of fixes
     * could have carried them, stopping once the line has clearly turned away.
     */
    private fun placeOnLine(
        segment: Segment,
        fix: Fix,
    ): Int? {
        var nearestIndex = activeIndex
        var nearestMetres = Double.MAX_VALUE
        var agreeingIndex = NOWHERE
        var agreeingMetres = Double.MAX_VALUE
        for (index in activeIndex..lastWithinReach(segment)) {
            val metres = metresTo(fix, segment.points[index])
            if (metres < agreeingMetres && runsWithTheRider(segment, index)) {
                agreeingMetres = metres
                agreeingIndex = index
            }
            if (metres < nearestMetres) {
                nearestMetres = metres
                nearestIndex = index
            } else if (metres > nearestMetres + TURNED_AWAY_METERS) {
                break
            }
        }
        if (agreeingIndex != NOWHERE && agreeingMetres <= strayLimitMeters) return agreeingIndex
        // Nothing on the line is going the rider's way: they are at the top of
        // an out-and-back about to turn round, or they have left it. Distance
        // alone decides which, exactly as it did before there was a heading.
        return if (nearestMetres > strayLimitMeters) null else nearestIndex
    }

    /**
     * The last point worth comparing against.
     *
     * Generous enough to pick the ride back up after a tunnel or a minute of
     * lost fixes, and short of the way home on a segment that doubles back.
     */
    private fun lastWithinReach(segment: Segment): Int {
        val from = segment.points[activeIndex].distanceMeters
        val beyond = segment.points.indexOfFirst { it.distanceMeters - from > searchAheadMeters }
        return if (beyond <= activeIndex) segment.points.lastIndex else beyond - 1
    }

    /** True where the line at this point runs the same way the rider is going. */
    private fun runsWithTheRider(
        segment: Segment,
        index: Int,
    ): Boolean {
        val heading = headingDeg ?: return true
        val leg = if (index < segment.points.lastIndex) index else index - 1
        val line = bearingDegrees(segment.points[leg], segment.points[leg + 1]) ?: return true
        val apart = abs((line - heading + STRAIGHT_ON + FULL_CIRCLE) % FULL_CIRCLE - STRAIGHT_ON)
        return apart <= QUARTER_CIRCLE
    }

    private fun abandon() {
        active = null
        activeIndex = 0
        startedAtEpochMs = 0
        strayingSinceEpochMs = 0L
    }

    /** Forgets the ride. */
    fun clear() {
        abandon()
        finishedAtEpochMs = 0L
        previousFix = null
        headingDeg = null
        state = null
    }

    private fun metresTo(
        fix: Fix,
        point: SegmentPoint,
    ) = Haversine.metresBetween(fix.latitudeDeg, fix.longitudeDeg, point.latitudeDeg, point.longitudeDeg)

    private companion object {
        /**
         * How close counts as starting. Strava says itself that a device's live
         * time is provisional and its own matching may disagree, so this is a
         * judgement about when to start a stopwatch rather than a boundary
         * anyone can be precise about.
         */
        const val START_RADIUS_METERS = 25.0

        /** Further from the line than this and the rider is not on it. */
        const val STRAY_LIMIT_METERS = 60.0

        /** How far ahead of the rider's last place on the line to look. */
        const val SEARCH_AHEAD_METERS = 1_500.0

        /**
         * Once the line has led this much further away, it has turned a corner
         * rather than carried on, and the nearest point is behind us.
         */
        const val TURNED_AWAY_METERS = 30.0

        /** Below this a bearing is the receiver's noise rather than a direction. */
        const val HEADING_BASELINE_METERS = 3.0

        /** No candidate found yet. */
        const val NOWHERE = -1

        const val FULL_CIRCLE = 360.0
        const val STRAIGHT_ON = 180.0

        /** Further round than this and the line is not going the rider's way. */
        const val QUARTER_CIRCLE = 90.0

        /** Off the line for longer than this and the rider has left it. */
        const val STRAY_GRACE_MS = 30_000L

        /** Long enough to look at the finishing time before it goes. */
        const val RESULT_LINGER_MS = 30_000L

        const val MILLIS_PER_SECOND = 1000.0
    }
}

/** Which way one position lies from another, in degrees from north. */
private fun bearingDegrees(
    from: SegmentPoint,
    to: SegmentPoint,
): Double? = bearingDegrees(from.latitudeDeg, from.longitudeDeg, to.latitudeDeg, to.longitudeDeg)

private fun bearingDegrees(
    from: Fix,
    to: Fix,
): Double? = bearingDegrees(from.latitudeDeg, from.longitudeDeg, to.latitudeDeg, to.longitudeDeg)

private fun bearingDegrees(
    fromLatitudeDeg: Double,
    fromLongitudeDeg: Double,
    toLatitudeDeg: Double,
    toLongitudeDeg: Double,
): Double? {
    if (fromLatitudeDeg == toLatitudeDeg && fromLongitudeDeg == toLongitudeDeg) return null
    val fromLat = Math.toRadians(fromLatitudeDeg)
    val toLat = Math.toRadians(toLatitudeDeg)
    val deltaLon = Math.toRadians(toLongitudeDeg - fromLongitudeDeg)
    val y = sin(deltaLon) * cos(toLat)
    val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(deltaLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}
