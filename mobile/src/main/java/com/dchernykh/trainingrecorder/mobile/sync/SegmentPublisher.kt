package com.dchernykh.trainingrecorder.mobile.sync

import android.content.Context
import android.net.Uri
import com.dchernykh.trainingrecorder.core.datalayer.SegmentContract
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Sends segments to the watch, one Data Layer item each.
 *
 * Not urgent, unlike the settings. A settings change is the rider watching the
 * phone and waiting for the watch to agree; a segment is for a ride that has
 * not started, and letting the Data Layer send it on its own schedule saves the
 * battery a wake-up per segment on the first sync.
 */
class SegmentPublisher(
    private val context: Context,
) {
    fun publish(segment: Segment) {
        val request =
            PutDataMapRequest.create(SegmentContract.path(segment.id)).apply {
                dataMap.putString(SegmentContract.KEY_PAYLOAD, SegmentContract.encode(segment))
                // The Data Layer drops an item whose bytes are unchanged, so a
                // segment republished after the watch was reinstalled would
                // silently never arrive.
                dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest())
    }

    /** Unstarring a segment on Strava has to take it off the watch too. */
    fun remove(id: Long) {
        Wearable.getDataClient(context).deleteDataItems(Uri.parse("wear://*${SegmentContract.path(id)}"))
    }

    private companion object {
        const val KEY_UPDATED_AT = "updatedAt"
    }
}
