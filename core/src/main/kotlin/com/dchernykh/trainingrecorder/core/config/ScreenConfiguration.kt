package com.dchernykh.trainingrecorder.core.config

import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportType

/** Which tier a resolved screen set actually came from. */
enum class ConfigLevel(
    val id: String,
) {
    DEFAULT("default"),
    DISCIPLINE("discipline"),
    SPORT_TYPE("sport_type"),
    ;

    companion object {
        fun byId(id: String): ConfigLevel? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One screen the rider swipes to, as an ordered list of field ids. An entry of
 * null is an empty slot, which is how a screen keeps its shape while a field is
 * being chosen.
 */
data class Screen(
    val slots: List<String?>,
) {
    init {
        require(slots.isNotEmpty()) { "a screen needs at least one slot" }
        require(slots.size <= MAX_SLOTS) { "a screen holds at most $MAX_SLOTS slots, got ${slots.size}" }
    }

    val slotCount: Int get() = slots.size

    fun withSlot(
        index: Int,
        fieldId: String?,
    ): Screen {
        require(index in slots.indices) { "slot $index is outside 0..${slots.size - 1}" }
        return copy(slots = slots.toMutableList().also { it[index] = fieldId })
    }

    /** Grows or shrinks the screen, keeping the fields that still fit. */
    fun resized(slotCount: Int): Screen {
        require(slotCount in 1..MAX_SLOTS) { "slot count must be 1..$MAX_SLOTS, got $slotCount" }
        return Screen(List(slotCount) { slots.getOrNull(it) })
    }

    companion object {
        /**
         * Ten is the ceiling the Garmin data field settled on for round watches
         * and the point past which a value stops being readable at a glance.
         */
        const val MAX_SLOTS = 10

        fun empty(slotCount: Int): Screen = Screen(List(slotCount) { null })
    }
}

/** The screens for one sport, in swipe order. */
data class ScreenSet(
    val screens: List<Screen>,
) {
    init {
        require(screens.isNotEmpty()) { "a sport needs at least one screen" }
        require(screens.size <= MAX_SCREENS) { "at most $MAX_SCREENS screens, got ${screens.size}" }
    }

    fun withScreen(
        index: Int,
        screen: Screen,
    ): ScreenSet {
        require(index in screens.indices) { "screen $index is outside 0..${screens.size - 1}" }
        return copy(screens = screens.toMutableList().also { it[index] = screen })
    }

    fun plusScreen(screen: Screen): ScreenSet = copy(screens = screens + screen)

    fun minusScreen(index: Int): ScreenSet {
        require(index in screens.indices) { "screen $index is outside 0..${screens.size - 1}" }
        require(screens.size > 1) { "the last screen cannot be removed" }
        return copy(screens = screens.filterIndexed { i, _ -> i != index })
    }

    companion object {
        const val MAX_SCREENS = 10
    }
}

/**
 * Screen layouts for every sport, held as three tiers that inherit downwards:
 * a default, an optional override per [Discipline], and an optional override per
 * [SportType].
 *
 * Nothing is copied until it is edited. Asking for a sport that has no override
 * of its own yields its discipline's set, or the default - so changing the
 * default reaches every sport that never diverged. The moment a level is edited
 * it forks: the resolved value is written down at that level and stops tracking
 * its parent. That is the "as soon as we start changing the setting for a
 * specific sport, it forks" behaviour, and [resetSportType] undoes it.
 */
data class ScreenConfiguration(
    val default: ScreenSet,
    val byDiscipline: Map<Discipline, ScreenSet> = emptyMap(),
    val bySportType: Map<String, ScreenSet> = emptyMap(),
) {
    fun resolve(sport: SportType): ScreenSet = bySportType[sport.id] ?: byDiscipline[sport.discipline] ?: default

    fun resolve(discipline: Discipline): ScreenSet = byDiscipline[discipline] ?: default

    /** Where the set returned by [resolve] actually came from. */
    fun levelOf(sport: SportType): ConfigLevel =
        when {
            bySportType.containsKey(sport.id) -> ConfigLevel.SPORT_TYPE
            byDiscipline.containsKey(sport.discipline) -> ConfigLevel.DISCIPLINE
            else -> ConfigLevel.DEFAULT
        }

    fun isForked(sport: SportType): Boolean = bySportType.containsKey(sport.id)

    fun isForked(discipline: Discipline): Boolean = byDiscipline.containsKey(discipline)

    /** Edits the sport's own copy, forking it from its parent on first write. */
    fun withScreensFor(
        sport: SportType,
        screens: ScreenSet,
    ): ScreenConfiguration = copy(bySportType = bySportType + (sport.id to screens))

    fun withScreensFor(
        discipline: Discipline,
        screens: ScreenSet,
    ): ScreenConfiguration = copy(byDiscipline = byDiscipline + (discipline to screens))

    fun withDefaultScreens(screens: ScreenSet): ScreenConfiguration = copy(default = screens)

    /**
     * Forks without changing anything, so an editor can open a sport and start
     * mutating its own copy rather than its parent's.
     */
    fun fork(sport: SportType): ScreenConfiguration =
        if (isForked(sport)) this else withScreensFor(sport, resolve(sport))

    fun fork(discipline: Discipline): ScreenConfiguration =
        if (isForked(discipline)) this else withScreensFor(discipline, resolve(discipline))

    /** Drops the sport's own copy so it inherits again. */
    fun resetSportType(sport: SportType): ScreenConfiguration = copy(bySportType = bySportType - sport.id)

    /**
     * Drops the discipline's own copy. Sports that forked separately keep their
     * copies - only the ones still inheriting move back to the default.
     */
    fun resetDiscipline(discipline: Discipline): ScreenConfiguration = copy(byDiscipline = byDiscipline - discipline)

    companion object {
        /**
         * What a rider sees before configuring anything: elapsed time, distance
         * and heart rate on one screen. Every sport inherits it until edited.
         */
        fun initial(): ScreenConfiguration =
            ScreenConfiguration(
                default = ScreenSet(listOf(Screen(listOf("timer_elapsed", "distance_total", "hr")))),
            )
    }
}
