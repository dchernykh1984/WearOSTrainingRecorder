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
    fun aWorkoutNoServiceWillEverTakeStopsHoldingSpace() {
        // Both halves matter: a refused ride is not retried, so keeping it
        // forever would strand it and fill the watch at the same time.
        val refused = workout("refused", 1, uploads = connectors.associateWith { UploadState.FAILED })
        assertTrue(refused.isSafeToDelete(connectors))
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
    fun aWorkoutIsSafeOnlyWhenEveryEnabledServiceHasIt() {
        val partial = workout("p", 1, uploads = mapOf("garmin" to UploadState.UPLOADED))
        assertFalse(partial.isSafeToDelete(connectors))
        assertTrue(partial.isSafeToDelete(setOf("garmin")))
        assertTrue(partial.isSafeToDelete(emptySet()), "with no services enabled nothing is owed")
    }

    @Test
    fun theByteBudgetEvictsEvenWhenTheCountFits() {
        val big = (1..10).map { workout("w$it", it.toLong(), bytes = 30L * 1024 * 1024) }
        val evicted = RetentionPolicy.evictable(big, connectors, maxTotalBytes = 100L * 1024 * 1024)
        assertEquals(7, evicted.size)
        assertEquals("w1", evicted.first())
    }

    @Test
    fun aSingleWorkoutLargerThanTheBudgetIsStillEvictedRatherThanLoopingForever() {
        val huge = workout("huge", 1, bytes = 500L * 1024 * 1024)
        val evicted = RetentionPolicy.evictable(listOf(huge), connectors, maxTotalBytes = 1024)
        assertEquals(listOf("huge"), evicted)
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
