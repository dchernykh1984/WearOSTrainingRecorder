package com.dchernykh.trainingrecorder.core.sync

import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import kotlin.math.min
import kotlin.math.pow

/** One outstanding attempt: a workout, a service, and when to try again. */
data class PendingUpload(
    val workoutId: String,
    val connectorId: String,
    val attempts: Int = 0,
    val nextAttemptAtEpochMs: Long = 0,
) {
    init {
        require(workoutId.isNotBlank()) { "a pending upload needs a workout id" }
        require(connectorId.isNotBlank()) { "a pending upload needs a connector id" }
        require(attempts >= 0) { "attempt count cannot be negative" }
    }
}

/**
 * Decides what to retry and when.
 *
 * Uploads fail for two very different reasons and the queue must not treat them
 * alike. A rejected file is never retried - retrying a duplicate only burns the
 * rider's rate limit. A retryable failure backs off exponentially, because the
 * usual cause is no signal, and hammering a dead connection is what empties a
 * battery on a long ride.
 */
object UploadQueue {
    /** First retry after half a minute; doubling from there. */
    const val BASE_BACKOFF_MS = 30_000L

    /** An hour is long enough that a queue left overnight costs nothing. */
    const val MAX_BACKOFF_MS = 3_600_000L

    /** Past this many failures the service is treated as broken, not busy. */
    const val MAX_ATTEMPTS = 10

    private const val BACKOFF_BASE = 2.0

    fun backoffMs(attempts: Int): Long {
        require(attempts >= 0) { "attempt count cannot be negative" }
        if (attempts == 0) return 0
        val grown = BASE_BACKOFF_MS.toDouble() * BACKOFF_BASE.pow(attempts - 1)
        return min(grown, MAX_BACKOFF_MS.toDouble()).toLong()
    }

    /**
     * Records a failure and schedules the next attempt. A server that asked for
     * a specific delay is obeyed rather than second-guessed - that is what a
     * `Retry-After` means.
     */
    fun afterFailure(
        pending: PendingUpload,
        nowEpochMs: Long,
        retryAfterSeconds: Int? = null,
    ): PendingUpload {
        val attempts = pending.attempts + 1
        val delay = retryAfterSeconds?.let { it.toLong() * MILLIS_PER_SECOND } ?: backoffMs(attempts)
        return pending.copy(attempts = attempts, nextAttemptAtEpochMs = nowEpochMs + delay)
    }

    fun hasGivenUp(pending: PendingUpload): Boolean = pending.attempts >= MAX_ATTEMPTS

    /**
     * What to record against the workout after an attempt.
     *
     * A queue entry that has given up must be written down as [UploadState.FAILED]
     * rather than left pending. Leaving it pending means the retention policy can
     * never consider the workout synced, so nothing is ever evicted and the watch
     * fills up - a storage leak caused entirely by a service that is down.
     */
    fun stateAfter(
        pending: PendingUpload,
        result: UploadResult,
    ): UploadState =
        when {
            result is UploadResult.Retryable && hasGivenUp(afterFailure(pending, 0)) -> UploadState.FAILED
            else -> result.state
        }

    /** The uploads due now, oldest first, excluding the ones that gave up. */
    fun due(
        queue: List<PendingUpload>,
        nowEpochMs: Long,
    ): List<PendingUpload> =
        queue
            .filterNot { hasGivenUp(it) }
            .filter { it.nextAttemptAtEpochMs <= nowEpochMs }
            .sortedBy { it.nextAttemptAtEpochMs }

    /**
     * Builds the queue from what each workout still owes. Anything already
     * uploaded or permanently rejected is left out.
     */
    fun from(
        workouts: List<WorkoutSummary>,
        connectorIds: Set<String>,
    ): List<PendingUpload> =
        workouts
            .sortedBy { it.startedAtEpochMs }
            .flatMap { workout ->
                connectorIds
                    .sorted()
                    .filter { workout.uploads[it] == null || workout.uploads[it] == UploadState.PENDING }
                    .map {
                        PendingUpload(
                            workoutId = workout.id,
                            connectorId = it,
                            // Carried over from the workout, so a restart resumes
                            // the backoff rather than starting it again.
                            attempts = workout.uploadAttempts[it] ?: 0,
                        )
                    }
            }

    private const val MILLIS_PER_SECOND = 1000L
}
