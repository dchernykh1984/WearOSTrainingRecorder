package com.dchernykh.trainingrecorder.core.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the phone goes back to Strava, and what it asks for.
 *
 * The cost is the point. Strava allows a couple of hundred requests a quarter of
 * an hour, shared with the uploads that are the app's actual job, and a refresh
 * that walks every starred segment every day would spend the rider's allowance
 * on discovering that nothing had changed.
 */
class SegmentSyncTest {
    private val now = 1_782_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun listing(
        id: Long,
        best: Double? = null,
    ) = StarredSegment(id, "Segment $id", 1000.0, 55.0, 37.0, bestSeconds = best)

    @Test
    fun theButtonAlwaysGoes() {
        // Even a second after the last one: the rider pressed it because they
        // believe something has changed, and arguing with them costs a request.
        assertTrue(SegmentSync.isDue(SyncTrigger.MANUAL, lastSyncEpochMs = now - 1000, nowEpochMs = now))
    }

    @Test
    fun anUploadIsTheMomentWorthChecking() {
        // The ride that just went up is the one that sets new bests.
        assertTrue(SegmentSync.isDue(SyncTrigger.AFTER_UPLOAD, now - hour, now))
    }

    @Test
    fun twoRefreshesAMinuteApartAreOneRefresh() {
        assertFalse(
            SegmentSync.isDue(SyncTrigger.AFTER_UPLOAD, now - 60_000, now),
            "nothing can have changed in a minute, and asking costs a request",
        )
    }

    @Test
    fun openingTheAppAllDayCostsOneRefresh() {
        assertFalse(SegmentSync.isDue(SyncTrigger.APP_OPENED, now - hour, now))
        assertTrue(SegmentSync.isDue(SyncTrigger.APP_OPENED, now - 7 * hour, now))
    }

    @Test
    fun theBackgroundCheckIsDaily() {
        assertFalse(SegmentSync.isDue(SyncTrigger.PERIODIC, now - 12 * hour, now))
        assertTrue(SegmentSync.isDue(SyncTrigger.PERIODIC, now - 25 * hour, now))
    }

    @Test
    fun aQuietDayCostsNothingBeyondTheListing() {
        val stored = listOf(StoredSegment(1L, hasLine = true, bestSeconds = 553.0))

        val plan = SegmentSync.plan(stored, listOf(listing(1L, best = 553.0)))

        assertTrue(plan.isEmpty, "nothing has moved")
        assertEquals(0, plan.requests)
    }

    @Test
    fun aNewlyStarredSegmentNeedsItsLine() {
        val plan = SegmentSync.plan(stored = emptyList(), starred = listOf(listing(1L, best = 553.0)))

        assertEquals(listOf(1L), plan.linesToFetch)
        assertEquals(listOf(1L), plan.effortsToFetch, "and the effort to chase along it")
        assertTrue(plan.toDrop.isEmpty())
    }

    @Test
    fun aNewPersonalBestReplacesTheReference() {
        val stored = listOf(StoredSegment(1L, hasLine = true, bestSeconds = 553.0))

        val plan = SegmentSync.plan(stored, listOf(listing(1L, best = 540.0)))

        assertEquals(listOf(1L), plan.effortsToFetch, "they went quicker, so the curve to chase has changed")
        assertTrue(plan.linesToFetch.isEmpty(), "the road is where it was")
    }

    @Test
    fun aSegmentTheRiderUnstarredStopsAppearing() {
        val stored =
            listOf(
                StoredSegment(1L, hasLine = true, bestSeconds = 553.0),
                StoredSegment(2L, hasLine = true),
            )

        val plan = SegmentSync.plan(stored, listOf(listing(1L, best = 553.0)))

        assertEquals(listOf(2L), plan.toDrop)
    }

    @Test
    fun aSegmentNeverRiddenIsKeptWithoutAskingForAnEffort() {
        val stored = listOf(StoredSegment(1L, hasLine = true))

        val plan = SegmentSync.plan(stored, listOf(listing(1L)))

        assertTrue(plan.isEmpty, "there is no effort to fetch and nothing to drop")
    }

    @Test
    fun aFirstRunCostsTwoRequestsPerSegmentAndThenNever() {
        val starred = (1L..10L).map { listing(it, best = 500.0 + it) }

        val plan = SegmentSync.plan(stored = emptyList(), starred = starred)

        // Ten lines and ten efforts, once ever - the lines are fixed pieces of
        // road and are never asked for again.
        assertEquals(10, plan.linesToFetch.size)
        assertEquals(10, plan.effortsToFetch.size)
        assertEquals(20, plan.requests)

        // And the day after, with the same ten and no new bests, nothing at all.
        val settled = (1L..10L).map { StoredSegment(it, hasLine = true, bestSeconds = 500.0 + it) }
        assertEquals(0, SegmentSync.plan(settled, starred).requests)
    }
}
