package com.dchernykh.trainingrecorder.wear.health

import android.content.Context
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationData
import com.dchernykh.trainingrecorder.core.sensor.SensorOrigin
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await

/** What the watch itself is producing right now. */
data class BuiltInSample(
    val readings: Map<String, SensorReading> = emptyMap(),
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val altitudeMeters: Double? = null,
)

/**
 * Drives Health Services for one workout.
 *
 * The point of going through Health Services rather than reading GNSS and the
 * optical sensor directly is battery: it batches sensor delivery and lets the
 * processor sleep between batches, and it keeps the session alive across our own
 * process being killed. Doing it by hand costs hours of ride time.
 */
class ExerciseRecorder(
    context: Context,
    private val client: ExerciseClient = HealthServices.getClient(context).exerciseClient,
) {
    private val availability = MutableStateFlow<Map<String, Availability>>(emptyMap())

    /** Per data type, whether the watch can currently produce it. */
    val dataAvailability = availability.asStateFlow()

    suspend fun start(
        sportTypeId: String,
        gpsEnabled: Boolean = true,
    ) {
        val capabilities = client.getCapabilitiesAsync().await()
        val exerciseType = ExerciseTypes.forSport(sportTypeId)
        // getExerciseTypeCapabilities throws for a type the device does not
        // support, and for UNKNOWN. Degrading to "no data types" starts a bare
        // session instead, which is what ExerciseTypes promises: a drifted
        // mapping must not stop the rider recording.
        val supported =
            runCatching { capabilities.getExerciseTypeCapabilities(exerciseType).supportedDataTypes }
                .getOrDefault(emptySet())
        val config =
            ExerciseConfig
                .Builder(exerciseType)
                // Asking for a type the device cannot produce fails the whole
                // start, so the request is intersected with what it offers.
                .setDataTypes(ExerciseTypes.requestedDataTypes.intersect(supported))
                // Auto-pause is off: the rider decides. A hill start that the
                // watch reads as a stop silently loses distance.
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(gpsEnabled && needsGps(sportTypeId))
                .build()
        client.startExerciseAsync(config).await()
    }

    suspend fun pause() = client.pauseExerciseAsync().await()

    suspend fun resume() = client.resumeExerciseAsync().await()

    suspend fun end() = client.endExerciseAsync().await()

    suspend fun markLap() = client.markLapAsync().await()

    /** Flushes whatever is batched, so a finished workout keeps its last seconds. */
    suspend fun flush() = client.flushAsync().await()

    /**
     * The live stream of samples. Registration is torn down when the collector
     * goes away, which is what stops the session outliving the screen.
     */
    fun samples(): Flow<BuiltInSample> =
        callbackFlow {
            val callback =
                object : ExerciseUpdateCallback {
                    override fun onRegistered() = Unit

                    override fun onRegistrationFailed(throwable: Throwable) {
                        close(throwable)
                    }

                    override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                        trySend(update.toSample())
                    }

                    override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

                    override fun onAvailabilityChanged(
                        dataType: DataType<*, *>,
                        newAvailability: Availability,
                    ) {
                        availability.value = availability.value + (dataType.name to newAvailability)
                    }
                }
            client.setUpdateCallback(callback)
            awaitClose { client.clearUpdateCallbackAsync(callback) }
        }

    private fun needsGps(sportTypeId: String): Boolean {
        val sport = SportCatalogue.byId(sportTypeId) ?: return true
        // Indoor sports have nothing to track, and a GNSS radio hunting for
        // satellites through a roof is pure battery drain.
        return sport.healthServicesExerciseType !in INDOOR_EXERCISE_TYPES
    }

    private companion object {
        val INDOOR_EXERCISE_TYPES = setOf("BIKING_STATIONARY", "RUNNING_TREADMILL", "SWIMMING_POOL")
    }
}

/** Folds an update into the shape the shared sensor merge expects. */
internal fun ExerciseUpdate.toSample(): BuiltInSample {
    val metrics = latestMetrics
    val now = System.currentTimeMillis()
    val readings = mutableMapOf<String, SensorReading>()

    fun record(
        fieldId: String,
        value: Double?,
    ) {
        value?.let { readings[fieldId] = SensorReading(it, SensorOrigin.BUILT_IN, now) }
    }

    record("hr", metrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value)
    record("speed_current", metrics.getData(DataType.SPEED).lastOrNull()?.value)
    record("distance_total", metrics.getData(DataType.DISTANCE_TOTAL)?.total)
    record("calories", metrics.getData(DataType.CALORIES_TOTAL)?.total)
    record("altitude", metrics.getData(DataType.ABSOLUTE_ELEVATION).lastOrNull()?.value)
    record("ascent_total", metrics.getData(DataType.ELEVATION_GAIN_TOTAL)?.total)
    record(
        "cadence",
        metrics
            .getData(DataType.STEPS_PER_MINUTE)
            .lastOrNull()
            ?.value
            ?.toDouble(),
    )

    val location: LocationData? = metrics.getData(DataType.LOCATION).lastOrNull()?.value
    return BuiltInSample(
        readings = readings,
        latitudeDeg = location?.latitude,
        longitudeDeg = location?.longitude,
        altitudeMeters = location?.altitude ?: readings["altitude"]?.value,
    )
}
