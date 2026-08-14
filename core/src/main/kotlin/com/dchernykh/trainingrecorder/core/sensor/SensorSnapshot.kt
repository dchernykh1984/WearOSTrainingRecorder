package com.dchernykh.trainingrecorder.core.sensor

import com.dchernykh.trainingrecorder.core.field.SensorProfile

/** Where a live value came from, which decides what wins when both are present. */
enum class SensorOrigin(
    val id: String,
) {
    /** A paired Bluetooth LE sensor - a strap, a power meter, a speed pod. */
    EXTERNAL("external"),

    /** The watch itself: optical heart rate, GNSS, barometer, accelerometer. */
    BUILT_IN("built_in"),
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
         * Longer than the external window on purpose: Health Services delivers
         * in batches, and blanking a field because one batch was late would
         * flicker for no reason. Fifteen seconds is far more than a late batch
         * and far less than a rider staring at a number that stopped being true.
         *
         * This window is why the constant exists at all. Built-in readings used
         * to be copied into the snapshot without any check, so a source that
         * went quiet left its last value on screen for the rest of the ride -
         * a heart rate frozen at 66 while the rider moved about, looking every
         * bit as live as a real one.
         */
        const val BUILT_IN_STALE_AFTER_MS = 15_000L

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
            )

        /**
         * Folds the two sources into what the screens read. External wins while
         * it is fresh; otherwise the built-in reading is used while it is fresh
         * in its own right; when neither is, the field shows nothing.
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
            val merged = mutableMapOf<String, SensorReading>()
            builtIn.forEach { (fieldId, reading) ->
                if (isCurrent(fieldId, reading, nowEpochMs, builtInStaleAfterMs)) merged[fieldId] = reading
            }
            external.forEach { (fieldId, reading) ->
                if (isCurrent(fieldId, reading, nowEpochMs, staleAfterMs)) merged[fieldId] = reading
            }
            return SensorSnapshot(merged.toMap(), connectedProfiles)
        }

        private fun isCurrent(
            fieldId: String,
            reading: SensorReading,
            nowEpochMs: Long,
            windowMs: Long,
        ): Boolean = fieldId in CUMULATIVE_FIELDS || nowEpochMs - reading.atEpochMs <= windowMs
    }
}
