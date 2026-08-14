package com.dchernykh.trainingrecorder.core.race

import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.field.FieldCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

/**
 * What the rider sets on the phone to identify themselves in a race. All three
 * values are entered there and only there; the watch merely uses them.
 */
data class RaceStatsConfig(
    val siteUrl: String = DEFAULT_SITE_URL,
    val competitionId: String = "",
    val bib: String = "",
    val refreshSeconds: Int = DEFAULT_REFRESH_SECONDS,
) {
    val isComplete: Boolean
        get() = siteUrl.isNotBlank() && competitionId.isNotBlank() && bib.isNotBlank()

    init {
        require(refreshSeconds in MIN_REFRESH_SECONDS..MAX_REFRESH_SECONDS) {
            "refresh must be $MIN_REFRESH_SECONDS..$MAX_REFRESH_SECONDS s, got $refreshSeconds"
        }
    }

    companion object {
        const val DEFAULT_SITE_URL = "https://universalbicycle.team"

        /**
         * Sixty seconds is what the Amazfit app uses, and standings only move
         * when a rider crosses a lap line. On a long race the rider can stretch
         * it; on a short one fresh data is worth more than the battery.
         */
        const val DEFAULT_REFRESH_SECONDS = 60
        const val MIN_REFRESH_SECONDS = 10
        const val MAX_REFRESH_SECONDS = 3600

        /**
         * The intervals the rider can actually choose between.
         *
         * The range spans ten seconds to an hour, and offering it as a continuous
         * line makes the part anyone wants unreachable: every value under two
         * minutes lives in the first three per cent of a slider, where one pixel
         * is worth a quarter of a minute. Picking sixty seconds - the default,
         * and the one the sibling apps use - was not possible at all.
         *
         * Spaced the way the choice actually matters: finely where a rider is
         * trading freshness against battery in a short race, coarsely out at the
         * end where the difference between half an hour and an hour is nothing.
         */
        val REFRESH_CHOICES =
            listOf(10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800, 3600)

        /**
         * The offered interval closest to [seconds].
         *
         * Needed because a stored value need not be one of the choices: it may
         * come from an older build, from a hand-edited settings file, or from a
         * later version of this list.
         */
        fun nearestRefresh(seconds: Int): Int = REFRESH_CHOICES.minBy { abs(it - seconds) }
    }
}

/** One parsed response: pre-formatted strings keyed exactly as the server sent them. */
data class RaceStatsSnapshot(
    val stats: Map<String, String> = emptyMap(),
    val updatedAt: String? = null,
) {
    val isEmpty: Boolean get() = stats.isEmpty()

    companion object {
        val EMPTY = RaceStatsSnapshot()
    }
}

/**
 * Builds the request URL. Ported from the Amazfit app so both clients hit the
 * same shape: `{site}/api/v1/live-stats/{competitionId}/{bib}`, with the site's
 * trailing slashes and surrounding whitespace stripped.
 */
object RaceStatsUrl {
    private const val PATH = "/api/v1/live-stats/"

    private fun encode(segment: String): String =
        java.net.URLEncoder
            .encode(segment, Charsets.UTF_8.name())
            .replace("+", "%20")

    fun build(config: RaceStatsConfig): String? {
        if (!config.isComplete) return null
        val base = config.siteUrl.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) return null
        // The rider types these, so a stray space or slash would otherwise
        // build a malformed URL or point at a different endpoint entirely.
        return base + PATH + encode(config.competitionId.trim()) + "/" + encode(config.bib.trim())
    }
}

/**
 * Reads the payload without knowing what any of it means.
 *
 * Only `stats` is looked at; every other top-level key is ignored. Values are
 * taken verbatim because the server has already formatted them ("+0:12",
 * "3/10", "DSQ"), and keys the app does not recognise are dropped rather than
 * causing a failure - that is what lets the server add a metric without an app
 * update.
 */
object RaceStatsParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val knownKeys: Set<String> =
        FieldCatalogue.forCategory(FieldCategory.RACE_STATS).map { it.id }.toSet() +
            FieldCatalogue.raceStatsSupportingKeys

    fun parse(body: String): RaceStatsSnapshot {
        val root =
            runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return RaceStatsSnapshot.EMPTY
        val stats = root["stats"] as? JsonObject ?: return RaceStatsSnapshot.EMPTY
        val values =
            stats.entries
                .filter { it.key in knownKeys }
                .mapNotNull { (key, element) ->
                    (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
                }.toMap()
        val updatedAt = (root["updated_at"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return RaceStatsSnapshot(values, updatedAt)
    }
}

/**
 * Turns a snapshot into what a slot shows.
 *
 * The place fields are the only ones that are composed rather than copied: the
 * server sends the position and the size of the field separately, and they read
 * as "3/10". A non-numeric position such as "DSQ" is shown on its own, because
 * "DSQ/10" is nonsense.
 */
object RaceStatsFormatter {
    private val placeDenominators = mapOf("place_abs" to "qty_abs", "place_group" to "qty_group")

    fun displayValue(
        fieldId: String,
        snapshot: RaceStatsSnapshot,
    ): String {
        val denominatorKey = placeDenominators[fieldId]
        val value = snapshot.stats[fieldId] ?: return FieldCatalogue.EMPTY_VALUE
        if (denominatorKey == null) return value
        val quantity = snapshot.stats[denominatorKey]
        return if (quantity.isNullOrBlank() || value.toIntOrNull() == null) value else "$value/$quantity"
    }
}
