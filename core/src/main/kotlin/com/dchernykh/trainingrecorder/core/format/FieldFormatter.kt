package com.dchernykh.trainingrecorder.core.format

import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Metric or imperial, chosen once and applied to every field. */
enum class UnitSystem(
    val id: String,
) {
    METRIC("metric"),
    IMPERIAL("imperial"),
    ;

    companion object {
        fun byId(id: String): UnitSystem? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Turns raw values into what a slot shows.
 *
 * Formatting lives here rather than in the UI because it is where the fiddly
 * decisions are - a pace of zero is not "0:00" but nothing at all, a duration
 * drops its hours until there are some, and a distance changes precision as it
 * grows. All of that is worth testing without a screen.
 */
object FieldFormatter {
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
    private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
    private const val MILLIS_PER_SECOND = 1000L
    private const val METERS_PER_KM = 1000.0
    private const val METERS_PER_MILE = 1609.344
    private const val METERS_PER_FOOT = 0.3048
    private const val SECONDS_PER_HOUR_D = 3600.0
    private const val SHORT_DISTANCE_METERS = 1000.0
    private const val PRECISE_DISTANCE_KM = 100.0
    private const val FEET_PER_MILE = 5280
    private const val SWIM_UNIT_METERS = 100.0

    /** A pace slower than this is a stop, not a pace worth showing. */
    private const val SLOWEST_MEANINGFUL_PACE_SECONDS = 3599.0

    val empty: String get() = FieldCatalogue.EMPTY_VALUE

    /** `h:mm:ss`, or `m:ss` while under an hour, which is what fits a watch. */
    fun duration(millis: Long?): String {
        if (millis == null || millis < 0) return empty
        val total = millis / MILLIS_PER_SECOND
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            "$hours:${pad(minutes)}:${pad(seconds)}"
        } else {
            "$minutes:${pad(seconds)}"
        }
    }

    /**
     * Kilometres or miles, with a small-unit reading until the first whole one.
     *
     * Guard clauses: absent, negative, and below one unit in each system.
     */
    @Suppress("ReturnCount")
    fun distance(
        meters: Double?,
        units: UnitSystem = UnitSystem.METRIC,
    ): String {
        if (meters == null || meters < 0) return empty
        // Below the first whole unit a decimal reading is useless - "0.06 mi"
        // tells a runner nothing - so both systems fall back to their small unit.
        // Rounded first, then compared: 999.6 m rounds to 1000, and "1000 m"
        // beside a field that switches to kilometres at 1000 reads as a bug.
        if (units == UnitSystem.METRIC) {
            val whole = meters.roundToLong()
            if (whole < SHORT_DISTANCE_METERS) return "$whole m"
        } else {
            val feet = (meters / METERS_PER_FOOT).roundToLong()
            if (feet < FEET_PER_MILE) return "$feet ft"
        }
        val value = if (units == UnitSystem.METRIC) meters / METERS_PER_KM else meters / METERS_PER_MILE
        val suffix = if (units == UnitSystem.METRIC) "km" else "mi"
        val decimals = if (value >= PRECISE_DISTANCE_KM) 1 else 2
        return "${trim(value, decimals)} $suffix"
    }

    fun speed(
        metersPerSecond: Double?,
        units: UnitSystem = UnitSystem.METRIC,
    ): String {
        if (metersPerSecond == null || metersPerSecond < 0) return empty
        val perHour = metersPerSecond * SECONDS_PER_HOUR_D
        val value = if (units == UnitSystem.METRIC) perHour / METERS_PER_KM else perHour / METERS_PER_MILE
        val suffix = if (units == UnitSystem.METRIC) "km/h" else "mph"
        return "${trim(value, 1)} $suffix"
    }

    /**
     * Minutes and seconds per kilometre or mile. Standing still has no pace, so
     * it reads empty rather than as an enormous number.
     */
    fun pace(
        metersPerSecond: Double?,
        units: UnitSystem = UnitSystem.METRIC,
    ): String {
        val unitMeters = if (units == UnitSystem.METRIC) METERS_PER_KM else METERS_PER_MILE
        val suffix = if (units == UnitSystem.METRIC) "/km" else "/mi"
        return paceOver(metersPerSecond, unitMeters, suffix)
    }

    /**
     * Swimming pace, per 100 m regardless of the unit system: every pool in the
     * world is measured in metres, and a swimmer who reads miles per hour on the
     * bike still wants 1:45 /100m in the water.
     */
    fun pacePer100m(metersPerSecond: Double?): String = paceOver(metersPerSecond, SWIM_UNIT_METERS, "/100m")

    private fun paceOver(
        metersPerSecond: Double?,
        unitMeters: Double,
        suffix: String,
    ): String {
        if (metersPerSecond == null || metersPerSecond <= 0) return empty
        val secondsPerUnit = unitMeters / metersPerSecond
        if (secondsPerUnit > SLOWEST_MEANINGFUL_PACE_SECONDS) return empty
        val minutes = (secondsPerUnit / SECONDS_PER_MINUTE).toInt()
        val seconds = (secondsPerUnit % SECONDS_PER_MINUTE).roundToInt()
        return if (seconds == SECONDS_PER_MINUTE) {
            "${minutes + 1}:00$suffix"
        } else {
            "$minutes:${pad(seconds.toLong())}$suffix"
        }
    }

    fun elevation(
        meters: Double?,
        units: UnitSystem = UnitSystem.METRIC,
    ): String {
        if (meters == null) return empty
        val value = if (units == UnitSystem.METRIC) meters else meters / METERS_PER_FOOT
        val suffix = if (units == UnitSystem.METRIC) "m" else "ft"
        return "${value.roundToLong()} $suffix"
    }

    fun integer(
        value: Double?,
        suffix: String = "",
    ): String {
        if (value == null) return empty
        val text = value.roundToLong().toString()
        return if (suffix.isEmpty()) text else "$text $suffix"
    }

    fun percent(value: Double?): String = if (value == null) empty else "${value.roundToInt()}%"

    /**
     * For ratios that live below one - intensity factor, watts per kilogram -
     * where rounding to a whole number throws the field away entirely.
     */
    fun decimal(
        value: Double?,
        decimals: Int = 2,
    ): String = if (value == null) empty else trim(value, decimals)

    /** Signed, because a gradient reads very differently up and down. */
    fun grade(percentValue: Double?): String {
        if (percentValue == null) return empty
        val sign = if (percentValue > 0) "+" else ""
        return "$sign${trim(percentValue, 1)}%"
    }

    fun clockTime(
        epochMs: Long?,
        offsetMinutes: Int = 0,
    ): String {
        if (epochMs == null || epochMs < 0) return empty
        val local = epochMs / MILLIS_PER_SECOND + offsetMinutes.toLong() * SECONDS_PER_MINUTE
        val minutesOfDay = Math.floorMod(local / SECONDS_PER_MINUTE, MINUTES_PER_HOUR.toLong() * 24)
        return "${minutesOfDay / MINUTES_PER_HOUR}:${pad(minutesOfDay % MINUTES_PER_HOUR)}"
    }

    private fun pad(value: Long): String = if (value < 10) "0$value" else value.toString()

    /** Drops a trailing zero so `12.50` reads `12.5` and `12.00` reads `12`. */
    private fun trim(
        value: Double,
        decimals: Int,
    ): String {
        val factor = generateSequence(1.0) { it * 10 }.elementAt(decimals)
        val rounded = (value * factor).roundToLong() / factor
        if (abs(rounded - rounded.roundToLong()) < 1.0 / (factor * 2)) return rounded.roundToLong().toString()
        val text = rounded.toString()
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }
}
