package com.dchernykh.trainingrecorder.core.workout

/** How a workout stands with one storage service. */
enum class UploadState(
    val id: String,
) {
    PENDING("pending"),
    UPLOADED("uploaded"),
    FAILED("failed"),
    ;

    companion object {
        fun byId(id: String): UploadState? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What the watch keeps about a finished workout. The samples live in a FIT file
 * on disk; this is the index entry beside it.
 */
data class WorkoutSummary(
    val id: String,
    val sportTypeId: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val fileSizeBytes: Long,
    val uploads: Map<String, UploadState> = emptyMap(),
    /**
     * How many times each service has been tried. Persisted with the workout
     * because the queue is rebuilt from these entries: held only in memory, the
     * count would reset on every restart, `hasGivenUp` would never fire, and a
     * service that is down would keep a workout pending - and therefore
     * undeletable - forever.
     */
    val uploadAttempts: Map<String, Int> = emptyMap(),
    /**
     * When each service was last tried. Persisted alongside the count because
     * the backoff is meaningless without it: re-serving the delay from the
     * moment the queue is rebuilt means it has never elapsed by the time the
     * same pass asks what is due, so a single failure would strand the workout
     * forever - pending, unretried, and therefore undeletable.
     */
    val uploadAttemptedAt: Map<String, Long> = emptyMap(),
    /**
     * What each service said last time, when it did not take the ride.
     *
     * Kept because "waiting to upload" on its own is unactionable: it looks the
     * same whether the watch has no signal, the token has expired, or the
     * service is refusing every request. The rider cannot read a logcat, so the
     * one place this can be shown is the phone's history - which means it has
     * to be written down here first.
     */
    val uploadReasons: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "a workout needs an id" }
        require(durationSeconds >= 0) { "duration cannot be negative" }
        require(distanceMeters >= 0) { "distance cannot be negative" }
        require(fileSizeBytes >= 0) { "file size cannot be negative" }
    }

    /**
     * True when every enabled service has reached a terminal answer.
     *
     * [UploadState.FAILED] counts as terminal on purpose. A service that has
     * refused a file, or that has been tried until the queue gave up, is never
     * going to take it - and keeping the workout forever in the hope that it
     * might both strands the ride and fills the watch. Only [UploadState.PENDING]
     * and a service that has not been offered the workout at all hold it back.
     */
    fun isSafeToDelete(enabledConnectors: Set<String>): Boolean =
        enabledConnectors.all {
            when (uploads[it]) {
                UploadState.UPLOADED, UploadState.FAILED -> true
                UploadState.PENDING, null -> false
            }
        }
}

/**
 * Decides what to drop when the watch fills up.
 *
 * Two rules, in order. A workout that has not reached every enabled service is
 * never dropped, however old it is - losing an unsynced ride is the one failure
 * this app must not have. Everything else is trimmed oldest-first until both the
 * count and the byte budget fit.
 *
 * The numbers: a two-hour ride sampled once a second is roughly a quarter of a
 * megabyte of FIT, so two hundred workouts is about fifty megabytes in normal
 * use, and the 256 MB ceiling is what stops a season of six-hour days from
 * filling a watch.
 */
object RetentionPolicy {
    const val MAX_KEPT = 200
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    /** Ids to delete, oldest first. */
    fun evictable(
        workouts: List<WorkoutSummary>,
        enabledConnectors: Set<String>,
        maxKept: Int = MAX_KEPT,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ): List<String> {
        require(maxKept >= MIN_KEPT) { "keeping fewer than $MIN_KEPT workouts is not sensible, got $maxKept" }
        require(maxTotalBytes > 0) { "the byte budget must be positive" }

        val newestFirst = workouts.sortedByDescending { it.startedAtEpochMs }
        val evicted = mutableListOf<String>()
        var kept = 0
        var bytes = 0L
        // Strictly oldest-first: once either budget is spent, everything older
        // goes. Skipping a workout that does not fit and keeping smaller older
        // ones would be best-fit, which reads as the watch deleting the ride the
        // rider just finished while a month-old one survives.
        //
        // The newest is kept whatever it costs. A single ride larger than the
        // whole budget is a reason to be over budget, not a reason to delete the
        // ride that was just recorded.
        var full = false
        newestFirst.forEachIndexed { position, workout ->
            val newest = position == 0
            full = full || (!newest && (kept >= maxKept || bytes + workout.fileSizeBytes > maxTotalBytes))
            if (full && workout.isSafeToDelete(enabledConnectors)) {
                evicted += workout.id
            } else {
                kept++
                bytes += workout.fileSizeBytes
            }
        }
        return evicted.reversed()
    }

    const val MIN_KEPT = 50
}
