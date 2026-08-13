package com.dchernykh.trainingrecorder.core.fit

import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FitActivityEncoderTest {
    private val start = 1_770_000_000_000L

    private fun workout(
        sportTypeId: String = "cycling_road",
        points: List<TrackPoint> = samplePoints(),
    ) = RecordedWorkout(
        sportTypeId = sportTypeId,
        startedAtEpochMs = start,
        totalTimerSeconds = 120.0,
        totalElapsedSeconds = 130.0,
        totalDistanceMeters = 1000.0,
        points = points,
    )

    private fun samplePoints() =
        (0 until 3).map {
            TrackPoint(
                timestampEpochMs = start + it * 1000L,
                latitudeDeg = 43.238949 + it * 0.0001,
                longitudeDeg = 76.889709 + it * 0.0001,
                altitudeMeters = 800.0 + it,
                heartRateBpm = 140 + it,
                cadenceRpm = 90,
                speedMps = 8.3,
                powerWatts = 250 + it,
                distanceMeters = it * 8.3,
                temperatureC = 21,
            )
        }

    private fun encodeToTemp(workout: RecordedWorkout): File {
        val file = File.createTempFile("workout", ".fit")
        file.deleteOnExit()
        FitActivityEncoder.encode(workout, file)
        return file
    }

    private class Collector {
        val records = mutableListOf<RecordMesg>()
        val sessions = mutableListOf<SessionMesg>()
    }

    private fun decode(file: File): Collector {
        val collector = Collector()
        val broadcaster = MesgBroadcaster(Decode())
        broadcaster.addListener(com.garmin.fit.RecordMesgListener { collector.records += it })
        broadcaster.addListener(com.garmin.fit.SessionMesgListener { collector.sessions += it })
        file.inputStream().use { broadcaster.run(it) }
        return collector
    }

    @Test
    fun theFileGarminsOwnDecoderReadsBackIsTheOneWeWrote() {
        val decoded = decode(encodeToTemp(workout()))
        assertEquals(3, decoded.records.size)
        assertEquals(1, decoded.sessions.size)
    }

    @Test
    fun theFileIsAValidFitStreamAccordingToTheSdk() {
        val file = encodeToTemp(workout())
        assertTrue(file.length() > 0, "an empty file is not a FIT file")
        file.inputStream().use { assertTrue(Decode().checkFileIntegrity(it), "the SDK rejects the file we produced") }
    }

    @Test
    fun theSessionCarriesTheSportAndTheTotals() {
        val session = decode(encodeToTemp(workout())).sessions.single()
        assertEquals(Sport.CYCLING, session.sport)
        assertEquals(120.0f, session.totalTimerTime)
        assertEquals(130.0f, session.totalElapsedTime)
        assertEquals(1000.0f, session.totalDistance)
        assertEquals(1, session.numLaps)
    }

    @Test
    fun everyDisciplineMapsOntoAFitSport() {
        assertEquals(Sport.CYCLING, FitActivityEncoder.fitSport("cycling_gravel"))
        assertEquals(Sport.RUNNING, FitActivityEncoder.fitSport("run_trail"))
        assertEquals(Sport.SWIMMING, FitActivityEncoder.fitSport("swim_pool"))
        assertEquals(Sport.CROSS_COUNTRY_SKIING, FitActivityEncoder.fitSport("xc_skate"))
        assertEquals(Sport.GENERIC, FitActivityEncoder.fitSport("no_such_sport"))
    }

    @Test
    fun coordinatesSurviveTheRoundTripThroughSemicircles() {
        val decoded = decode(encodeToTemp(workout()))
        val first = decoded.records.first()
        val degreesPerSemicircle = 180.0 / 2147483648.0
        val lat = first.positionLat * degreesPerSemicircle
        val lon = first.positionLong * degreesPerSemicircle
        assertTrue(abs(lat - 43.238949) < 1e-6, "latitude drifted to $lat")
        assertTrue(abs(lon - 76.889709) < 1e-6, "longitude drifted to $lon")
    }

    @Test
    fun semicirclesUseTheFullSignedRange() {
        assertEquals(0, FitActivityEncoder.semicircles(0.0))
        assertEquals(1073741824, FitActivityEncoder.semicircles(90.0))
        assertEquals(-1073741824, FitActivityEncoder.semicircles(-90.0))
    }

    @Test
    fun sensorValuesAreCarriedThrough() {
        val first = decode(encodeToTemp(workout())).records.first()
        assertEquals(140, first.heartRate.toInt())
        assertEquals(90, first.cadence.toInt())
        assertEquals(250, first.power)
        assertEquals(21, first.temperature.toInt())
    }

    @Test
    fun aWorkoutWithoutSensorsStillProducesAValidFile() {
        val bare = workout(points = listOf(TrackPoint(timestampEpochMs = start)))
        val decoded = decode(encodeToTemp(bare))
        assertEquals(1, decoded.records.size)
        assertEquals(1, decoded.sessions.size)
    }

    @Test
    fun aWorkoutWithNoPointsAtAllStillProducesAValidFile() {
        val empty = workout(points = emptyList())
        val decoded = decode(encodeToTemp(empty))
        assertEquals(0, decoded.records.size)
        assertEquals(1, decoded.sessions.size, "a session is written even when nothing was recorded")
    }

    @Test
    fun impossibleTotalsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            RecordedWorkout("cycling_road", start, -1.0, 10.0, 0.0, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            RecordedWorkout("cycling_road", start, 100.0, 10.0, 0.0, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            RecordedWorkout("cycling_road", start, 10.0, 10.0, -1.0, emptyList())
        }
    }

    @Test
    fun theEndIsTheStartPlusTheElapsedTime() {
        assertEquals(start + 130_000L, workout().endedAtEpochMs)
    }
}
