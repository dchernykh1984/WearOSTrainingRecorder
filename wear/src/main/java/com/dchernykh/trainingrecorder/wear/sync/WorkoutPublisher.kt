package com.dchernykh.trainingrecorder.wear.sync

import android.content.Context
import com.dchernykh.trainingrecorder.core.datalayer.WorkoutSummaryContract
import com.dchernykh.trainingrecorder.wear.storage.WorkoutRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Tells the phone what the watch has recorded.
 *
 * The one thing that travels watch-to-phone. Published whole rather than as
 * changes: the list is fifty rows of a few fields, the Data Layer keeps only the
 * latest item per path anyway, and a phone that missed an update would otherwise
 * need a protocol to ask what it had missed.
 *
 * Called after a ride is saved and after every upload attempt settles, because
 * those are the two moments the answer changes - a new ride appears, or one that
 * said "waiting" now says "uploaded".
 */
class WorkoutPublisher(
    private val context: Context,
) {
    /**
     * Sends the current list, or does nothing if it cannot.
     *
     * Failures are swallowed: this is a convenience for a screen on another
     * device, and a watch with no phone in range must not fail a save or an
     * upload over it.
     */
    fun publish(repository: WorkoutRepository) {
        runCatching {
            val payload = WorkoutSummaryContract.encode(repository.all())
            val request =
                PutDataMapRequest.create(WorkoutSummaryContract.PATH).apply {
                    dataMap.putString(WorkoutSummaryContract.KEY_PAYLOAD, payload)
                    // The Data Layer drops an item whose bytes have not changed,
                    // and two rides can be identical in everything this carries -
                    // same sport, same round distance, same upload state. Without
                    // a changing field the second one would never be delivered.
                    dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                }
            // Urgent: the rider who just finished a ride is the rider most likely
            // to pick up the phone and look for it. The default delivery window
            // is measured in tens of minutes, which reads as the ride not having
            // been recorded at all.
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
        }
    }

    private companion object {
        const val KEY_UPDATED_AT = "updatedAt"
    }
}
