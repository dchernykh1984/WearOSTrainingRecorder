package com.dchernykh.trainingrecorder.core.fit

import com.dchernykh.trainingrecorder.core.track.ClimbTotal

/**
 * A ride recovered from a journal that outlived the process that wrote it.
 *
 * [movingMillis] is carried rather than derived because it cannot be: the gap
 * between the first and last sample is elapsed time, and how much of it the rider
 * was actually moving is only known to the recording that was interrupted.
 */
data class RecoveredRide(
    val sportTypeId: String,
    val startedAtEpochMs: Long,
    val points: List<TrackPoint>,
    val movingMillis: Long,
) {
    /**
     * The ride as the encoder wants it.
     *
     * Elapsed time is measured to the last sample that reached the disk, not to
     * now: a watch that died at nine and is switched on at noon did not record a
     * three-hour ride.
     */
    fun toWorkout(): RecordedWorkout {
        val lastTimestamp = points.lastOrNull()?.timestampEpochMs ?: startedAtEpochMs
        val elapsedSeconds = ((lastTimestamp - startedAtEpochMs).coerceAtLeast(0)) / MILLIS_PER_SECOND
        val movingSeconds = (movingMillis / MILLIS_PER_SECOND).coerceIn(0.0, elapsedSeconds)
        val climbed = climb()
        return RecordedWorkout(
            sportTypeId = sportTypeId,
            startedAtEpochMs = startedAtEpochMs,
            totalTimerSeconds = movingSeconds,
            totalElapsedSeconds = elapsedSeconds,
            totalDistanceMeters = points.lastOrNull { it.distanceMeters != null }?.distanceMeters ?: 0.0,
            // Worked out again from the altitudes rather than carried in the
            // file. They are safe to add up: the journal only ever holds heights
            // above the sea, never a raw barometric reading, so there is no
            // calibration step in the series to be mistaken for a hill.
            totalAscentMeters = climbed.ascentMeters,
            totalDescentMeters = climbed.descentMeters,
            points = points,
        )
    }

    /**
     * The climb the recovered points describe.
     *
     * Safe to add up because the journal only ever holds heights above the sea:
     * a raw barometric reading is never written, so the series has no
     * calibration step in it to be mistaken for a hill.
     */
    private fun climb(): ClimbTotal = ClimbTotal().apply { points.mapNotNull { it.altitudeMeters }.forEach(::record) }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}

/**
 * The ride on disk while it is still being ridden.
 *
 * The samples used to live only in memory until the rider pressed Finish, which
 * made a process kill mid-ride lose the whole track - exactly the one failure
 * this app is not allowed to have. This is the wire format for an append-only
 * file written as each sample arrives: a header line naming the ride, then one
 * line per sample.
 *
 * Append-only and line-based on purpose. A watch that loses power writes no
 * footer and gets no chance to close the file, so the format has to be one where
 * whatever reached the disk is readable on its own and the last line - which may
 * be half-written - can simply be dropped. Rewriting a whole document per sample
 * would also mean a file write per second for six hours, which is a battery cost
 * the rider pays for nothing.
 *
 * Tab-separated rather than JSON per line because every byte here is written
 * while riding: the fields are fixed, positional and never nested, so the
 * structure a JSON object would carry is structure that is already known.
 */
object TrackJournal {
    /**
     * Bumped when the column order changes.
     *
     * A journal from any other version is refused rather than misread, in both
     * directions: the columns are positional, so a reader that guessed would
     * recover a ride with the altitude in the heart rate column, which is worse
     * than admitting the file cannot be read. Refusing an older one costs at
     * most a single interrupted ride, and only for a rider who is upgrading at
     * the exact moment they were interrupted.
     */
    const val VERSION = 1

    private const val SEPARATOR = '\t'
    private const val HEADER_TAG = "ride"
    private const val POINT_TAG = "p"

