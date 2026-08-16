package com.dchernykh.trainingrecorder.core.track

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideTrackTest {
    private val start = 1_782_000_000_000L

    /** A hundred metres north of the last one, a second later. */
    private fun northOf(
        fix: Fix,
        metres: Double,
        afterSeconds: Long = 1,
    ) = Fix(
        latitudeDeg = fix.latitudeDeg + metres / METERS_PER_DEGREE_LATITUDE,
        longitudeDeg = fix.longitudeDeg,
        atEpochMs = fix.atEpochMs + afterSeconds * 1000,
    )

    @Test
    fun aKnownDistanceComesBackAsThatDistance() {
        // A degree of latitude is about 111 km everywhere, which is what makes
        // it the one check that needs no reference table.
        val from = Fix(55.0, 37.0, start)
        val to = Fix(56.0, 37.0, start + 3_600_000)
        val metres = Haversine.metresBetween(from, to)
        assertTrue(abs(metres - 111_195) < 500, "a degree of latitude should be about 111 km, got $metres")
    }

    @Test
    fun theSameSpotIsNoDistanceAtAll() {
        val fix = Fix(55.0, 37.0, start)
        assertEquals(0.0, Haversine.metresBetween(fix, fix))
    }

    @Test
    fun aRideAddsUpToWhatItCovered() {
        val track = RideTrack()
        var fix = Fix(55.0, 37.0, start)
        repeat(10) {
            fix = northOf(fix, metres = 10.0)
            track.record(fix)
        }
        // Ten steps, but the first fix only sets the baseline - nine are
        // measured.
        assertTrue(abs(track.distanceMeters - 90) < 1, "expected about 90 m, got ${track.distanceMeters}")
        assertTrue(abs(assertNotNull(track.speedMps) - 10.0) < 0.1)
    }

    @Test
    fun aWalkerCoversTheGroundTheyActuallyWalked() {
        // The bug that ended the filtering experiment. Fixes arrive about once a
        // second and a walker covers a metre and a half in that time, so a three
        // metre step threshold - perfectly reasonable for cycling - discarded
        // every single step. Two hundred metres recorded three, which is worse
        // than recording nothing because it looks like it worked.
        val track = RideTrack()
        var fix = Fix(55.0, 37.0, start)
        repeat(140) {
            fix = northOf(fix, metres = 1.4)
            track.record(fix)
        }
        assertTrue(
            abs(track.distanceMeters - 196) < 2,
            "expected 196 m of walking, got ${track.distanceMeters}",
        )
        assertTrue(abs(assertNotNull(track.speedMps) - 1.4) < 0.05)
    }

    @Test
    fun aWanderingReceiverIsRecordedAsWandering() {
        // Deliberately not filtered. Where the receiver moves about, the ride
        // shows that movement - which is the truth about the measurement -
        // rather than a tidier number the app decided on. Anything that judges a
        // fix unreal is a guess, and this one was wrong for every walk.
        val track = RideTrack()
        val centre = Fix(55.0, 37.0, start)
        (0..60).forEach { second ->
            val wobble = if (second % 2 == 0) 2.0 else -2.0
            track.record(
                Fix(
                    latitudeDeg = centre.latitudeDeg + wobble / METERS_PER_DEGREE_LATITUDE,
                    longitudeDeg = centre.longitudeDeg,
                    atEpochMs = start + second * 1000L,
                ),
            )
        }
        assertTrue(track.distanceMeters > 0, "the wander is what the receiver reported")
    }

    @Test
    fun aFixThatJumpsIsCountedRatherThanSecondGuessed() {
        // A jump reads as a burst of speed, which is what a jumping receiver
        // actually looks like. Refusing it needs a threshold, and a threshold
        // is a guess about which measurements are allowed to be true.
        val track = RideTrack()
        val here = Fix(55.0, 37.0, start)
        track.record(here)
        track.record(Fix(55.02, 37.0, start + 1000))
        assertTrue(track.distanceMeters > 2000, "the jump is the data")
        assertTrue(assertNotNull(track.speedMps) > 1000)
    }

    @Test
    fun fixesOutOfOrderAreIgnoredRatherThanCounted() {
        val track = RideTrack()
        val here = Fix(55.0, 37.0, start)
        track.record(here)
        track.record(Fix(55.001, 37.0, start - 5000))
        assertEquals(0.0, track.distanceMeters)
    }

    @Test
    fun theFastestStretchIsRemembered() {
        val track = RideTrack()
        var fix = Fix(55.0, 37.0, start)
        fix = northOf(fix, metres = 10.0).also { track.record(it) }
        fix = northOf(fix, metres = 20.0).also { track.record(it) }
        fix = northOf(fix, metres = 5.0).also { track.record(it) }
        assertTrue(abs(track.maxSpeedMps - 20.0) < 0.2, "got ${track.maxSpeedMps}")
    }

    @Test
    fun thereIsNoSpeedBeforeThereAreTwoFixes() {
        val track = RideTrack()
        track.record(Fix(55.0, 37.0, start))
        assertNull(track.speedMps)
    }

    @Test
    fun clearingLeavesNothingOfTheLastRide() {
        val track = RideTrack()
        var fix = Fix(55.0, 37.0, start)
        repeat(3) { fix = northOf(fix, metres = 10.0).also(track::record) }
        track.clear()
        assertEquals(0.0, track.distanceMeters)
        assertEquals(0.0, track.maxSpeedMps)
        assertNull(track.speedMps)
        // And the next ride starts from its own first fix rather than the last
        // ride's, which would otherwise be a step across the country.
        track.record(Fix(43.0, 76.0, start + 100_000))
        assertEquals(0.0, track.distanceMeters)
    }

    private companion object {
        const val METERS_PER_DEGREE_LATITUDE = 111_195.0
    }

    @Test
    fun aBatchHandedOverAtOnceStillReadsAtTheSpeedItWasRidden() {
        // What the wrist turn does. With the screen off the platform holds a
        // minute of positions and releases the lot in a few milliseconds. Each
        // fix carries when it was *measured*, so handing them over together
        // changes nothing - stamped on arrival instead, the same minute of
        // travel would land inside those milliseconds and read as hundreds of
        // kilometres an hour before the next honest value replaced it.
        val track = RideTrack()
        val measured =
            (0 until 60).map { second ->
                Fix(
                    latitudeDeg = 55.0 + second * 8.0 / METERS_PER_DEGREE_LATITUDE,
                    longitudeDeg = 37.0,
                    atEpochMs = start + second * 1000L,
                )
            }
        measured.forEach(track::record)
        assertTrue(abs(track.distanceMeters - 472) < 5, "expected about 472 m, got ${track.distanceMeters}")
        assertTrue(abs(track.maxSpeedMps - 8.0) < 0.1, "no fix may read faster than it was ridden")
    }

    @Test
    fun stampingABatchOnArrivalIsWhatProducedTheAbsurdSpeeds() {
        // The failure, written down so the contract above is not mistaken for
        // decoration: the same minute of riding, with every fix carrying the
        // moment it was handed over instead of the moment it was taken.
        val track = RideTrack()
        (0 until 60).forEach { second ->
            track.record(
                Fix(
                    latitudeDeg = 55.0 + second * 8.0 / METERS_PER_DEGREE_LATITUDE,
                    longitudeDeg = 37.0,
                    atEpochMs = start + second * 2L,
                ),
            )
        }
        assertTrue(
            track.maxSpeedMps > 1000,
            "delivery timestamps should read as nonsense, which is why they are not used",
        )
    }
}
