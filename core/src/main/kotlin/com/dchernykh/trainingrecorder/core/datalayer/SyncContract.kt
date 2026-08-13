package com.dchernykh.trainingrecorder.core.datalayer

import com.dchernykh.trainingrecorder.core.config.ConfigLevel
import com.dchernykh.trainingrecorder.core.config.Screen
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.config.ScreenSet
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.sport.Discipline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Everything the phone sends the watch, in one payload.
 *
 * Kept as an explicit wire format rather than serialized classes: the two apps
 * are installed separately and can be different versions, so a reader that
 * ignores what it does not recognise and falls back on what is missing is worth
 * more than a schema that refuses the whole message over one unknown key.
 */
data class WatchSettings(
    val screens: ScreenConfiguration,
    val race: RaceStatsConfig = RaceStatsConfig(),
    val units: UnitSystem = UnitSystem.METRIC,
    val languageTag: String? = null,
) {
    companion object {
        /** The Data Layer path the phone writes and the watch listens on. */
        const val PATH = "/settings"

        /**
         * Bumped when the shape changes incompatibly. An older watch that reads
         * a newer version keeps its previous settings rather than guessing.
         */
        const val VERSION = 1
    }
}

/** Encodes and decodes [WatchSettings] for the Data Layer. */
object SyncContract {
    private val json = Json { ignoreUnknownKeys = true }

    private const val KEY_VERSION = "version"
    private const val KEY_UNITS = "units"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_RACE = "race"
    private const val KEY_SCREENS = "screens"
    private const val KEY_DEFAULT = "default"
    private const val KEY_BY_DISCIPLINE = "byDiscipline"
    private const val KEY_BY_SPORT = "bySport"
    private const val KEY_SITE = "site"
    private const val KEY_COMPETITION = "competition"
    private const val KEY_BIB = "bib"
    private const val KEY_REFRESH = "refreshSeconds"

    fun encode(settings: WatchSettings): String =
        buildJsonObject {
            put(KEY_VERSION, WatchSettings.VERSION)
            put(KEY_UNITS, settings.units.id)
            settings.languageTag?.let { put(KEY_LANGUAGE, it) }
            put(KEY_RACE, encodeRace(settings.race))
            put(KEY_SCREENS, encodeScreens(settings.screens))
        }.toString()

    // Four guard clauses, one per way a payload can be unusable. Each one means
    // "keep the settings you already have", and saying that at the point of
    // discovery reads better than accumulating a nullable result.

    /** Returns null when the payload is unusable, so the watch keeps what it has. */
    @Suppress("ReturnCount")
    fun decode(payload: String): WatchSettings? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        val version = (root[KEY_VERSION] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        if (version > WatchSettings.VERSION) return null
        val screens = decodeScreens(root[KEY_SCREENS] as? JsonObject) ?: return null
        return WatchSettings(
            screens = screens,
            race = decodeRace(root[KEY_RACE] as? JsonObject),
            units = UnitSystem.byId(string(root, KEY_UNITS).orEmpty()) ?: UnitSystem.METRIC,
            languageTag = string(root, KEY_LANGUAGE),
        )
    }

    private fun string(
        root: JsonObject,
        key: String,
    ): String? = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun encodeRace(race: RaceStatsConfig) =
        buildJsonObject {
            put(KEY_SITE, race.siteUrl)
            put(KEY_COMPETITION, race.competitionId)
            put(KEY_BIB, race.bib)
            put(KEY_REFRESH, race.refreshSeconds)
        }

    private fun decodeRace(node: JsonObject?): RaceStatsConfig {
        if (node == null) return RaceStatsConfig()
        val refresh =
            (node[KEY_REFRESH] as? JsonPrimitive)
                ?.content
                ?.toIntOrNull()
                ?.coerceIn(RaceStatsConfig.MIN_REFRESH_SECONDS, RaceStatsConfig.MAX_REFRESH_SECONDS)
                ?: RaceStatsConfig.DEFAULT_REFRESH_SECONDS
        return RaceStatsConfig(
            siteUrl = string(node, KEY_SITE) ?: RaceStatsConfig.DEFAULT_SITE_URL,
            competitionId = string(node, KEY_COMPETITION).orEmpty(),
            bib = string(node, KEY_BIB).orEmpty(),
            refreshSeconds = refresh,
        )
    }

    private fun encodeScreens(config: ScreenConfiguration) =
        buildJsonObject {
            put(KEY_DEFAULT, encodeSet(config.default))
            put(
                KEY_BY_DISCIPLINE,
                buildJsonObject { config.byDiscipline.forEach { (k, v) -> put(k.id, encodeSet(v)) } },
            )
            put(KEY_BY_SPORT, buildJsonObject { config.bySportType.forEach { (k, v) -> put(k, encodeSet(v)) } })
        }

    private fun encodeSet(set: ScreenSet) =
        buildJsonArray {
            set.screens.forEach { screen ->
                add(buildJsonArray { screen.slots.forEach { add(it?.let(::JsonPrimitive) ?: JsonNull) } })
            }
        }

    private fun decodeScreens(node: JsonObject?): ScreenConfiguration? {
        val default = decodeSet(node?.get(KEY_DEFAULT) as? JsonArray) ?: return null
        val byDiscipline =
            (node?.get(KEY_BY_DISCIPLINE) as? JsonObject)
                ?.mapNotNull { (key, value) ->
                    val discipline = Discipline.byId(key) ?: return@mapNotNull null
                    val set = decodeSet(value as? JsonArray) ?: return@mapNotNull null
                    discipline to set
                }?.toMap()
                .orEmpty()
        val bySport =
            (node?.get(KEY_BY_SPORT) as? JsonObject)
                ?.mapNotNull { (key, value) -> decodeSet(value as? JsonArray)?.let { key to it } }
                ?.toMap()
                .orEmpty()
        return ScreenConfiguration(default, byDiscipline, bySport)
    }

    private fun decodeSet(node: JsonArray?): ScreenSet? {
        if (node == null || node.isEmpty()) return null
        val screens =
            node.mapNotNull { screenNode ->
                val slots = (screenNode as? JsonArray) ?: return@mapNotNull null
                if (slots.isEmpty() || slots.size > Screen.MAX_SLOTS) return@mapNotNull null
                Screen(slots.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content })
            }
        return if (screens.isEmpty()) null else ScreenSet(screens.take(ScreenSet.MAX_SCREENS))
    }

    /** Exposed so the phone can label which tier a sport is currently reading. */
    fun levelLabelKey(level: ConfigLevel): String = "config_level_${level.id}"
}
