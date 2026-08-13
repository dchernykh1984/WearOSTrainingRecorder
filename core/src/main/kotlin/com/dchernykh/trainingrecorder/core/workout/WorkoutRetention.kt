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
) {
    init {
        require(id.isNotBlank()) { "a workout needs an id" }
        require(durationSeconds >= 0) { "duration cannot be negative" }
        require(distanceMeters >= 0) { "distance cannot be negative" }
        require(fileSizeBytes >= 0) { "file size cannot be negative" }
    }

    /**
     * True only when every service the rider enabled has taken it. A workout
     * that no service has claimed yet is never safe to delete.
     */
    fun isSafeToDelete(enabledConnectors: Set<String>): Boolean =
        enabledConnectors.all { uploads[it] == UploadState.UPLOADED }
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
        newestFirst.forEach { workout ->
            val overCount = kept >= maxKept
            val overBytes = bytes + workout.fileSizeBytes > maxTotalBytes
            if ((overCount || overBytes) && workout.isSafeToDelete(enabledConnectors)) {
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
