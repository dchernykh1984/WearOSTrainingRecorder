package com.dchernykh.trainingrecorder.core.datalayer

import com.dchernykh.trainingrecorder.core.config.ConfigLevel
import com.dchernykh.trainingrecorder.core.config.Screen
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.config.ScreenSet
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncContractTest {
    private val gravel = SportCatalogue.byId("cycling_gravel")!!

    private val settings =
        WatchSettings(
            screens =
                ScreenConfiguration
                    .initial()
                    .withScreensFor(Discipline.CYCLING, ScreenSet(listOf(Screen(listOf("power", "hr")))))
                    .withScreensFor(gravel, ScreenSet(listOf(Screen(listOf("grade", null, "altitude"))))),
            race = RaceStatsConfig(competitionId = "259", bib = "1", refreshSeconds = 120),
            units = UnitSystem.IMPERIAL,
            languageTag = "kk",
        )

    @Test
    fun everythingSurvivesTheRoundTrip() {
        val decoded = assertNotNull(SyncContract.decode(SyncContract.encode(settings)))
        assertEquals(settings, decoded)
    }

    @Test
    fun theThreeTiersSurviveSeparately() {
        val decoded = assertNotNull(SyncContract.decode(SyncContract.encode(settings)))
        assertEquals(settings.screens.default, decoded.screens.default)
        assertEquals(
            listOf("power", "hr"),
            decoded.screens
                .resolve(Discipline.CYCLING)
                .screens[0]
                .slots,
        )
        assertEquals(
            listOf("grade", null, "altitude"),
            decoded.screens
                .resolve(gravel)
                .screens[0]
                .slots,
        )
    }

    @Test
    fun anEmptySlotStaysEmptyRatherThanCollapsing() {
        val decoded = assertNotNull(SyncContract.decode(SyncContract.encode(settings)))
        assertEquals(
            3,
            decoded.screens
                .resolve(gravel)
                .screens[0]
                .slotCount,
        )
        assertNull(
            decoded.screens
                .resolve(gravel)
                .screens[0]
                .slots[1],
        )
    }

    @Test
    fun anUnknownKeyIsIgnoredRatherThanRejectingTheMessage() {
        val payload = SyncContract.encode(settings).dropLast(1) + ""","somethingNew":42}"""
        assertNotNull(SyncContract.decode(payload), "a newer phone must not brick an older watch")
    }

    @Test
    fun aPayloadFromANewerContractIsRefusedSoTheWatchKeepsWhatItHas() {
        val payload = SyncContract.encode(settings).replace("\"version\":1", "\"version\":99")
        assertNull(SyncContract.decode(payload))
    }

    @Test
    fun rubbishIsRefusedRatherThanPartiallyApplied() {
        listOf("", "not json", "[]", "{}", """{"version":1}""").forEach {
            assertNull(SyncContract.decode(it), "expected a refusal for: $it")
        }
    }

    @Test
    fun missingOptionalSectionsFallBackToDefaults() {
        val minimal = """{"version":1,"screens":{"default":[["hr"]]}}"""
        val decoded = assertNotNull(SyncContract.decode(minimal))
        assertEquals(UnitSystem.METRIC, decoded.units)
        assertNull(decoded.languageTag)
        assertEquals(RaceStatsConfig.DEFAULT_SITE_URL, decoded.race.siteUrl)
        assertEquals(RaceStatsConfig.DEFAULT_REFRESH_SECONDS, decoded.race.refreshSeconds)
        assertEquals(
            listOf("hr"),
            decoded.screens.default.screens[0]
                .slots,
        )
    }

    @Test
    fun anUnknownUnitSystemFallsBackToMetricRatherThanFailing() {
        val payload = """{"version":1,"units":"furlongs","screens":{"default":[["hr"]]}}"""
        assertEquals(UnitSystem.METRIC, assertNotNull(SyncContract.decode(payload)).units)
    }

    @Test
    fun anOutOfRangeRefreshIsClampedRatherThanThrowing() {
        val tooFast = """{"version":1,"race":{"refreshSeconds":1},"screens":{"default":[["hr"]]}}"""
        assertEquals(
            RaceStatsConfig.MIN_REFRESH_SECONDS,
            assertNotNull(SyncContract.decode(tooFast)).race.refreshSeconds,
        )
        val tooSlow = """{"version":1,"race":{"refreshSeconds":999999},"screens":{"default":[["hr"]]}}"""
        assertEquals(
            RaceStatsConfig.MAX_REFRESH_SECONDS,
            assertNotNull(SyncContract.decode(tooSlow)).race.refreshSeconds,
        )
    }

    @Test
    fun aScreenWithTooManySlotsIsDroppedRatherThanCrashing() {
        val slots = (1..Screen.MAX_SLOTS + 1).joinToString(",") { "\"hr\"" }
        val payload = """{"version":1,"screens":{"default":[[$slots],["power"]]}}"""
        val decoded = assertNotNull(SyncContract.decode(payload))
        assertEquals(1, decoded.screens.default.screens.size)
        assertEquals(
            listOf("power"),
            decoded.screens.default.screens[0]
                .slots,
        )
    }

    @Test
    fun anUnknownDisciplineOverrideIsSkippedWithoutLosingTheRest() {
        val overrides = """{"quidditch":[["power"]],"cycling":[["cadence"]]}"""
        val payload = """{"version":1,"screens":{"default":[["hr"]],"byDiscipline":$overrides}}"""
        val decoded = assertNotNull(SyncContract.decode(payload))
        assertEquals(
            listOf("cadence"),
            decoded.screens
                .resolve(Discipline.CYCLING)
                .screens[0]
                .slots,
        )
        assertEquals(1, decoded.screens.byDiscipline.size)
    }

    @Test
    fun thePathAndVersionAreStablePartsOfTheContract() {
        assertEquals("/settings", WatchSettings.PATH)
        assertEquals(1, WatchSettings.VERSION)
    }

    @Test
    fun theTierLabelKeysAreDerivedFromTheLevelIds() {
        ConfigLevel.entries.forEach {
            assertEquals("config_level_${it.id}", SyncContract.levelLabelKey(it))
        }
    }

    @Test
    fun aLayoutSavedUnderAnOldFieldNameStillCarriesThatField() {
        // The phone's own saved settings and the watch's copy both come back
        // through here, so migrating a renamed field once covers both ends.
        val payload =
            """
            {"version":1,"units":"metric","screens":{"default":[["segment_ahead","segment_ahead_distance"]]}}
            """.trimIndent()

        val decoded = assertNotNull(SyncContract.decode(payload))

        assertEquals(
            listOf("segment_gap", "segment_gap_distance"),
            decoded.screens.default.screens
                .first()
                .slots,
        )
    }
}
