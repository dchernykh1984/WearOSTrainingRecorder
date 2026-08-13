package com.dchernykh.trainingrecorder.wear.health

import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseType
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue

/**
 * Resolves the platform's exercise type from the id the shared catalogue carries.
 *
 * The catalogue names the constant as a string on purpose - it has no Android
 * dependency - so the lookup lands here. A name that no longer exists resolves to
 * [ExerciseType.UNKNOWN] rather than throwing: Health Services still records a
 * session for an unknown type, and refusing to start a workout because a mapping
 * drifted would be a far worse failure than a slightly wrong label.
 */
object ExerciseTypes {
    private val byName: Map<String, ExerciseType> =
        mapOf(
            "ALPINE_SKIING" to ExerciseType.ALPINE_SKIING,
            "BACKPACKING" to ExerciseType.BACKPACKING,
            "BIKING" to ExerciseType.BIKING,
            "BIKING_STATIONARY" to ExerciseType.BIKING_STATIONARY,
            "CROSS_COUNTRY_SKIING" to ExerciseType.CROSS_COUNTRY_SKIING,
            "HIKING" to ExerciseType.HIKING,
            "MOUNTAIN_BIKING" to ExerciseType.MOUNTAIN_BIKING,
            "ROLLER_SKATING" to ExerciseType.ROLLER_SKATING,
            "RUNNING" to ExerciseType.RUNNING,
            "RUNNING_TREADMILL" to ExerciseType.RUNNING_TREADMILL,
            "SKIING" to ExerciseType.SKIING,
            "SNOWSHOEING" to ExerciseType.SNOWSHOEING,
            "SWIMMING_OPEN_WATER" to ExerciseType.SWIMMING_OPEN_WATER,
            "SWIMMING_POOL" to ExerciseType.SWIMMING_POOL,
            "WALKING" to ExerciseType.WALKING,
        )

    fun forSport(sportTypeId: String): ExerciseType {
        val name = SportCatalogue.byId(sportTypeId)?.healthServicesExerciseType ?: return ExerciseType.UNKNOWN
        return byName[name] ?: ExerciseType.UNKNOWN
    }

    /** Every name the catalogue can produce, so a test can prove none is missing. */
    val knownNames: Set<String> get() = byName.keys

    /**
     * What the app asks Health Services for.
     *
     * Deliberately modest: each extra type is another sensor kept awake, and the
     * watch batches what it can only when nothing forces a faster cadence. What
     * is not here comes from Bluetooth instead - power and cycling cadence have
     * no Health Services equivalent worth using.
     */
    val requestedDataTypes: Set<DataType<*, *>> =
        setOf(
            DataType.HEART_RATE_BPM,
            DataType.LOCATION,
            DataType.DISTANCE_TOTAL,
            DataType.SPEED,
            DataType.CALORIES_TOTAL,
            DataType.ABSOLUTE_ELEVATION,
            DataType.ELEVATION_GAIN_TOTAL,
            DataType.STEPS_PER_MINUTE,
        )
}
