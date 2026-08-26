package com.dchernykh.trainingrecorder.core.segment

import com.dchernykh.trainingrecorder.core.track.Fix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The speeds here are the speeds of a bicycle: eight metres a second, one fix a
 * second, which is what the receiver actually delivers. Test rides at implausible
 * speeds have twice now defended real bugs in this project, so these ones are
 * kept honest on purpose.
 */
class SegmentTrackerTest {
    private val start = 1_782_000_000_000L
    private val baseLat = 55.0
    private val baseLon = 37.0

    /** A straight kilometre north, with a point every ten metres. */
    private fun straightSegment(
        reference: List<EffortPoint> = emptyList(),
        climbing: Boolean = false,
    ) = Segment(
        id = 1L,
        name = "Test hill",
        points =
            (0..100).map { step ->
                val metres = step * 10.0
                SegmentPoint(
                    latitudeDeg = baseLat + metres / METERS_PER_DEGREE_LATITUDE,
                    longitudeDeg = baseLon,
                    distanceMeters = metres,
                    altitudeMeters = if (climbing) 100.0 + metres * GRADE else null,
                )
            },
        reference = reference,
    )

    /**
     * Five hundred metres out and five hundred back, five metres to the side.
     *
     * The shape that breaks a naive matcher: from any point on the way out, the
     * way home is closer than most of the segment ahead.
     */
    private fun outAndBackSegment(): Segment {
        val out =
            (0..50).map { step ->
                val metres = step * 10.0
                SegmentPoint(baseLat + metres / METERS_PER_DEGREE_LATITUDE, baseLon, metres)
            }
        val back =
            (1..50).map { step ->
                val north = 500.0 - step * 10.0
                SegmentPoint(
                    latitudeDeg = baseLat + north / METERS_PER_DEGREE_LATITUDE,
                    longitudeDeg = baseLon + LANE_METERS / metersPerDegreeLongitude(baseLat),
                    distanceMeters = 500.0 + step * 10.0,
                )
            }
        return Segment(id = 2L, name = "Out and back", points = out + back)
    }

    /** Rides the line at eight metres a second, a fix a second. */
    private fun ride(
        tracker: SegmentTracker,
        segment: Segment,
        toMeters: Double = segment.distanceMeters,
        speedMps: Double = RIDING_SPEED_MPS,
        startingAt: Long = start,
        sidewaysMeters: Double = 0.0,
    ): Long {
        var atEpochMs = startingAt
        var metres = 0.0
        while (metres <= toMeters) {
            tracker.record(alongside(segment, metres, atEpochMs, sidewaysMeters))
            metres += speedMps
            atEpochMs += 1000
        }
        return atEpochMs
    }

    /** The position a rider this far along the segment would report. */
    private fun alongside(
        segment: Segment,
        distanceMeters: Double,
        atEpochMs: Long,
        sidewaysMeters: Double = 0.0,
    ): Fix {
        val after = segment.points.indexOfFirst { it.distanceMeters >= distanceMeters }.coerceAtLeast(1)
        val before = segment.points[after - 1]
        val ahead = segment.points[after]
        val span = ahead.distanceMeters - before.distanceMeters
        val part = if (span > 0) (distanceMeters - before.distanceMeters) / span else 0.0
        return Fix(
            latitudeDeg = before.latitudeDeg + part * (ahead.latitudeDeg - before.latitudeDeg),
            longitudeDeg =
                before.longitudeDeg + part * (ahead.longitudeDeg - before.longitudeDeg) +
                    sidewaysMeters / metersPerDegreeLongitude(baseLat),
            atEpochMs = atEpochMs,
        )
    }

    /** A reference effort at a steady pace, sampled once a second. */
    private fun steadyEffort(
        distanceMeters: Double,
        seconds: Double,
    ): List<EffortPoint> {
        val speed = distanceMeters / seconds
        return (0..seconds.toInt()).map { EffortPoint(it * speed, it.toDouble()) }
    }

