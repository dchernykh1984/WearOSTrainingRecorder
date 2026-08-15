package com.dchernykh.trainingrecorder.core.config

import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTargetTest {
    private val road = requireNotNull(SportCatalogue.byId("cycling_road"))
    private val gravel = requireNotNull(SportCatalogue.byId("cycling_gravel"))
    private val running = requireNotNull(SportCatalogue.byId("run_road"))
    private val two = ScreenSet(listOf(Screen(listOf("hr", "power"))))
    private val three = ScreenSet(listOf(Screen(listOf("hr", "power", "cadence"))))

    @Test
    fun everyTargetSurvivesBeingWrittenDownAsAString() {
        // What a rotation does to the editor: the target is stored as a string
        // and rebuilt from it, so a target that cannot make the round trip is a
        // rider thrown out of the screen they were halfway through.
        val targets =
            listOf(
                ConfigTarget.Default,
                ConfigTarget.OfDiscipline(Discipline.CYCLING),
                ConfigTarget.OfSport(road),
            )
        targets.forEach { assertEquals(it, ConfigTarget.byKey(it.key)) }
    }

    @Test
    fun aKeyForSomethingThisBuildDoesNotKnowIsRefused() {
        assertNull(ConfigTarget.byKey("sport:hang_gliding"))
        assertNull(ConfigTarget.byKey("discipline:quidditch"))
        assertNull(ConfigTarget.byKey("nonsense"))
    }

    @Test
    fun editingTheDefaultReachesEverySportThatNeverDiverged() {
        // The whole point of exposing it: one edit, every sport.
        val edited = ScreenConfiguration.initial().withScreensFor(ConfigTarget.Default, two)
        assertEquals(two, edited.resolve(road))
        assertEquals(two, edited.resolve(running))
    }

    @Test
    fun editingADisciplineReachesItsOwnSportsAndNoOthers() {
        val edited = ScreenConfiguration.initial().withScreensFor(ConfigTarget.OfDiscipline(Discipline.CYCLING), two)
        assertEquals(two, edited.resolve(road))
        assertEquals(two, edited.resolve(gravel))
        assertEquals(ScreenConfiguration.initial().default, edited.resolve(running))
    }

    @Test
    fun aSportThatDivergedStopsFollowingItsDiscipline() {
        val edited =
            ScreenConfiguration
                .initial()
                .withScreensFor(ConfigTarget.OfSport(road), three)
                .withScreensFor(ConfigTarget.OfDiscipline(Discipline.CYCLING), two)
        assertEquals(three, edited.resolve(road))
        assertEquals(two, edited.resolve(gravel))
        assertEquals(ConfigLevel.SPORT_TYPE, edited.levelOf(ConfigTarget.OfSport(road)))
        assertEquals(ConfigLevel.DISCIPLINE, edited.levelOf(ConfigTarget.OfSport(gravel)))
    }

    @Test
    fun resettingASportPutsItBackUnderItsDiscipline() {
        val edited =
            ScreenConfiguration
                .initial()
                .withScreensFor(ConfigTarget.OfDiscipline(Discipline.CYCLING), two)
                .withScreensFor(ConfigTarget.OfSport(road), three)
                .reset(ConfigTarget.OfSport(road))
        assertEquals(two, edited.resolve(road))
    }

    @Test
    fun resettingADisciplinePutsItBackUnderTheDefaultWithoutTouchingItsSports() {
        val edited =
            ScreenConfiguration
                .initial()
                .withScreensFor(ConfigTarget.OfDiscipline(Discipline.CYCLING), two)
                .withScreensFor(ConfigTarget.OfSport(road), three)
                .reset(ConfigTarget.OfDiscipline(Discipline.CYCLING))
        assertEquals(ScreenConfiguration.initial().default, edited.resolve(gravel))
        assertEquals(three, edited.resolve(road), "a sport with its own copy keeps it")
    }

    @Test
    fun theDefaultHasNothingToResetTo() {
        // Resetting it could only mean throwing the rider's layout away for a
        // built-in one they never asked for, so it does nothing at all.
        val edited = ScreenConfiguration.initial().withScreensFor(ConfigTarget.Default, two)
        assertEquals(edited, edited.reset(ConfigTarget.Default))
        assertEquals(ConfigLevel.DEFAULT, edited.levelOf(ConfigTarget.Default))
    }

    @Test
    fun theDefaultBelongsToNoDisciplineSoItOffersEveryField() {
        assertNull(ConfigTarget.Default.fieldsOf)
        assertEquals(Discipline.CYCLING, ConfigTarget.OfDiscipline(Discipline.CYCLING).fieldsOf)
        assertEquals(Discipline.CYCLING, ConfigTarget.OfSport(road).fieldsOf)
        assertNotNull(ConfigTarget.OfSport(running).fieldsOf)
    }

    @Test
    fun onlyATierWithALayoutOfItsOwnHasSomethingToReset() {
        // A sport reading its discipline's layout reports DISCIPLINE, so
        // deciding this from the level alone offers a Reset that looks active
        // and does nothing.
        val edited = ScreenConfiguration.initial().withScreensFor(ConfigTarget.OfDiscipline(Discipline.CYCLING), two)
        assertEquals(ConfigLevel.DISCIPLINE, edited.levelOf(ConfigTarget.OfSport(road)))
        assertFalse(edited.isForked(ConfigTarget.OfSport(road)), "it has nothing of its own")
        assertTrue(edited.isForked(ConfigTarget.OfDiscipline(Discipline.CYCLING)))
        assertFalse(edited.isForked(ConfigTarget.Default), "the default has nowhere to go back to")
    }
}
