package com.dchernykh.trainingrecorder.core.config

import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScreenConfigurationTest {
    private val road = SportCatalogue.byId("cycling_road")!!
    private val gravel = SportCatalogue.byId("cycling_gravel")!!
    private val trail = SportCatalogue.byId("run_trail")!!

    private fun screenSet(vararg fields: String) = ScreenSet(listOf(Screen(fields.toList())))

    @Test
    fun everythingInheritsTheDefaultUntilEdited() {
        val config = ScreenConfiguration.initial()
        SportCatalogue.all.forEach {
            assertEquals(config.default, config.resolve(it), "${it.id} should inherit the default")
            assertEquals(ConfigLevel.DEFAULT, config.levelOf(it))
        }
    }

    @Test
    fun changingTheDefaultReachesEverySportThatNeverDiverged() {
        val changed = ScreenConfiguration.initial().withDefaultScreens(screenSet("power", "cadence"))
        assertEquals(screenSet("power", "cadence"), changed.resolve(road))
        assertEquals(screenSet("power", "cadence"), changed.resolve(trail))
    }

    @Test
    fun aDisciplineOverrideShadowsTheDefaultForItsSportsOnly() {
        val config =
            ScreenConfiguration.initial().withScreensFor(Discipline.CYCLING, screenSet("power", "speed_current"))
        assertEquals(screenSet("power", "speed_current"), config.resolve(road))
        assertEquals(screenSet("power", "speed_current"), config.resolve(gravel))
        assertEquals(ConfigLevel.DISCIPLINE, config.levelOf(road))
        assertEquals(config.default, config.resolve(trail))
        assertEquals(ConfigLevel.DEFAULT, config.levelOf(trail))
    }

    @Test
    fun aSportOverrideShadowsItsDiscipline() {
        val config =
            ScreenConfiguration
                .initial()
                .withScreensFor(Discipline.CYCLING, screenSet("power"))
                .withScreensFor(gravel, screenSet("grade", "altitude"))
        assertEquals(screenSet("grade", "altitude"), config.resolve(gravel))
        assertEquals(ConfigLevel.SPORT_TYPE, config.levelOf(gravel))
        assertEquals(screenSet("power"), config.resolve(road), "road must still follow its discipline")
    }

    @Test
    fun forkingCopiesTheResolvedSetWithoutChangingWhatIsShown() {
        val config = ScreenConfiguration.initial().withScreensFor(Discipline.CYCLING, screenSet("power"))
        val before = config.resolve(gravel)
        val forked = config.fork(gravel)
        assertEquals(before, forked.resolve(gravel), "forking must not change the visible layout")
        assertTrue(forked.isForked(gravel))
        assertEquals(ConfigLevel.SPORT_TYPE, forked.levelOf(gravel))
    }

    @Test
    fun aForkedSportStopsTrackingItsParent() {
        val config = ScreenConfiguration.initial().withScreensFor(Discipline.CYCLING, screenSet("power")).fork(gravel)
        val afterParentChange = config.withScreensFor(Discipline.CYCLING, screenSet("hr"))
        assertEquals(screenSet("power"), afterParentChange.resolve(gravel), "the fork must be immune to its parent")
        assertEquals(screenSet("hr"), afterParentChange.resolve(road))
    }

    @Test
    fun forkingTwiceIsANoOp() {
        val once = ScreenConfiguration.initial().fork(road)
        assertSame(once, once.fork(road))
    }

    @Test
    fun resettingASportMakesItInheritAgain() {
        val config =
            ScreenConfiguration
                .initial()
                .withScreensFor(Discipline.CYCLING, screenSet("power"))
                .withScreensFor(gravel, screenSet("grade"))
        val reset = config.resetSportType(gravel)
        assertEquals(screenSet("power"), reset.resolve(gravel))
        assertFalse(reset.isForked(gravel))
    }

    @Test
    fun resettingADisciplineLeavesSeparatelyForkedSportsAlone() {
        val config =
            ScreenConfiguration
                .initial()
                .withScreensFor(Discipline.CYCLING, screenSet("power"))
                .withScreensFor(gravel, screenSet("grade"))
        val reset = config.resetDiscipline(Discipline.CYCLING)
        assertEquals(screenSet("grade"), reset.resolve(gravel), "gravel forked on its own and must survive")
        assertEquals(config.default, reset.resolve(road))
    }

    @Test
    fun screensCanBeAddedResizedAndRemoved() {
        var screens = ScreenSet(listOf(Screen.empty(2)))
        screens = screens.plusScreen(Screen(listOf("hr", "power", "cadence")))
        assertEquals(2, screens.screens.size)
        screens = screens.withScreen(0, screens.screens[0].resized(4))
        assertEquals(4, screens.screens[0].slotCount)
        assertEquals(listOf(null, null, null, null), screens.screens[0].slots)
        screens = screens.minusScreen(0)
        assertEquals(1, screens.screens.size)
        assertEquals(listOf("hr", "power", "cadence"), screens.screens[0].slots)
    }

    @Test
    fun growingAScreenKeepsTheFieldsAlreadyPlaced() {
        val grown = Screen(listOf("hr", "power")).resized(4)
        assertEquals(listOf("hr", "power", null, null), grown.slots)
    }

    @Test
    fun shrinkingAScreenDropsTheFieldsThatNoLongerFit() {
        val shrunk = Screen(listOf("hr", "power", "cadence")).resized(2)
        assertEquals(listOf("hr", "power"), shrunk.slots)
    }

    @Test
    fun aSlotCanBeSetAndCleared() {
        val screen = Screen.empty(3).withSlot(1, "hr")
        assertEquals(listOf(null, "hr", null), screen.slots)
        assertNull(screen.withSlot(1, null).slots[1])
    }

    @Test
    fun theLastScreenCannotBeRemoved() {
        assertFailsWith<IllegalArgumentException> { ScreenSet(listOf(Screen.empty(1))).minusScreen(0) }
    }

    @Test
    fun degenerateShapesAreRejected() {
        assertFailsWith<IllegalArgumentException> { Screen(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Screen(List(Screen.MAX_SLOTS + 1) { null }) }
        assertFailsWith<IllegalArgumentException> { ScreenSet(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Screen.empty(1).withSlot(5, "hr") }
        assertFailsWith<IllegalArgumentException> { Screen.empty(1).resized(0) }
    }

    @Test
    fun configLevelByIdRoundTrips() {
        ConfigLevel.entries.forEach { assertEquals(it, ConfigLevel.byId(it.id)) }
        assertNull(ConfigLevel.byId("nope"))
    }

    @Test
    fun resolvingByDisciplineFallsBackToTheDefault() {
        val config = ScreenConfiguration.initial()
        assertEquals(config.default, config.resolve(Discipline.SWIMMING))
        val withOverride = config.withScreensFor(Discipline.SWIMMING, screenSet("swolf"))
        assertEquals(screenSet("swolf"), withOverride.resolve(Discipline.SWIMMING))
        assertTrue(withOverride.isForked(Discipline.SWIMMING))
    }
}