    @Test
    fun beforeTheStartItReportsHowFarThereIsToGo() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))

        // Two hundred metres south of the start, riding towards it.
        tracker.record(Fix(baseLat - 200.0 / METERS_PER_DEGREE_LATITUDE, baseLon, start))

        val state = assertNotNull(tracker.state, "the nearest segment should be reported")
        assertEquals(segment.id, state.segment.id)
        assertFalse(state.riding, "not on the segment yet")
        val toStart = assertNotNull(state.toStartMeters)
        assertTrue(abs(toStart - 200.0) < 1.0, "expected about 200 m to the start, got $toStart")
    }

    @Test
    fun reachingTheStartBeginsTheEffort() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))

        tracker.record(alongside(segment, 0.0, start))

        val state = assertNotNull(tracker.state)
        assertTrue(state.riding, "the effort should be running")
        assertEquals(0.0, state.elapsedSeconds)
    }

    @Test
    fun theClockStartsAtTheLineRatherThanAtTheRunUp() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))
        var atEpochMs = start

        // Twenty metres of run-up inside the start radius, then the line itself.
        // A clock started at the first fix inside the radius would already read
        // seven seconds by the time the rider reached the start.
        listOf(-20.0, -12.0, -4.0, 0.0, 8.0).forEach { metres ->
            tracker.record(
                Fix(baseLat + metres / METERS_PER_DEGREE_LATITUDE, baseLon, atEpochMs),
            )
            atEpochMs += 1000
        }

        val state = assertNotNull(tracker.state)
        assertTrue(state.riding)
        // Nearest the start at the fourth fix, one second before the fifth.
        assertEquals(1.0, state.elapsedSeconds, "the run-up is not part of the effort")
    }

    @Test
    fun aKilometreRiddenIsAKilometreTimed() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))

        ride(tracker, segment)

        val state = assertNotNull(tracker.state)
        assertTrue(state.finished, "the segment should have finished")
        // A kilometre at eight metres a second is 125 seconds.
        assertTrue(
            abs(state.elapsedSeconds - 125.0) <= 1.0,
            "expected about 125 s, got ${state.elapsedSeconds}",
        )
        assertEquals(1000.0, state.coveredMeters)
        assertEquals(0.0, state.remainingMeters)
    }

    @Test
    fun aSlowerRideIsReportedAsBehind() {
        // The rider's best was 125 s; today they are riding at seven.
        val segment = straightSegment(reference = steadyEffort(1000.0, 125.0))
        val tracker = SegmentTracker(listOf(segment))

        ride(tracker, segment, toMeters = 700.0, speedMps = 7.0)

        val state = assertNotNull(tracker.state)
        val ahead = assertNotNull(state.aheadSeconds, "there is a reference effort to compare against")
        // 700 m at 7 m/s is 100 s; the reference took 87.5 s to the same point.
        // Ahead is positive, so being down reads as a negative number.
        assertTrue(ahead < -10.0, "expected to be well over ten seconds down, got $ahead")
        val metres = assertNotNull(state.aheadMeters)
        assertTrue(metres < -50.0, "expected to be nearly a hundred metres back, got $metres")
    }

    @Test
    fun aFasterRideIsReportedAsAhead() {
        val segment = straightSegment(reference = steadyEffort(1000.0, 125.0))
        val tracker = SegmentTracker(listOf(segment))

        ride(tracker, segment, toMeters = 700.0, speedMps = 9.0)

        val state = assertNotNull(tracker.state)
        val ahead = assertNotNull(state.aheadSeconds)
        assertTrue(ahead > 5.0, "expected to be seconds up, got $ahead")
        assertTrue(assertNotNull(state.aheadMeters) > 20.0, "expected to be metres up the road")
    }

    @Test
    fun aSegmentNeverRiddenBeforeIsTimedWithNobodyToRace() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))

        ride(tracker, segment, toMeters = 500.0)

        val state = assertNotNull(tracker.state)
        assertTrue(state.riding)
        assertTrue(state.elapsedSeconds > 60.0, "the clock still runs")
        assertNull(state.aheadSeconds, "with no reference effort there is nothing to be ahead of")
        assertNull(state.aheadMeters)
    }

    @Test
    fun aSegmentThatDoublesBackDoesNotSkipToTheWayHome() {
        val segment = outAndBackSegment()
        val tracker = SegmentTracker(listOf(segment))

        // Ride only the outward leg, three metres to one side of the recorded
        // line, which is ordinary receiver error and puts the rider closer to
        // the homeward leg than to the line they are actually on. A matcher
        // that searched the whole segment would put them on the way home and
        // call the effort nearly done.
        ride(tracker, segment, toMeters = 480.0, sidewaysMeters = OFFSET_METERS)

        val state = assertNotNull(tracker.state)
        assertTrue(state.riding, "still on the way out")
        assertTrue(
            state.coveredMeters in 460.0..520.0,
            "expected to be near the turn, got ${state.coveredMeters} m",
        )
    }

    @Test
    fun theWholeOutAndBackIsTimedInFull() {
        val segment = outAndBackSegment()
        val tracker = SegmentTracker(listOf(segment))

        ride(tracker, segment, sidewaysMeters = OFFSET_METERS)

        val state = assertNotNull(tracker.state)
        assertTrue(state.finished, "the segment should have finished")
        // A kilometre at eight metres a second, not the half it looks like.
        assertTrue(
            abs(state.elapsedSeconds - 125.0) <= 2.0,
            "expected about 125 s, got ${state.elapsedSeconds}",
        )
    }

    @Test
    fun ridingOffTheSegmentAbandonsTheEffort() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))
        var atEpochMs = ride(tracker, segment, toMeters = 300.0)

        // Turned off the road: a minute of riding two hundred metres to the side.
        repeat(60) {
            tracker.record(alongside(segment, 300.0, atEpochMs, sidewaysMeters = 200.0))
            atEpochMs += 1000
        }

        val state = assertNotNull(tracker.state)
        assertFalse(state.riding, "the effort should have been given up")
        assertNotNull(state.toStartMeters, "and the start reported as somewhere to go back to")
    }

    @Test
    fun oneStrayFixDoesNotThrowTheEffortAway() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))
        val atEpochMs = ride(tracker, segment, toMeters = 300.0)

        // A single fix in the next street, which is a receiver having a moment.
        tracker.record(alongside(segment, 304.0, atEpochMs, sidewaysMeters = 200.0))
        tracker.record(alongside(segment, 312.0, atEpochMs + 1000))

        val state = assertNotNull(tracker.state)
        assertTrue(state.riding, "the effort should have survived it")
        assertTrue(state.coveredMeters >= 300.0, "and carried on up the road")
    }

    @Test
    fun theFinishingTimeStaysOnScreenAfterwards() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))
        var atEpochMs = ride(tracker, segment)
        val finishing = assertNotNull(tracker.state).elapsedSeconds

        // Riding on past the finish for ten seconds.
        repeat(10) {
            tracker.record(
                Fix(baseLat + (1000.0 + it * RIDING_SPEED_MPS) / METERS_PER_DEGREE_LATITUDE, baseLon, atEpochMs),
            )
            atEpochMs += 1000
        }

        val state = assertNotNull(tracker.state)
        assertTrue(state.finished, "the result should still be showing")
        assertEquals(finishing, state.elapsedSeconds, "and should not still be counting")
    }

    @Test
    fun theClimbLeftIsWhatIsStillAbove() {
        val segment = straightSegment(climbing = true)
        val tracker = SegmentTracker(listOf(segment))

        ride(tracker, segment, toMeters = 400.0)

        val state = assertNotNull(tracker.state)
        // A steady five percent: six hundred metres left is thirty metres up.
        val ascent = assertNotNull(state.remainingAscentMeters)
        assertTrue(abs(ascent - 30.0) < 2.0, "expected about 30 m of climbing left, got $ascent")
        val grade = assertNotNull(state.remainingGradePercent)
        assertTrue(abs(grade - 5.0) < 0.5, "expected about five percent, got $grade")
    }

    @Test
    fun theNearestOfSeveralSegmentsIsTheOneShown() {
        val near = straightSegment()
        val far =
            Segment(
                id = 3L,
                name = "Somewhere else",
                points =
                    (0..10).map {
                        SegmentPoint(baseLat + 5.0, baseLon + it * 0.001, it * 60.0)
                    },
            )
        val tracker = SegmentTracker(listOf(far, near))

        tracker.record(Fix(baseLat - 500.0 / METERS_PER_DEGREE_LATITUDE, baseLon, start))

        assertEquals(near.id, assertNotNull(tracker.state).segment.id)
    }

    @Test
    fun withNoStarredSegmentsThereIsNothingToShow() {
        val tracker = SegmentTracker()

        tracker.record(Fix(baseLat, baseLon, start))

        assertNull(tracker.state)
    }

    @Test
    fun clearingForgetsTheRide() {
        val segment = straightSegment()
        val tracker = SegmentTracker(listOf(segment))
        ride(tracker, segment, toMeters = 300.0)

        tracker.clear()

        assertNull(tracker.state)
    }

    private fun metersPerDegreeLongitude(latitudeDeg: Double) =
        METERS_PER_DEGREE_LATITUDE * kotlin.math.cos(latitudeDeg * Math.PI / 180.0)

    private companion object {
        const val METERS_PER_DEGREE_LATITUDE = 111_195.0

        /** Twenty-nine kilometres an hour, which is a bicycle. */
        const val RIDING_SPEED_MPS = 8.0

        /** How far apart the two legs of the out-and-back run. */
        const val LANE_METERS = 5.0

        /** Ordinary receiver error, towards the other side of the road. */
        const val OFFSET_METERS = 3.0

        /** A steady five percent, as a rise per metre. */
        const val GRADE = 0.05
    }
}
