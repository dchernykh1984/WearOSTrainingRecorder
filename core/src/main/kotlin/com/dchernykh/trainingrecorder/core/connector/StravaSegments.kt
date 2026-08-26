package com.dchernykh.trainingrecorder.core.connector

import com.dchernykh.trainingrecorder.core.segment.EffortPoint
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.core.segment.SegmentPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** A starred segment as the list endpoint describes it, before its line is known. */
data class StarredSegment(
    val id: Long,
    val name: String,
    val distanceMeters: Double,
    val startLatitudeDeg: Double,
    val startLongitudeDeg: Double,
    /** The rider's best, where Strava reports one. */
    val bestSeconds: Double? = null,
    /** The effort that best was, which is what has a time-and-distance curve. */
    val bestEffortId: Long? = null,
)

/**
 * Reading segments out of Strava, kept away from any HTTP client.
 *
 * What live segments need is settled before the ride and never during it: the
 * line to follow and the effort to follow it against. Both are fetched on the
 * phone, where there is a network and a rate limit worth respecting, and sent
 * to the watch once.
 *
 * Only the rider's own efforts are available. Strava withdrew leaderboards from
 * the API in 2020 and the KOM ranking that replaced them is a subscriber field
 * on a per-athlete basis, so there is no honest way to show a rider their gap to
 * anyone else's time. Racing your own best is what remains - and is, for most
 * riders on most climbs, the comparison they were making anyway.
 */
@Suppress("TooManyFunctions")
object StravaSegments {
    const val BASE_URL = "https://www.strava.com/api/v3"

    /** Starred segments, newest first, one page at a time. */
    fun starredUrl(
        page: Int = 1,
        perPage: Int = PAGE_SIZE,
    ): String = "$BASE_URL/segments/starred?page=$page&per_page=$perPage"

    fun segmentUrl(id: Long): String = "$BASE_URL/segments/$id"

    /** The line itself: where it goes, how far along, and how high. */
    fun streamsUrl(id: Long): String = "$BASE_URL/segments/$id/streams?keys=latlng,distance,altitude&key_by_type=true"

    /** One of the rider's own efforts, as distance against time. */
    fun effortStreamsUrl(effortId: Long): String =
        "$BASE_URL/segment_efforts/$effortId/streams?keys=distance,time&key_by_type=true"

    /** The starred list, skipping anything without a usable start. */
    fun starredFrom(body: String): List<StarredSegment> {
        val root = parse(body) as? JsonArray ?: return emptyList()
        return root.mapNotNull { starredSegment(it as? JsonObject ?: return@mapNotNull null) }
    }

    /** The same shape, from the single-segment endpoint. */
    fun segmentFrom(body: String): StarredSegment? {
        val node = parse(body) as? JsonObject ?: return null
        return starredSegment(node)
    }

    @Suppress("ReturnCount")
    private fun starredSegment(node: JsonObject): StarredSegment? {
        val id = number(node, "id")?.toLong() ?: return null
        val name = text(node, "name") ?: return null
        val start = coordinates(node["start_latlng"]) ?: return null
        val best = node["athlete_pr_effort"] as? JsonObject ?: node["athlete_segment_stats"] as? JsonObject
        return StarredSegment(
            id = id,
            name = name,
            distanceMeters = number(node, "distance") ?: 0.0,
            startLatitudeDeg = start.first,
            startLongitudeDeg = start.second,
            bestSeconds = best?.let { number(it, "pr_elapsed_time") ?: number(it, "elapsed_time") },
            bestEffortId = best?.let { number(it, "id")?.toLong() },
        )
    }

    /**
     * The segment's line, from the three streams that describe it.
     *
     * Altitude is optional and the two climbing fields simply go quiet without
     * it; position and distance are not, because a line with no distance along
     * it cannot say how much is left.
     */
    @Suppress("ReturnCount")
    fun lineFrom(body: String): List<SegmentPoint> {
        val root = parse(body) as? JsonObject ?: return emptyList()
        val positions = (root["latlng"] as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
        val distances = numbers(root, "distance")
        val altitudes = numbers(root, "altitude")
        if (distances.size < positions.size) return emptyList()
        return positions.mapIndexedNotNull { index, node ->
            val position = coordinates(node) ?: return@mapIndexedNotNull null
            SegmentPoint(
                latitudeDeg = position.first,
                longitudeDeg = position.second,
                distanceMeters = distances[index],
                altitudeMeters = altitudes.getOrNull(index),
            )
        }
    }

    /** An effort as a curve of distance against elapsed time. */
    fun effortCurveFrom(body: String): List<EffortPoint> {
        val root = parse(body) as? JsonObject ?: return emptyList()
        val distances = numbers(root, "distance")
        val times = numbers(root, "time")
        val length = minOf(distances.size, times.size)
        return (0 until length).map { EffortPoint(distances[it], times[it]) }
    }

    /**
     * A stand-in curve for an effort whose own is not available.
     *
     * Strava will not hand over the streams of an effort on an activity the
     * rider has kept private, and asking for a scope wide enough to read every
     * private activity they own - to draw one line on a watch - is not a trade
     * worth making. So the total time is spread evenly over the distance.
     *
     * This is a real approximation and it shows: against an evenly paced version
     * of a personal best, a rider who starts hard reads as up on a climb they
     * are in fact level on. The final time is exact either way, and it is the
     * one that goes in the book.
     */
    fun evenlyPaced(
        distanceMeters: Double,
        elapsedSeconds: Double,
    ): List<EffortPoint> {
        if (distanceMeters <= 0 || elapsedSeconds <= 0) return emptyList()
        return listOf(EffortPoint(0.0, 0.0), EffortPoint(distanceMeters, elapsedSeconds))
    }

    /** Everything gathered into what the watch actually follows. */
    fun segment(
        starred: StarredSegment,
        line: List<SegmentPoint>,
        reference: List<EffortPoint> = emptyList(),
    ): Segment? {
        if (line.size < 2) return null
        val best = starred.bestSeconds
        val curve =
            when {
                reference.size >= 2 -> reference
                best != null -> evenlyPaced(line.last().distanceMeters, best)
                else -> emptyList()
            }
        return Segment(id = starred.id, name = starred.name, points = line, reference = curve)
    }

    private fun parse(body: String) =
        runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(body) }.getOrNull()

    private fun text(
        node: JsonObject,
        key: String,
    ): String? = (node[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun number(
        node: JsonObject,
        key: String,
    ): Double? = (node[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun numbers(
        root: JsonObject,
        key: String,
    ): List<Double> {
        val data = (root[key] as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
        return data.map { it.jsonPrimitive.content.toDoubleOrNull() ?: 0.0 }
    }

    /** Strava writes a position as a two-element array, and an absent one as empty. */
    @Suppress("ReturnCount")
    private fun coordinates(node: JsonElement?): Pair<Double, Double>? {
        val pair = (node as? JsonArray)?.takeIf { it.size == 2 } ?: return null
        val latitude = pair[0].jsonPrimitive.content.toDoubleOrNull() ?: return null
        val longitude = pair[1].jsonPrimitive.content.toDoubleOrNull() ?: return null
        return latitude to longitude
    }

    /** Strava's own page size; asking for more is refused rather than truncated. */
    const val PAGE_SIZE = 30

    /**
     * How many pages of starred segments to take.
     *
     * A limit rather than "all of them" because each new segment costs two
     * requests and an item on the watch, and a rider who has starred four
     * hundred climbs over ten years is not racing all of them. Ninety is far
     * past what anyone has starred deliberately, and it keeps the first sync
     * inside a single rate-limit window.
     */
    const val MAX_PAGES = 3
}
