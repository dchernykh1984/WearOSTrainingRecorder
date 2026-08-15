package com.dchernykh.trainingrecorder.wear.health

import android.content.Context
import android.os.SystemClock
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import androidx.health.services.client.data.LocationData
import androidx.health.services.client.data.SampleDataPoint
import com.dchernykh.trainingrecorder.core.sensor.FixStatus
import com.dchernykh.trainingrecorder.core.sensor.SensorOrigin
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    /** Where [fix] is kept alive; the caller's scope, so it ends with the caller. */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val availability = MutableStateFlow<Map<String, Availability>>(emptyMap())

    /** Per data type, whether the watch can currently produce it. */
    val dataAvailability = availability.asStateFlow()

    /**
     * Where the position has got to, for the indicator on the ride screen.
     *
     * Read from what Health Services reports rather than inferred from whether
     * coordinates have arrived: a watch that is still acquiring has not sent any
     * either, and the two are worth telling apart.
     */
    val fix: StateFlow<FixStatus> =
        availability
            .map { fixStatusOf(it[DataType.LOCATION.name]) }
            .stateIn(scope, SharingStarted.Eagerly, FixStatus.NONE)

    suspend fun start(
        sportTypeId: String,
        gpsEnabled: Boolean = true,
        poolLengthMeters: Float = DEFAULT_POOL_LENGTH_METERS,
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
        val useGps = gpsEnabled && needsGps(sportTypeId)
        // ExerciseConfig refuses LOCATION unless GPS is on, so the type has to
        // leave the request rather than merely be ignored - otherwise an indoor
        // start throws instead of recording.
        val dataTypes =
            ExerciseTypes.requestedDataTypes
                .intersect(supported)
                .filterNot { !useGps && it == DataType.LOCATION }
                .toSet()
        val builder =
            ExerciseConfig
                .Builder(exerciseType)
                // Asking for a type the device cannot produce fails the whole
                // start, so the request is intersected with what it offers.
                .setDataTypes(dataTypes)
                // Auto-pause is off: the rider decides. A hill start that the
                // watch reads as a stop silently loses distance.
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(useGps)
        // A pool swim without a length is rejected outright: lengths and SWOLF
        // are meaningless without knowing how long a length is.
        if (exerciseType == ExerciseType.SWIMMING_POOL) {
            builder.setSwimmingPoolLengthMeters(poolLengthMeters)
        }
        client.startExerciseAsync(builder.build()).await()
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
                        // update, not a read-modify-write: callbacks for
                        // different data types arrive concurrently.
                        availability.update { it + (dataType.name to newAvailability) }
                    }
                }
            client.setUpdateCallback(callback)
            awaitClose { client.clearUpdateCallbackAsync(callback) }
        }

    /**
     * Whether this sport has a position to track at all.
     *
     * Public because the ride screen asks the same question: an indoor ride is
     * not failing to find satellites, it never asked for any, and a red GPS on a
     * turbo trainer is a bug report waiting to be filed.
     */
    fun needsGps(sportTypeId: String): Boolean {
        val sport = SportCatalogue.byId(sportTypeId) ?: return true
        // Indoor sports have nothing to track, and a GNSS radio hunting for
        // satellites through a roof is pure battery drain.
        return sport.healthServicesExerciseType !in INDOOR_EXERCISE_TYPES
    }

    private companion object {
        val INDOOR_EXERCISE_TYPES = setOf("BIKING_STATIONARY", "RUNNING_TREADMILL", "SWIMMING_POOL")

        /** The commonest pool in the world; the rider can change it in settings. */
        const val DEFAULT_POOL_LENGTH_METERS = 25f
    }
}

/**
 * What Health Services says about the position, in the three states a rider
 * needs. Anything that is not a real fix and is not actively looking is "no" -
 * including the unknown, since a state this build does not recognise is not one
 * to reassure anybody with.
 */
internal fun fixStatusOf(availability: Availability?): FixStatus =
    when (availability) {
        LocationAvailability.ACQUIRED_TETHERED, LocationAvailability.ACQUIRED_UNTETHERED -> FixStatus.ACQUIRED
        LocationAvailability.ACQUIRING -> FixStatus.ACQUIRING
        else -> FixStatus.NONE
    }

/**
 * Folds an update into the shape the shared sensor merge expects.
 *
 * Each reading is stamped with when it was *measured*, not when this ran.
 * Health Services batches, and the gap between the two is the whole batch - so
 * stamping on arrival makes a two-minute-old heart rate look like it was taken
 * this second, and the staleness rule downstream, which exists precisely to stop
 * that, is handed a timestamp that can never be stale.
 */
internal fun ExerciseUpdate.toSample(): BuiltInSample {
    val metrics = latestMetrics
    val now = System.currentTimeMillis()
    // Data points carry their age since boot; this is what turns that into a
    // wall clock without asking the platform twice per field.
    val bootAtEpochMs = now - SystemClock.elapsedRealtime()
    val readings = mutableMapOf<String, SensorReading>()

    fun record(
        fieldId: String,
        value: Double?,
        atEpochMs: Long = now,
    ) {
        value?.let { readings[fieldId] = SensorReading(it, SensorOrigin.BUILT_IN, atEpochMs) }
    }

    fun SampleDataPoint<*>.measuredAt(): Long = bootAtEpochMs + timeDurationFromBoot.toMillis()

    metrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { record("hr", it.value, it.measuredAt()) }
    metrics.getData(DataType.SPEED).lastOrNull()?.let { record("speed_current", it.value, it.measuredAt()) }
    metrics.getData(DataType.ABSOLUTE_ELEVATION).lastOrNull()?.let {
        record("altitude", it.value, it.measuredAt())
    }
    metrics.getData(DataType.STEPS_PER_MINUTE).lastOrNull()?.let {
        record("cadence", it.value.toDouble(), it.measuredAt())
    }
    // Running totals, which never go stale and so need no measurement time.
    record("distance_total", metrics.getData(DataType.DISTANCE_TOTAL)?.total)
    record("calories", metrics.getData(DataType.CALORIES_TOTAL)?.total)
    record("ascent_total", metrics.getData(DataType.ELEVATION_GAIN_TOTAL)?.total)

    val location: LocationData? = metrics.getData(DataType.LOCATION).lastOrNull()?.value
    return BuiltInSample(
        readings = readings,
        latitudeDeg = location?.latitude?.real(),
        longitudeDeg = location?.longitude?.real(),
        // The barometer as a fallback, which is usually the better number
        // anyway: a fix's altitude is the weakest thing GNSS produces.
        altitudeMeters = location?.altitude?.real() ?: readings["altitude"]?.value,
    )
}

/**
 * Null for a coordinate that is not one.
 *
 * Health Services fills a missing part of a fix with `Double.MAX_VALUE` rather
 * than leaving it out, and the value passes every null check on the way to the
 * FIT file. It showed up as an altitude of 1.8e308 in a recorded track - a
 * number that is either rejected by the service the ride is uploaded to or,
 * worse, accepted. A fix with no altitude is ordinary; writing down a fake one
 * is not.
 */
private fun Double.real(): Double? = takeIf { it.isFinite() && it != Double.MAX_VALUE }
