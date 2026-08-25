package com.dchernykh.trainingrecorder.core.connector

/** What prompted a refresh, which is what decides whether one is due. */
enum class SyncTrigger {
    /** The rider pressed the button, and the answer is always yes. */
    MANUAL,

    /**
     * A ride has just reached Strava. The best moment there is: the segments
     * the rider cares about are exactly the ones they have just been over, and
     * any new personal best is minutes old.
     */
    AFTER_UPLOAD,

    /** The phone app came to the front. */
    APP_OPENED,

    /** The daily background check, for stars added on the website. */
    PERIODIC,
}

/** A segment already on the phone, as far as deciding what to fetch is concerned. */
data class StoredSegment(
    val id: Long,
    /** False while only the listing is known and the line has still to come. */
    val hasLine: Boolean,
    /** The best time the stored reference was built from. */
    val bestSeconds: Double? = null,
)

/** What a refresh has to fetch, once the starred listing has come back. */
data class SegmentPlan(
    /** Segments whose line is not on the phone yet. */
    val linesToFetch: List<Long> = emptyList(),
    /** Segments where the rider has a new best, so the reference is out of date. */
    val effortsToFetch: List<Long> = emptyList(),
    /** Segments no longer starred, which should stop appearing on the watch. */
    val toDrop: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = linesToFetch.isEmpty() && effortsToFetch.isEmpty() && toDrop.isEmpty()

    /** Requests this plan will cost, which is what the rate limit counts. */
    val requests: Int get() = linesToFetch.size + effortsToFetch.size
}

/**
 * When to go and look at Strava again, and what to ask for when we do.
 *
 * Segments change in two ways and they want different treatment. The list of
 * starred ones changes when the rider stars something, usually at a desk, and
 * a day is soon enough to notice. Personal bests change when the rider rides,
 * which the app watches happen - so that refresh is not scheduled at all, it is
 * triggered by the upload that caused it.
 *
 * What keeps the whole thing cheap is that the listing carries `pr_elapsed_time`
 * already. One request says whether anything at all has moved; a quiet day
 * costs exactly that one request, and a segment's line - which is a fixed piece
 * of road and never changes - is fetched once and kept.
 */
object SegmentSync {
    /**
     * Whether to go now.
     *
     * The interval is per trigger rather than one number, because the triggers
     * are asking different questions. Only the floor is shared: two refreshes a
     * minute apart cannot both find something, and the second is a request spent
     * on nothing.
     */
    fun isDue(
        trigger: SyncTrigger,
        lastSyncEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean {
        val since = nowEpochMs - lastSyncEpochMs
        if (trigger == SyncTrigger.MANUAL) return true
        if (since < FLOOR_MS) return false
        return when (trigger) {
            SyncTrigger.AFTER_UPLOAD -> true
            SyncTrigger.APP_OPENED -> since >= APP_OPENED_MS
            SyncTrigger.PERIODIC -> since >= PERIODIC_MS
            SyncTrigger.MANUAL -> true
        }
    }

    /**
     * What the refresh has to fetch.
     *
     * A segment's line is asked for once and never again - it is a fixed piece
     * of road. Its reference effort is asked for only when the listing says the
     * rider's best is not the one already stored, which is the difference
     * between a daily refresh costing one request and costing two per starred
     * segment.
     */
    fun plan(
        stored: List<StoredSegment>,
        starred: List<StarredSegment>,
    ): SegmentPlan {
        val known = stored.associateBy { it.id }
        val starredIds = starred.map { it.id }.toSet()
        return SegmentPlan(
            linesToFetch = starred.filter { known[it.id]?.hasLine != true }.map { it.id },
            effortsToFetch =
                starred
                    .filter { it.bestSeconds != null }
                    .filter { known[it.id]?.bestSeconds != it.bestSeconds }
                    .map { it.id },
            toDrop = stored.map { it.id }.filterNot { it in starredIds },
        )
    }

    /** Two refreshes closer together than this cannot both find anything. */
    private const val FLOOR_MS = 5 * 60 * 1000L

    /** Long enough that opening the app repeatedly costs nothing. */
    private const val APP_OPENED_MS = 6 * 60 * 60 * 1000L

    /** Stars are added at a desk, and a day is soon enough to notice. */
    private const val PERIODIC_MS = 24 * 60 * 60 * 1000L
}
