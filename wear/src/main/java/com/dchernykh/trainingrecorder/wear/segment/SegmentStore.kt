package com.dchernykh.trainingrecorder.wear.segment

import android.content.Context
import com.dchernykh.trainingrecorder.core.datalayer.SegmentContract
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * The segments the phone has sent, kept on the watch.
 *
 * Kept rather than held in memory because the whole point of them is a ride
 * with no phone and no signal: whatever the watch is going to race the rider
 * against has to be on the watch before the ride starts, and still be there
 * after the app has been closed and reopened at the trailhead.
 *
 * One file per segment, holding exactly the payload that arrived. A payload the
 * contract refuses is not stored, which is what lets an older watch sit beside
 * a phone that has been updated past it.
 */
class SegmentStore(
    private val directory: File,
) {
    constructor(context: Context) : this(File(context.filesDir, "segments"))

    fun read(): List<Segment> =
        directory
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(SUFFIX) }
            ?.mapNotNull { file -> runCatching { SegmentContract.decode(file.readText()) }.getOrNull() }
            .orEmpty()

    /** Stores what arrived, or nothing at all if it cannot be read. */
    fun write(payload: String) {
        val segment = SegmentContract.decode(payload) ?: return
        directory.mkdirs()
        val file = File(directory, "${segment.id}$SUFFIX")
        val temporary = File(directory, "${segment.id}$SUFFIX.part")
        temporary.writeText(payload)
        temporary.renameTo(file)
    }

    /** Unstarred on Strava, so it stops being something the watch times. */
    fun delete(id: Long) {
        File(directory, "$id$SUFFIX").delete()
    }

    companion object {
        private const val SUFFIX = ".json"

        /**
         * Pulls whatever the phone has already published.
         *
         * Needed for the same reason the settings need it: the listener only
         * hears *changes*, so a watch reinstalled after the segments were sent
         * would otherwise have none until the rider starred something new.
         */
        suspend fun fetchExisting(context: Context): List<String> =
            runCatching {
                Wearable
                    .getDataClient(context)
                    .dataItems
                    .await()
                    .filter { SegmentContract.idFrom(it.uri.path) != null }
                    .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getString(SegmentContract.KEY_PAYLOAD) }
            }.getOrDefault(emptyList())
    }
}
