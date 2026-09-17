package com.dchernykh.trainingrecorder.core.sync

import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a fresh authorization does to rides a dead session refused.
 *
 * This is the shape of a real complaint: eight rides sitting at "Upload refused
 * - garmin: session expired - attempts: 10", with nothing the rider could do
 * about it. Reconnecting the service did nothing, because the queue had already
 * written those rides off and stopped building entries for them.
 */
class UploadReinstateTest {
    private val start = 1_782_000_000_000L

    private fun ride(
        id: String,
        state: UploadState?,
        attempts: Int,
        reason: String? = null,
        connector: String = "garmin",
    ) = WorkoutSummary(
        id = id,
        sportTypeId = "cycling_road",
        startedAtEpochMs = start,
        durationSeconds = 2_400,
        distanceMeters = 2_400.0,
        fileSizeBytes = 120_000,
        uploads = state?.let { mapOf(connector to it) }.orEmpty(),
        uploadAttempts = mapOf(connector to attempts),
        uploadAttemptedAt = mapOf(connector to start + 1000),
        uploadReasons = reason?.let { mapOf(connector to it) }.orEmpty(),
    )

    @Test
    fun aRideThatRanOutOfAttemptsIsOfferedAgain() {
        val exhausted = ride("ride-1", UploadState.FAILED, attempts = 10, reason = "session expired")

        val reinstated = UploadQueue.reinstate(listOf(exhausted), setOf("garmin")).single()

        assertNull(reinstated.uploads["garmin"], "as far as garmin is concerned this ride is new")
        assertNull(reinstated.uploadAttempts["garmin"], "and the count starts from nothing")
        assertNull(reinstated.uploadReasons["garmin"], "the old explanation no longer applies")
    }

    @Test
    fun theReinstatedRideIsBackInTheQueueAndDueNow() {
        // The part that makes it work: `from` builds entries only for services
        // the workout has not settled with, so clearing the attempt count alone
        // would have changed nothing.
        val exhausted = ride("ride-1", UploadState.FAILED, attempts = 10, reason = "session expired")
        assertTrue(
            UploadQueue.from(listOf(exhausted), setOf("garmin")).isEmpty(),
            "a written-off ride is not in the queue at all",
        )

        val reinstated = UploadQueue.reinstate(listOf(exhausted), setOf("garmin"))

        val due = UploadQueue.due(UploadQueue.from(reinstated, setOf("garmin"), nowEpochMs = start), start)
        assertEquals(listOf("ride-1"), due.map { it.workoutId })
        assertEquals(0, due.single().attempts)
    }

    @Test
    fun aRejectionThatStuckIsLeftAlone() {
        // A duplicate activity is refused on the first attempt and would be
        // refused again. Retrying it spends the rider's rate limit to learn
        // nothing, which is what the give-up rule is for.
        val rejected = ride("ride-2", UploadState.FAILED, attempts = 1, reason = "duplicate activity")

        val reinstated = UploadQueue.reinstate(listOf(rejected), setOf("garmin")).single()

        assertEquals(UploadState.FAILED, reinstated.uploads["garmin"])
        assertEquals(1, reinstated.uploadAttempts["garmin"])
    }

    @Test
    fun aRideStillWorkingThroughItsBackoffIsUntouched() {
        val pending = ride("ride-3", UploadState.PENDING, attempts = 3)

        val reinstated = UploadQueue.reinstate(listOf(pending), setOf("garmin")).single()

        assertEquals(3, reinstated.uploadAttempts["garmin"], "its backoff is still running and still correct")
    }

    @Test
    fun aRideAlreadyDeliveredIsNotOfferedAgain() {
        val done = ride("ride-4", UploadState.UPLOADED, attempts = 2)

        val reinstated = UploadQueue.reinstate(listOf(done), setOf("garmin")).single()

        assertEquals(UploadState.UPLOADED, reinstated.uploads["garmin"], "uploading it twice is a duplicate")
    }

    @Test
    fun onlyTheServiceThatWasReconnectedComesBack() {
        // Reconnecting Garmin says nothing about whether Strava is working.
        val both =
            ride("ride-5", UploadState.FAILED, attempts = 10, reason = "session expired").copy(
                uploads = mapOf("garmin" to UploadState.FAILED, "strava" to UploadState.FAILED),
                uploadAttempts = mapOf("garmin" to 10, "strava" to 10),
            )

        val reinstated = UploadQueue.reinstate(listOf(both), setOf("garmin")).single()

        assertNull(reinstated.uploads["garmin"])
        assertEquals(UploadState.FAILED, reinstated.uploads["strava"])
        assertEquals(10, reinstated.uploadAttempts["strava"])
    }

    @Test
    fun awholeBacklogComesBackAtOnce() {
        // Eight rides is what the rider actually had waiting.
        val backlog =
            (1..8).map {
                ride("ride-$it", UploadState.FAILED, attempts = 10, reason = "session expired")
            }

        val reinstated = UploadQueue.reinstate(backlog, setOf("garmin"))

        val due = UploadQueue.due(UploadQueue.from(reinstated, setOf("garmin"), nowEpochMs = start), start)
        assertEquals(8, due.size, "all of them, not just the newest")
    }

    @Test
    fun reconnectingWithNothingStuckChangesNothing() {
        val clean = listOf(ride("ride-6", UploadState.UPLOADED, attempts = 1))

        assertEquals(clean, UploadQueue.reinstate(clean, setOf("garmin", "strava")))
    }
}
