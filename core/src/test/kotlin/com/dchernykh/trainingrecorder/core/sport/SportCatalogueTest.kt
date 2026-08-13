package com.dchernykh.trainingrecorder.core.sport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportCatalogueTest {
    @Test
    fun idsAreUnique() {
        val ids = SportCatalogue.all.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals(ids.size, ids.toSet().size, "duplicate sport ids: $duplicates")
    }

    @Test
    fun everyTypeSitsInTheListOfItsOwnDiscipline() {
        val lists =
            mapOf(
                Discipline.CYCLING to SportCatalogue.cycling,
                Discipline.RUNNING to SportCatalogue.running,
                Discipline.SWIMMING to SportCatalogue.swimming,
                Discipline.XC_SKIING to SportCatalogue.xcSkiing,
            )
        lists.forEach { (discipline, types) ->
            types.forEach { assertEquals(discipline, it.discipline, "${it.id} is filed under the wrong discipline") }
        }
    }

    @Test
    fun forDisciplinePartitionsTheCatalogue() {
        val regrouped = Discipline.entries.flatMap { SportCatalogue.forDiscipline(it) }
        assertEquals(SportCatalogue.all.toSet(), regrouped.toSet())
        assertEquals(SportCatalogue.all.size, regrouped.size)
    }

    @Test
    fun byIdRoundTrips() {
        SportCatalogue.all.forEach { assertEquals(it, SportCatalogue.byId(it.id)) }
        assertNull(SportCatalogue.byId("no_such_sport"))
    }

    @Test
    fun disciplineByIdRoundTrips() {
        Discipline.entries.forEach { assertEquals(it, Discipline.byId(it.id)) }
        assertNull(Discipline.byId("no_such_discipline"))
    }

    @Test
    fun defaultSportIsInTheCatalogue() {
        assertTrue(SportCatalogue.default in SportCatalogue.all)
    }

    @Test
    fun everySportNamesAStravaTypeAndAHealthServicesType() {
        SportCatalogue.all.forEach {
            assertTrue(it.stravaSportType.isNotBlank(), "${it.id} has no Strava sport type")
            assertTrue(
                it.healthServicesExerciseType.matches(Regex("[A-Z][A-Z_]+")),
                "${it.id} has a malformed Health Services constant name: ${it.healthServicesExerciseType}",
            )
        }
    }

    @Test
    fun onlyRollerSkiingLacksAGarminType() {
        val withoutGarmin =
            SportCatalogue.all
                .filter { it.garminTypeKey == null }
                .map { it.id }
                .toSet()
        assertEquals(setOf("xc_rollerski_classic", "xc_rollerski_skate"), withoutGarmin)
    }

    @Test
    fun aMissingGarminTypeIsAlwaysMarkedInexact() {
        SportCatalogue.all.filter { it.garminTypeKey == null }.forEach {
            assertFalse(it.garminExact, "${it.id} has no Garmin type but claims an exact mapping")
        }
    }

    @Test
    fun stravaFlagsOnlyAppearOnPlainRideAndRun() {
        SportCatalogue.all.filter { it.stravaTrainer || it.stravaCommute }.forEach {
            assertTrue(
                it.stravaSportType in setOf("Ride", "Run"),
                "${it.id} sets a Strava flag on ${it.stravaSportType}, which Strava only honours on Ride and Run",
            )
        }
    }

    @Test
    fun theCatalogueStaysSmallEnoughToPickOnAWatch() {
        Discipline.entries.forEach {
            val count = SportCatalogue.forDiscipline(it).size
            assertTrue(count in 1..10, "${it.id} offers $count sports; the agreed ceiling is 10")
        }
    }

    @Test
    fun indoorCyclingIsExpressedAsAStravaTrainerFlag() {
        val indoor = assertNotNull(SportCatalogue.byId("cycling_indoor"))
        assertEquals("Ride", indoor.stravaSportType)
        assertTrue(indoor.stravaTrainer)
        assertFalse(indoor.stravaCommute)
    }
}
