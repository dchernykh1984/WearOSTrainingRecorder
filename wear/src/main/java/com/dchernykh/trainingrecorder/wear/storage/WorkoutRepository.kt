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
    ): WorkoutSummary {
        val target = fileFor(workoutId)
        // Written beside the target and moved into place, so a watch that dies
        // mid-write leaves no half-file that a later upload would happily send.
        val temporary = File(directory, "$workoutId.fit.part")
        FitActivityEncoder.encode(workout, temporary)
        check(temporary.renameTo(target)) { "could not move the finished workout into place" }

        val summary =
            WorkoutSummary(
                id = workoutId,
                sportTypeId = workout.sportTypeId,
                startedAtEpochMs = workout.startedAtEpochMs,
                durationSeconds = workout.totalTimerSeconds.toLong(),
                distanceMeters = workout.totalDistanceMeters,
                fileSizeBytes = target.length(),
                uploads = enabledConnectors.associateWith { UploadState.PENDING },
            )
        writeIndex(loadIndex() + summary)
        prune(enabledConnectors)
        return summary
    }

    fun all(): List<WorkoutSummary> = loadIndex().sortedByDescending { it.startedAtEpochMs }

    fun markUploaded(
        workoutId: String,
        connectorId: String,
        state: UploadState,
    ) {
        writeIndex(
            loadIndex().map {
                if (it.id == workoutId) it.copy(uploads = it.uploads + (connectorId to state)) else it
            },
        )
    }

    /** Drops what the retention policy allows, files and index entries together. */
    fun prune(enabledConnectors: Set<String>) {
        val index = loadIndex()
        val evicted = RetentionPolicy.evictable(index, enabledConnectors).toSet()
        if (evicted.isEmpty()) return
        evicted.forEach { fileFor(it).delete() }
        writeIndex(index.filterNot { it.id in evicted })
    }

    private fun loadIndex(): List<WorkoutSummary> {
        if (!indexFile.exists()) return emptyList()
        val root =
            runCatching { json.parseToJsonElement(indexFile.readText()) as? JsonArray }.getOrNull()
                ?: return emptyList()
        return root.mapNotNull { it as? JsonObject }.mapNotNull(::toSummary)
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

    private fun number(
        node: JsonObject,
        key: String,
    ): Double? = (node[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
}
