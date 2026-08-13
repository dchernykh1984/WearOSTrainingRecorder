package com.dchernykh.trainingrecorder.mobile.settings

import android.content.Context
import com.dchernykh.trainingrecorder.core.connector.CredentialContract
import com.dchernykh.trainingrecorder.core.datalayer.SyncContract
import com.dchernykh.trainingrecorder.core.datalayer.WatchSettings
import java.io.File

/**
 * What the phone remembers between launches.
 *
 * The phone owns the configuration, so this is the master copy - the watch's
 * file is a replica it can rebuild from. Two files rather than one, so that
 * credentials never travel in the same payload as the settings; the reason is
 * spelled out in [CredentialContract].
 */
class PhoneSettingsStore(
    context: Context,
    private val settingsFile: File = File(context.filesDir, "settings.json"),
    private val credentialsFile: File = File(context.filesDir, "credentials.json"),
) {
    fun readSettings(): WatchSettings? =
        if (settingsFile.exists()) SyncContract.decode(settingsFile.readText()) else null

    fun writeSettings(settings: WatchSettings) {
        replace(settingsFile, SyncContract.encode(settings))
    }

    fun readCredentials(): Map<String, Map<String, String>> =
        if (credentialsFile.exists()) CredentialContract.decode(credentialsFile.readText()).orEmpty() else emptyMap()

    fun writeCredentials(credentials: Map<String, Map<String, String>>) {
        replace(credentialsFile, CredentialContract.encode(credentials), private = true)
    }

    fun clearCredentials() {
        credentialsFile.delete()
    }

    /**
     * Written beside the target and moved into place. A phone killed mid-write
     * otherwise leaves a truncated file, and the next launch reads it as "no
     * configuration" and quietly resets every screen the rider set up.
     */
    private fun replace(
        target: File,
        payload: String,
        private: Boolean = false,
    ) {
        val temporary = File(target.parentFile, target.name + ".part")
        temporary.writeText(payload)
        if (private) {
            temporary.setReadable(false, false)
            temporary.setReadable(true, true)
        }
        // Checked, because a failed rename leaves the stale file in place while
        // looking like a successful write - and the rider's edit is then quietly
        // gone on the next launch, which is exactly what this dance prevents.
        check(temporary.renameTo(target)) { "could not replace ${target.name}" }
    }
}
