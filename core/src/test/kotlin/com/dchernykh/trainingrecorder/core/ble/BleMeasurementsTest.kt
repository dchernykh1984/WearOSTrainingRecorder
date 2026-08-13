package com.dchernykh.trainingrecorder.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleMeasurementsTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // ---- Heart Rate, 0x2A37 ----

    @Test
    fun anEightBitHeartRateIsRead() {
        val measurement = assertNotNull(HeartRateMeasurement.parse(bytes(0x00, 0x48)))
        assertEquals(72, measurement.heartRateBpm)
        assertNull(measurement.sensorContact)
        assertTrue(measurement.rrIntervalsMs.isEmpty())
    }

    @Test
    fun aSixteenBitHeartRateIsRead() {
        // Flag bit 0 set, value 0x0123 = 291 - above what a byte can hold.
        val measurement = assertNotNull(HeartRateMeasurement.parse(bytes(0x01, 0x23, 0x01)))
        assertEquals(291, measurement.heartRateBpm)
    }

    @Test
    fun sensorContactIsOnlyReportedWhenTheStrapSupportsIt() {
        assertNull(HeartRateMeasurement.parse(bytes(0x00, 0x48))!!.sensorContact)
        assertEquals(false, HeartRateMeasurement.parse(bytes(0x04, 0x48))!!.sensorContact)
        assertEquals(true, HeartRateMeasurement.parse(bytes(0x06, 0x48))!!.sensorContact)
    }

    @Test
    fun energyExpendedAndRrIntervalsAreReadInOrder() {
        // Flags 0x18 = energy + RR. Energy 0x0064 = 100 kJ, then two RR values.
        val data = bytes(0x18, 0x48, 0x64, 0x00, 0x00, 0x04, 0x00, 0x03)
        val measurement = assertNotNull(HeartRateMeasurement.parse(data))
        assertEquals(72, measurement.heartRateBpm)
        assertEquals(100, measurement.energyExpendedKj)
        assertEquals(2, measurement.rrIntervalsMs.size)
        assertEquals(1000.0, measurement.rrIntervalsMs[0], 0.01, "1024 ticks is exactly one second")
        assertEquals(750.0, measurement.rrIntervalsMs[1], 0.01)
    }

    @Test
    fun aTruncatedHeartRatePacketIsRejectedRatherThanGuessed() {
        assertNull(HeartRateMeasurement.parse(bytes()))
        assertNull(HeartRateMeasurement.parse(bytes(0x00)))
        assertNull(HeartRateMeasurement.parse(bytes(0x01, 0x23)), "a 16-bit value needs two bytes")
        assertNull(HeartRateMeasurement.parse(bytes(0x08, 0x48, 0x64)), "energy needs two bytes")
    }

    // ---- Cycling Speed and Cadence, 0x2A5B ----

    @Test
    fun crankOnlyCadenceDataIsRead() {
        // Flags 0x02 = crank present. 300 revolutions at tick 0x0400.
        val measurement = assertNotNull(CscMeasurement.parse(bytes(0x02, 0x2C, 0x01, 0x00, 0x04)))
        assertNull(measurement.cumulativeWheelRevolutions)
        assertEquals(300, measurement.cumulativeCrankRevolutions)
        assertEquals(1024, measurement.lastCrankEventTime)
    }

    @Test
    fun wheelAndCrankTogetherAreReadAtTheRightOffsets() {
        // Flags 0x03. Wheel 0x00010000 revs at tick 0x0100, crank 10 at tick 0x0200.
        val data = bytes(0x03, 0x00, 0x00, 0x01, 0x00, 0x00, 0x01, 0x0A, 0x00, 0x00, 0x02)
        val measurement = assertNotNull(CscMeasurement.parse(data))
        assertEquals(65536L, measurement.cumulativeWheelRevolutions)
        assertEquals(256, measurement.lastWheelEventTime)
        assertEquals(10, measurement.cumulativeCrankRevolutions)
        assertEquals(512, measurement.lastCrankEventTime)
    }

    @Test
    fun aTruncatedCscPacketIsRejected() {
        assertNull(CscMeasurement.parse(bytes()))
        assertNull(CscMeasurement.parse(bytes(0x01, 0x00, 0x00)))
        assertNull(CscMeasurement.parse(bytes(0x02, 0x2C)))
    }

    // ---- Cycling Power, 0x2A63 ----

    @Test
    fun instantaneousPowerIsMandatoryAndSigned() {
        val measurement = assertNotNull(CyclingPowerMeasurement.parse(bytes(0x00, 0x00, 0xFA, 0x00)))
        assertEquals(250, measurement.instantaneousPowerWatts)
        // Coasting downhill against a headwind really can read negative.
        val negative = assertNotNull(CyclingPowerMeasurement.parse(bytes(0x00, 0x00, 0xFF, 0xFF)))
        assertEquals(-1, negative.instantaneousPowerWatts)
    }

    @Test
    fun pedalBalanceIsReadInHalfPercent() {
        // Flag 0x0001, balance 0x65 = 101 half-percent = 50.5%.
        val measurement = assertNotNull(CyclingPowerMeasurement.parse(bytes(0x01, 0x00, 0xFA, 0x00, 0x65)))
        assertEquals(50.5, measurement.pedalPowerBalancePercent!!, 0.001)
    }

    @Test
    fun crankDataIsFoundAfterTheOptionalFieldsThatPrecedeIt() {
        // Flags 0x0025 = balance + torque + crank, so crank sits after 1 + 2 bytes.
        val data = bytes(0x25, 0x00, 0xFA, 0x00, 0x65, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x02)
        val measurement = assertNotNull(CyclingPowerMeasurement.parse(data))
        assertEquals(250, measurement.instantaneousPowerWatts)
        assertEquals(50.5, measurement.pedalPowerBalancePercent!!, 0.001)
        assertEquals(10, measurement.cumulativeCrankRevolutions)
        assertEquals(512, measurement.lastCrankEventTime)
    }

    @Test
    fun aTruncatedPowerPacketIsRejected() {
        assertNull(CyclingPowerMeasurement.parse(bytes(0x00, 0x00, 0xFA)))
        assertNull(CyclingPowerMeasurement.parse(bytes(0x01, 0x00, 0xFA, 0x00)))
    }

    // ---- Running Speed and Cadence, 0x2A53 ----

    @Test
    fun speedAndCadenceAreReadInTheirOwnUnits() {
        // 0x0200 = 512 units = 2 m/s; cadence 180 steps per minute.
        val measurement = assertNotNull(RscMeasurement.parse(bytes(0x00, 0x00, 0x02, 0xB4)))
        assertEquals(2.0, measurement.speedMps, 0.001)
        assertEquals(180, measurement.cadenceSpm)
        assertNull(measurement.running)
    }

    @Test
    fun strideLengthAndDistanceAreReadWhenPresent() {
        // Flags 0x07 = stride + distance + running. Stride 0x0078 = 1.20 m,
        // distance 0x000003E8 = 1000 decimetres = 100 m.
        val data = bytes(0x07, 0x00, 0x02, 0xB4, 0x78, 0x00, 0xE8, 0x03, 0x00, 0x00)
        val measurement = assertNotNull(RscMeasurement.parse(data))
        assertEquals(1.20, measurement.strideLengthMeters!!, 0.001)
        assertEquals(100.0, measurement.totalDistanceMeters!!, 0.001)
        assertEquals(true, measurement.running)
    }

    @Test
    fun aTruncatedRscPacketIsRejected() {
        assertNull(RscMeasurement.parse(bytes(0x00, 0x00, 0x02)))
        assertNull(RscMeasurement.parse(bytes(0x01, 0x00, 0x02, 0xB4)))
        assertNull(RscMeasurement.parse(bytes(0x02, 0x00, 0x02, 0xB4, 0x00, 0x00)))
    }

    // ---- Revolution rate ----

    @Test
    fun aSteadyCadenceIsComputedFromTheCounters() {
        // 30 revolutions in 30 seconds is 60 rpm.
        val rpm = RevolutionRate.rpm(0, 0, 30, 30 * 1024)
        assertEquals(60.0, rpm!!, 0.001)
    }

    @Test
    fun theSixtyFourSecondTimerRolloverDoesNotProduceASpike() {
        // The timer wrapped: 65000 -> 400 is 936 ticks, not a negative jump.
        val rpm = RevolutionRate.rpm(100, 65_000, 101, 400)
        assertNotNull(rpm)
        assertEquals(1.0 / (936.0 / 1024.0) * 60.0, rpm, 0.001)
        assertTrue(rpm < 200, "a rollover must not read as an impossible cadence")
    }

    @Test
    fun theRevolutionCounterRolloverIsHandledToo() {
        val rpm = RevolutionRate.rpm(65_530, 0, 4, 1024)
        assertEquals(600.0, rpm!!, 0.001, "10 revolutions in one second")
    }

    @Test
    fun aRepeatedSampleYieldsNothingRatherThanADivisionByZero() {
        assertNull(RevolutionRate.rpm(100, 1024, 100, 1024))
    }

    @Test
    fun aStoppedWheelReadsAsZeroRatherThanNull() {
        assertEquals(0.0, RevolutionRate.rpm(100, 0, 100, 1024)!!, 0.001)
    }

    @Test
    fun wheelCountersUseTheWiderModulus() {
        val rpm =
            RevolutionRate.rpm(
                previousRevolutions = RevolutionRate.UINT32_MODULUS - 2,
                previousEventTime = 0,
                currentRevolutions = 8,
                currentEventTime = 1024,
                revolutionModulus = RevolutionRate.UINT32_MODULUS,
            )
        assertEquals(600.0, rpm!!, 0.001, "10 revolutions across the 32-bit wrap")
    }
}
