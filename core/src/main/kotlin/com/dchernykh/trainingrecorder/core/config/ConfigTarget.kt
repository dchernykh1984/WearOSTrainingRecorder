package com.dchernykh.trainingrecorder.core.config

import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.dchernykh.trainingrecorder.core.sport.SportType

/**
 * Which tier of [ScreenConfiguration] an editor is pointed at.
 *
 * The configuration always had three tiers; only the sport one could be reached,
 * so the default that every sport inherits was invisible and uneditable - a
 * rider wanting the same three fields everywhere had to set them thirty-five
 * times. This is the missing handle.
 *
 * Each target carries a [key] because a phone editor has to survive rotation,
 * and what it survives on is a string.
 */
sealed interface ConfigTarget {
    val key: String

    /** What every sport inherits until something below it diverges. */
    data object Default : ConfigTarget {
        override val key: String = "default"
    }

    /** What one discipline's sports inherit instead of the default. */
    data class OfDiscipline(
        val discipline: Discipline,
    ) : ConfigTarget {
        override val key: String = "$DISCIPLINE_PREFIX${discipline.id}"
    }

    /** One sport's own layout. */
    data class OfSport(
        val sport: SportType,
    ) : ConfigTarget {
        override val key: String = "$SPORT_PREFIX${sport.id}"
    }

    /**
     * The discipline whose fields this target should offer, or null for the
     * default - which belongs to no discipline and therefore offers everything.
     */
    val fieldsOf: Discipline?
        get() =
            when (this) {
                is Default -> null
                is OfDiscipline -> discipline
                is OfSport -> sport.discipline
            }

    companion object {
        private const val DISCIPLINE_PREFIX = "discipline:"
        private const val SPORT_PREFIX = "sport:"

        /** Null for a key that names nothing this build knows about. */
        fun byKey(key: String): ConfigTarget? =
            when {
                key == Default.key -> Default
                key.startsWith(DISCIPLINE_PREFIX) ->
                    Discipline.byId(key.removePrefix(DISCIPLINE_PREFIX))?.let(::OfDiscipline)

                key.startsWith(SPORT_PREFIX) ->
                    SportCatalogue.byId(key.removePrefix(SPORT_PREFIX))?.let(::OfSport)

                else -> null
            }
    }
}

/** The layout this target currently reads, inherited or its own. */
fun ScreenConfiguration.resolve(target: ConfigTarget): ScreenSet =
    when (target) {
        is ConfigTarget.Default -> default
        is ConfigTarget.OfDiscipline -> resolve(target.discipline)
        is ConfigTarget.OfSport -> resolve(target.sport)
    }

/**
 * Where that layout came from. The default has nowhere to inherit from, so it is
 * always its own - which is what makes its editor the one with no Reset.
 */
fun ScreenConfiguration.levelOf(target: ConfigTarget): ConfigLevel =
    when (target) {
        is ConfigTarget.Default -> ConfigLevel.DEFAULT
        is ConfigTarget.OfDiscipline ->
            if (isForked(target.discipline)) ConfigLevel.DISCIPLINE else ConfigLevel.DEFAULT

        is ConfigTarget.OfSport -> levelOf(target.sport)
    }

/**
 * Whether this tier holds a layout of its own, which is the only case where
 * resetting it does anything.
 *
 * Not the same question as [levelOf]. A sport reading its discipline's layout
 * reports DISCIPLINE, and offering it a Reset on that basis gives the rider a
 * button that looks active and does nothing.
 */
fun ScreenConfiguration.isForked(target: ConfigTarget): Boolean =
    when (target) {
        is ConfigTarget.Default -> false
        is ConfigTarget.OfDiscipline -> isForked(target.discipline)
        is ConfigTarget.OfSport -> isForked(target.sport)
    }

/** Edits this tier, forking it from its parent on the first write. */
fun ScreenConfiguration.withScreensFor(
    target: ConfigTarget,
    screens: ScreenSet,
): ScreenConfiguration =
    when (target) {
        is ConfigTarget.Default -> withDefaultScreens(screens)
        is ConfigTarget.OfDiscipline -> withScreensFor(target.discipline, screens)
        is ConfigTarget.OfSport -> withScreensFor(target.sport, screens)
    }

/**
 * Drops this tier's own copy so it inherits again.
 *
 * The default is left alone: there is nothing above it to fall back to, and
 * "reset" there could only mean discarding the rider's layout for a built-in
 * one they never asked for.
 */
fun ScreenConfiguration.reset(target: ConfigTarget): ScreenConfiguration =
    when (target) {
        is ConfigTarget.Default -> this
        is ConfigTarget.OfDiscipline -> resetDiscipline(target.discipline)
        is ConfigTarget.OfSport -> resetSportType(target.sport)
    }
