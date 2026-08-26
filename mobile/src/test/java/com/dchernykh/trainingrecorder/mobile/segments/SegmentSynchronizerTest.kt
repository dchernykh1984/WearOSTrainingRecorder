package com.dchernykh.trainingrecorder.mobile.segments

import com.dchernykh.trainingrecorder.core.connector.CredentialContract
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.StravaSegments
import com.dchernykh.trainingrecorder.core.connector.SyncTrigger
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SegmentTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The order the refresh does things in, and what it does when a request comes
 * back wrong. The requests themselves are somebody else's problem: what is
 * tested here is that a rate limit stops the pass without losing what it had,
 * that an unstarred segment leaves the watch, and that a segment's line is
 * never fetched twice.
 */
class SegmentSynchronizerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_782_000_000_000L

    /** Every request that was made, in order. */
    private val asked = mutableListOf<String>()

    private val published = mutableListOf<Segment>()
    private val removed = mutableListOf<Long>()

    private val target =
        object : SegmentTarget {
            override fun publish(segment: Segment) {
                published += segment
            }

            override fun remove(id: Long) {
                removed += id
            }
        }

    private fun starred(vararg entries: Pair<Long, Double?>) =
        entries.joinToString(",", "[", "]") { (id, best) ->
            val effort = best?.let { """, "athlete_pr_effort": { "id": ${id + 1000}, "pr_elapsed_time": $it }""" }
            """{ "id": $id, "name": "Segment $id", "distance": 1000.0,
               "start_latlng": [55.0, 37.0]${effort.orEmpty()} }"""
        }

    private val streams =
        """
        {
          "latlng": { "data": [[55.0, 37.0], [55.001, 37.0], [55.002, 37.0]] },
          "distance": { "data": [0.0, 111.2, 222.4] },
          "altitude": { "data": [100.0, 110.0, 125.0] }
        }
        """.trimIndent()

    private val effort =
        """
        { "distance": { "data": [0, 222.4] }, "time": { "data": [0, 553] } }
        """.trimIndent()

    private fun store() = SegmentStore(File(folder.root, "segments"), File(folder.root, "sync.txt"))

    private fun settings(): PhoneSettingsStore {
        val credentials = File(folder.root, "credentials.json")
        credentials.writeText(
            CredentialContract.encode(
                mapOf(
                    StravaProtocol.ID to
                        mapOf(
                            StravaProtocol.ACCESS_TOKEN to "token",
                            // Far enough ahead that nothing tries to renew it.
                            StravaProtocol.EXPIRES_AT to (now / 1000 + 86_400).toString(),
                        ),
                ),
            ),
        )
        return PhoneSettingsStore(File(folder.root, "settings.json"), credentials)
    }

    private fun synchronizer(
        store: SegmentStore = store(),
        answer: (String) -> ApiResponse,
    ) = SegmentSynchronizer(
        store = store,
        settings = settings(),
        publisher = target,
        reader = { url, _ ->
            asked += url
            answer(url)
        },
        refresher = { null },
        now = { now },
    )

    private fun ok(body: String) = ApiResponse(200, body)

    @Test
    fun aStarredSegmentIsFetchedWholeAndSentToTheWatch() {
        val outcome =
            synchronizer { url ->
                when {
                    url.contains("/segments/starred") -> ok(starred(1L to 553.0))
                    url.contains("/streams") -> ok(streams)
                    else -> ApiResponse(404, "")
                }
            }.sync(SyncTrigger.MANUAL)

        assertTrue(outcome is SyncOutcome.Updated)
        assertEquals(1, published.size)
        assertEquals("Segment 1", published.first().name)
        assertEquals(553.0, published.first().referenceSeconds!!, 0.001)
    }

    @Test
    fun aSegmentsLineIsNeverFetchedTwice() {
        val store = store()
        val answer = { url: String ->
            when {
                url.contains("/segments/starred") -> ok(starred(1L to 553.0))
                url.contains("/segment_efforts/") -> ok(effort)
                url.contains("/streams") -> ok(streams)
                else -> ApiResponse(404, "")
            }
        }
        synchronizer(store, answer).sync(SyncTrigger.MANUAL)
        asked.clear()

        // The rider went quicker, so the effort is worth fetching again - but
        // the road has not moved.
        val second = { url: String ->
            if (url.contains("/segments/starred")) ok(starred(1L to 540.0)) else answer(url)
        }
        synchronizer(store, second).sync(SyncTrigger.MANUAL)

        assertFalse(
            "the line is a fixed piece of road: $asked",
            asked.any { it.contains("/segments/1/streams") },
        )
        assertTrue("but the time to beat has changed", asked.any { it.contains("/segment_efforts/") })
    }

    @Test
    fun aRateLimitStopsThePassAndKeepsWhatItHad() {
        val outcome =
            synchronizer { url ->
                when {
                    url.contains("/segments/starred") -> ok(starred(1L to 553.0, 2L to 400.0))
                    url.contains("/segments/1/streams") -> ok(streams)
                    else -> ApiResponse(429, "rate limited")
                }
            }.sync(SyncTrigger.MANUAL)

        assertTrue("expected a rate limit, got $outcome", outcome is SyncOutcome.RateLimited)
        // The first segment made it; the second did not, and the pass was not
        // recorded as clean, so the next trigger will come back for it.
        assertEquals(0L, store().lastSyncEpochMs())
    }

    @Test
    fun aSegmentTheRiderUnstarredLeavesTheWatch() {
        val store = store()
        synchronizer(store) { url ->
            when {
                url.contains("/segments/starred") -> ok(starred(1L to 553.0, 2L to 400.0))
                url.contains("/streams") -> ok(streams)
                else -> ApiResponse(404, "")
            }
        }.sync(SyncTrigger.MANUAL)
        assertEquals(2, store.read().size)

        synchronizer(store) { url ->
            if (url.contains("/segments/starred")) ok(starred(1L to 553.0)) else ok(streams)
        }.sync(SyncTrigger.MANUAL)

        assertEquals(listOf(2L), removed)
        assertEquals(listOf(1L), store.read().map { it.id })
    }

    @Test
    fun anEffortStravaWillNotShareFallsBackToAnEvenPace() {
        // A personal best set on a private activity: the listing knows the time,
        // the streams endpoint refuses. Better an even-paced comparison than no
        // segment at all.
        val outcome =
            synchronizer { url ->
                when {
                    url.contains("/segments/starred") -> ok(starred(1L to 553.0))
                    url.contains("/segment_efforts/") -> ApiResponse(403, "")
                    else -> ok(streams)
                }
            }.sync(SyncTrigger.MANUAL)

        assertTrue(outcome is SyncOutcome.Updated)
        val segment = published.single()
        assertEquals(553.0, segment.referenceSeconds!!, 0.001)
        // Half way through an even pace is half the time.
        assertEquals(276.5, segment.referenceSecondsAt(segment.distanceMeters / 2)!!, 1.0)
    }

    @Test
    fun withoutStravaConnectedNothingHappens() {
        val store = store()
        val synchronizer =
            SegmentSynchronizer(
                store = store,
                settings = PhoneSettingsStore(File(folder.root, "s.json"), File(folder.root, "c.json")),
                publisher = target,
                reader = { url, _ ->
                    asked += url
                    ApiResponse(200, "")
                },
                refresher = { null },
                now = { now },
            )

        assertEquals(SyncOutcome.NotConnected, synchronizer.sync(SyncTrigger.MANUAL))
        assertTrue("nothing should have been asked of Strava", asked.isEmpty())
    }

    @Test
    fun aBackgroundTriggerTooSoonAfterTheLastOneDoesNothing() {
        val store = store()
        store.markSynced(now - 60_000)

        val outcome = synchronizer(store) { ok(starred()) }.sync(SyncTrigger.PERIODIC)

        assertEquals(SyncOutcome.TooSoon, outcome)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun theStarredListIsTheFirstThingAskedFor() {
        synchronizer { ok("[]") }.sync(SyncTrigger.MANUAL)

        assertEquals(StravaSegments.starredUrl(), asked.first())
    }
}
