package com.dchernykh.trainingrecorder.core.sensor

import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.field.SensorProfile

/** Where a live value came from, which decides what wins when both are present. */
enum class SensorOrigin(
    val id: String,
) {
    /** A paired Bluetooth LE sensor - a strap, a power meter, a speed pod. */
    EXTERNAL("external"),

    /** The watch itself: optical heart rate, GNSS, barometer, accelerometer. */
    BUILT_IN("built_in"),

    /**
     * Worked out by the app from other readings rather than measured: averages,
     * maxima, rolling power, gradient, climb.
     *
     * A third origin because the takeover rule is about two *sensors*
     * disagreeing, and a statistic is not a sensor. Marked BUILT_IN, every
     * derived power figure was thrown away the moment a power meter connected -
     * which is the only time any of them mean anything - because the rule saw a
     * field belonging to a connected profile and dropped the watch's answer,
     * while the meter itself reports only instantaneous watts and never an
     * average.
     */
    DERIVED("derived"),
    ;

    companion object {
        fun byId(id: String): SensorOrigin? = entries.firstOrNull { it.id == id }
    }
}

/** One reading, tagged with where it came from and when. */
data class SensorReading(
    val value: Double,
    val origin: SensorOrigin,
    val atEpochMs: Long,
)

/**
 * The live values a screen renders from.
 *
 * The rule the whole app hangs on lives in [merge]: an external sensor always
 * beats the watch's own, a stale external reading falls back to the built-in
 * one, and when neither exists the field simply shows nothing. That is the
 * "strap, else the optical sensor, else write nothing" behaviour, kept here in
 * plain Kotlin so it is testable without a watch.
 */
data class SensorSnapshot(
    val readings: Map<String, SensorReading> = emptyMap(),
    val connectedProfiles: Set<SensorProfile> = emptySet(),
) {
    fun value(fieldId: String): Double? = readings[fieldId]?.value

    fun origin(fieldId: String): SensorOrigin? = readings[fieldId]?.origin

    fun isConnected(profile: SensorProfile): Boolean = profile in connectedProfiles

    companion object {
        /**
         * How long an external reading stays authoritative after its last
         * update. A chest strap notifies about once a second, so three seconds
         * is a missed beat or two rather than a real dropout - long enough not
         * to flap, short enough that a strap falling off hands over to the
         * optical sensor before the rider notices.
         */
        const val EXTERNAL_STALE_AFTER_MS = 3_000L

        /**
         * How long the watch's own reading stays on screen after its last
         * update.
         *
         * Much longer than the external window, because the two sources behave
         * nothing alike. A strap notifies about once a second, so three seconds
         * of silence means something. Health Services *batches*, and how long it
         * holds a batch is its decision, not ours - with the screen off it can
         * be a minute or more, which is exactly the saving the app goes through
         * it for. Fifteen seconds looked reasonable and was not: a rider who let
         * the screen sleep and looked again found a blank heart rate, and the
         * window that was meant to stop a lie was producing one of its own.
         *
         * The window still exists for the case it was written for. Built-in
         * readings used to be copied in without any check at all, so a source
         * that went quiet left its last value on screen for the rest of the
         * ride - a heart rate frozen at 66 while the rider moved about, looking
         * every bit as live as a real one. Two minutes is far beyond any batch
         * and far short of that.
         */
        const val BUILT_IN_STALE_AFTER_MS = 120_000L

        /**
         * Readings that are running totals rather than measurements of now.
         *
         * These must never age out. Distance does not stop being true because no
         * batch arrived in the last fifteen seconds - it is the same distance,
         * and blanking it mid-ride would be a worse lie than showing it. Only
         * values that describe this instant can go stale.
         */
        val CUMULATIVE_FIELDS =
            setOf(
                "distance_total",
                "distance_lap",
                "distance_lap_last",
                "ascent_total",
                "descent_total",
                "calories",
                "lap_count",
                "stroke_count",
                "lengths",
                // Averages and maxima are facts about the ride so far, not
                // measurements of this instant. Left out, they aged like a live
                // reading: Health Services can batch minutes apart, and the
                // average speed of a ride in progress would blank between
                // batches - which is the same lie the staleness rule exists to
                // prevent, told about a number that cannot go stale.
                "speed_avg",
                "speed_max",
                "hr_avg",
                "hr_max",
                "cadence_avg",
                "cadence_max",
                "power_avg",
                "power_max",
                "power_normalized",
            )

        /**
         * Folds the two sources into what the screens read.
         *
         * A connected external sensor takes its fields over completely: the
         * watch's own reading for those fields is not used at all, not even
         * while the sensor is between notifications. That is what a rider means
         * by pairing a strap - a chest strap and an optical sensor disagree, and
         * a field that quietly alternates between them is worse than either.
         * Where no sensor is connected the built-in reading stands on its own,
         * and where neither is current the field shows nothing.
         */
        fun merge(
            external: Map<String, SensorReading>,
            builtIn: Map<String, SensorReading>,
            nowEpochMs: Long,
            connectedProfiles: Set<SensorProfile> = emptySet(),
            staleAfterMs: Long = EXTERNAL_STALE_AFTER_MS,
            builtInStaleAfterMs: Long = BUILT_IN_STALE_AFTER_MS,
        ): SensorSnapshot {
            require(staleAfterMs > 0) { "the staleness window must be positive" }
            require(builtInStaleAfterMs > 0) { "the built-in staleness window must be positive" }
            val superseded = fieldsCoveredBy(connectedProfiles)
            val merged = mutableMapOf<String, SensorReading>()
            builtIn.forEach { (fieldId, reading) ->
                // Only a measurement is superseded. What the app worked out for
                // itself has no competitor: a power meter reports watts, never
                // the thirty-second average of them.
                if (reading.origin == SensorOrigin.BUILT_IN && fieldId in superseded) return@forEach
                if (isCurrent(fieldId, reading, nowEpochMs, builtInStaleAfterMs)) merged[fieldId] = reading
            }
            external.forEach { (fieldId, reading) ->
                if (isCurrent(fieldId, reading, nowEpochMs, staleAfterMs)) merged[fieldId] = reading
            }
            return SensorSnapshot(merged.toMap(), connectedProfiles)
        }

        /**
         * The fields a connected sensor owns, and which the watch therefore
         * stops contributing to.
         *
         * Read from the catalogue rather than listed here: which sensor supplies
         * a field is already recorded once, on the field, and a second copy of
         * that mapping is a second thing to forget to update.
         */
        fun fieldsCoveredBy(connectedProfiles: Set<SensorProfile>): Set<String> {
            if (connectedProfiles.isEmpty()) return emptySet()
            return FieldCatalogue.all
                .filter { it.preferredProfile in connectedProfiles }
                .map { it.id }
                .toSet()
        }

        private fun isCurrent(
            fieldId: String,
            reading: SensorReading,
            nowEpochMs: Long,
            windowMs: Long,
        ): Boolean = fieldId in CUMULATIVE_FIELDS || nowEpochMs - reading.atEpochMs <= windowMs
    }
}
