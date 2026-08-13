package com.dchernykh.trainingrecorder.core.layout

import kotlin.math.abs
import kotlin.math.sqrt

/** The physical shape of the watch face the screens are drawn on. */
enum class ScreenShape(
    val id: String,
) {
    ROUND("round"),
    SQUARE("square"),
    ;

    companion object {
        fun byId(id: String): ScreenShape? = entries.firstOrNull { it.id == id }
    }
}

/** A horizontal band of a round layout, holding one or two cells. */
data class Band(
    val columns: Int,
) {
    init {
        require(columns in 1..MAX_COLUMNS) { "a band holds 1 or 2 cells, got $columns" }
    }

    companion object {
        const val MAX_COLUMNS = 2
    }
}

/** A uniform grid, which is what a square screen wants. */
data class Grid(
    val columns: Int,
    val rows: Int,
) {
    init {
        require(columns >= 1) { "a grid needs at least one column" }
        require(rows >= 1) { "a grid needs at least one row" }
    }

    val cellCount: Int get() = columns * rows
}

/**
 * Turns a slot count into a shape.
 *
 * The round rule is not invented here: it reproduces the band structure Garmin's
 * own native data-field layouts use, which the sibling GarminRaceStats project
 * verified against every round device in the Connect IQ SDK. Two invariants come
 * out of that survey and are enforced by tests - the first and last band are
 * always full width, because the bezel eats the corners hardest there, and the
 * bands hold exactly as many cells as were asked for.
 */
object LayoutPlanner {
    /**
     * 1 -> [1], 2 -> [1,1], 3 -> [1,1,1], 4 -> [1,2,1], 5 -> [1,1,2,1],
     * 6 -> [1,2,2,1], 8 -> [1,2,2,2,1].
     */
    fun planRound(slotCount: Int): List<Band> {
        require(slotCount >= 1) { "a screen shows at least one slot, got $slotCount" }
        if (slotCount == 1) return listOf(Band(1))
        if (slotCount == 2) return listOf(Band(1), Band(1))

        val bands = mutableListOf(Band(1))
        var interior = slotCount - 2
        if (interior % 2 == 1) {
            // An odd interior gets one full-width band just below the top, which
            // is what Garmin's own "5 Fields" round layout does.
            bands += Band(1)
            interior--
        }
        repeat(interior / 2) { bands += Band(Band.MAX_COLUMNS) }
        bands += Band(1)
        return bands
    }

    /**
     * A square screen has no bezel to work around, so it takes a plain grid.
     * Two columns need enough slots to be worth splitting and enough width for a
     * value to stay readable.
     */
    fun planSquare(
        slotCount: Int,
        widthPx: Int,
        heightPx: Int,
    ): Grid {
        require(slotCount >= 1) { "a screen shows at least one slot, got $slotCount" }
        require(widthPx > 0 && heightPx > 0) { "screen size must be positive, got ${widthPx}x$heightPx" }
        val twoColumns =
            slotCount >= MIN_SLOTS_FOR_TWO_COLUMNS &&
                widthPx >= MIN_TWO_COLUMN_WIDTH_PX &&
                heightPx >= MIN_CELL_HEIGHT_PX * 2
        val columns = if (twoColumns) 2 else 1
        return Grid(columns = columns, rows = (slotCount + columns - 1) / columns)
    }

    fun cellCount(bands: List<Band>): Int = bands.sumOf { it.columns }

    /**
     * Half the width of the circle's chord at [dy] from the centre. Zero past
     * the rim rather than a domain error, so callers need no bounds check.
     */
    fun halfChord(
        radius: Double,
        dy: Double,
    ): Double = if (abs(dy) >= radius) 0.0 else sqrt(radius * radius - dy * dy)

    /**
     * The horizontal span that stays on screen across the whole height of a
     * block, not merely at its middle. Measuring at the middle is what lets a
     * caption's corners fall off a round screen.
     */
    fun spanFor(
        radius: Double,
        top: Double,
        bottom: Double,
    ): ClosedFloatingPointRange<Double> {
        require(bottom >= top) { "bottom ($bottom) must not be above top ($top)" }
        val dy = maxOf(abs(radius - top), abs(bottom - radius))
        val half = halfChord(radius, dy)
        return (radius - half)..(radius + half)
    }

    /** Slots beyond what the screen can show at a legible size are dropped. */
    fun visibleSlots(
        slotCount: Int,
        heightPx: Int,
        minRowHeightPx: Int = MIN_ROW_HEIGHT_PX,
    ): Int {
        require(slotCount >= 1) { "a screen shows at least one slot, got $slotCount" }
        require(minRowHeightPx >= 1) { "a row needs a positive minimum height" }
        return minOf(slotCount, maxOf(1, heightPx / minRowHeightPx))
    }

    const val MIN_SLOTS_FOR_TWO_COLUMNS = 4
    const val MIN_TWO_COLUMN_WIDTH_PX = 180
    const val MIN_CELL_HEIGHT_PX = 34
    const val MIN_ROW_HEIGHT_PX = 22
}
