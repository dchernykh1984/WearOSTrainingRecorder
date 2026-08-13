package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.sport.Discipline

/**
 * Groups fields in the picker. A flat list of ninety-odd fields is unusable;
 * the rider almost always knows the category before the field.
 */
enum class FieldCategory(
    val id: String,
) {
    TIME("time"),
    DISTANCE("distance"),
    SPEED("speed"),
    HEART_RATE("heart_rate"),
    CADENCE("cadence"),
    POWER("power"),
    ELEVATION("elevation"),
    ENERGY("energy"),
    SWIMMING("swimming"),
    LAPS("laps"),
    SENSORS("sensors"),
    RACE_STATS("race_stats"),
    ;

    companion object {
        fun byId(id: String): FieldCategory? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The Bluetooth LE service a field needs, when it needs one at all.
 *
 * UUIDs are the assigned 16-bit values; the watch module expands them to the
 * full base UUID. [NONE] covers everything the watch produces on its own -
 * timers, GNSS, the built-in optical heart-rate sensor and the barometer.
 */
enum class SensorProfile(
    val id: String,
    val serviceUuid: String?,
) {
    NONE("none", null),
    HEART_RATE("heart_rate", "180D"),
    CYCLING_SPEED_CADENCE("csc", "1816"),
    CYCLING_POWER("cps", "1818"),
    RUNNING_SPEED_CADENCE("rsc", "1814"),
    FITNESS_MACHINE("ftms", "1826"),
    BATTERY("battery", "180F"),
    ENVIRONMENTAL("environmental", "181A"),
    ;

    companion object {
        fun byId(id: String): SensorProfile? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One value the rider can place in a screen slot.
 *
 * There is deliberately no label here. Labels are localized Android string
 * resources keyed by [id] in each app module, so adding a language never touches
 * this catalogue.
 *
 * [disciplines] restricts where a field is offered - SWOLF is meaningless on a
 * bike - and an empty set means "offer everywhere".
 *
 * [preferredProfile] is the external sensor that supplies the field at its best
 * quality. [fallsBackToBuiltIn] marks the fields the watch can still produce
 * without that sensor: heart rate falls back to the optical sensor, speed to
 * GNSS. A field with a preferred profile and no fallback simply reads as empty
 * until its sensor is connected.
 */
data class DataFieldDef(
    val id: String,
    val category: FieldCategory,
    val preferredProfile: SensorProfile = SensorProfile.NONE,
    val fallsBackToBuiltIn: Boolean = true,
    val disciplines: Set<Discipline> = emptySet(),
) {
    val requiresExternalSensor: Boolean
        get() = preferredProfile != SensorProfile.NONE && !fallsBackToBuiltIn

    fun availableFor(discipline: Discipline): Boolean = disciplines.isEmpty() || discipline in disciplines
}
