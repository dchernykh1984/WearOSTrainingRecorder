package com.dchernykh.trainingrecorder.wear.upload

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dchernykh.trainingrecorder.core.connector.ConnectorRegistry
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.dchernykh.trainingrecorder.core.sync.PendingUpload
import com.dchernykh.trainingrecorder.core.sync.UploadQueue
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.dchernykh.trainingrecorder.wear.connector.GarminConnector
import com.dchernykh.trainingrecorder.wear.connector.StravaConnector
import com.dchernykh.trainingrecorder.wear.storage.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val credentials = CredentialStore(context)
    private val registry =
        ConnectorRegistry(
            listOf(
                StravaConnector(onTokensRefreshed = ::rememberStravaTokens),
                GarminConnector(),
            ),
        )

    /** Strava rotates the refresh token, so the new pair has to be written down. */
    private fun rememberStravaTokens(updated: Map<String, String>) {
        val stored = credentials.read()
        credentials.write(CredentialContract.encode(stored + (StravaProtocol.ID to updated)))
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val stored = credentials.read()
            val configured = registry.configured(stored).map { it.id }.toSet()
            // Nothing set up yet is a finished job, not a failed one: rescheduling
            // would spin against a rider who simply has not connected a service.
            if (configured.isEmpty()) return@withContext Result.success()

            // Read once and indexed: the index is a single file, and re-reading
            // it per upload would be a file parse for every workout in the queue.
            val workouts = repository.all().associateBy { it.id }
            val due = UploadQueue.due(UploadQueue.from(workouts.values.toList(), configured, now()), now())

            var deferred = false
            due.forEach { pending ->
                if (attempt(pending, workouts[pending.workoutId], stored)) deferred = true
            }
            // Anything the queue has given up on can now be reclaimed.
            repository.prune(configured)
            // Retried through WorkManager rather than looping here, so a watch
            // that goes back into a tunnel is not holding a wakelock while it
            // waits. The queue's own backoff still gates the next attempt.
            if (deferred) Result.retry() else Result.success()
        }

    /** True when the attempt should be tried again later. */
    private suspend fun attempt(
        pending: PendingUpload,
        workout: WorkoutSummary?,
        stored: Map<String, Map<String, String>>,
    ): Boolean {
        val connector = registry.byId(pending.connectorId)
        val file = repository.fileFor(pending.workoutId)
        // The index outlives the file if a prune was interrupted, and sending a
        // missing file would be a rejected upload for a reason that is ours.
        if (connector == null || workout == null || !file.exists()) return false

        val result =
            runCatching {
                connector.upload(
                    WorkoutUpload(
                        workoutId = workout.id,
                        sportTypeId = workout.sportTypeId,
                        fileName = "${workout.id}.fit",
                        activityName = SportCatalogue.byId(workout.sportTypeId)?.id ?: workout.sportTypeId,
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
        )
        return result is UploadResult.Retryable && !UploadQueue.hasGivenUp(pending.copy(attempts = attempts))
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val WORK_NAME = "upload-workouts"

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
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
