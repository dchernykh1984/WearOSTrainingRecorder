package com.dchernykh.trainingrecorder.mobile.connect

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.GarminReauthorization
import com.dchernykh.trainingrecorder.core.connector.ReauthOutcome
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SettingsPublisher
import com.dchernykh.trainingrecorder.mobile.sync.WorkoutHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Signs in to Garmin again when the watch reports a dead session.
 *
 * The watch refreshes its own token and retries a refusal with a fresh one. It
 * cannot do more than that: a dead refresh token is replaced only by a full
 * sign-in, and a full sign-in needs the login and password, which stay on the
 * phone by design and are never sent to the watch. So the watch reports what
 * happened and the phone is the only thing that can act on it.
 *
 * What makes this worth doing automatically is the shape of the failure. A
 * token expiring weeks after the rider connected Garmin is not an event they
 * caused or can predict, and it used to cost them every ride recorded
 * afterwards until they happened to look at the history and reconnect by hand.
 *
 * Fresh tokens are published the ordinary way, and the watch does the rest: new
 * credentials arriving are what put the rides it had given up on back in the
 * queue.
 */
class GarminReauthWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val store = PhoneSettingsStore(applicationContext)
            val credentials = store.readCredentials()[GarminProtocol.ID].orEmpty()
            val login = credentials[GarminProtocol.LOGIN].orEmpty()
            val password = credentials[GarminProtocol.PASSWORD].orEmpty()
            val (lastAttempt, lastOutcome) = store.readReauth()

            val due =
                GarminReauthorization.shouldSignIn(
                    workouts = history(),
                    lastAttemptEpochMs = lastAttempt,
                    lastOutcome = lastOutcome,
                    nowEpochMs = System.currentTimeMillis(),
                    hasCredentials = login.isNotBlank() && password.isNotBlank(),
                )
            if (!due) return@withContext Result.success()

            // Written down before the result is known. A sign-in that takes the
            // process down with it would otherwise leave no trace, and the next
            // trigger would try again immediately - which is the loop the
            // interval exists to prevent.
            store.writeReauth(System.currentTimeMillis(), ReauthOutcome.FAILED)
            record(store, GarminAuthorization().signIn(login, password))
        }

    /** Writes down how it went, and acts on it when it went well. */
    private fun record(
        store: PhoneSettingsStore,
        result: GarminAuthResult,
    ): Result {
        val outcome =
            when (result) {
                is GarminAuthResult.Authorized -> ReauthOutcome.SIGNED_IN
                // Garmin has just sent the rider a code they did not ask for.
                // That is the price of finding out that this account has a
                // second factor; it is paid once, because this outcome stops
                // any further attempt, and the Services screen then says where
                // the message came from.
                is GarminAuthResult.NeedsCode -> ReauthOutcome.NEEDS_CODE
                is GarminAuthResult.WrongCredentials -> ReauthOutcome.WRONG_CREDENTIALS
                is GarminAuthResult.Failed -> ReauthOutcome.FAILED
            }
        store.writeReauth(System.currentTimeMillis(), outcome)
        if (result is GarminAuthResult.Authorized) {
            val stored = store.readCredentials()
            val merged = stored + (GarminProtocol.ID to (stored[GarminProtocol.ID].orEmpty() + result.tokens))
            store.writeCredentials(merged)
            // Only what the watch is allowed to see. The login and password are
            // the whole reason this runs here rather than there.
            SettingsPublisher(applicationContext).publishCredentials(CredentialContract.publishable(merged))
        }
        // Never retried by WorkManager. Whether another attempt is worth making
        // is the interval's decision and nobody else's, and a retry chain would
        // be a second opinion on it running on a different clock.
        return Result.success()
    }

    private fun history(): List<WorkoutSummary> =
        runCatching { WorkoutHistoryStore(applicationContext).read() }.getOrDefault(emptyList())

    companion object {
        private const val WORK_NAME = "garmin-reauth"
        private const val BACKOFF_MINUTES = 30L

        /**
         * Asked for whenever the watch says something that might mean a dead
         * session. Whether anything actually happens is
         * [GarminReauthorization.shouldSignIn]'s decision - this is cheap to
         * enqueue and the watch publishes its history often.
         */
        fun consider(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<GarminReauthWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
