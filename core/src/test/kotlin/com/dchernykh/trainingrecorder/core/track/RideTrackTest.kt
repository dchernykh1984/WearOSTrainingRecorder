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
    fun aWatchLeftOnATableDoesNotGoForARide() {
        // The failure this filter exists for: a stationary receiver still emits
        // fixes a metre or two apart, and an hour of that is a ride that never
        // happened.
        val track = RideTrack()
        var fix = Fix(55.0, 37.0, start)
        repeat(3600) {
            fix = northOf(fix, metres = 1.5)
            track.record(fix)
        }
        assertEquals(0.0, track.distanceMeters, "an hour of jitter is not a ride")
        assertEquals(0.0, track.speedMps, "a stopped rider is stopped")
    }

    @Test
    fun oneAbsurdFixDoesNotAddAKilometreToTheRide() {
        val track = RideTrack()
        val here = Fix(55.0, 37.0, start)
        track.record(here)
        track.record(northOf(here, metres = 10.0))
        val before = track.distanceMeters
        // A kilometre in a second, which is not a bicycle.
        track.record(Fix(55.02, 37.0, start + 2000))
        assertEquals(before, track.distanceMeters, "an impossible step must not count")
    }

    @Test
    fun theRideCarriesOnFromWhereTheBadFixWas() {
        // The refused fix still becomes the baseline. Measuring the next step
        // from the position the rider left long ago would turn one bad fix into
        // a wrong distance for the rest of the ride.
        val track = RideTrack()
        track.record(Fix(55.0, 37.0, start))
        track.record(Fix(55.02, 37.0, start + 1000))
        val jumped = Fix(55.02, 37.0, start + 1000)
        track.record(northOf(jumped, metres = 10.0, afterSeconds = 1))
        assertTrue(abs(track.distanceMeters - 10) < 1, "expected 10 m from the new baseline")
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
}
