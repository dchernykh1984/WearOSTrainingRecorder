package com.dchernykh.trainingrecorder.mobile.segments

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dchernykh.trainingrecorder.core.connector.SyncTrigger
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SegmentPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Runs a segment refresh where the rider is not waiting for it.
 *
 * Through WorkManager rather than from a screen because two of the three
 * moments worth refreshing at happen with the app closed: a ride reaching
 * Strava wakes the phone through the Data Layer, and the daily check for stars
 * added on the website happens whenever the system feels like it. Only the
 * button in the app is a rider standing there.
 */
class SegmentSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val trigger = SyncTrigger.byId(inputData.getString(KEY_TRIGGER)) ?: SyncTrigger.PERIODIC
        val outcome = withContext(Dispatchers.IO) { synchronizer(applicationContext).sync(trigger) }
        return when (outcome) {
            // Both mean "Strava could not answer now", which is exactly what a
            // retry with a backoff is for. A rate limit resets on the quarter
            // hour and the first backoff is longer than that.
            is SyncOutcome.RateLimited -> Result.retry()
            is SyncOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val KEY_TRIGGER = "trigger"
        private const val ONE_OFF = "segment-sync"
        private const val DAILY = "segment-sync-daily"
        private const val BACKOFF_MINUTES = 30L

        fun synchronizer(context: Context) =
            SegmentSynchronizer(
                store = SegmentStore(context),
                settings = PhoneSettingsStore(context),
                publisher = SegmentPublisher(context),
            )

        /** Once now, when something has happened that could have changed a best. */
        fun runNow(
            context: Context,
            trigger: SyncTrigger,
        ) {
            val request =
                OneTimeWorkRequestBuilder<SegmentSyncWorker>()
                    .setInputData(workDataOf(KEY_TRIGGER to trigger.id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
            // Kept rather than replaced: a refresh already running is doing the
            // same work, and restarting it would throw away the requests it has
            // already spent.
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * The standing daily check.
         *
         * For the one thing nothing else notices: a segment starred on the
         * website from a desk, with no ride and no phone involved. Everything
         * else has a trigger of its own, which is why this can afford to be as
         * infrequent as a day.
         */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<SegmentSyncWorker>(1, TimeUnit.DAYS)
                    .setInputData(workDataOf(KEY_TRIGGER to SyncTrigger.PERIODIC.id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
