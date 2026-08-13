package com.dchernykh.trainingrecorder.mobile.sync

import android.content.Context
import android.net.Uri
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
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

    /**
     * Sends the rider's own service credentials to the watch, so an upload can
     * happen while the phone is nowhere near.
     *
     * On its own path rather than folded into the settings, for the reason
     * [CredentialContract] gives: settings get copied around, and a token that
     * travels with them is a token that leaks.
     */
    fun publishCredentials(credentials: Map<String, Map<String, String>>) {
        val request =
            PutDataMapRequest.create(CredentialContract.PATH).apply {
                dataMap.putString(CredentialContract.KEY_PAYLOAD, CredentialContract.encode(credentials))
                dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
    }

    /**
     * Withdraws them again. Disconnecting a service on the phone has to reach
     * the watch, or it keeps uploading with a token the rider revoked.
     */
    fun clearCredentials() {
        Wearable.getDataClient(context).deleteDataItems(
            Uri.parse("wear://*${CredentialContract.PATH}"),
        )
    }

    private companion object {
        const val KEY_PAYLOAD = "payload"
        const val KEY_UPDATED_AT = "updatedAt"
    }
}
