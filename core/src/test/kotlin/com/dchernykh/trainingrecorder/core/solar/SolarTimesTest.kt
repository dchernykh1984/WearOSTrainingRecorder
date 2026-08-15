package com.dchernykh.trainingrecorder.core.solar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checked against places and dates where the answer is published, because an
 * astronomical formula is exactly the kind of code that runs, returns a
 * plausible number, and is wrong.
 */
class SolarTimesTest {
    private val moscow = 55.7558 to 37.6173
    private val london = 51.5074 to -0.1278

    /** Minutes past midnight UTC, which is how the published tables are read. */
    private fun utcMinutes(epochMs: Long): Long = Math.floorMod(epochMs / MILLIS_PER_MINUTE, MINUTES_PER_DAY)

    private fun assertCloseTo(
        expectedUtcMinutes: Long,
        actualEpochMs: Long,
        what: String,
    ) {
        val actual = utcMinutes(actualEpochMs)
        assertTrue(
            abs(actual - expectedUtcMinutes) <= TOLERANCE_MINUTES,
            "$what: expected ${expectedUtcMinutes / 60}:${expectedUtcMinutes % 60} UTC, " +
                "got ${actual / 60}:${actual % 60}",
        )
    }

    @Test
    fun moscowAtTheSummerSolstice() {
        // 03:44 and 21:18 Moscow time, which is UTC+3 all year.
        val events = assertNotNull(SolarTimes.at(moscow.first, moscow.second, JUNE_SOLSTICE_2026))
        assertCloseTo(44, events.sunriseEpochMs, "sunrise")
        assertCloseTo(18 * 60 + 18, events.sunsetEpochMs, "sunset")
    }

    @Test
    fun moscowAtTheWinterSolstice() {
        // 08:57 and 15:57 Moscow time - the shortest day of the year there.
        val events = assertNotNull(SolarTimes.at(moscow.first, moscow.second, DECEMBER_SOLSTICE_2026))
        assertCloseTo(5 * 60 + 57, events.sunriseEpochMs, "sunrise")
        assertCloseTo(12 * 60 + 57, events.sunsetEpochMs, "sunset")
    }

    @Test
    fun londonAtTheEquinox() {
        val events = assertNotNull(SolarTimes.at(london.first, london.second, MARCH_EQUINOX_2026))
        assertCloseTo(6 * 60 + 2, events.sunriseEpochMs, "sunrise")
        assertCloseTo(18 * 60 + 11, events.sunsetEpochMs, "sunset")
    }

    @Test
    fun theLongitudeSignIsTheOneAPlaceNearGreenwichCannotCatch() {
        // Both conventions give London the same answer, which is why getting it
        // backwards survives a casual check. Moscow is two and a half hours of
        // longitude east: the wrong sign puts its sunrise five hours out.
        val here = assertNotNull(SolarTimes.at(moscow.first, moscow.second, JUNE_SOLSTICE_2026))
        val mirrored = assertNotNull(SolarTimes.at(moscow.first, -moscow.second, JUNE_SOLSTICE_2026))
        val apart = abs(utcMinutes(here.sunriseEpochMs) - utcMinutes(mirrored.sunriseEpochMs))
        assertTrue(apart > 240, "east and west of Greenwich must not agree, they were $apart minutes apart")
    }

    @Test
    fun theSunDoesNotSetOverSvalbardInJune() {
        assertNull(SolarTimes.at(78.2232, 15.6469, JUNE_SOLSTICE_2026), "polar day has no sunset to print")
    }

    @Test
    fun theSunDoesNotRiseOverSvalbardInDecember() {
        assertNull(SolarTimes.at(78.2232, 15.6469, DECEMBER_SOLSTICE_2026), "polar night has no sunrise to print")
    }

    @Test
    fun theEquatorGetsRoughlyTwelveHoursOfDaylightWheneverItIsAsked() {
        listOf(JUNE_SOLSTICE_2026, DECEMBER_SOLSTICE_2026, MARCH_EQUINOX_2026).forEach { day ->
            val events = assertNotNull(SolarTimes.at(0.0, 0.0, day))
            val hours = (events.sunsetEpochMs - events.sunriseEpochMs).toDouble() / MILLIS_PER_HOUR
            assertTrue(abs(hours - 12) < 0.3, "the equator should get twelve hours, got $hours")
        }
    }

    @Test
    fun theSouthernHemisphereHasItsShortDayInJune() {
        val june = assertNotNull(SolarTimes.at(-33.8688, 151.2093, JUNE_SOLSTICE_2026))
        val december = assertNotNull(SolarTimes.at(-33.8688, 151.2093, DECEMBER_SOLSTICE_2026))
        assertTrue(
            june.sunsetEpochMs - june.sunriseEpochMs < december.sunsetEpochMs - december.sunriseEpochMs,
            "Sydney's winter is in June",
        )
    }

    @Test
    fun theAnswerIsTheSameDayWheneverDuringItYouAsk() {
        // A ride is not the moment it started, and the field must not change its
        // answer at noon. Every hour of one day, one answer.
        val morning = assertNotNull(SolarTimes.at(moscow.first, moscow.second, JUNE_SOLSTICE_2026))
        (0..23).forEach { hour ->
            val events =
                assertNotNull(
                    SolarTimes.at(moscow.first, moscow.second, JUNE_SOLSTICE_2026_MIDNIGHT + hour * MILLIS_PER_HOUR),
                )
            assertEquals(
                utcMinutes(morning.sunriseEpochMs),
                utcMinutes(events.sunriseEpochMs),
                "asked at $hour:00 UTC the answer moved",
            )
        }
    }

    @Test
    fun aPositionOffTheGlobeIsRefusedRatherThanAnswered() {
        assertFailsWith<IllegalArgumentException> { SolarTimes.at(91.0, 0.0, JUNE_SOLSTICE_2026) }
        assertFailsWith<IllegalArgumentException> { SolarTimes.at(0.0, 181.0, JUNE_SOLSTICE_2026) }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val MINUTES_PER_DAY = 1440L

        /** A minute is far finer than the light changes, and finer than the formula claims. */
        const val TOLERANCE_MINUTES = 2L

        /** 2026-06-21 12:00 UTC. */
        const val JUNE_SOLSTICE_2026 = 1_782_043_200_000L

        /** 2026-06-21 00:00 UTC. */
        const val JUNE_SOLSTICE_2026_MIDNIGHT = 1_782_000_000_000L

        /** 2026-12-21 09:00 UTC. */
        const val DECEMBER_SOLSTICE_2026 = 1_797_843_600_000L

        /** 2026-03-20 12:00 UTC. */
        const val MARCH_EQUINOX_2026 = 1_774_008_000_000L
    }
}
