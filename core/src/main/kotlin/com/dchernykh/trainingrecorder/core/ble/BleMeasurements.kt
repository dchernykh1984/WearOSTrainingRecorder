package com.dchernykh.trainingrecorder.core.ble

/**
 * Parsers for the Bluetooth LE fitness characteristics.
 *
 * These live in the shared module rather than the watch app on purpose: they are
 * pure byte handling, and byte handling is exactly the code that is worth testing
 * without a device. Every value is little-endian, which is the one rule the whole
 * GATT spec follows.
 */
internal object Le {
    fun u8(
        data: ByteArray,
        offset: Int,
    ): Int = data[offset].toInt() and 0xFF

    fun u16(
        data: ByteArray,
        offset: Int,
    ): Int = u8(data, offset) or (u8(data, offset + 1) shl 8)

    fun s16(
        data: ByteArray,
        offset: Int,
    ): Int = u16(data, offset).toShort().toInt()

    fun u32(
        data: ByteArray,
        offset: Int,
    ): Long = (u16(data, offset).toLong()) or (u16(data, offset + 2).toLong() shl 16)
}

/** Heart Rate Measurement, characteristic `0x2A37`. */
data class HeartRateMeasurement(
    val heartRateBpm: Int,
    val energyExpendedKj: Int? = null,
    val rrIntervalsMs: List<Double> = emptyList(),
    val sensorContact: Boolean? = null,
) {
    companion object {
        private const val FLAG_UINT16 = 0x01
        private const val FLAG_CONTACT_DETECTED = 0x02
        private const val FLAG_CONTACT_SUPPORTED = 0x04
        private const val FLAG_ENERGY = 0x08
        private const val FLAG_RR = 0x10

        /** RR intervals are counted in 1/1024 of a second. */
        private const val RR_UNITS_PER_SECOND = 1024.0
        private const val MILLIS_PER_SECOND = 1000.0

        // Guard clauses against short packets. A malformed notification is
        // common on cheap sensors, and bailing out early is clearer than
        // threading a nullable result through the offset arithmetic.
        @Suppress("ReturnCount")
        fun parse(data: ByteArray): HeartRateMeasurement? {
            if (data.size < 2) return null
            val flags = Le.u8(data, 0)
            val wide = flags and FLAG_UINT16 != 0
            var offset = 1
            val bpm =
                if (wide) {
                    if (data.size < offset + 2) return null
                    Le.u16(data, offset).also { offset += 2 }
                } else {
                    Le.u8(data, offset).also { offset += 1 }
                }
            val contact =
                if (flags and FLAG_CONTACT_SUPPORTED != 0) flags and FLAG_CONTACT_DETECTED != 0 else null
            var energy: Int? = null
            if (flags and FLAG_ENERGY != 0) {
                if (data.size < offset + 2) return null
                energy = Le.u16(data, offset)
                offset += 2
            }
            val rr = mutableListOf<Double>()
            if (flags and FLAG_RR != 0) {
                while (offset + 1 < data.size) {
                    rr += Le.u16(data, offset) * MILLIS_PER_SECOND / RR_UNITS_PER_SECOND
                    offset += 2
                }
            }
            return HeartRateMeasurement(bpm, energy, rr, contact)
        }
    }
}

/** Cycling Speed and Cadence Measurement, characteristic `0x2A5B`. */
data class CscMeasurement(
    val cumulativeWheelRevolutions: Long? = null,
    val lastWheelEventTime: Int? = null,
    val cumulativeCrankRevolutions: Int? = null,
    val lastCrankEventTime: Int? = null,
) {
    companion object {
        private const val FLAG_WHEEL = 0x01
        private const val FLAG_CRANK = 0x02

        // Guard clauses against short packets. A malformed notification is
        // common on cheap sensors, and bailing out early is clearer than
        // threading a nullable result through the offset arithmetic.
        @Suppress("ReturnCount")
        fun parse(data: ByteArray): CscMeasurement? {
            if (data.isEmpty()) return null
            val flags = Le.u8(data, 0)
            var offset = 1
            var wheelRevs: Long? = null
            var wheelTime: Int? = null
            if (flags and FLAG_WHEEL != 0) {
                if (data.size < offset + 6) return null
                wheelRevs = Le.u32(data, offset)
                wheelTime = Le.u16(data, offset + 4)
                offset += 6
            }
            var crankRevs: Int? = null
            var crankTime: Int? = null
            if (flags and FLAG_CRANK != 0) {
                if (data.size < offset + 4) return null
                crankRevs = Le.u16(data, offset)
                crankTime = Le.u16(data, offset + 2)
            }
            return CscMeasurement(wheelRevs, wheelTime, crankRevs, crankTime)
        }
    }
}

