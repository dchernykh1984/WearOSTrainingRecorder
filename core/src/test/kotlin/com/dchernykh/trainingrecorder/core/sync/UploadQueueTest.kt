package com.dchernykh.trainingrecorder.core.sync

import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadQueueTest {
    private val now = 1_770_000_000_000L
    private val connectors = setOf("garmin", "strava")

    private fun workout(
        id: String,
        startedAt: Long = now,
        uploads: Map<String, UploadState> = emptyMap(),
        attempts: Map<String, Int> = emptyMap(),
        attemptedAt: Map<String, Long> = emptyMap(),
    ) = WorkoutSummary(id, "cycling_road", startedAt, 60, 100.0, 1024, uploads, attempts, attemptedAt)

    @Test
    fun theAttemptCountSurvivesARestart() {
        // Rebuilt from zero the backoff would restart on every launch, the queue
        // would never give up, and the workout would stay undeletable forever.
        val tried = workout("w1", attempts = mapOf("strava" to 4))
        val entry = UploadQueue.from(listOf(tried), setOf("strava"), now).single()
        assertEquals(4, entry.attempts)
    }

    @Test
    fun theBackoffIsMeasuredFromTheLastAttemptRatherThanFromNow() {
        // Re-served from now, the delay has never elapsed by the time the same
        // pass asks what is due - so one failure would strand the workout for
        // good: never retried, never FAILED, and therefore never deletable.
        val tried = workout("w1", attempts = mapOf("strava" to 4), attemptedAt = mapOf("strava" to now))
        val entry = UploadQueue.from(listOf(tried), setOf("strava"), now).single()
        assertEquals(now + UploadQueue.backoffMs(4), entry.nextAttemptAtEpochMs)
        assertTrue(UploadQueue.due(listOf(entry), now).isEmpty())
    }

    @Test
    fun anEntryBecomesDueOnceItsBackoffHasActuallyElapsed() {
        val tried = workout("w1", attempts = mapOf("strava" to 4), attemptedAt = mapOf("strava" to now))
        val later = now + UploadQueue.backoffMs(4)
        val entry = UploadQueue.from(listOf(tried), setOf("strava"), later).single()
        assertEquals(1, UploadQueue.due(listOf(entry), later).size, "the delay has passed, so it is due again")
    }

    @Test
    fun aDeviceRestartingDuringAnOutageStillWaits() {
        // The whole reason the timestamp is persisted: rebuilt from zero, a watch
        // rebooting a few times would burn all ten attempts in seconds.
        val tried = workout("w1", attempts = mapOf("strava" to 4), attemptedAt = mapOf("strava" to now))
        val soonAfter = now + 1_000
        val entry = UploadQueue.from(listOf(tried), setOf("strava"), soonAfter).single()
        assertTrue(UploadQueue.due(listOf(entry), soonAfter).isEmpty())
    }

    @Test
    fun anAttemptCountWithNoTimestampIsDueRatherThanStranded() {
        // An index written by an older build carries counts but no timestamps.
        // Treating that as "wait forever" would strand every workout on it.
        val legacy = workout("w1", attempts = mapOf("strava" to 4))
        val entry = UploadQueue.from(listOf(legacy), setOf("strava"), now).single()
        assertEquals(1, UploadQueue.due(listOf(entry), now).size)
    }

    @Test
    fun anUntriedEntryIsStillDueImmediately() {
        val entry = UploadQueue.from(listOf(workout("w1")), setOf("strava"), now).single()
        assertEquals(now, entry.nextAttemptAtEpochMs)
        assertEquals(1, UploadQueue.due(listOf(entry), now).size)
    }

    @Test
    fun theFirstAttemptIsImmediate() {
        assertEquals(0, UploadQueue.backoffMs(0))
    }

    @Test
    fun retriesBackOffExponentiallyFromHalfAMinute() {
        assertEquals(30_000, UploadQueue.backoffMs(1))
        assertEquals(60_000, UploadQueue.backoffMs(2))
        assertEquals(120_000, UploadQueue.backoffMs(3))
        assertEquals(240_000, UploadQueue.backoffMs(4))
    }

    @Test
    fun theBackoffIsCappedSoAQueueLeftOvernightStillWakesUp() {
        assertEquals(UploadQueue.MAX_BACKOFF_MS, UploadQueue.backoffMs(20))
        assertTrue(UploadQueue.backoffMs(8) <= UploadQueue.MAX_BACKOFF_MS)
    }

    @Test
    fun aNegativeAttemptCountIsRejected() {
        assertFailsWith<IllegalArgumentException> { UploadQueue.backoffMs(-1) }
    }

    @Test
    fun aFailureSchedulesTheNextAttempt() {
        val pending = PendingUpload("w1", "strava")
        val afterFirst = UploadQueue.afterFailure(pending, now)
        assertEquals(1, afterFirst.attempts)
        assertEquals(now + 30_000, afterFirst.nextAttemptAtEpochMs)

        val afterSecond = UploadQueue.afterFailure(afterFirst, now + 30_000)
        assertEquals(2, afterSecond.attempts)
        assertEquals(now + 30_000 + 60_000, afterSecond.nextAttemptAtEpochMs)
    }

    @Test
    fun aServerThatAsksForADelayIsObeyedRatherThanSecondGuessed() {
        val pending = PendingUpload("w1", "strava")
        val scheduled = UploadQueue.afterFailure(pending, now, retryAfterSeconds = 900)
        assertEquals(now + 900_000, scheduled.nextAttemptAtEpochMs, "Retry-After wins over our own backoff")
    }

    @Test
    fun afterEnoughFailuresTheQueueGivesUp() {
        var pending = PendingUpload("w1", "strava")
        repeat(UploadQueue.MAX_ATTEMPTS) { pending = UploadQueue.afterFailure(pending, now) }
        assertTrue(UploadQueue.hasGivenUp(pending))
        assertFalse(UploadQueue.hasGivenUp(PendingUpload("w1", "strava", attempts = 1)))
    }

    @Test
    fun aQueueEntryThatGaveUpIsRecordedAsFailedSoStorageCanStillBeReclaimed() {
        // Left as PENDING the workout would never count as synced, the retention
        // policy would never evict it, and the watch would fill up because a
        // service was down.
        val exhausted = PendingUpload("w1", "strava", attempts = UploadQueue.MAX_ATTEMPTS - 1)
        assertEquals(UploadState.FAILED, UploadQueue.stateAfter(exhausted, UploadResult.Retryable("no network")))
    }

    @Test
    fun aRetryableFailureWithAttemptsLeftStaysPending() {
        val fresh = PendingUpload("w1", "strava")
        assertEquals(UploadState.PENDING, UploadQueue.stateAfter(fresh, UploadResult.Retryable("no network")))
    }

    @Test
    fun successAndRejectionKeepTheirOwnStates() {
        val fresh = PendingUpload("w1", "strava")
        assertEquals(UploadState.UPLOADED, UploadQueue.stateAfter(fresh, UploadResult.Success()))
        assertEquals(UploadState.FAILED, UploadQueue.stateAfter(fresh, UploadResult.Rejected("duplicate")))
    }

    @Test
    fun onlyUploadsWhoseTimeHasComeAreDue() {
        val queue =
            listOf(
                PendingUpload("w1", "strava", nextAttemptAtEpochMs = now - 1000),
                PendingUpload("w2", "strava", nextAttemptAtEpochMs = now + 1000),
                PendingUpload("w3", "garmin", nextAttemptAtEpochMs = now),
            )
        assertEquals(listOf("w1", "w3"), UploadQueue.due(queue, now).map { it.workoutId })
    }

    @Test
    fun anUploadThatGaveUpIsNeverDueAgain() {
        val exhausted = PendingUpload("w1", "strava", attempts = UploadQueue.MAX_ATTEMPTS, nextAttemptAtEpochMs = 0)
        assertTrue(UploadQueue.due(listOf(exhausted), now).isEmpty())
    }

    @Test
    fun theOldestDueUploadGoesFirst() {
        val queue =
            listOf(
                PendingUpload("newer", "strava", nextAttemptAtEpochMs = now - 100),
                PendingUpload("older", "strava", nextAttemptAtEpochMs = now - 5000),
            )
        assertEquals(listOf("older", "newer"), UploadQueue.due(queue, now).map { it.workoutId })
    }

    @Test
    fun theQueueIsBuiltFromWhatEachWorkoutStillOwes() {
        val workouts =
            listOf(
                workout("w2", startedAt = now + 1000),
                workout("w1", startedAt = now, uploads = mapOf("strava" to UploadState.UPLOADED)),
            )
        val queue = UploadQueue.from(workouts, connectors)
        assertEquals(
            listOf("w1" to "garmin", "w2" to "garmin", "w2" to "strava"),
            queue.map { it.workoutId to it.connectorId },
            "oldest workout first, and an already uploaded pair is left out",
        )
    }

    @Test
    fun aPermanentlyRejectedPairIsNotQueuedAgain() {
        val rejected = workout("w1", uploads = mapOf("strava" to UploadState.FAILED))
        val queue = UploadQueue.from(listOf(rejected), connectors)
        assertEquals(listOf("garmin"), queue.map { it.connectorId })
    }

    @Test
    fun aPendingPairIsQueued() {
        val pending = workout("w1", uploads = mapOf("strava" to UploadState.PENDING))
        val queue = UploadQueue.from(listOf(pending), connectors)
        assertEquals(listOf("garmin", "strava"), queue.map { it.connectorId })
    }

    @Test
    fun degeneratePendingUploadsAreRejected() {
        assertFailsWith<IllegalArgumentException> { PendingUpload("", "strava") }
        assertFailsWith<IllegalArgumentException> { PendingUpload("w1", "") }
        assertFailsWith<IllegalArgumentException> { PendingUpload("w1", "strava", attempts = -1) }
    }
}
