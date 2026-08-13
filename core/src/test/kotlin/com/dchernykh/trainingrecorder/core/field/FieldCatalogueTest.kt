package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.sport.Discipline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldCatalogueTest {
    @Test
    fun idsAreUnique() {
        val ids = FieldCatalogue.all.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate field ids: $duplicates")
    }

    @Test
    fun everyCategoryOffersAtLeastOneField() {
        FieldCategory.entries.forEach {
            assertTrue(FieldCatalogue.forCategory(it).isNotEmpty(), "category ${it.id} is empty")
        }
    }

    @Test
    fun categoryListsPartitionTheCatalogue() {
        val regrouped = FieldCategory.entries.flatMap { FieldCatalogue.forCategory(it) }
        assertEquals(FieldCatalogue.all.size, regrouped.size)
        assertEquals(FieldCatalogue.all.toSet(), regrouped.toSet())
    }

    @Test
    fun byIdRoundTrips() {
        FieldCatalogue.all.forEach { assertEquals(it, FieldCatalogue.byId(it.id)) }
        assertNull(FieldCatalogue.byId("no_such_field"))
    }

    @Test
    fun categoryByIdRoundTrips() {
        FieldCategory.entries.forEach { assertEquals(it, FieldCategory.byId(it.id)) }
        assertNull(FieldCategory.byId("nope"))
    }

    @Test
    fun sensorProfileByIdRoundTrips() {
        SensorProfile.entries.forEach { assertEquals(it, SensorProfile.byId(it.id)) }
        assertNull(SensorProfile.byId("nope"))
    }

    @Test
    fun onlyTheNoneProfileLacksAServiceUuid() {
        SensorProfile.entries.forEach {
            if (it == SensorProfile.NONE) {
                assertNull(it.serviceUuid)
            } else {
                assertTrue(
                    it.serviceUuid!!.matches(Regex("[0-9A-F]{4}")),
                    "${it.id} has a malformed 16-bit service UUID: ${it.serviceUuid}",
                )
            }
        }
    }

    @Test
    fun heartRateAlwaysFallsBackToTheBuiltInSensor() {
        val hrFields = FieldCatalogue.forCategory(FieldCategory.HEART_RATE)
        assertTrue(hrFields.isNotEmpty())
        hrFields.forEach {
            assertEquals(SensorProfile.HEART_RATE, it.preferredProfile, "${it.id} should prefer a strap")
            assertTrue(it.fallsBackToBuiltIn, "${it.id} must fall back to the optical sensor")
            assertFalse(it.requiresExternalSensor, "${it.id} must not be gated on an external sensor")
        }
    }

    @Test
    fun powerFieldsRequireAPowerMeterAndAreCyclingOnly() {
        FieldCatalogue.forCategory(FieldCategory.POWER).forEach {
            assertEquals(SensorProfile.CYCLING_POWER, it.preferredProfile)
            assertTrue(it.requiresExternalSensor, "${it.id} cannot be synthesised without a power meter")
            assertTrue(it.availableFor(Discipline.CYCLING))
            assertFalse(it.availableFor(Discipline.SWIMMING), "${it.id} should not be offered for swimming")
        }
    }

    @Test
    fun swimmingFieldsAreOfferedOnlyForSwimming() {
        FieldCatalogue.forCategory(FieldCategory.SWIMMING).forEach {
            assertTrue(it.availableFor(Discipline.SWIMMING))
            assertFalse(it.availableFor(Discipline.CYCLING), "${it.id} should not be offered for cycling")
        }
    }

    @Test
    fun unrestrictedFieldsAreOfferedEverywhere() {
        val timer = FieldCatalogue.byId("timer_elapsed")!!
        Discipline.entries.forEach { assertTrue(timer.availableFor(it)) }
    }

    @Test
    fun forDisciplineNeverReturnsAFieldFromAnotherSport() {
        Discipline.entries.forEach { discipline ->
            FieldCatalogue.forDiscipline(discipline).forEach {
                assertTrue(it.availableFor(discipline), "${it.id} leaked into ${discipline.id}")
            }
        }
    }

    @Test
    fun raceStatsIdsMatchTheTimingServerKeysExactly() {
        // Captured from a live GET of /api/v1/live-stats/259/1 - the identity
        // mapping is what lets the server add stats without a watch update.
        val serverKeys =
            setOf(
                "laps",
                "qty_abs",
                "place_abs",
                "qty_group",
                "place_group",
                "gap_next_abs",
                "gap_prev_abs",
                "gap_leader_abs",
                "gap_next_group",
                "gap_prev_group",
                "gap_leader_group",
                "gap_next_abs_delta",
                "gap_prev_abs_delta",
                "gap_leader_abs_delta",
                "gap_next_group_delta",
                "gap_prev_group_delta",
                "gap_leader_group_delta",
            )
        val fieldIds = FieldCatalogue.forCategory(FieldCategory.RACE_STATS).map { it.id }.toSet()
        assertEquals(serverKeys, fieldIds + FieldCatalogue.raceStatsSupportingKeys)
    }

    @Test
    fun theQuantityKeysAreNotSelectableFields() {
        FieldCatalogue.raceStatsSupportingKeys.forEach {
            assertNull(FieldCatalogue.byId(it), "$it is a denominator, not a field the rider can pick")
        }
    }

    @Test
    fun raceStatsNeedNoSensor() {
        FieldCatalogue.forCategory(FieldCategory.RACE_STATS).forEach {
            assertEquals(SensorProfile.NONE, it.preferredProfile)
            assertFalse(it.requiresExternalSensor)
        }
    }
}
