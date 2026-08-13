package com.dchernykh.trainingrecorder.wear.upload

import android.content.Context
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
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
    fun write(payload: String) {
        // Parsed before it replaces anything: a payload we cannot read would
        // otherwise wipe working credentials and leave every upload stuck.
        if (CredentialContract.decode(payload) == null) return
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(payload)
        // Narrowed before it is moved into place, so the file is never briefly
        // world-readable under its final name.
        temporary.setReadable(false, false)
        temporary.setReadable(true, true)
        temporary.renameTo(file)
    }

    /** Per connector id, the credential map that connector expects. */
    fun read(): Map<String, Map<String, String>> =
        if (file.exists()) CredentialContract.decode(file.readText()).orEmpty() else emptyMap()

    fun clear() {
        file.delete()
    }
}
