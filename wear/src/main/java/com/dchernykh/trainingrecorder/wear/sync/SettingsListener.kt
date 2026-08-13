package com.dchernykh.trainingrecorder.wear.sync

import android.content.Context
import com.dchernykh.trainingrecorder.core.datalayer.SyncContract
import com.dchernykh.trainingrecorder.core.datalayer.WatchSettings
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Receives the settings the phone owns.
 *
 * A [WearableListenerService] rather than a listener on the activity: the phone
 * may push while the watch app is not running, and a change that only lands when
 * the rider happens to have the app open is not a setting, it is a coincidence.
 */
class SettingsListener : WearableListenerService() {
    override fun onDataChanged(events: com.google.android.gms.wearable.DataEventBuffer) {
        events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchSettings.PATH }
            .forEach { event ->
                val payload =
                    DataMapItem.fromDataItem(event.dataItem).dataMap.getString(SettingsStore.KEY_PAYLOAD)
                if (payload != null) SettingsStore(this).write(payload)
            }
    }
}

/**
 * The last settings the phone sent, kept on disk.
 *
 * Written whole and read whole: the payload is a few kilobytes, and a partial
 * write is worse than a stale one, so the file is replaced atomically. A payload
 * the contract refuses is discarded rather than stored, which is what lets an
 * older watch survive a newer phone.
 */
class SettingsStore(
    context: Context,
    private val file: File = File(context.filesDir, "settings.json"),
) {
    fun write(payload: String) {
        if (SyncContract.decode(payload) == null) return
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(payload)
        temporary.renameTo(file)
    }

    fun read(): WatchSettings? = if (file.exists()) SyncContract.decode(file.readText()) else null

    companion object {
        const val KEY_PAYLOAD = "payload"

        /**
         * Pulls whatever the phone last published.
         *
         * Needed on a fresh install: the listener only hears *changes*, so a
         * watch that installs after the phone published would otherwise sit on
         * defaults until the rider touched a setting.
         */
        suspend fun fetchExisting(context: Context): String? =
            runCatching {
                Wearable
                    .getDataClient(context)
                    .dataItems
                    .await()
                    .firstOrNull { it.uri.path == WatchSettings.PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap.getString(KEY_PAYLOAD) }
            }.getOrNull()
    }
}
