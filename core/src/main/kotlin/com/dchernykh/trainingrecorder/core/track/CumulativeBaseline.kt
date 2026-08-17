package com.dchernykh.trainingrecorder.core.track

/**
 * Turns a counter that was already running into one that starts at this ride.
 *
 * The platform reports distance, calories and climb as totals rather than as
 * changes, and those totals do not necessarily begin where the ride does: an
 * exercise session that was already under way hands over its accumulated figures
 * on the first update. The ride then opens with nine hundred metres already
 * covered, drops back to nothing the moment the app's own measurement takes
 * over, and the file carries a first point that no service can make sense of.
 *
 * Subtracting the first value seen makes every one of them a change since the
 * ride began, which is what a rider means by "distance" and what the field says
 * it is.
 *
 * A counter that goes backwards - which is what a genuinely new session looks
 * like - re-baselines rather than reporting a negative total. Nothing else is
 * assumed about it.
 */
class CumulativeBaseline {
    private val firstSeen = mutableMapOf<String, Double>()

    /** The value as a change since the ride began. */
    fun sinceStart(
        field: String,
        total: Double,
    ): Double {
        val baseline = firstSeen[field]
        if (baseline == null || total < baseline) {
            firstSeen[field] = total
            return 0.0
        }
        return total - baseline
    }

    fun clear() = firstSeen.clear()
}
