package com.dchernykh.trainingrecorder.core.sensor

import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.field.SensorProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    /**
     * The failure this window exists for, seen on a real watch: the strap went
     * quiet, the optical sensor had already stopped, and the last heart rate sat
     * on screen for a minute looking exactly like a live one.
     */
    @Test
    fun `a built-in reading that stopped arriving is not shown for ever`() {
        val stale =
            mapOf("hr" to SensorReading(66.0, SensorOrigin.BUILT_IN, now - SensorSnapshot.BUILT_IN_STALE_AFTER_MS - 1))

        val merged = SensorSnapshot.merge(external = emptyMap(), builtIn = stale, nowEpochMs = now)

        assertNull(merged.value("hr"))
    }

    @Test
    fun `a built-in reading survives a late batch`() {
        val recent =
            mapOf("hr" to SensorReading(66.0, SensorOrigin.BUILT_IN, now - SensorSnapshot.BUILT_IN_STALE_AFTER_MS + 1))

        val merged = SensorSnapshot.merge(external = emptyMap(), builtIn = recent, nowEpochMs = now)

        assertEquals(66.0, merged.value("hr"))
    }

    /**
     * The watch's own window is the longer one: Health Services batches, and a
     * strap notifies about once a second.
     */
    @Test
    fun `the watch gets more grace than a strap does`() {
        assertTrue(SensorSnapshot.BUILT_IN_STALE_AFTER_MS > SensorSnapshot.EXTERNAL_STALE_AFTER_MS)
    }

    /**
     * A total does not stop being true because no batch arrived. Blanking the
     * distance mid-ride would be a worse lie than showing the last one.
     */
    @Test
    fun `a running total never goes stale`() {
        val ancient =
            mapOf(
                "distance_total" to SensorReading(12_345.0, SensorOrigin.BUILT_IN, now - HOUR_MS),
                "ascent_total" to SensorReading(430.0, SensorOrigin.BUILT_IN, now - HOUR_MS),
                "calories" to SensorReading(500.0, SensorOrigin.BUILT_IN, now - HOUR_MS),
            )

        val merged = SensorSnapshot.merge(external = emptyMap(), builtIn = ancient, nowEpochMs = now)

        assertEquals(12_345.0, merged.value("distance_total"))
        assertEquals(430.0, merged.value("ascent_total"))
        assertEquals(500.0, merged.value("calories"))
    }

    @Test
    fun `every field named cumulative is one the catalogue knows`() {
        SensorSnapshot.CUMULATIVE_FIELDS.forEach { fieldId ->
            assertNotNull(FieldCatalogue.byId(fieldId), "unknown field id: $fieldId")
        }
    }

    /**
     * Both sources dying leaves nothing, which is the documented behaviour:
     * strap, else the optical sensor, else write nothing.
     */
    @Test
    fun `when both sources go quiet the field empties`() {
        val merged =
            SensorSnapshot.merge(
                external = mapOf("hr" to SensorReading(140.0, SensorOrigin.EXTERNAL, now - HOUR_MS)),
                builtIn = mapOf("hr" to SensorReading(66.0, SensorOrigin.BUILT_IN, now - HOUR_MS)),
                nowEpochMs = now,
            )

        assertNull(merged.value("hr"))
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }

    @Test
    fun aConnectedStrapSilencesTheWatchsOwnHeartRate() {
        // What the rider asked for by pairing a strap and taking the watch off:
        // the optical sensor must not fill the gaps between the strap's beats.
        // Half a ride of numbers alternating between two disagreeing sensors is
        // worse than either of them alone.
        val merged =
            SensorSnapshot.merge(
                external = emptyMap(),
                builtIn = mapOf("hr" to SensorReading(70.0, SensorOrigin.BUILT_IN, now)),
                nowEpochMs = now,
                connectedProfiles = setOf(SensorProfile.HEART_RATE),
            )
        assertNull(merged.value("hr"), "the watch's own reading should be ignored entirely")
    }

    @Test
    fun theWatchsOwnReadingStandsWhenNothingIsConnected() {
        val merged =
            SensorSnapshot.merge(
                external = emptyMap(),
                builtIn = mapOf("hr" to SensorReading(70.0, SensorOrigin.BUILT_IN, now)),
                nowEpochMs = now,
            )
        assertEquals(70.0, merged.value("hr"))
    }

    @Test
    fun aConnectedStrapTakesOverOnlyItsOwnFields() {
        // A heart-rate strap says nothing about altitude, and silencing the
        // barometer because a strap connected would be a strange way to lose a
        // climb.
        val merged =
            SensorSnapshot.merge(
                external = mapOf("hr" to SensorReading(150.0, SensorOrigin.EXTERNAL, now)),
                builtIn =
                    mapOf(
                        "hr" to SensorReading(70.0, SensorOrigin.BUILT_IN, now),
                        "altitude" to SensorReading(300.0, SensorOrigin.BUILT_IN, now),
                    ),
                nowEpochMs = now,
                connectedProfiles = setOf(SensorProfile.HEART_RATE),
            )
        assertEquals(150.0, merged.value("hr"))
        assertEquals(300.0, merged.value("altitude"))
    }

    @Test
    fun everyFieldASensorCoversNamesItsOwnProfile() {
        // The takeover is derived from the catalogue, so this is the check that
        // the catalogue actually says which sensor supplies what.
        val covered = SensorSnapshot.fieldsCoveredBy(setOf(SensorProfile.HEART_RATE))
        assertTrue(covered.contains("hr"), "heart rate should be covered by a heart-rate strap")
        covered.forEach {
            assertEquals(SensorProfile.HEART_RATE, FieldCatalogue.byId(it)?.preferredProfile, it)
        }
    }
}
