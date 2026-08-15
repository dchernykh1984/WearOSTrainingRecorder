package com.dchernykh.trainingrecorder.core.workout

import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.dchernykh.trainingrecorder.core.sport.SportType

/**
 * Orders the sport picker so the first entry is almost always the right one.
 *
 * The rule is recency by *kind*: the sport of the last workout comes first, then
 * the most recent workout of a different sport, and so on. Repeating the same
 * sport ten times therefore does not push everything else off the screen - the
 * second entry is still whatever the rider did before that. Sports never used
 * follow in catalogue order.
 */
object SportOrdering {
    fun order(
        history: List<String>,
        catalogue: List<SportType> = SportCatalogue.all,
    ): List<SportType> {
        val known = catalogue.associateBy { it.id }
        val recent =
            history
                .asReversed()
                .asSequence()
                .distinct()
                .mapNotNull { known[it] }
                .toList()
        return recent + catalogue.filterNot { it in recent }
    }

    /**
     * Only the sports the rider has actually used, most recent kind first.
     *
     * The picker's shortcut list. Separate from [order] because the two answer
     * different questions: [order] is "every sport, best guess first", this is
     * "the ones worth a single tap". A rider with three sports should not have
     * to scroll past eleven to reach the fourth.
     */
    fun favourites(
        history: List<String>,
        catalogue: List<SportType> = SportCatalogue.all,
    ): List<SportType> {
        val known = catalogue.associateBy { it.id }
        return history
            .asReversed()
            .asSequence()
            .distinct()
            .mapNotNull { known[it] }
            .toList()
    }

    /**
     * Drops a sport from the shortcut list.
     *
     * Every occurrence, not the most recent one: a sport used ten times would
     * otherwise need forgetting ten times, which reads as a button that does
     * nothing. The sport itself is untouched - it is still in the catalogue,
     * still under its discipline, and starting it again puts it back here.
     */
    fun forget(
        history: List<String>,
        sportTypeId: String,
    ): List<String> = history.filterNot { it == sportTypeId }

    /** The sport to preselect: the last one used, or the catalogue default. */
    fun preselected(
        history: List<String>,
        catalogue: List<SportType> = SportCatalogue.all,
    ): SportType = order(history, catalogue).firstOrNull() ?: SportCatalogue.default

    /**
     * Records a use. History is kept oldest-first and capped, since only the
     * first few distinct entries can ever affect the order.
     */
    fun record(
        history: List<String>,
        sportTypeId: String,
        limit: Int = HISTORY_LIMIT,
    ): List<String> {
        require(limit >= 1) { "history limit must be positive, got $limit" }
        return (history + sportTypeId).takeLast(limit)
    }

    const val HISTORY_LIMIT = 100
}
