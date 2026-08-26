package com.dchernykh.trainingrecorder.mobile.segments

import com.dchernykh.trainingrecorder.core.connector.SegmentPlan
import com.dchernykh.trainingrecorder.core.connector.SegmentSync
import com.dchernykh.trainingrecorder.core.connector.StarredSegment
import com.dchernykh.trainingrecorder.core.connector.StoredSegment
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.StravaSegments
import com.dchernykh.trainingrecorder.core.connector.SyncTrigger
import com.dchernykh.trainingrecorder.core.segment.EffortPoint
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SegmentTarget

/** How a refresh ended, in terms the rider's screen can say out loud. */
sealed interface SyncOutcome {
    /** Nothing to do: Strava is not connected on this phone. */
    data object NotConnected : SyncOutcome

    /** Too soon since the last one. */
    data object TooSoon : SyncOutcome

    data class Updated(
        val segments: Int,
        val fetched: Int,
        val dropped: Int,
    ) : SyncOutcome

    /** Strava asked us to slow down; what was fetched before that still stands. */
    data class RateLimited(
        val fetched: Int,
    ) : SyncOutcome

    data class Failed(
        val reason: String,
    ) : SyncOutcome
}

/**
 * Keeps the phone's copy of the rider's starred segments in step with Strava,
 * and the watch's copy in step with the phone's.
 *
 * The shape of the work is decided by [SegmentSync] and the parsing by
 * [StravaSegments]; what is left here is the order to do it in and what to do
 * when a request comes back wrong. Segments are stored and published one at a
 * time as they arrive, so a refresh cut off half way leaves the watch with the
 * half that made it rather than with nothing.
 */
