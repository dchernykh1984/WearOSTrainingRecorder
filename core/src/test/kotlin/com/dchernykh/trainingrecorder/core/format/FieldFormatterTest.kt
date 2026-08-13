package com.dchernykh.trainingrecorder.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FieldFormatterTest {
    private val empty = FieldFormatter.empty

    @Test
    fun durationsDropTheHoursUntilThereAreSome() {
        assertEquals("0:00", FieldFormatter.duration(0))
        assertEquals("0:09", FieldFormatter.duration(9_000))
        assertEquals("1:05", FieldFormatter.duration(65_000))
        assertEquals("59:59", FieldFormatter.duration(3_599_000))
        assertEquals("1:00:00", FieldFormatter.duration(3_600_000))
        assertEquals("5:04:03", FieldFormatter.duration(18_243_000))
    }

    @Test
    fun anAbsentOrImpossibleDurationReadsEmpty() {
        assertEquals(empty, FieldFormatter.duration(null))
        assertEquals(empty, FieldFormatter.duration(-1))
    }

    @Test
    fun shortDistancesAreShownInMetres() {
        assertEquals("0 m", FieldFormatter.distance(0.0))
        assertEquals("450 m", FieldFormatter.distance(450.0))
        assertEquals("999 m", FieldFormatter.distance(999.4))
    }

    @Test
    fun longerDistancesSwitchToKilometresAndLosePrecisionAsTheyGrow() {
        assertEquals("1 km", FieldFormatter.distance(1_000.0))
        assertEquals("1.25 km", FieldFormatter.distance(1_250.0))
        // 42.195 rounds to 42.20, and the trailing zero is dropped.
        assertEquals("42.2 km", FieldFormatter.distance(42_195.0))
        assertEquals("123.5 km", FieldFormatter.distance(123_456.0), "past 100 km two decimals are noise")
    }

    @Test
    fun imperialDistancesUseMiles() {
        assertEquals("1 mi", FieldFormatter.distance(1609.344, UnitSystem.IMPERIAL))
        assertEquals("26.22 mi", FieldFormatter.distance(42_195.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun shortImperialDistancesUseFeetRatherThanAUselessFractionOfAMile() {
        assertEquals("328 ft", FieldFormatter.distance(100.0, UnitSystem.IMPERIAL))
        assertEquals("0 ft", FieldFormatter.distance(0.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun speedIsPerHourInBothSystems() {
        assertEquals("36 km/h", FieldFormatter.speed(10.0))
        assertEquals("22.4 mph", FieldFormatter.speed(10.0, UnitSystem.IMPERIAL))
        assertEquals("0 km/h", FieldFormatter.speed(0.0))
        assertEquals(empty, FieldFormatter.speed(null))
    }

    @Test
    fun paceIsMinutesAndSecondsPerUnit() {
        // 1000 m in 300 s is five minutes per kilometre.
        assertEquals("5:00/km", FieldFormatter.pace(1000.0 / 300.0))
        assertEquals("4:00/km", FieldFormatter.pace(1000.0 / 240.0))
        assertEquals("8:03/mi", FieldFormatter.pace(1000.0 / 300.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun standingStillHasNoPaceRatherThanAnEnormousOne() {
        assertEquals(empty, FieldFormatter.pace(0.0))
        assertEquals(empty, FieldFormatter.pace(-1.0))
        assertEquals(empty, FieldFormatter.pace(null))
        assertEquals(empty, FieldFormatter.pace(0.2), "a crawl is a stop, not a 1:23:00 pace")
    }

    @Test
    fun aPaceThatRoundsToSixtySecondsCarriesIntoTheMinute() {
        // 1000 / 299.6 is just under 5:00, and the seconds round to 60.
        assertEquals("5:00/km", FieldFormatter.pace(1000.0 / 299.6))
    }

    @Test
    fun elevationIsWholeUnits() {
        assertEquals("800 m", FieldFormatter.elevation(800.4))
        assertEquals("-12 m", FieldFormatter.elevation(-12.0))
        assertEquals("2625 ft", FieldFormatter.elevation(800.0, UnitSystem.IMPERIAL))
        assertEquals(empty, FieldFormatter.elevation(null))
    }

    @Test
    fun integersCarryTheirSuffixWhenGivenOne() {
        assertEquals("152", FieldFormatter.integer(151.7))
        assertEquals("250 W", FieldFormatter.integer(250.0, "W"))
        assertEquals(empty, FieldFormatter.integer(null))
    }

    @Test
    fun percentagesAreWholeNumbers() {
        assertEquals("87%", FieldFormatter.percent(86.6))
        assertEquals(empty, FieldFormatter.percent(null))
    }

    @Test
    fun gradeIsSignedBecauseUpAndDownReadDifferently() {
        assertEquals("+7.5%", FieldFormatter.grade(7.5))
        assertEquals("-7.5%", FieldFormatter.grade(-7.5))
        assertEquals("0%", FieldFormatter.grade(0.0))
        assertEquals(empty, FieldFormatter.grade(null))
    }

    @Test
    fun theClockIsShownInLocalTime() {
        // 1970-01-01T09:05:00Z
        val epoch = (9L * 3600 + 5 * 60) * 1000
        assertEquals("9:05", FieldFormatter.clockTime(epoch))
        assertEquals("15:05", FieldFormatter.clockTime(epoch, offsetMinutes = 360))
        assertEquals(empty, FieldFormatter.clockTime(null))
    }

    @Test
    fun aClockOffsetThatCrossesMidnightWrapsRatherThanGoingNegative() {
        val epoch = 30L * 60 * 1000
        assertEquals("23:30", FieldFormatter.clockTime(epoch, offsetMinutes = -60))
    }

    @Test
    fun unitSystemRoundTripsThroughItsId() {
        UnitSystem.entries.forEach { assertEquals(it, UnitSystem.byId(it.id)) }
        assertNull(UnitSystem.byId("furlongs"))
    }
}
