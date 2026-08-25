package com.dchernykh.trainingrecorder.core.segment

/** One point of a segment's line, with how far along the segment it sits. */
data class SegmentPoint(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val distanceMeters: Double,
    val altitudeMeters: Double? = null,
)

/** How far along a reference effort was after a given elapsed time. */
data class EffortPoint(
    val distanceMeters: Double,
    val elapsedSeconds: Double,
)

/**
 * A segment the rider has starred, with the effort they are riding against.
 *
 * Everything needed to run the comparison is here, because during the ride there
 * is nothing to ask. This is the whole trick of live segments and the thing most
 * people expect to work differently: the watch does not consult anything while
 * the rider is on the segment - it was handed the line and the reference effort
 * beforehand and does the arithmetic itself. Which is just as well, since the
 * places worth racing rarely have a signal.
 *
 * [reference] is the rider's own best effort as a curve of distance against
 * time. A total time alone would only say who won at the finish; the curve is
 * what lets the watch say "four seconds down" in the middle of the climb.
 */
data class Segment(
    val id: Long,
    val name: String,
    val points: List<SegmentPoint>,
    val reference: List<EffortPoint> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "a segment needs a name" }
        require(points.size >= MIN_POINTS) { "a segment needs at least $MIN_POINTS points, got ${points.size}" }
    }

    val start: SegmentPoint get() = points.first()

    val finish: SegmentPoint get() = points.last()

    val distanceMeters: Double get() = finish.distanceMeters

    /** The reference effort's total, which is the time to beat. */
    val referenceSeconds: Double? get() = reference.lastOrNull()?.elapsedSeconds

    /** What the reference effort had taken by this point, or null without one. */
    fun referenceSecondsAt(distanceMeters: Double): Double? =
        interpolate(reference, distanceMeters, { it.distanceMeters }, { it.elapsedSeconds })

    /** How far the reference effort had gone by this time, or null without one. */
    fun referenceMetersAt(elapsedSeconds: Double): Double? =
        interpolate(reference, elapsedSeconds, { it.elapsedSeconds }, { it.distanceMeters })

    /** Height at a point along the line, where the segment carries altitudes. */
    fun altitudeAt(distanceMeters: Double): Double? {
        val known = points.mapNotNull { point -> point.altitudeMeters?.let { point.distanceMeters to it } }
        return interpolate(known, distanceMeters, { it.first }, { it.second })
    }

    /** Metres of climbing still to come from this point on. */
    fun ascentAfter(distanceMeters: Double): Double? {
        val heights =
            points
                .filter { it.distanceMeters >= distanceMeters }
                .mapNotNull { it.altitudeMeters }
        val from = altitudeAt(distanceMeters) ?: return null
        if (heights.isEmpty()) return null
        return (listOf(from) + heights)
            .zipWithNext { lower, upper -> (upper - lower).coerceAtLeast(0.0) }
            .sum()
    }

    /**
     * Average gradient of what is left, as a percentage.
     *
     * The average, not the climbing: on a segment that goes up and then down,
     * what is left to climb and how steep the rest averages out are different
     * questions, and a rider looking at the top of a hill wants both.
     */
    fun gradeAfter(distanceMeters: Double): Double? {
        val remaining = this.distanceMeters - distanceMeters
        val from = altitudeAt(distanceMeters)
        val to = finish.altitudeMeters
        if (remaining <= 0 || from == null || to == null) return null
        return (to - from) / remaining * PERCENT
    }

    private companion object {
        /** A line needs two ends before it is a line. */
        const val MIN_POINTS = 2

        const val PERCENT = 100.0
    }
}

/**
 * Reads a value off a curve between the two samples that straddle it.
 *
 * A reference effort is recorded once a second and the rider is compared against
 * it once a second, so the two almost never line up. Stepping to the nearest
 * sample instead would make the gap jump about by whole seconds while the rider
 * held a perfectly steady pace.
 */
@Suppress("ReturnCount")
private fun <T> interpolate(
    points: List<T>,
    at: Double,
    xOf: (T) -> Double,
    yOf: (T) -> Double,
): Double? {
    if (points.isEmpty()) return null
    val first = points.first()
    if (at <= xOf(first)) return yOf(first)
    val last = points.last()
    if (at >= xOf(last)) return yOf(last)
    val after = points.indexOfFirst { xOf(it) >= at }
    val before = points[after - 1]
    val ahead = points[after]
    val span = xOf(ahead) - xOf(before)
    if (span <= 0) return yOf(before)
    return yOf(before) + (at - xOf(before)) / span * (yOf(ahead) - yOf(before))
}
