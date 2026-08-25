package com.dchernykh.trainingrecorder.core.datalayer

import com.dchernykh.trainingrecorder.core.segment.EffortPoint
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.core.segment.SegmentPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/**
 * How a segment travels from the phone to the watch.
 *
 * One Data Layer item per segment rather than one holding all of them. Items are
 * capped at a hundred kilobytes and a segment's line is not small, so a single
 * payload would work with five starred segments and fail with fifty - and fail
 * by dropping every one of them, on the ride where the rider finally starred
 * enough climbs to care. Per segment, an item is a few kilobytes, an unchanged
 * one is not re-sent, and unstarring one is a deletion the watch already knows
 * how to hear.
 *
 * Numbers are rounded on the way out. Six decimal places of latitude is about
 * ten centimetres, which is finer than any watch knows where it is, and the
 * rounding halves the payload.
 */
object SegmentContract {
    const val PATH_PREFIX = "/segment/"

    const val KEY_PAYLOAD = "payload"

    /** Bumped when the shape changes; an older watch ignores a newer segment. */
    const val VERSION = 1

    fun path(id: Long): String = "$PATH_PREFIX$id"

    /** The id in a path, or null where the path is something else entirely. */
    fun idFrom(path: String?): Long? = path?.removePrefix(PATH_PREFIX)?.takeIf { it != path }?.toLongOrNull()

    fun encode(segment: Segment): String =
        buildJsonObject {
            put(KEY_VERSION, VERSION)
            put(KEY_ID, segment.id)
            put(KEY_NAME, segment.name)
            put(KEY_POINTS, encodePoints(thin(segment.points, MAX_POINTS)))
            put(KEY_REFERENCE, encodeReference(thin(segment.reference, MAX_REFERENCE)))
        }.toString()

    /** Null when the payload is unusable, so the watch keeps what it has. */
    @Suppress("ReturnCount")
    fun decode(payload: String): Segment? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        val version = number(root[KEY_VERSION])?.toInt() ?: return null
        if (version > VERSION) return null
        val id = number(root[KEY_ID])?.toLong() ?: return null
        val name = (root[KEY_NAME] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val points = decodePoints(root[KEY_POINTS] as? JsonArray)
        if (points.size < 2) return null
        return Segment(id, name, points, decodeReference(root[KEY_REFERENCE] as? JsonArray))
    }

    /**
     * Every nth point, keeping the ends.
     *
     * A segment's line arrives at whatever resolution Strava recorded it, which
     * for a long descent is thousands of points. The matcher works on metres
     * rather than points and a line thinned to one point every few metres still
     * describes the same road, so the item stays inside its limit.
     */
    fun <T> thin(
        points: List<T>,
        limit: Int,
    ): List<T> {
        if (points.size <= limit || limit < 2) return points
        val step = (points.size - 1).toDouble() / (limit - 1)
        val kept = (0 until limit).map { points[(it * step).roundToLong().toInt().coerceAtMost(points.lastIndex)] }
        return kept.distinct()
    }

    private fun encodePoints(points: List<SegmentPoint>) =
        buildJsonArray {
            points.forEach { point ->
                add(
                    buildJsonArray {
                        add(round(point.latitudeDeg, DEGREE_PLACES))
                        add(round(point.longitudeDeg, DEGREE_PLACES))
                        add(round(point.distanceMeters, METRE_PLACES))
                        point.altitudeMeters?.let { add(round(it, METRE_PLACES)) } ?: add(JsonNull)
                    },
                )
            }
        }

    private fun encodeReference(reference: List<EffortPoint>) =
        buildJsonArray {
            reference.forEach { point ->
                add(
                    buildJsonArray {
                        add(round(point.distanceMeters, METRE_PLACES))
                        add(round(point.elapsedSeconds, METRE_PLACES))
                    },
                )
            }
        }

    private fun decodePoints(array: JsonArray?): List<SegmentPoint> =
        array.orEmpty().mapNotNull { element ->
            val values = element as? JsonArray ?: return@mapNotNull null
            if (values.size < POINT_FIELDS) return@mapNotNull null
            val latitude = number(values[0]) ?: return@mapNotNull null
            val longitude = number(values[1]) ?: return@mapNotNull null
            val distance = number(values[2]) ?: return@mapNotNull null
            SegmentPoint(latitude, longitude, distance, number(values[3]))
        }

    private fun decodeReference(array: JsonArray?): List<EffortPoint> =
        array.orEmpty().mapNotNull { element ->
            val values = element as? JsonArray ?: return@mapNotNull null
            if (values.size < REFERENCE_FIELDS) return@mapNotNull null
            val distance = number(values[0]) ?: return@mapNotNull null
            val seconds = number(values[1]) ?: return@mapNotNull null
            EffortPoint(distance, seconds)
        }

    private fun number(element: JsonElement?): Double? =
        (element as? JsonPrimitive)?.takeIf { it != JsonNull }?.content?.toDoubleOrNull()

    private fun round(
        value: Double,
        places: Int,
    ): Double {
        val factor = generateSequence(1.0) { it * 10 }.elementAt(places)
        return (value * factor).roundToLong() / factor
    }

    private val json = Json { ignoreUnknownKeys = true }

    private const val KEY_VERSION = "v"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_POINTS = "pts"
    private const val KEY_REFERENCE = "ref"

    private const val POINT_FIELDS = 4
    private const val REFERENCE_FIELDS = 2

    /** About ten centimetres, which is finer than any watch knows where it is. */
    private const val DEGREE_PLACES = 6
    private const val METRE_PLACES = 1

    /**
     * Enough to describe a twenty kilometre climb to within a few metres, and
     * far enough inside the Data Layer's hundred kilobyte item to survive a
     * segment with an unusually dense track.
     */
    const val MAX_POINTS = 600

    /** The same, for the effort being chased. */
    const val MAX_REFERENCE = 600
}
