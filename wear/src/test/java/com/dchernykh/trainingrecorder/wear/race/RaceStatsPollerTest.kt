package com.dchernykh.trainingrecorder.wear.race

import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * That a bib corrected mid-race reaches the next request.
 *
 * The bug: the loop built its URL once and then ran for the whole ride, so a
 * rider who had typed their number wrong was shown somebody else's standings
 * until they stopped and started the recording again - which in a race is the
 * one thing they cannot do.
 *
 * Every test here is given a timeout. The loop only ends when the collector
 * has what it asked for, so a regression that stops it emitting hangs rather
 * than fails, and a hung job in CI costs an hour to recognise.
 */
class RaceStatsPollerTest {
    private val asked = mutableListOf<String>()

    private fun config(bib: String) =
        RaceStatsConfig(
            siteUrl = "https://example.test",
            competitionId = "spring-race",
            bib = bib,
            refreshSeconds = 10,
        )

    private fun url(bib: String) = "https://example.test/api/v1/live-stats/spring-race/$bib"

    /** The shape the timing server answers with: the values live under "stats". */
    private val standings = """{"stats":{"place_abs":"7","qty_abs":"40"}}"""

    private fun poller() =
        RaceStatsPoller(
            fetch = {
                asked += it
                standings
            },
            // No waiting. The shortest interval the config allows is ten
            // seconds, and a test that honours it is a test nobody runs.
            sleep = {},
        )

    @Test(timeout = TIMEOUT_MS)
    fun aBibCorrectedMidRaceIsUsedOnTheNextRequest() {
        var ticks = 0
        val configs = { if (ticks++ == 0) config("12") else config("34") }

        runBlocking { poller().poll(configs).take(2).toList() }

        // The first two, not the count: with no waiting between ticks the
        // producer runs ahead into the flow's buffer, which says nothing about
        // how often a real poller asks.
        assertEquals(url("12"), asked[0])
        assertEquals(url("34"), asked[1])
    }

    @Test(timeout = TIMEOUT_MS)
    fun anIncompleteBibIsWaitedOutRatherThanEndingTheRace() {
        // Half a number typed, or one cleared mid-race. The loop has to keep
        // going: ending it would mean finishing the number needs the ride
        // restarted, which is the whole bug.
        var ticks = 0
        val configs = { if (ticks++ == 0) config("") else config("34") }

        runBlocking { poller().poll(configs).take(1).toList() }

        assertEquals(
            "the tick with half a number typed must ask for nothing at all",
            url("34"),
            asked.first(),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
