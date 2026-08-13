package com.dchernykh.trainingrecorder.core.sensor

import com.dchernykh.trainingrecorder.core.field.SensorProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensorSnapshotTest {
    private val now = 1_770_000_000_000L

    private fun strap(
        bpm: Double,
        atEpochMs: Long = now,
    ) = mapOf("hr" to SensorReading(bpm, SensorOrigin.EXTERNAL, atEpochMs))

    private fun optical(
        bpm: Double,
        atEpochMs: Long = now,
    ) = mapOf("hr" to SensorReading(bpm, SensorOrigin.BUILT_IN, atEpochMs))

    @Test
    fun aStrapBeatsTheOpticalSensor() {
        val snapshot = SensorSnapshot.merge(strap(152.0), optical(148.0), now)
        assertEquals(152.0, snapshot.value("hr"))
        assertEquals(SensorOrigin.EXTERNAL, snapshot.origin("hr"))
    }

    @Test
    fun withoutAStrapTheOpticalSensorIsUsed() {
        val snapshot = SensorSnapshot.merge(emptyMap(), optical(148.0), now)
        assertEquals(148.0, snapshot.value("hr"))
        assertEquals(SensorOrigin.BUILT_IN, snapshot.origin("hr"))
    }

    @Test
    fun withNeitherSensorTheFieldIsSimplyEmpty() {
        val snapshot = SensorSnapshot.merge(emptyMap(), emptyMap(), now)
        assertNull(snapshot.value("hr"))
        assertNull(snapshot.origin("hr"))
    }

    @Test
    fun aStrapThatWentQuietHandsBackToTheOpticalSensor() {
        val stale = now - SensorSnapshot.EXTERNAL_STALE_AFTER_MS - 1
        val snapshot = SensorSnapshot.merge(strap(152.0, stale), optical(148.0), now)
        assertEquals(148.0, snapshot.value("hr"), "a stale strap must not keep showing an old number")
        assertEquals(SensorOrigin.BUILT_IN, snapshot.origin("hr"))
    }

    @Test
    fun aStrapIsStillTrustedRightUpToTheStalenessLimit() {
        val edge = now - SensorSnapshot.EXTERNAL_STALE_AFTER_MS
        val snapshot = SensorSnapshot.merge(strap(152.0, edge), optical(148.0), now)
        assertEquals(152.0, snapshot.value("hr"))
    }

    @Test
    fun aStaleStrapWithNoOpticalFallbackLeavesTheFieldEmpty() {
        val stale = now - SensorSnapshot.EXTERNAL_STALE_AFTER_MS - 1
        val snapshot = SensorSnapshot.merge(strap(152.0, stale), emptyMap(), now)
        assertNull(snapshot.value("hr"), "there is nothing to show, so show nothing")
    }

    @Test
    fun eachFieldFallsBackIndependently() {
        val stale = now - SensorSnapshot.EXTERNAL_STALE_AFTER_MS - 1
        val external =
            mapOf(
                "hr" to SensorReading(152.0, SensorOrigin.EXTERNAL, stale),
                "power" to SensorReading(240.0, SensorOrigin.EXTERNAL, now),
            )
        val snapshot = SensorSnapshot.merge(external, optical(148.0), now)
        assertEquals(148.0, snapshot.value("hr"))
        assertEquals(240.0, snapshot.value("power"), "a live power meter is unaffected by a dead strap")
    }

    @Test
    fun connectedProfilesAreCarriedThrough() {
        val snapshot =
            SensorSnapshot.merge(
                strap(152.0),
                emptyMap(),
                now,
                connectedProfiles = setOf(SensorProfile.HEART_RATE, SensorProfile.CYCLING_POWER),
            )
        assertTrue(snapshot.isConnected(SensorProfile.HEART_RATE))
        assertTrue(snapshot.isConnected(SensorProfile.CYCLING_POWER))
        assertFalse(snapshot.isConnected(SensorProfile.RUNNING_SPEED_CADENCE))
    }

    @Test
    fun aNonPositiveStalenessWindowIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SensorSnapshot.merge(emptyMap(), emptyMap(), now, staleAfterMs = 0)
        }
    }

    @Test
    fun sensorOriginRoundTripsThroughItsId() {
        SensorOrigin.entries.forEach { assertEquals(it, SensorOrigin.byId(it.id)) }
        assertNull(SensorOrigin.byId("nope"))
    }
}
