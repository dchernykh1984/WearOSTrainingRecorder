package com.dchernykh.trainingrecorder.wear.sync

import android.content.Context
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
import com.dchernykh.trainingrecorder.core.datalayer.SyncContract
import com.dchernykh.trainingrecorder.core.datalayer.WatchSettings
import com.dchernykh.trainingrecorder.wear.upload.CredentialStore
import com.dchernykh.trainingrecorder.wear.upload.UploadWorker
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
        events.forEach { event ->
            // A deletion is how the phone revokes credentials, and handling only
            // TYPE_CHANGED would leave the watch uploading with a token the
            // rider has just disconnected.
            if (event.type == DataEvent.TYPE_DELETED) {
                if (event.dataItem.uri.path == CredentialContract.PATH) CredentialStore(this).clear()
                return@forEach
            }
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (event.dataItem.uri.path) {
                WatchSettings.PATH ->
                    dataMap.getString(SettingsStore.KEY_PAYLOAD)?.let { SettingsStore(this).write(it) }
                CredentialContract.PATH ->
                    dataMap.getString(CredentialContract.KEY_PAYLOAD)?.let {
                        CredentialStore(this).write(it)
                        // A service the rider just connected has a backlog
                        // waiting for it: everything recorded before they got
                        // round to setting it up.
                        UploadWorker.schedule(this)
                    }
            }
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
    private val historyFile: File = File(context.filesDir, "sport-history.txt"),
) {
    fun write(payload: String) {
        if (SyncContract.decode(payload) == null) return
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(payload)
        temporary.renameTo(file)
    }

    fun read(): WatchSettings? = if (file.exists()) SyncContract.decode(file.readText()) else null

    /**
     * Which sports were used most recently, oldest first.
     *
     * Kept here rather than in the view model because the picker's whole promise
     * is that the sport of the last ride is at the top - which is worth nothing
     * if it is forgotten the moment the app closes.
     */
    fun readHistory(): List<String> =
        if (historyFile.exists()) {
            historyFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

    fun writeHistory(sportTypeIds: List<String>) {
        val temporary = File(historyFile.parentFile, historyFile.name + ".part")
        temporary.writeText(sportTypeIds.joinToString("\n"))
        temporary.renameTo(historyFile)
    }

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
