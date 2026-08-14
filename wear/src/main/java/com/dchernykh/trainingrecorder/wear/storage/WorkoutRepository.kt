package com.dchernykh.trainingrecorder.wear.storage

import android.content.Context
import com.dchernykh.trainingrecorder.core.fit.FitActivityEncoder
import com.dchernykh.trainingrecorder.core.fit.RecordedWorkout
import com.dchernykh.trainingrecorder.core.workout.RetentionPolicy
import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Finished workouts on the watch: a FIT file each, plus a small index beside
 * them.
 *
 * Files rather than a database because that is what the shape is - one immutable
 * blob per workout, written once and read back whole. A database would add a
 * schema to migrate for no gain, and the index is small enough that rewriting it
 * whole is cheaper than the machinery to update it in place.
 */
class WorkoutRepository(
    context: Context,
    private val directory: File = File(context.filesDir, "workouts"),
) {
    private val indexFile = File(directory, "index.json")

    /**
     * Every read-modify-write of the index goes through this. A recording thread
     * saving a workout and an upload worker marking one uploaded otherwise
     * clobber each other, and a lost "uploaded" mark means the file is sent
     * again - which Strava rejects as a duplicate and the queue then records as
     * permanently failed.
     */
    private val lock get() = INDEX_LOCK
    private val json = Json { ignoreUnknownKeys = true }

    init {
        directory.mkdirs()
    }

    fun fileFor(workoutId: String): File = File(directory, "$workoutId.fit")

    /** Writes the FIT file and records it, then trims to the retention budget. */
    fun save(
        workoutId: String,
        workout: RecordedWorkout,
        enabledConnectors: Set<String>,
    ): WorkoutSummary =
        synchronized(lock) {
            saveLocked(workoutId, workout, enabledConnectors)
        }

    private fun saveLocked(
        workoutId: String,
        workout: RecordedWorkout,
        enabledConnectors: Set<String>,
    ): WorkoutSummary {
        val target = fileFor(workoutId)
        // Written beside the target and moved into place, so a watch that dies
        // mid-write leaves no half-file that a later upload would happily send.
        val temporary = File(directory, "$workoutId.fit.part")
        FitActivityEncoder.encode(workout, temporary)
        check(temporary.renameTo(target)) { "could not move the finished workout into place" }

        // What the index already knows about this id, if anything. A ride
        // recovered from its journal is saved under the id its interrupted run
        // would have used, and that run may well have got as far as uploading:
        // starting its upload state from scratch would send a ride Strava
        // already has, be refused as a duplicate, and record it as failed.
        val known = loadIndex().firstOrNull { it.id == workoutId }
        val summary =
            WorkoutSummary(
                id = workoutId,
                sportTypeId = workout.sportTypeId,
                startedAtEpochMs = workout.startedAtEpochMs,
                durationSeconds = workout.totalTimerSeconds.toLong(),
                distanceMeters = workout.totalDistanceMeters,
                fileSizeBytes = target.length(),
                uploads = enabledConnectors.associateWith { UploadState.PENDING } + known?.uploads.orEmpty(),
                uploadAttempts = known?.uploadAttempts.orEmpty(),
                uploadAttemptedAt = known?.uploadAttemptedAt.orEmpty(),
                uploadReasons = known?.uploadReasons.orEmpty(),
            )
        // Replaced rather than appended, so saving the same id twice lands on
        // itself. A ride recovered from its journal is deliberately saved under
        // the id its interrupted run would have used, and a second row for it
        // would be a duplicate everywhere it is shown - including the phone's
        // history, whose list is keyed by id and crashes outright on a repeat.
        writeIndex(loadIndex().filterNot { it.id == workoutId } + summary)
        val index = loadIndex()
        val evicted = RetentionPolicy.evictable(index, enabledConnectors).toSet()
        if (evicted.isNotEmpty()) {
            writeIndex(index.filterNot { it.id in evicted })
            evicted.forEach { fileFor(it).delete() }
        }
        return summary
    }

    fun all(): List<WorkoutSummary> = synchronized(lock) { loadIndex().sortedByDescending { it.startedAtEpochMs } }

    fun markUploaded(
        workoutId: String,
        connectorId: String,
        state: UploadState,
        attempts: Int,
        attemptedAtEpochMs: Long,
        reason: String? = null,
    ) = synchronized(lock) {
        writeIndex(
            loadIndex().map {
                if (it.id != workoutId) {
                    it
                } else {
                    it.copy(
                        uploads = it.uploads + (connectorId to state),
                        uploadAttempts = it.uploadAttempts + (connectorId to attempts),
                        uploadAttemptedAt = it.uploadAttemptedAt + (connectorId to attemptedAtEpochMs),
                        // Cleared on success rather than left behind: an old
                        // explanation next to a delivered ride is worse than
                        // none, because it reads as a ride that failed.
                        uploadReasons =
                            if (reason == null) {
                                it.uploadReasons - connectorId
                            } else {
                                it.uploadReasons + (connectorId to reason)
                            },
                    )
                }
            },
        )
    }

    /**
     * Drops what the retention policy allows.
     *
     * The index is rewritten *before* the files are removed. The other order
     * leaves entries pointing at files that are gone if the write is interrupted,
     * and an upload then opens a missing file; this way the worst case is an
     * orphaned file, which costs space and nothing else.
     */
    fun prune(enabledConnectors: Set<String>) =
        synchronized(lock) {
            val index = loadIndex()
            val evicted = RetentionPolicy.evictable(index, enabledConnectors).toSet()
            if (evicted.isNotEmpty()) {
                writeIndex(index.filterNot { it.id in evicted })
                evicted.forEach { fileFor(it).delete() }
            }
        }

    /**
     * Throws rather than returning an empty list when the index is unreadable.
     *
     * Swallowing a parse failure is worse than crashing: the next save would
     * rewrite the index with one entry and orphan every workout recorded before
     * it. A corrupt index is rare and recoverable; silently discarding a season
     * of rides is neither.
     */
    private fun loadIndex(): List<WorkoutSummary> {
        if (!indexFile.exists()) return emptyList()
        val root =
            json.parseToJsonElement(indexFile.readText()) as? JsonArray
                ?: error("the workout index is not a JSON array")
        return root.map { node ->
            val entry = node as? JsonObject ?: error("a workout index entry is not an object")
            toSummary(entry) ?: error("a workout index entry could not be read")
        }
    }

    private fun toSummary(node: JsonObject): WorkoutSummary? {
        val id = text(node, "id") ?: return null
        val uploads =
            (node["uploads"] as? JsonObject)
                ?.mapNotNull { (key, value) ->
                    UploadState.byId((value as? JsonPrimitive)?.content.orEmpty())?.let { key to it }
                }?.toMap()
                .orEmpty()
        return runCatching {
            WorkoutSummary(
                id = id,
                sportTypeId = text(node, "sport").orEmpty(),
                startedAtEpochMs = number(node, "startedAt")?.toLong() ?: return null,
                durationSeconds = number(node, "duration")?.toLong() ?: 0,
                distanceMeters = number(node, "distance") ?: 0.0,
                fileSizeBytes = number(node, "bytes")?.toLong() ?: 0,
                uploads = uploads,
                uploadAttempts =
                    (node["attempts"] as? JsonObject)
                        ?.mapNotNull { (key, value) ->
                            (value as? JsonPrimitive)?.content?.toIntOrNull()?.let { key to it }
                        }?.toMap()
                        .orEmpty(),
                uploadAttemptedAt =
                    (node["attemptedAt"] as? JsonObject)
                        ?.mapNotNull { (key, value) ->
                            (value as? JsonPrimitive)?.content?.toLongOrNull()?.let { key to it }
                        }?.toMap()
                        .orEmpty(),
                uploadReasons =
                    (node["reasons"] as? JsonObject)
                        ?.mapNotNull { (key, value) ->
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content }
                        }?.toMap()
                        .orEmpty(),
            )
        }.getOrNull()
    }

    private fun writeIndex(summaries: List<WorkoutSummary>) {
        val payload =
            buildJsonArray {
                summaries.forEach { summary ->
                    add(
                        buildJsonObject {
                            put("id", summary.id)
                            put("sport", summary.sportTypeId)
                            put("startedAt", summary.startedAtEpochMs)
                            put("duration", summary.durationSeconds)
                            put("distance", summary.distanceMeters)
                            put("bytes", summary.fileSizeBytes)
                            put(
                                "uploads",
                                buildJsonObject { summary.uploads.forEach { (k, v) -> put(k, v.id) } },
                            )
                            put(
                                "attempts",
                                buildJsonObject { summary.uploadAttempts.forEach { (k, v) -> put(k, v) } },
                            )
                            put(
                                "attemptedAt",
                                buildJsonObject { summary.uploadAttemptedAt.forEach { (k, v) -> put(k, v) } },
                            )
                            put(
                                "reasons",
                                buildJsonObject { summary.uploadReasons.forEach { (k, v) -> put(k, v) } },
                            )
                        },
                    )
                }
            }
        val temporary = File(directory, "index.json.part")
        temporary.writeText(payload.toString())
        check(temporary.renameTo(indexFile)) { "could not replace the workout index" }
    }

    private fun text(
        node: JsonObject,
        key: String,
    ): String? = (node[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        /**
         * Shared, not per-instance: the recording service and the upload worker
         * each build their own repository, and a per-instance lock would serialise
         * neither of the two callers it exists for.
         */
        val INDEX_LOCK = Any()
    }

    private fun number(
        node: JsonObject,
        key: String,
    ): Double? = (node[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
}
