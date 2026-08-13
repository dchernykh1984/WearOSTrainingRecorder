package com.dchernykh.trainingrecorder.core.fit

import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import com.garmin.fit.Activity
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import java.io.File
import java.util.Date
import kotlin.math.roundToInt

/** One sample. Everything but the timestamp is absent when its sensor is. */
data class TrackPoint(
    val timestampEpochMs: Long,
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val altitudeMeters: Double? = null,
    val heartRateBpm: Int? = null,
    val cadenceRpm: Int? = null,
    val speedMps: Double? = null,
    val powerWatts: Int? = null,
    val distanceMeters: Double? = null,
    val temperatureC: Int? = null,
)

/** A finished recording, ready to be written out. */
data class RecordedWorkout(
    val sportTypeId: String,
    val startedAtEpochMs: Long,
    val totalTimerSeconds: Double,
    val totalElapsedSeconds: Double,
    val totalDistanceMeters: Double,
    val points: List<TrackPoint>,
) {
    init {
        require(totalTimerSeconds >= 0) { "timer time cannot be negative" }
        require(totalElapsedSeconds >= totalTimerSeconds) { "elapsed time cannot be shorter than timer time" }
        require(totalDistanceMeters >= 0) { "distance cannot be negative" }
    }

    val endedAtEpochMs: Long get() = startedAtEpochMs + (totalElapsedSeconds * MILLIS_PER_SECOND).toLong()

    private companion object {
        const val MILLIS_PER_SECOND = 1000
    }
}

/**
 * Writes a FIT activity file with Garmin's own encoder.
 *
 * The message order is the one the FIT activity profile requires and every
 * importer expects: file id, a timer-start event, the records, a timer-stop
 * event, one lap, the session, and the activity. Getting that order wrong is the
 * usual reason an upload is accepted and then shows as empty.
 */
object FitActivityEncoder {
    /**
     * 255 is the FIT "development" manufacturer. Anything else would claim to be
     * hardware from a vendor we are not.
     */
    private const val MANUFACTURER_DEVELOPMENT = 255
    private const val SEMICIRCLES_PER_DEGREE = 2147483648.0 / 180.0

    fun encode(
        workout: RecordedWorkout,
        target: File,
    ) {
        val encoder = FileEncoder(target, Fit.ProtocolVersion.V2_0)
        try {
            encoder.write(fileId(workout))
            encoder.write(timerEvent(workout.startedAtEpochMs, EventType.START))
            workout.points.forEach { encoder.write(record(it)) }
            encoder.write(timerEvent(workout.endedAtEpochMs, EventType.STOP_ALL))
            encoder.write(lap(workout))
            encoder.write(session(workout))
            encoder.write(activity(workout))
        } finally {
            encoder.close()
        }
    }

    /** FIT stores coordinates as semicircles, not degrees. */
    fun semicircles(degrees: Double): Int = (degrees * SEMICIRCLES_PER_DEGREE).roundToInt()

    fun fitSport(sportTypeId: String): Sport =
        when (SportCatalogue.byId(sportTypeId)?.discipline) {
            Discipline.CYCLING -> Sport.CYCLING
            Discipline.RUNNING -> Sport.RUNNING
            Discipline.SWIMMING -> Sport.SWIMMING
            Discipline.XC_SKIING -> Sport.CROSS_COUNTRY_SKIING
            null -> Sport.GENERIC
        }

    private fun dateTime(epochMs: Long) = DateTime(Date(epochMs))

    private fun fileId(workout: RecordedWorkout) =
        FileIdMesg().apply {
            type = com.garmin.fit.File.ACTIVITY
            manufacturer = MANUFACTURER_DEVELOPMENT
            product = 1
            serialNumber = 1L
            timeCreated = dateTime(workout.startedAtEpochMs)
        }

    private fun timerEvent(
        epochMs: Long,
        type: EventType,
    ) = EventMesg().apply {
        timestamp = dateTime(epochMs)
        event = Event.TIMER
        eventType = type
    }

    private fun record(point: TrackPoint) =
        RecordMesg().apply {
            timestamp = dateTime(point.timestampEpochMs)
            point.latitudeDeg?.let { positionLat = semicircles(it) }
            point.longitudeDeg?.let { positionLong = semicircles(it) }
            point.altitudeMeters?.let { altitude = it.toFloat() }
            point.heartRateBpm?.let { heartRate = it.toShort() }
            point.cadenceRpm?.let { cadence = it.toShort() }
            point.speedMps?.let { speed = it.toFloat() }
            point.powerWatts?.let { power = it }
            point.distanceMeters?.let { distance = it.toFloat() }
            point.temperatureC?.let { temperature = it.toByte() }
        }

    private fun lap(workout: RecordedWorkout) =
        LapMesg().apply {
            messageIndex = 0
            timestamp = dateTime(workout.endedAtEpochMs)
            startTime = dateTime(workout.startedAtEpochMs)
            event = Event.LAP
            eventType = EventType.STOP
            sport = fitSport(workout.sportTypeId)
            totalElapsedTime = workout.totalElapsedSeconds.toFloat()
            totalTimerTime = workout.totalTimerSeconds.toFloat()
            totalDistance = workout.totalDistanceMeters.toFloat()
        }

    private fun session(workout: RecordedWorkout) =
        SessionMesg().apply {
            messageIndex = 0
            timestamp = dateTime(workout.endedAtEpochMs)
            startTime = dateTime(workout.startedAtEpochMs)
            event = Event.SESSION
            eventType = EventType.STOP
            sport = fitSport(workout.sportTypeId)
            totalElapsedTime = workout.totalElapsedSeconds.toFloat()
            totalTimerTime = workout.totalTimerSeconds.toFloat()
            totalDistance = workout.totalDistanceMeters.toFloat()
            firstLapIndex = 0
            numLaps = 1
        }

    private fun activity(workout: RecordedWorkout) =
        ActivityMesg().apply {
            timestamp = dateTime(workout.endedAtEpochMs)
            totalTimerTime = workout.totalTimerSeconds.toFloat()
            numSessions = 1
            type = Activity.MANUAL
            event = Event.ACTIVITY
            eventType = EventType.STOP
        }
}