class SegmentSynchronizer(
    private val store: SegmentStore,
    private val settings: PhoneSettingsStore,
    private val publisher: SegmentTarget,
    private val reader: StravaReader = HttpStravaReader(),
    private val refresher: TokenRefresher = StravaTokenRefresher(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Suppress("ReturnCount")
    fun sync(trigger: SyncTrigger): SyncOutcome {
        val credentials = settings.readCredentials()[StravaProtocol.ID].orEmpty()
        if (credentials[StravaProtocol.ACCESS_TOKEN].isNullOrBlank()) return SyncOutcome.NotConnected
        if (!SegmentSync.isDue(trigger, store.lastSyncEpochMs(), now())) return SyncOutcome.TooSoon
        val token = freshToken(credentials) ?: return SyncOutcome.Failed("the Strava token could not be renewed")

        val starred = starred(token) ?: return SyncOutcome.Failed("Strava would not list the starred segments")

        val held = store.read().associateBy { it.id }
        val plan = SegmentSync.plan(held.values.map(::asStored), starred.segments)
        val fetched = fetchAll(plan, starred.segments.associateBy { it.id }, held, token)
        // Nothing is dropped from a listing that was cut short: a segment the
        // rate limit stopped us reading is not a segment the rider unstarred,
        // and deleting it would take a climb off the watch for no reason.
        if (!starred.limited) {
            plan.toDrop.forEach {
                store.delete(it)
                publisher.remove(it)
            }
        }
        // Only a clean pass counts as a sync. Recording one that stopped at the
        // rate limit would tell the next trigger there was nothing to do, and
        // the segments left unfetched would wait a day for no reason.
        val limited = fetched.limited || starred.limited
        if (!limited) store.markSynced(now())
        return if (limited) {
            SyncOutcome.RateLimited(fetched.count)
        } else {
            SyncOutcome.Updated(segments = starred.segments.size, fetched = fetched.count, dropped = plan.toDrop.size)
        }
    }

    private data class Starred(
        val segments: List<StarredSegment>,
        val limited: Boolean,
    )

    /**
     * The starred listing, a page at a time.
     *
     * Paged because a rider with more than thirty starred segments would
     * otherwise get the first thirty and no sign that the rest existed - and
     * "my climb is missing and nothing says why" is the worst kind of bug to
     * have in a feature that is quiet by design.
     */
    private fun starred(token: String): Starred? {
        val all = mutableListOf<StarredSegment>()
        for (page in 1..StravaSegments.MAX_PAGES) {
            val response = reader.get(StravaSegments.starredUrl(page), token)
            if (response.rateLimited) return Starred(all, limited = true)
            // A failure on the first page is a failure; on a later one it is a
            // partial answer, and the segments already listed are still good.
            if (!response.ok) return if (page == 1) null else Starred(all, limited = false)
            val listed = StravaSegments.starredFrom(response.body)
            all += listed
            if (listed.size < StravaSegments.PAGE_SIZE) break
        }
        return Starred(all, limited = false)
    }

    private fun asStored(segment: Segment) =
        StoredSegment(segment.id, hasLine = segment.points.size >= 2, bestSeconds = segment.referenceSeconds)

    private data class Fetched(
        val count: Int,
        val limited: Boolean,
    )

    /**
     * Fetches everything the plan asked for, stopping at the rate limit.
     *
     * Stored and published one at a time as they arrive, so a refresh cut off
     * half way leaves the watch with the half that made it rather than nothing.
     */
    private fun fetchAll(
        plan: SegmentPlan,
        starred: Map<Long, StarredSegment>,
        held: Map<Long, Segment>,
        token: String,
    ): Fetched {
        var count = 0
        var limited = false
        (plan.linesToFetch + plan.effortsToFetch).distinct().forEach { id ->
            if (limited) return@forEach
            val listed = starred[id] ?: return@forEach
            when (refetch(listed, held[id], token)) {
                Refetch.FETCHED -> count++
                Refetch.LIMITED -> limited = true
                Refetch.SKIPPED -> Unit
            }
        }
        return Fetched(count, limited)
    }

    private enum class Refetch { FETCHED, SKIPPED, LIMITED }

    /**
     * Fetches one segment whole: its line, and the effort to chase along it.
     *
     * Whole rather than in parts because a line without a reference and a
     * reference without a line are both unusable, and storing half of one only
     * means working out which half is missing on the next pass.
     */
    @Suppress("ReturnCount")
    private fun refetch(
        starred: StarredSegment,
        held: Segment?,
        token: String,
    ): Refetch {
        val line =
            if (held != null && held.points.size >= 2) {
                // A fixed piece of road. Fetched once, ever.
                held.points
            } else {
                val streams = reader.get(StravaSegments.streamsUrl(starred.id), token)
                if (streams.rateLimited) return Refetch.LIMITED
                if (!streams.ok) return Refetch.SKIPPED
                StravaSegments.lineFrom(streams.body)
            }
        val reference = referenceFor(starred, token) ?: return Refetch.LIMITED
        val segment = StravaSegments.segment(starred, line, reference) ?: return Refetch.SKIPPED
        store.write(segment)
        publisher.publish(segment)
        return Refetch.FETCHED
    }

    /**
     * The rider's best effort as a curve, or an empty one to fall back on.
     *
     * Null means the rate limit, which is the only answer that should stop the
     * whole refresh. A refusal is not: Strava will not hand over the streams of
     * an effort on a private activity, and the even-paced stand-in that
     * [StravaSegments.segment] falls back to is worth more than skipping the
     * segment entirely.
     */
    @Suppress("ReturnCount")
    private fun referenceFor(
        starred: StarredSegment,
        token: String,
    ): List<EffortPoint>? {
        val effortId = starred.bestEffortId ?: return emptyList()
        val streams = reader.get(StravaSegments.effortStreamsUrl(effortId), token)
        if (streams.rateLimited) return null
        if (!streams.ok) return emptyList()
        return StravaSegments.effortCurveFrom(streams.body)
    }

    /**
     * A token good for the next few minutes.
     *
     * Renewed here rather than left to fail, because a refresh that spends its
     * requests discovering the token expired has spent them for nothing.
     */
    private fun freshToken(credentials: Map<String, String>): String? {
        val seconds = now() / MILLIS_PER_SECOND
        if (!StravaProtocol.needsRefresh(credentials, seconds)) {
            return credentials[StravaProtocol.ACCESS_TOKEN]
        }
        val renewed = refresher.refresh(credentials) ?: return credentials[StravaProtocol.ACCESS_TOKEN]
        val stored = settings.readCredentials()
        val merged = stored + (StravaProtocol.ID to (stored[StravaProtocol.ID].orEmpty() + renewed))
        settings.writeCredentials(merged)
        return renewed[StravaProtocol.ACCESS_TOKEN]
    }

    /** Every segment the phone holds, for the screen that lists them. */
    fun segments(): List<Segment> = store.read()

    fun lastSyncEpochMs(): Long = store.lastSyncEpochMs()

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
