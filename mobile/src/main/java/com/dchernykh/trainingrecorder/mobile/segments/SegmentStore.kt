package com.dchernykh.trainingrecorder.mobile.segments

import android.content.Context
import com.dchernykh.trainingrecorder.core.connector.StoredSegment
import com.dchernykh.trainingrecorder.core.datalayer.SegmentContract
import com.dchernykh.trainingrecorder.core.segment.Segment
import java.io.File

/**
 * The segments the phone has fetched, kept on disk.
 *
 * One file per segment, in the same wire format the watch reads. Nothing is
 * translated on the way through: what is stored is exactly what is published,
 * so a segment that survives the trip to disk survives the trip to the wrist,
 * and there is no third representation to get out of step with the other two.
 *
 * A segment's line is a fixed piece of road and never has to be fetched twice,
 * which is what makes keeping them worth the disk they take.
 */
class SegmentStore(
    private val directory: File,
    private val stateFile: File,
) {
    constructor(context: Context) : this(
        File(context.filesDir, "segments"),
        File(context.filesDir, "segment-sync.txt"),
    )

    /** Everything on disk, skipping anything that no longer decodes. */
    fun read(): List<Segment> =
        files().mapNotNull { file ->
            runCatching { SegmentContract.decode(file.readText()) }.getOrNull()
        }

    /** The same, reduced to what deciding the next refresh needs. */
    fun stored(): List<StoredSegment> =
        read().map { StoredSegment(it.id, hasLine = it.points.size >= 2, bestSeconds = it.referenceSeconds) }

    fun write(segment: Segment) {
        directory.mkdirs()
        val file = File(directory, "${segment.id}.json")
        val temporary = File(directory, "${segment.id}.json.part")
        temporary.writeText(SegmentContract.encode(segment))
        temporary.renameTo(file)
    }

    fun delete(id: Long) {
        File(directory, "$id.json").delete()
    }

    /**
     * When the phone last got a clean answer out of Strava.
     *
     * Zero when it never has, which every trigger reads as due - a phone that
     * has never synced should not wait a day to try.
     */
    fun lastSyncEpochMs(): Long = runCatching { stateFile.readText().trim().toLong() }.getOrDefault(0L)

    fun markSynced(atEpochMs: Long) {
        runCatching { stateFile.writeText(atEpochMs.toString()) }
    }

    private fun files(): List<File> = directory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
}
