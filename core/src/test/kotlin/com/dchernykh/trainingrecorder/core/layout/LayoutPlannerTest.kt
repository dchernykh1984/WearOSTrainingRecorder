package com.dchernykh.trainingrecorder.core.layout

import com.dchernykh.trainingrecorder.core.config.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutPlannerTest {
    private fun columns(slotCount: Int) = LayoutPlanner.planRound(slotCount).map { it.columns }

    @Test
    fun roundBandsMatchGarminsOwnNativeLayouts() {
        // Taken from the shapes Garmin ships for its native round data fields.
        assertEquals(listOf(1), columns(1))
        assertEquals(listOf(1, 1), columns(2))
        assertEquals(listOf(1, 1, 1), columns(3))
        assertEquals(listOf(1, 2, 1), columns(4))
        assertEquals(listOf(1, 1, 2, 1), columns(5))
        assertEquals(listOf(1, 2, 2, 1), columns(6))
        assertEquals(listOf(1, 2, 2, 2, 1), columns(8))
    }

    @Test
    fun theFirstAndLastBandAreAlwaysFullWidth() {
        (1..Screen.MAX_SLOTS).forEach {
            val bands = LayoutPlanner.planRound(it)
            assertEquals(1, bands.first().columns, "band layout for $it slots starts with a split band")
            assertEquals(1, bands.last().columns, "band layout for $it slots ends with a split band")
        }
    }

    @Test
    fun bandsHoldExactlyTheRequestedNumberOfCells() {
        (1..Screen.MAX_SLOTS).forEach {
            assertEquals(it, LayoutPlanner.cellCount(LayoutPlanner.planRound(it)), "wrong cell count for $it slots")
        }
    }

    @Test
    fun noBandEverHoldsMoreThanTwoCells() {
        (1..Screen.MAX_SLOTS).forEach { slots ->
            LayoutPlanner.planRound(slots).forEach { assertTrue(it.columns <= Band.MAX_COLUMNS) }
        }
    }

    @Test
    fun aRoundLayoutNeedsAtLeastOneSlot() {
        assertFailsWith<IllegalArgumentException> { LayoutPlanner.planRound(0) }
        assertFailsWith<IllegalArgumentException> { Band(3) }
    }

    @Test
    fun squareScreensSplitIntoTwoColumnsOnlyWhenItIsWorthIt() {
        assertEquals(Grid(2, 2), LayoutPlanner.planSquare(4, 454, 454))
        assertEquals(Grid(2, 4), LayoutPlanner.planSquare(8, 454, 454))
        // Too few slots to bother splitting.
        assertEquals(Grid(1, 3), LayoutPlanner.planSquare(3, 454, 454))
        // Too narrow for two readable values side by side.
        assertEquals(Grid(1, 4), LayoutPlanner.planSquare(4, 160, 454))
        // Too short for two stacked rows.
        assertEquals(Grid(1, 4), LayoutPlanner.planSquare(4, 454, 60))
    }

    @Test
    fun anOddSlotCountRoundsTheGridUp() {
        val grid = LayoutPlanner.planSquare(5, 454, 454)
        assertEquals(2, grid.columns)
        assertEquals(3, grid.rows)
        assertTrue(grid.cellCount >= 5)
    }

    @Test
    fun aSquareLayoutRejectsNonsenseInput() {
        assertFailsWith<IllegalArgumentException> { LayoutPlanner.planSquare(0, 454, 454) }
        assertFailsWith<IllegalArgumentException> { LayoutPlanner.planSquare(4, 0, 454) }
        assertFailsWith<IllegalArgumentException> { LayoutPlanner.planSquare(4, 454, 0) }
        assertFailsWith<IllegalArgumentException> { Grid(0, 1) }
        assertFailsWith<IllegalArgumentException> { Grid(1, 0) }
    }

    @Test
    fun theChordIsWidestAtTheCentreAndVanishesAtTheRim() {
        val radius = 100.0
        assertEquals(100.0, LayoutPlanner.halfChord(radius, 0.0), 1e-9)
        assertEquals(0.0, LayoutPlanner.halfChord(radius, 100.0), 1e-9)
        assertEquals(0.0, LayoutPlanner.halfChord(radius, 250.0), 1e-9)
        assertEquals(60.0, LayoutPlanner.halfChord(radius, 80.0), 1e-9)
    }

    private fun assertCornerInside(
        radius: Double,
        x: Double,
        y: Double,
        diameter: Int,
    ) {
        val dx = x - radius
        val dy = y - radius
        assertTrue(
            dx * dx + dy * dy <= radius * radius + TOLERANCE,
            "corner ($x, $y) falls outside a ${diameter}px circle",
        )
    }

    private fun assertBlockFits(
        diameter: Int,
        top: Double,
        bottom: Double,
    ) {
        val radius = diameter / 2.0
        val span = LayoutPlanner.spanFor(radius, top, bottom)
        assertCornerInside(radius, span.start, top, diameter)
        assertCornerInside(radius, span.endInclusive, top, diameter)
        assertCornerInside(radius, span.start, bottom, diameter)
        assertCornerInside(radius, span.endInclusive, bottom, diameter)
    }

    @Test
    fun everyCornerOfABlockStaysInsideTheCircle() {
        listOf(208, 240, 260, 384, 416, 454).forEach { diameter ->
            var top = 0.0
            while (top < diameter) {
                assertBlockFits(diameter, top, minOf(top + BLOCK_HEIGHT, diameter.toDouble()))
                top += BLOCK_STEP
            }
        }
    }

    @Test
    fun aSpanIsCentredOnTheCircle() {
        val span = LayoutPlanner.spanFor(100.0, 90.0, 110.0)
        assertEquals(100.0, (span.start + span.endInclusive) / 2, 1e-9)
    }

    @Test
    fun aBlockEntirelyPastTheRimHasNoWidth() {
        val span = LayoutPlanner.spanFor(100.0, 210.0, 220.0)
        assertEquals(0.0, span.endInclusive - span.start, 1e-9)
    }

    @Test
    fun anInvertedBlockIsRejected() {
        assertFailsWith<IllegalArgumentException> { LayoutPlanner.spanFor(100.0, 50.0, 10.0) }
    }

    @Test
    fun slotsThatCannotBeRenderedLegiblyAreDropped() {
        assertEquals(10, LayoutPlanner.visibleSlots(10, 454))
        assertEquals(3, LayoutPlanner.visibleSlots(10, 79))
        // Even an absurdly short screen still draws one slot rather than none.
        assertEquals(1, LayoutPlanner.visibleSlots(10, 10))
        assertEquals(2, LayoutPlanner.visibleSlots(2, 454), "never invents slots that were not configured")
    }

    @Test
    fun visibleSlotsRejectsNonsenseInput() {
        assertFailsWith<IllegalArgumentException> { LayoutPlanner.visibleSlots(0, 454) }
        assertFailsWith<IllegalArgumentException> { LayoutPlanner.visibleSlots(4, 454, 0) }
    }

    @Test
    fun screenShapeByIdRoundTrips() {
        ScreenShape.entries.forEach { assertEquals(it, ScreenShape.byId(it.id)) }
        assertNull(ScreenShape.byId("oval"))
    }

    private companion object {
        const val TOLERANCE = 1e-6
        const val BLOCK_HEIGHT = 40.0
        const val BLOCK_STEP = 20.0
    }
}
