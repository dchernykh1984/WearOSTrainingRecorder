package com.dchernykh.trainingrecorder.core.datalayer

import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the watch tells the phone about the rides it holds.
 *
 * The only thing that travels watch-to-phone. Everything else on the Data Layer
 * goes the other way - the phone owns the configuration and the credentials, the
 * watch consumes them - but the workouts exist only on the watch, and the history
 * screen the rider asked for is on the phone. Without this path that screen is
 * written, rendered, and permanently empty.
 *
 * Summaries only, never the FIT files. The phone is not a second copy of the
 * rides: it shows what was recorded and whether it has reached the services, and
 * pushing megabytes of track over Bluetooth to answer that question would cost
 * the rider's battery for something no screen displays.
 */
object WorkoutSummaryContract {
    /** The Data Layer path the watch writes and the phone listens on. */
    const val PATH = "/workouts"

    const val KEY_PAYLOAD = "payload"

    /**
     * Bumped when the shape changes incompatibly. A phone that reads a newer
     * version keeps the list it already has rather than showing a wrong one.
     */
    const val VERSION = 1

    /**
     * The most recent rides, and no more.
     *
     * A Data Layer item is capped at 100 KB and the watch may hold two hundred
     * workouts. Sending the newest fifty keeps the payload around ten kilobytes,
     * and a history screen is scrolled from the top - the ride from four months
     * ago is not what the rider opened it for.
     */
    const val MAX_SUMMARIES = 50

    /**
     * Enough for anything a service or an exception actually says, and a bound
     * on the one field here whose length nothing else limits. Fifty rides times
     * two services of unbounded text is how a 100 KB item is overrun by a
     * message no screen has room for anyway.
     */
    const val MAX_REASON = 120

    private val json = Json { ignoreUnknownKeys = true }

    private const val KEY_VERSION = "version"
    private const val KEY_WORKOUTS = "workouts"
    private const val KEY_ID = "id"
    private const val KEY_SPORT = "sport"
    private const val KEY_STARTED_AT = "startedAt"
    private const val KEY_DURATION = "duration"
    private const val KEY_DISTANCE = "distance"
    private const val KEY_BYTES = "bytes"
    private const val KEY_UPLOADS = "uploads"
    private const val KEY_ATTEMPTS = "attempts"
    private const val KEY_REASONS = "reasons"

    /**
     * Newest first and truncated, so the cap keeps the rides the phone is most
     * likely to be asked about rather than whichever the index listed first.
     */
    fun encode(summaries: List<WorkoutSummary>): String =
        buildJsonObject {
            put(KEY_VERSION, VERSION)
            put(
                KEY_WORKOUTS,
                buildJsonArray {
                    summaries
                        .sortedByDescending { it.startedAtEpochMs }
                        .take(MAX_SUMMARIES)
                        .forEach { add(encodeSummary(it)) }
                },
            )
        }.toString()

    private fun encodeSummary(summary: WorkoutSummary) =
        buildJsonObject {
            put(KEY_ID, summary.id)
            put(KEY_SPORT, summary.sportTypeId)
            put(KEY_STARTED_AT, summary.startedAtEpochMs)
            put(KEY_DURATION, summary.durationSeconds)
            put(KEY_DISTANCE, summary.distanceMeters)
            put(KEY_BYTES, summary.fileSizeBytes)
            put(KEY_UPLOADS, buildJsonObject { summary.uploads.forEach { (id, state) -> put(id, state.id) } })
            // Added without bumping the version: a phone that predates these
            // keys ignores what it does not know, and one that expects them
            // treats their absence as "nothing to explain". Both read the
            // history correctly, which is the only compatibility that matters
            // when watch and phone update on their own schedules.
            put(KEY_ATTEMPTS, buildJsonObject { summary.uploadAttempts.forEach { (id, n) -> put(id, n) } })
            put(
                KEY_REASONS,
                buildJsonObject { summary.uploadReasons.forEach { (id, why) -> put(id, why.take(MAX_REASON)) } },
            )
        }

    /**
     * Null when the payload is unusable, so the phone keeps the history it has
     * rather than replacing it with nothing. An entry that cannot be read is
     * dropped on its own - one damaged row is not a reason to blank the list.
     *
     * Four guard clauses, one per way a payload can be unusable, following the
     * shape [SyncContract] uses in the other direction. Each one means "keep the
     * history you already have", and saying that where it is discovered reads
     * better than threading a nullable result to the end.
     */
    @Suppress("ReturnCount")
    fun decode(payload: String): List<WorkoutSummary>? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        val version = (root[KEY_VERSION] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        if (version > VERSION) return null
        val workouts = root[KEY_WORKOUTS] as? JsonArray ?: return null
        // Distinct, because the phone's list is keyed by id and Compose throws
        // outright on a repeat. The watch should never send one - saving is
        // idempotent by id - but a crash on the receiving end is a poor way to
        // find out that it did.
        return workouts.mapNotNull { toSummary(it as? JsonObject) }.distinctBy { it.id }
    }

    private fun toSummary(node: JsonObject?): WorkoutSummary? {
        val id = node?.let { text(it, KEY_ID) } ?: return null
        val startedAt = number(node, KEY_STARTED_AT)?.toLong() ?: return null
        return runCatching {
            WorkoutSummary(
                id = id,
                sportTypeId = text(node, KEY_SPORT).orEmpty(),
                startedAtEpochMs = startedAt,
                durationSeconds = number(node, KEY_DURATION)?.toLong() ?: 0L,
                distanceMeters = number(node, KEY_DISTANCE) ?: 0.0,
                fileSizeBytes = number(node, KEY_BYTES)?.toLong() ?: 0L,
                uploads = uploads(node),
                uploadAttempts =
                    (node[KEY_ATTEMPTS] as? JsonObject)
                        ?.mapNotNull { (key, value) ->
                            (value as? JsonPrimitive)?.content?.toIntOrNull()?.let { key to it }
                        }?.toMap()
                        .orEmpty(),
                uploadReasons =
                    (node[KEY_REASONS] as? JsonObject)
                        ?.mapNotNull { (key, value) ->
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content }
                        }?.toMap()
                        .orEmpty(),
            )
        }.getOrNull()
    }

    private fun uploads(node: JsonObject): Map<String, UploadState> =
        (node[KEY_UPLOADS] as? JsonObject)
            ?.mapNotNull { (key, value) ->
                UploadState.byId((value as? JsonPrimitive)?.content.orEmpty())?.let { key to it }
            }?.toMap()
            .orEmpty()

    private fun text(
        node: JsonObject,
        key: String,
    ): String? = (node[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun number(
        node: JsonObject,
        key: String,
    ): Double? = (node[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
}