/** Cycling Power Measurement, characteristic `0x2A63`. */
data class CyclingPowerMeasurement(
    val instantaneousPowerWatts: Int,
    val pedalPowerBalancePercent: Double? = null,
    val accumulatedEnergyKj: Int? = null,
    val cumulativeCrankRevolutions: Int? = null,
    val lastCrankEventTime: Int? = null,
) {
    companion object {
        private const val FLAG_BALANCE = 0x0001
        private const val FLAG_TORQUE = 0x0004
        private const val FLAG_WHEEL = 0x0010
        private const val FLAG_CRANK = 0x0020
        private const val FLAG_EXTREME_FORCE = 0x0040
        private const val FLAG_EXTREME_TORQUE = 0x0080
        private const val FLAG_EXTREME_ANGLES = 0x0100
        private const val FLAG_TOP_DEAD_SPOT = 0x0200
        private const val FLAG_BOTTOM_DEAD_SPOT = 0x0400
        private const val FLAG_ENERGY = 0x0800

        /** Balance is reported in halves of a percent. */
        private const val BALANCE_UNITS_PER_PERCENT = 2.0

        // Guard clauses against short packets. A malformed notification is
        // common on cheap sensors, and bailing out early is clearer than
        // threading a nullable result through the offset arithmetic.
        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        fun parse(data: ByteArray): CyclingPowerMeasurement? {
            if (data.size < 4) return null
            val flags = Le.u16(data, 0)
            val power = Le.s16(data, 2)
            var offset = 4
            var balance: Double? = null
            if (flags and FLAG_BALANCE != 0) {
                if (data.size < offset + 1) return null
                balance = Le.u8(data, offset) / BALANCE_UNITS_PER_PERCENT
                offset += 1
            }
            if (flags and FLAG_TORQUE != 0) offset += 2
            if (flags and FLAG_WHEEL != 0) offset += 6
            var crankRevs: Int? = null
            var crankTime: Int? = null
            if (flags and FLAG_CRANK != 0) {
                if (data.size < offset + 4) return null
                crankRevs = Le.u16(data, offset)
                crankTime = Le.u16(data, offset + 2)
                offset += 4
            }
            if (flags and FLAG_EXTREME_FORCE != 0) offset += 4
            if (flags and FLAG_EXTREME_TORQUE != 0) offset += 4
            if (flags and FLAG_EXTREME_ANGLES != 0) offset += 3
            if (flags and FLAG_TOP_DEAD_SPOT != 0) offset += 2
            if (flags and FLAG_BOTTOM_DEAD_SPOT != 0) offset += 2
            var energy: Int? = null
            if (flags and FLAG_ENERGY != 0 && data.size >= offset + 2) energy = Le.u16(data, offset)
            return CyclingPowerMeasurement(power, balance, energy, crankRevs, crankTime)
        }
    }
}

/** Running Speed and Cadence Measurement, characteristic `0x2A53`. */
data class RscMeasurement(
    val speedMps: Double,
    val cadenceSpm: Int,
    val strideLengthMeters: Double? = null,
    val totalDistanceMeters: Double? = null,
    val running: Boolean? = null,
) {
    companion object {
        private const val FLAG_STRIDE = 0x01
        private const val FLAG_DISTANCE = 0x02
        private const val FLAG_RUNNING = 0x04

        private const val SPEED_UNITS_PER_MPS = 256.0
        private const val STRIDE_UNITS_PER_METER = 100.0
        private const val DISTANCE_UNITS_PER_METER = 10.0

        // Guard clauses against short packets. A malformed notification is
        // common on cheap sensors, and bailing out early is clearer than
        // threading a nullable result through the offset arithmetic.
        @Suppress("ReturnCount")
        fun parse(data: ByteArray): RscMeasurement? {
            if (data.size < 4) return null
            val flags = Le.u8(data, 0)
            val speed = Le.u16(data, 1) / SPEED_UNITS_PER_MPS
            val cadence = Le.u8(data, 3)
            var offset = 4
            var stride: Double? = null
            if (flags and FLAG_STRIDE != 0) {
                if (data.size < offset + 2) return null
                stride = Le.u16(data, offset) / STRIDE_UNITS_PER_METER
                offset += 2
            }
            var distance: Double? = null
            if (flags and FLAG_DISTANCE != 0) {
                if (data.size < offset + 4) return null
                distance = Le.u32(data, offset) / DISTANCE_UNITS_PER_METER
            }
            val running = if (flags and FLAG_RUNNING != 0) true else null
            return RscMeasurement(speed, cadence, stride, distance, running)
        }
    }
}

/**
 * Turns the revolution counters into a rate.
 *
 * Both counters and their event timers wrap - the crank counter every 65536
 * revolutions and the timer every 64 seconds, because it counts 1/1024 s in
 * sixteen bits. Ignoring that produces a spike to an absurd cadence roughly once
 * a minute, which is the classic bug in this profile, so the arithmetic is done
 * modulo the field width.
 */
object RevolutionRate {
    private const val UINT16 = 0x1_0000L
    private const val TIME_UNITS_PER_SECOND = 1024.0
    private const val SECONDS_PER_MINUTE = 60.0

    /** Revolutions per minute between two samples, or null when nothing moved. */
    fun rpm(
        previousRevolutions: Long,
        previousEventTime: Int,
        currentRevolutions: Long,
        currentEventTime: Int,
        revolutionModulus: Long = UINT16,
    ): Double? {
        val ticks = Math.floorMod(currentEventTime - previousEventTime, UINT16.toInt()).toDouble()
        if (ticks <= 0.0) return null
        val revolutions = Math.floorMod(currentRevolutions - previousRevolutions, revolutionModulus).toDouble()
        val seconds = ticks / TIME_UNITS_PER_SECOND
        return revolutions / seconds * SECONDS_PER_MINUTE
    }

    /** Wheel counters are 32-bit, so they need a wider modulus. */
    const val UINT32_MODULUS = 0x1_0000_0000L
}
