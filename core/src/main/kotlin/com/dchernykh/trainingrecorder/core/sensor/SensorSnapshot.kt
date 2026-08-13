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
         * Folds the two sources into what the screens read. External wins while
         * it is fresh; otherwise the built-in reading is used if there is one.
         */
        fun merge(
            external: Map<String, SensorReading>,
            builtIn: Map<String, SensorReading>,
            nowEpochMs: Long,
            connectedProfiles: Set<SensorProfile> = emptySet(),
            staleAfterMs: Long = EXTERNAL_STALE_AFTER_MS,
        ): SensorSnapshot {
            require(staleAfterMs > 0) { "the staleness window must be positive" }
            val merged = builtIn.toMutableMap()
            external.forEach { (fieldId, reading) ->
                if (nowEpochMs - reading.atEpochMs <= staleAfterMs) merged[fieldId] = reading
            }
            return SensorSnapshot(merged.toMap(), connectedProfiles)
        }
    }
}
