package com.dchernykh.trainingrecorder.wear.upload

import android.content.Context
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * The access tokens the watch needs to upload, keyed by connector.
 *
 * These are the rider's own credentials for their own accounts - the app ships
 * no keys of its own. They arrive from the phone, which is the only place they
 * are ever entered, and they are kept here because an upload has to work while
 * the phone is at home in a drawer.
 *
 * Stored in the app's private directory, which on Wear OS is covered by
 * file-based encryption and unreadable by other apps. The file is kept separate
 * from the settings for the reason [CredentialContract] gives: settings travel,
 * credentials must not travel with them.
 */
class CredentialStore(
    context: Context,
    private val file: File = File(context.filesDir, "credentials.json"),
) {
    /**
     * Stores what the phone sent, and says which services it changed anything
     * for.
     *
     * The answer matters because a service coming back is what lets the upload
     * queue offer it rides it had written off, and that must happen on a genuine
     * reconnection and not on every delivery. The phone stamps each publish with
     * the time, so the Data Layer hands the watch a fresh item whenever any
     * credential is saved - identical tokens included. Reinstating on the item
     * rather than on the contents would hand a permanently broken service ten
     * more attempts every time the rider touched an unrelated setting, which is
     * the retry loop the give-up rule exists to stop.
     */
    fun write(payload: String): Set<String> {
        // Parsed before it replaces anything: a payload we cannot read would
        // otherwise wipe working credentials and leave every upload stuck.
        val incoming = CredentialContract.decode(payload) ?: return emptySet()
        val previous = read()
        val changed = incoming.filter { (connectorId, fields) -> previous[connectorId] != fields }.keys
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(payload)
        // Narrowed before it is moved into place, so the file is never briefly
        // world-readable under its final name.
        temporary.setReadable(false, false)
        temporary.setReadable(true, true)
        temporary.renameTo(file)
        return changed
    }

    /** Per connector id, the credential map that connector expects. */
    fun read(): Map<String, Map<String, String>> =
        if (file.exists()) CredentialContract.decode(file.readText()).orEmpty() else emptyMap()

    fun clear() {
        file.delete()
    }

    companion object {
        /**
         * Pulls whatever the phone last published.
         *
         * Needed on a fresh install: the listener only hears *changes*, so a
         * watch set up after the rider connected their services would otherwise
         * have no token and quietly upload nothing.
         */
        suspend fun fetchExisting(context: Context): String? =
            runCatching {
                Wearable
                    .getDataClient(context)
                    .dataItems
                    .await()
                    .firstOrNull { it.uri.path == CredentialContract.PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap.getString(CredentialContract.KEY_PAYLOAD) }
            }.getOrNull()
    }
}
