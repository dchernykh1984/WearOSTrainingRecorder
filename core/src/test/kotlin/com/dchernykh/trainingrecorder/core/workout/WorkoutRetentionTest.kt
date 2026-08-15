package com.dchernykh.trainingrecorder.core.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRetentionTest {
    private val connectors = setOf("garmin", "strava")

    private fun workout(
        id: String,
        startedAt: Long,
        bytes: Long = 250_000,
        uploads: Map<String, UploadState> = connectors.associateWith { UploadState.UPLOADED },
    ) = WorkoutSummary(
        id = id,
        sportTypeId = "cycling_road",
        startedAtEpochMs = startedAt,
        durationSeconds = 3600,
        distanceMeters = 40_000.0,
        fileSizeBytes = bytes,
        uploads = uploads,
    )

    @Test
    fun nothingIsEvictedWhileTheWatchHasRoom() {
        val workouts = (1..10).map { workout("w$it", it.toLong()) }
        assertTrue(RetentionPolicy.evictable(workouts, connectors).isEmpty())
    }

    @Test
    fun theOldestUploadedWorkoutsGoFirstWhenTheCountIsExceeded() {
        val workouts = (1..5).map { workout("w$it", it.toLong()) }
        val evicted = RetentionPolicy.evictable(workouts, connectors, maxKept = 50)
        assertTrue(evicted.isEmpty(), "the floor of 50 must be respected")

        val many = (1..60).map { workout("w$it", it.toLong()) }
        val trimmed = RetentionPolicy.evictable(many, connectors, maxKept = 50)
        assertEquals(10, trimmed.size)
        assertEquals(listOf("w1", "w2", "w3"), trimmed.take(3), "oldest first")
        assertFalse(trimmed.contains("w60"), "the newest must survive")
    }

    @Test
    fun anUnsyncedWorkoutIsNeverEvictedHoweverOldItIs() {
        val pending = workout("oldest", 1, uploads = mapOf("garmin" to UploadState.PENDING))
        val rest = (2..60).map { workout("w$it", it.toLong()) }
        val evicted = RetentionPolicy.evictable(listOf(pending) + rest, connectors, maxKept = 50)
        assertFalse(evicted.contains("oldest"), "an unsynced workout must survive eviction")
    }

    @Test
    fun aRideNoServiceEverTookIsKeptHoweverOftenItWasRefused() {
        // The case that used to lose rides. The queue records FAILED both for a
        // file a service refused and for one it simply gave up retrying - which
        // is what an expired token looks like - so a ride that never left the
        // watch became deletable, and the next ride needing space took it.
        val refused = workout("refused", 1, uploads = connectors.associateWith { UploadState.FAILED })
        assertFalse(refused.isSafeToDelete(connectors), "it has never arrived anywhere")
    }

    @Test
    fun oneServiceAcceptingItIsEnoughToLetItGo() {
        // The ride exists somewhere other than the watch, which is the whole
        // question. Whether the other service also wanted it is its own business.
        val delivered =
            workout(
                "delivered",
                1,
                uploads = mapOf("garmin" to UploadState.UPLOADED, "strava" to UploadState.FAILED),
            )
        assertTrue(delivered.isSafeToDelete(connectors))
    }

    @Test
    fun aRiderWithNoServicesAtAllStillGetsToKeepRecording() {
        // Nothing can ever deliver these, so holding every one forever would
        // fill the watch and cost the rider the rides in front of them to keep
        // the ones behind. Age and space decide there, and only there.
        val kept = workout("kept", 1)
        assertTrue(kept.isSafeToDelete(emptySet()))
    }

    @Test
    fun weeksInTheMountainsWithNoSignalLoseNothing() {
        // No network means no attempt, so every ride sits PENDING - and none of
        // them may be evicted to make room for the next.
        val hike =
            (1..200).map {
                workout(
                    "day$it",
                    it.toLong(),
                    uploads = connectors.associateWith { UploadState.PENDING },
                )
            }
        assertTrue(
            RetentionPolicy.evictable(hike, connectors, maxKept = 50).isEmpty(),
            "a ride that has not arrived anywhere cannot be dropped to make room",
        )
    }

    @Test
    fun aPartlyFailedUploadStillWaitsOnTheServiceThatHasNotAnswered() {
        val waiting =
            workout(
                "waiting",
                1,
                uploads =
                    mapOf(
                        "garmin" to UploadState.FAILED,
                        "strava" to UploadState.PENDING,
                    ),
            )
        assertFalse(waiting.isSafeToDelete(connectors))
    }

    @Test
    fun aWorkoutIsSafeOnceAnyEnabledServiceHasIt() {
        val partial = workout("p", 1, uploads = mapOf("garmin" to UploadState.UPLOADED))
        assertTrue(partial.isSafeToDelete(connectors), "it exists off the watch")
        assertTrue(partial.isSafeToDelete(setOf("garmin")))
        assertTrue(partial.isSafeToDelete(emptySet()), "nothing can ever take it")
    }

    @Test
    fun theByteBudgetEvictsEvenWhenTheCountFits() {
        val big = (1..10).map { workout("w$it", it.toLong(), bytes = 30L * 1024 * 1024) }
        val evicted = RetentionPolicy.evictable(big, connectors, maxTotalBytes = 100L * 1024 * 1024)
        assertEquals(7, evicted.size)
        assertEquals("w1", evicted.first())
    }

    @Test
    fun evictionIsOldestFirstRatherThanBestFit() {
        // A big recent ride must not be dropped in favour of small old ones - that
        // reads as the watch deleting what the rider just finished.
        val workouts =
            listOf(
                workout("newest_big", 3, bytes = 90L * 1024 * 1024),
                workout("middle_small", 2, bytes = 1024),
                workout("oldest_small", 1, bytes = 1024),
            )
        val evicted = RetentionPolicy.evictable(workouts, connectors, maxTotalBytes = 50L * 1024 * 1024)
        assertEquals(listOf("oldest_small", "middle_small"), evicted)
    }

    @Test
    fun theNewestWorkoutIsKeptEvenWhenItAloneExceedsTheBudget() {
        // Being over budget is a better outcome than deleting the ride that was
        // just recorded.
        val huge = workout("huge", 1, bytes = 500L * 1024 * 1024)
        assertTrue(RetentionPolicy.evictable(listOf(huge), connectors, maxTotalBytes = 1024).isEmpty())
    }

    @Test
    fun theDefaultsAreTheAgreedOnes() {
        assertEquals(200, RetentionPolicy.MAX_KEPT)
        assertEquals(256L * 1024 * 1024, RetentionPolicy.MAX_TOTAL_BYTES)
        assertTrue(RetentionPolicy.MAX_KEPT >= RetentionPolicy.MIN_KEPT)
        assertEquals(50, RetentionPolicy.MIN_KEPT)
    }

    @Test
    fun nonsenseBudgetsAreRejected() {
        assertFailsWith<IllegalArgumentException> { RetentionPolicy.evictable(emptyList(), connectors, maxKept = 10) }
        assertFailsWith<IllegalArgumentException> {
            RetentionPolicy.evictable(emptyList(), connectors, maxTotalBytes = 0)
        }
    }

    @Test
    fun degenerateSummariesAreRejected() {
        assertFailsWith<IllegalArgumentException> { workout("", 1) }
        assertFailsWith<IllegalArgumentException> {
            WorkoutSummary("w", "cycling_road", 1, -1, 0.0, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            WorkoutSummary("w", "cycling_road", 1, 1, -1.0, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            WorkoutSummary("w", "cycling_road", 1, 1, 0.0, -1)
        }
    }

    @Test
    fun uploadStateByIdRoundTrips() {
        UploadState.entries.forEach { assertEquals(it, UploadState.byId(it.id)) }
        assertNull(UploadState.byId("nope"))
    }
}
