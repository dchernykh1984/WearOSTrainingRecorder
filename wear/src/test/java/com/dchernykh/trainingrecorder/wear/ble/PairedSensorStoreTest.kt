package com.dchernykh.trainingrecorder.wear.ble

import com.dchernykh.trainingrecorder.core.field.SensorProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PairedSensorStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = PairedSensorStore(File(folder.root, "sensors.json"))

    @Test
    fun aStrapKeepsEveryProfileItSpeaks() {
        // The bug this exists for: a chest strap advertising heart rate *and*
        // running cadence was remembered as one of the two, and the half that
        // was dropped was silently the half the rider wanted.
        val strap = PairedSensor("AA:BB", "Strap", listOf("heart_rate", "rsc"))
        store().remember(strap)
        val read = store().read().single()
        assertEquals(setOf(SensorProfile.HEART_RATE, SensorProfile.RUNNING_SPEED_CADENCE), read.profiles)
    }

    @Test
    fun sensorsPairedByAnOlderBuildSurviveTheUpgrade() {
        // Written by a build that knew one profile per sensor. Read as nothing,
        // a rider would open the pairing screen after updating and find their
        // strap gone.
        File(folder.root, "sensors.json").writeText(
            """[{"address":"AA:BB","name":"Strap","profile":"heart_rate"}]""",
        )
        val read = store().read().single()
        assertEquals("AA:BB", read.address)
        assertEquals(setOf(SensorProfile.HEART_RATE), read.profiles)
    }

    @Test
    fun anEntryWithNoProfileAtAllIsDropped() {
        File(folder.root, "sensors.json").writeText("""[{"address":"AA:BB","name":"Mystery"}]""")
        assertTrue(store().read().isEmpty())
    }

    @Test
    fun pairingTheSameSensorAgainReplacesItRatherThanDoublingIt() {
        val store = store()
        store.remember(PairedSensor("AA:BB", "Strap", listOf("heart_rate")))
        store.remember(PairedSensor("AA:BB", "Strap", listOf("heart_rate", "rsc")))
        assertEquals(1, store.read().size)
        assertEquals(
            2,
            store
                .read()
                .single()
                .profileIds.size,
        )
    }

    @Test
    fun forgettingRemovesOnlyThatSensor() {
        val store = store()
        store.remember(PairedSensor("AA:BB", "Strap", listOf("heart_rate")))
        store.remember(PairedSensor("CC:DD", "Power", listOf("cps")))
        store.forget("AA:BB")
        assertEquals(listOf("CC:DD"), store.read().map { it.address })
    }
}
