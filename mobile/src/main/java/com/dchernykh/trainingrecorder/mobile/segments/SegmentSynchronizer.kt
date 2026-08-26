package com.dchernykh.trainingrecorder.mobile.segments

import com.dchernykh.trainingrecorder.core.connector.SegmentPlan
import com.dchernykh.trainingrecorder.core.connector.SegmentSync
import com.dchernykh.trainingrecorder.core.connector.StarredSegment
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

        val listing = reader.get(StravaSegments.starredUrl(), token)
        if (listing.rateLimited) return SyncOutcome.RateLimited(fetched = 0)
        if (!listing.ok) return SyncOutcome.Failed("Strava answered ${listing.statusCode} to the starred list")
        val starred = StravaSegments.starredFrom(listing.body)

        val held = store.read().associateBy { it.id }
        val plan = SegmentSync.plan(store.stored(), starred)
        val fetched = fetchAll(plan, starred.associateBy { it.id }, held, token)
        plan.toDrop.forEach {
            store.delete(it)
            publisher.remove(it)
        }
        // Only a clean pass counts as a sync. Recording one that stopped at the
        // rate limit would tell the next trigger there was nothing to do, and
        // the segments left unfetched would wait a day for no reason.
        if (!fetched.limited) store.markSynced(now())
        return if (fetched.limited) {
            SyncOutcome.RateLimited(fetched.count)
        } else {
            SyncOutcome.Updated(segments = starred.size, fetched = fetched.count, dropped = plan.toDrop.size)
        }
    }

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
