package com.dchernykh.trainingrecorder.wear.storage

import android.content.Context
import com.dchernykh.trainingrecorder.core.fit.RecoveredRide
import com.dchernykh.trainingrecorder.core.fit.TrackJournal
import com.dchernykh.trainingrecorder.core.fit.TrackPoint
import java.io.File
import java.io.FileOutputStream

/**
 * The file a ride is written to while it is being ridden.
 *
 * The samples used to be held in a list until the rider pressed Finish, so a
 * process the system killed - or a watch that simply ran out of battery - took
 * the whole track with it. This appends each sample as it arrives, and
 * [recover] turns whatever reached the disk back into a workout on the next
 * launch.
 *
 * **How far the durability actually goes.** Every line is flushed out of the
 * process immediately, so a killed app loses nothing: the bytes are already the
 * operating system's. Surviving a battery pulled out mid-sentence needs more
 * than that - the data has to be forced through the kernel's cache onto flash -
 * and doing that once a second for six hours is a real battery and flash-wear
 * cost for a failure that is rare. So the file is synced every
 * [SYNC_EVERY_SAMPLES] samples instead, which bounds what a hard power loss can
 * take to the last half minute rather than the whole ride.
 */
class TrackJournalStore(
    context: Context,
    private val file: File = File(context.filesDir, "ride-journal.tsv"),
) {
    private var stream: FileOutputStream? = null
    private var sinceSync = 0

    /**
     * Starts a new journal, replacing whatever was there.
     *
     * Replacing rather than appending matters: a journal left by a ride that was
     * recovered on the last launch would otherwise have this ride's samples
     * written under its header, and the two would come back as one impossible
     * workout.
     */
    @Synchronized
    fun begin(
        sportTypeId: String,
        startedAtEpochMs: Long,
    ) {
        close()
        runCatching {
            file.parentFile?.mkdirs()
            val opened = FileOutputStream(file, false)
            opened.write((TrackJournal.header(sportTypeId, startedAtEpochMs) + "\n").toByteArray())
            opened.flush()
            opened.fd.sync()
            stream = opened
            sinceSync = 0
        }
    }

    /**
     * Records one sample.
     *
     * Failures are swallowed on purpose. The journal is insurance, and a full
     * disk or a closed descriptor must not take down the ride it exists to
     * protect - the in-memory samples are still there and the rider is still
     * riding.
     */
    @Synchronized
    fun append(
        point: TrackPoint,
        movingMillis: Long,
    ) {
        val open = stream ?: return
        runCatching {
            open.write((TrackJournal.line(point, movingMillis) + "\n").toByteArray())
            open.flush()
            sinceSync++
            if (sinceSync >= SYNC_EVERY_SAMPLES) {
                open.fd.sync()
                sinceSync = 0
            }
        }
    }

    /** Closes the journal and drops it, once the ride is safely a FIT file. */
    @Synchronized
    fun finish() {
        close()
        runCatching { file.delete() }
    }

    /**
     * The ride left behind by a run that never got to finish, if there is one.
     *
     * A journal with no samples is not offered back: it is what an interrupted
     * *start* leaves - the rider tapped a sport, the watch died before the first
     * fix - and recovering it would put a zero-length workout in the history and
     * send it to Strava.
     */
    @Synchronized
    fun recover(): RecoveredRide? {
        if (!file.exists()) return null
        return runCatching {
            file.useLines { TrackJournal.parse(it) }
        }.getOrNull()?.takeIf { it.points.isNotEmpty() }
    }

    private fun close() {
        runCatching { stream?.close() }
        stream = null
    }

    private companion object {
        /**
         * Half a minute at one sample a second. Chosen as the most a rider would
         * accept losing to a dead battery, weighed against writing to flash every
         * second of every ride.
         */
        const val SYNC_EVERY_SAMPLES = 30
    }
}