    /**
     * The last column of every line, and the only thing that proves the line
     * reached the disk whole.
     *
     * Counting columns is not enough: a line cut off part-way through its final
     * value still has the right number of them, and the half a number that
     * survives parses perfectly well as a smaller one. That is how a ride
     * recovered from a journal came to claim it had been moving for two minutes
     * when it had been moving for three hours.
     */
    private const val END = "."
    private const val HEADER_FIELDS = 5
    private const val POINT_FIELDS = 13

    /** Written once, when the recording starts. */
    fun header(
        sportTypeId: String,
        startedAtEpochMs: Long,
    ): String = listOf(HEADER_TAG, VERSION.toString(), sportTypeId, startedAtEpochMs.toString(), END).joinToString("\t")

    /**
     * One sample.
     *
     * [movingMillis] rides along on every line rather than being written once at
     * the end, because the end is the thing that may never happen.
     */
    fun line(
        point: TrackPoint,
        movingMillis: Long,
    ): String =
        listOf(
            POINT_TAG,
            point.timestampEpochMs.toString(),
            point.latitudeDeg.orBlank(),
            point.longitudeDeg.orBlank(),
            point.altitudeMeters.orBlank(),
            point.heartRateBpm.orBlank(),
            point.cadenceRpm.orBlank(),
            point.speedMps.orBlank(),
            point.powerWatts.orBlank(),
            point.distanceMeters.orBlank(),
            point.temperatureC.orBlank(),
            movingMillis.toString(),
            END,
        ).joinToString("\t")

    /**
     * Reads back whatever survived.
     *
     * Null when there is no usable header, because without one there is no sport
     * and no start time and the samples cannot be turned into a workout. A line
     * that does not parse is skipped rather than fatal: the last one is expected
     * to be truncated, and losing a second of a ride is not a reason to lose the
     * ride.
     */
    fun parse(lines: Sequence<String>): RecoveredRide? {
        var ride: Header? = null
        var unreadable = false
        var moving = 0L
        val points = mutableListOf<TrackPoint>()
        lines.forEach { raw ->
            val fields = raw.split(SEPARATOR)
            when (fields.firstOrNull()) {
                HEADER_TAG -> {
                    if (!isWhole(fields, HEADER_FIELDS)) return@forEach
                    if (fields[1].toIntOrNull() != VERSION) unreadable = true
                    ride = fields[3].toLongOrNull()?.let { Header(fields[2], it) }
                }

                POINT_TAG -> {
                    if (!isWhole(fields, POINT_FIELDS)) return@forEach
                    val timestamp = fields[1].toLongOrNull() ?: return@forEach
                    points += pointFrom(fields, timestamp)
                    moving = fields[11].toLongOrNull() ?: moving
                }
            }
        }
        return ride
            ?.takeUnless { unreadable }
            ?.let { RecoveredRide(it.sportTypeId, it.startedAtEpochMs, points.toList(), moving) }
    }

    /**
     * True when the line is all there.
     *
     * The marker is written last, so a line that has it is a line whose every
     * value was written before the process stopped.
     */
    private fun isWhole(
        fields: List<String>,
        expected: Int,
    ): Boolean = fields.size == expected && fields.last() == END

    /** What the first line names: which ride the samples below it belong to. */
    private data class Header(
        val sportTypeId: String,
        val startedAtEpochMs: Long,
    )

    private fun pointFrom(
        fields: List<String>,
        timestamp: Long,
    ) = TrackPoint(
        timestampEpochMs = timestamp,
        latitudeDeg = fields[2].toDoubleOrNull(),
        longitudeDeg = fields[3].toDoubleOrNull(),
        altitudeMeters = fields[4].toDoubleOrNull(),
        heartRateBpm = fields[5].toIntOrNull(),
        cadenceRpm = fields[6].toIntOrNull(),
        speedMps = fields[7].toDoubleOrNull(),
        powerWatts = fields[8].toIntOrNull(),
        distanceMeters = fields[9].toDoubleOrNull(),
        temperatureC = fields[10].toIntOrNull(),
    )

    /**
     * An absent reading is an empty column rather than the word "null", which
     * would parse back as a value on any reader that trusted it.
     */
    private fun Any?.orBlank(): String = this?.toString().orEmpty()
}
