package com.dchernykh.trainingrecorder.mobile.sync

import android.content.Context
import com.dchernykh.trainingrecorder.core.datalayer.SyncContract
import com.dchernykh.trainingrecorder.core.datalayer.WatchSettings
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Publishes the settings to the watch.
 *
 * The Data Layer keeps the item until a watch picks it up, so this works whether
 * or not one is connected right now - which is the whole reason configuration
 * lives on the phone and travels, rather than being typed on a watch screen.
 */
class SettingsPublisher(
    private val context: Context,
) {
    fun publish(settings: WatchSettings) {
        val request =
            PutDataMapRequest.create(WatchSettings.PATH).apply {
                dataMap.putString(KEY_PAYLOAD, SyncContract.encode(settings))
                // Without this the Data Layer drops an item whose bytes have not
                // changed, and re-sending the same settings after a reinstall
                // would silently do nothing.
                dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
    }

    private companion object {
        const val KEY_PAYLOAD = "payload"
        const val KEY_UPDATED_AT = "updatedAt"
    }
}
