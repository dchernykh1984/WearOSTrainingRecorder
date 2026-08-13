package com.dchernykh.trainingrecorder.core.workout

import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SportOrderingTest {
    private fun ids(history: List<String>) = SportOrdering.order(history).map { it.id }

    @Test
    fun withNoHistoryTheCatalogueOrderIsKept() {
        assertEquals(SportCatalogue.all.map { it.id }, ids(emptyList()))
        assertEquals(SportCatalogue.default, SportOrdering.preselected(emptyList()))
    }

    @Test
    fun theLastSportUsedComesFirst() {
        assertEquals("run_trail", ids(listOf("cycling_road", "run_trail")).first())
        assertEquals("run_trail", SportOrdering.preselected(listOf("cycling_road", "run_trail")).id)
    }

    @Test
    fun thenTheMostRecentWorkoutOfADifferentSport() {
        val order = ids(listOf("swim_pool", "cycling_gravel", "run_trail"))
        assertEquals(listOf("run_trail", "cycling_gravel", "swim_pool"), order.take(3))
    }

    @Test
    fun repeatingOneSportDoesNotPushTheOthersAway() {
        val order = ids(listOf("swim_pool", "cycling_gravel", "run_trail", "run_trail", "run_trail"))
        assertEquals(
            listOf("run_trail", "cycling_gravel", "swim_pool"),
            order.take(3),
            "the second entry must still be what came before the streak",
        )
    }

    @Test
    fun sportsNeverUsedFollowInCatalogueOrder() {
        val order = ids(listOf("run_trail"))
        val untouched = SportCatalogue.all.map { it.id }.filterNot { it == "run_trail" }
        assertEquals(untouched, order.drop(1))
    }

    @Test
    fun theOrderIsAlwaysAPermutationOfTheCatalogue() {
        val order = ids(listOf("run_trail", "swim_pool", "run_trail", "alpine_ski"))
        assertEquals(SportCatalogue.all.size, order.size)
        assertEquals(SportCatalogue.all.map { it.id }.toSet(), order.toSet())
    }

    @Test
    fun sportsThatNoLongerExistAreIgnored() {
        val order = ids(listOf("cycling_penny_farthing", "run_trail"))
        assertEquals("run_trail", order.first())
        assertEquals(SportCatalogue.all.size, order.size)
    }

    @Test
    fun recordingAppendsAndCapsTheHistory() {
        val history = SportOrdering.record(listOf("a", "b"), "c")
        assertEquals(listOf("a", "b", "c"), history)

        val long = (1..SportOrdering.HISTORY_LIMIT + 10).map { "s$it" }
        val capped = SportOrdering.record(long, "newest")
        assertEquals(SportOrdering.HISTORY_LIMIT, capped.size)
        assertEquals("newest", capped.last())
        assertTrue(capped.none { it == "s1" }, "the oldest entries fall off")
    }

    @Test
    fun aNonPositiveHistoryLimitIsRejected() {
        assertFailsWith<IllegalArgumentException> { SportOrdering.record(emptyList(), "a", limit = 0) }
    }
}
