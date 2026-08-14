package com.dchernykh.trainingrecorder.wear.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dchernykh.trainingrecorder.core.connector.ConnectorRegistry
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
import com.dchernykh.trainingrecorder.core.sync.PendingUpload
import com.dchernykh.trainingrecorder.core.sync.UploadQueue
import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.dchernykh.trainingrecorder.localization.Labels
import com.dchernykh.trainingrecorder.wear.connector.GarminConnector
import com.dchernykh.trainingrecorder.wear.connector.StravaConnector
import com.dchernykh.trainingrecorder.wear.storage.WorkoutRepository
import com.dchernykh.trainingrecorder.wear.sync.WorkoutPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Sends finished workouts to the services the rider has set up.
 *
 * Run through WorkManager rather than from the app because the rider closes the
 * app the moment the ride ends, and a service that is down when they do must not
 * mean the workout never arrives. The network constraint is what delivers the
 * "retries when connectivity returns" promise: the system wakes this up when the
 * watch has a route to the internet, whether that is its own Wi-Fi, LTE, or the
 * phone's connection over Bluetooth.
 *
 * Whether a given attempt should happen at all is [UploadQueue]'s decision - it
 * holds the backoff and the give-up rule, and it is unit-tested. This class is
 * the part that cannot be: the file handles, the network, and the index.
 */
class UploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val repository = WorkoutRepository(context)
    private val publisher = WorkoutPublisher(context)
    private val credentials = CredentialStore(context)
    private val registry =
        ConnectorRegistry(
            listOf(
                StravaConnector(onTokensRefreshed = ::rememberStravaTokens),
                GarminConnector(onTokensRefreshed = ::rememberGarminTokens),
            ),
        )

    /** Strava rotates the refresh token, so the new pair has to be written down. */
    private fun rememberStravaTokens(updated: Map<String, String>) = remember(StravaProtocol.ID, updated)

    /** Garmin rotates it too, and its tokens are the shorter-lived of the two. */
    private fun rememberGarminTokens(updated: Map<String, String>) = remember(GarminProtocol.ID, updated)

    private fun remember(
        connectorId: String,
        updated: Map<String, String>,
    ) {
        val stored = credentials.read()
        credentials.write(CredentialContract.encode(stored + (connectorId to updated)))
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val configured = registry.configured(credentials.read()).map { it.id }.toSet()
            // Nothing set up yet is a finished job, not a failed one: rescheduling
            // would spin against a rider who simply has not connected a service.
            if (configured.isEmpty()) return@withContext Result.success()

            // Read once and indexed: the index is a single file, and re-reading
            // it per upload would be a file parse for every workout in the queue.
            val workouts = repository.all().associateBy { it.id }
            val due = UploadQueue.due(UploadQueue.from(workouts.values.toList(), configured, now()), now())

            due.forEach { pending ->
                // Re-read per attempt: a refresh rotates Strava's token and
                // writes it back, and a snapshot taken before the drain would
                // send every later upload of the same pass a token that has
                // already been spent.
                attempt(pending, workouts[pending.workoutId], credentials.read())
            }
            // Anything the queue has given up on can now be reclaimed.
            repository.prune(configured)
            // Told once per drain rather than per upload: the phone's history
            // shows one line per ride, and the rider watching it cares that the
            // ride arrived, not which of the two services answered first.
            publisher.publish(repository)

            // Rebuilt from what is now on disk, because the attempts above have
            // just changed it, and asked about *everything* still owed rather
            // than what was due this pass. Asking only about this pass is what
            // stranded rides: WorkManager's retry can come round sooner than the
            // queue's own backoff, and a drain that then finds nothing due used
            // to report itself finished - ending the retry chain with the ride
            // still pending and nothing left to wake it. This cannot spin: every
            // pair gives up after MAX_ATTEMPTS, and the delay between passes is
            // WorkManager's, not ours.
            val remaining = UploadQueue.outstanding(UploadQueue.from(repository.all(), configured, now()))
            // Retried through WorkManager rather than looping here, so a watch
            // that goes back into a tunnel is not holding a wakelock while it
            // waits. The queue's own backoff still gates the next attempt.
            if (remaining.isEmpty()) Result.success() else Result.retry()
        }

    private suspend fun attempt(
        pending: PendingUpload,
        workout: WorkoutSummary?,
        stored: Map<String, Map<String, String>>,
    ) {
        val connector = registry.byId(pending.connectorId)
        val file = repository.fileFor(pending.workoutId)
        if (connector == null || workout == null) return
        if (!file.exists()) {
            // The index outlives the file if a prune was interrupted. Recorded as
            // failed rather than skipped: left pending it would never give up,
            // never be safe to delete, and hold its share of the budget forever.
            repository.markUploaded(
                workoutId = pending.workoutId,
                connectorId = connector.id,
                state = UploadState.FAILED,
                attempts = pending.attempts,
                attemptedAtEpochMs = now(),
                reason = "the recorded file is gone",
            )
            return
        }

        val result =
            runCatching {
                connector.upload(
                    WorkoutUpload(
                        workoutId = workout.id,
                        sportTypeId = workout.sportTypeId,
                        fileName = "${workout.id}.fit",
                        activityName = activityName(workout.sportTypeId),
                        byteCount = file.length(),
                        openStream = { file.inputStream() },
                    ),
                    stored[connector.id].orEmpty(),
                )
            }.getOrElse { UploadResult.Retryable(it.javaClass.simpleName) }

        val attempts = if (result is UploadResult.Success) pending.attempts else pending.attempts + 1
        repository.markUploaded(
            workoutId = pending.workoutId,
            connectorId = connector.id,
            state = UploadQueue.stateAfter(pending, result),
            attempts = attempts,
            attemptedAtEpochMs = now(),
            reason = result.failureReason,
        )
    }

    /**
     * What the ride is called on the service: the sport in the rider's language.
     *
     * Resolved through the labels rather than passing the catalogue id along -
     * a feed entry reading "cycling_road" is an internal identifier that escaped.
     */
    private fun activityName(sportTypeId: String): String {
        val label = Labels.sport(sportTypeId)
        return if (label != 0) applicationContext.getString(label) else sportTypeId
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val WORK_NAME = "upload-workouts"

        /** Grows a minute per retry; the queue's own backoff still gates each try. */
        private const val RETRY_DELAY_MINUTES = 1L

        /**
         * Asks for a drain as soon as the watch has a network.
         *
         * KEEP rather than REPLACE: a drain already running is doing the same
         * job, and replacing it would cancel a half-finished upload for nothing.
         */
        fun schedule(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    // Linear, and from a minute rather than the default thirty
                    // seconds doubling. The drain now stays in retry for as long
                    // as anything is owed, so the default would have the chain
                    // drifting to hours between passes - and because the work is
                    // unique and KEEP, a ride finished during one of those gaps
                    // would wait it out before anything looked at it. A pass
                    // that finds nothing due is a small file read, so a steady
                    // few minutes costs nothing and keeps the watch responsive.
                    .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
