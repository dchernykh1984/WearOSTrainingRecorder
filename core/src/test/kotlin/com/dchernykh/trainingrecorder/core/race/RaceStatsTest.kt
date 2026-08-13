package com.dchernykh.trainingrecorder.core.race

import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaceStatsTest {
    /** Captured verbatim from GET /api/v1/live-stats/259/1 on the live site. */
    private val liveBody =
        """
        {"competition_id": 259, "bib": "1", "stats": {"laps": "3/10", "qty_abs": "4",
        "place_abs": "3", "qty_group": "3", "place_group": "2", "gap_next_abs": "-0:11.3",
        "gap_prev_abs": "+0:22.0", "gap_leader_abs": "+0:35.7", "gap_next_group": "-0:11.3",
        "gap_prev_group": "+0:22.0", "gap_leader_group": "+0:22.0",
        "gap_next_abs_delta": "+0:12.5", "gap_prev_abs_delta": "+0:23.0",
        "gap_leader_abs_delta": "-0:00.1", "gap_next_group_delta": "+0:12.5",
        "gap_prev_group_delta": "+0:23.0", "gap_leader_group_delta": "+0:23.0"},
        "updated_at": "2026-07-16T06:21:51.409Z"}
        """.trimIndent()

    private fun config(
        site: String = RaceStatsConfig.DEFAULT_SITE_URL,
        competition: String = "259",
        bib: String = "1",
    ) = RaceStatsConfig(siteUrl = site, competitionId = competition, bib = bib)

    @Test
    fun theDefaultRefreshMatchesTheAmazfitApp() {
        assertEquals(60, RaceStatsConfig.DEFAULT_REFRESH_SECONDS)
        assertEquals(60, RaceStatsConfig().refreshSeconds)
    }

    @Test
    fun anAbsurdRefreshIntervalIsRejected() {
        assertFailsWith<IllegalArgumentException> { RaceStatsConfig(refreshSeconds = 0) }
        assertFailsWith<IllegalArgumentException> { RaceStatsConfig(refreshSeconds = 100_000) }
    }

    @Test
    fun aConfigIsIncompleteUntilAllThreeValuesAreSet() {
        assertFalse(RaceStatsConfig().isComplete)
        assertFalse(config(competition = "").isComplete)
        assertFalse(config(bib = " ").isComplete)
        assertTrue(config().isComplete)
    }

    @Test
    fun theUrlMatchesTheContractTheOtherWatchesUse() {
        assertEquals("https://universalbicycle.team/api/v1/live-stats/259/1", RaceStatsUrl.build(config()))
    }

    @Test
    fun trailingSlashesAndWhitespaceAreStripped() {
        val messy = config(site = "  https://universalbicycle.team///  ", competition = " 42 ", bib = " 117 ")
        assertEquals("https://universalbicycle.team/api/v1/live-stats/42/117", RaceStatsUrl.build(messy))
    }

    @Test
    fun riderTypedValuesAreEncodedIntoThePath() {
        // A stray space or slash would otherwise build a malformed URL, or point
        // at a different endpoint entirely.
        val messy = config(competition = "2 59", bib = "a/b")
        assertEquals("https://universalbicycle.team/api/v1/live-stats/2%2059/a%2Fb", RaceStatsUrl.build(messy))
    }

    @Test
    fun anIncompleteOrNonHttpConfigYieldsNoUrl() {
        assertNull(RaceStatsUrl.build(RaceStatsConfig()))
        assertNull(RaceStatsUrl.build(config(site = "ftp://example.org")))
        assertNull(RaceStatsUrl.build(config(site = "universalbicycle.team")))
    }

    @Test
    fun theLiveResponseParsesIntoEverySelectableField() {
        val snapshot = RaceStatsParser.parse(liveBody)
        assertEquals("2026-07-16T06:21:51.409Z", snapshot.updatedAt)
        assertEquals("+0:22.0", snapshot.stats["gap_prev_abs"])
        assertEquals("3/10", snapshot.stats["laps"])
        assertEquals(17, snapshot.stats.size, "the live payload carries 15 fields plus two denominators")
    }

    @Test
    fun unknownKeysAreIgnoredRatherThanFatal() {
        val body = """{"stats": {"place_abs": "7", "brand_new_metric": "42"}}"""
        val snapshot = RaceStatsParser.parse(body)
        assertEquals("7", snapshot.stats["place_abs"])
        assertNull(snapshot.stats["brand_new_metric"])
    }

    @Test
    fun nonStringValuesAreDropped() {
        val body = """{"stats": {"place_abs": 7, "place_group": "2"}}"""
        val snapshot = RaceStatsParser.parse(body)
        assertNull(snapshot.stats["place_abs"], "the contract is pre-formatted strings")
        assertEquals("2", snapshot.stats["place_group"])
    }

    @Test
    fun malformedOrEmptyPayloadsYieldAnEmptySnapshot() {
        listOf("", "not json", "[]", "{}", """{"stats": "nope"}""", """{"other": {"place_abs": "1"}}""").forEach {
            assertTrue(RaceStatsParser.parse(it).isEmpty, "expected an empty snapshot for: $it")
        }
    }

    @Test
    fun placeIsComposedWithTheFieldSize() {
        val snapshot = RaceStatsParser.parse(liveBody)
        assertEquals("3/4", RaceStatsFormatter.displayValue("place_abs", snapshot))
        assertEquals("2/3", RaceStatsFormatter.displayValue("place_group", snapshot))
    }

    @Test
    fun aNonNumericPlaceIsShownAlone() {
        val snapshot = RaceStatsParser.parse("""{"stats": {"place_abs": "DSQ", "qty_abs": "40"}}""")
        assertEquals("DSQ", RaceStatsFormatter.displayValue("place_abs", snapshot))
    }

    @Test
    fun aPlaceWithoutItsDenominatorIsShownAlone() {
        val snapshot = RaceStatsParser.parse("""{"stats": {"place_abs": "3"}}""")
        assertEquals("3", RaceStatsFormatter.displayValue("place_abs", snapshot))
    }

    @Test
    fun gapsAreShownExactlyAsTheServerFormattedThem() {
        val snapshot = RaceStatsParser.parse(liveBody)
        assertEquals("-0:11.3", RaceStatsFormatter.displayValue("gap_next_abs", snapshot))
        assertEquals("-0:00.1", RaceStatsFormatter.displayValue("gap_leader_abs_delta", snapshot))
        assertEquals("3/10", RaceStatsFormatter.displayValue("laps", snapshot))
    }

    @Test
    fun aMissingValueReadsAsTheEmptyPlaceholder() {
        assertEquals(
            FieldCatalogue.EMPTY_VALUE,
            RaceStatsFormatter.displayValue("gap_prev_abs", RaceStatsSnapshot.EMPTY),
        )
    }
}
