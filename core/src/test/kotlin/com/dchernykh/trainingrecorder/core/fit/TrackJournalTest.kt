package com.dchernykh.trainingrecorder.core.fit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackJournalTest {
    private val start = 1_770_000_000_000L

    private fun point(
        index: Int,
        distance: Double? = 100.0,
    ) = TrackPoint(
        timestampEpochMs = start + index * 1000L,
        latitudeDeg = 43.238949 + index * 0.0001,
        longitudeDeg = 76.889709,
        altitudeMeters = 800.0,
        heartRateBpm = 140 + index,
        cadenceRpm = 90,
        speedMps = 8.3,
        powerWatts = 250,
        distanceMeters = distance,
        temperatureC = 21,
    )

    private fun journal(
        points: List<TrackPoint>,
        moving: Long = 3000L,
        sportTypeId: String = "cycling_road",
    ): Sequence<String> =
        (
            listOf(TrackJournal.header(sportTypeId, start)) +
                points.map { TrackJournal.line(it, moving) }
        ).asSequence()

    @Test
    fun `reads back every sample it wrote`() {
        val written = (0 until 3).map(::point)

        val recovered = assertNotNull(TrackJournal.parse(journal(written)))

        assertEquals("cycling_road", recovered.sportTypeId)
        assertEquals(start, recovered.startedAtEpochMs)
        assertEquals(written, recovered.points)
        assertEquals(3000L, recovered.movingMillis)
    }

    @Test
    fun `keeps absent readings absent rather than turning them into zeroes`() {
        val sparse = TrackPoint(timestampEpochMs = start)

        val recovered = assertNotNull(TrackJournal.parse(journal(listOf(sparse))))

        assertEquals(listOf(sparse), recovered.points)
    }

    /**
     * The reason the format exists: a watch that loses power stops mid-line, and
     * everything written before that has to survive.
     */
    @Test
    fun `drops a half-written last line and keeps the rest`() {
        val complete = journal((0 until 3).map(::point)).toList()
        val truncated = complete.dropLast(1) + complete.last().substringBefore("\t8.3")

        val recovered = assertNotNull(TrackJournal.parse(truncated.asSequence()))

        assertEquals(2, recovered.points.size)
    }

    /**
     * The subtle half of the same failure: a line cut off inside its last value
     * still has every column, and the digits that survived parse as a perfectly
     * plausible smaller number - so the ride would come back claiming it had
     * moved for two minutes when it had moved for three hours.
     */
    @Test
    fun `drops a last line cut off inside its final column`() {
        val complete = journal(listOf(point(0), point(1)), moving = 10_740_000L).toList()
        val truncated = complete.dropLast(1) + complete.last().substringBefore("740000")

        val recovered = assertNotNull(TrackJournal.parse(truncated.asSequence()))

        assertEquals(1, recovered.points.size)
        assertEquals(10_740_000L, recovered.movingMillis)
    }

    @Test
    fun `refuses a journal it cannot place a ride in`() {
        assertNull(TrackJournal.parse(emptySequence()))
        assertNull(TrackJournal.parse(sequenceOf(TrackJournal.line(point(0), 1000L))))
    }

    /**
     * A future version may reorder the columns, and reading those positions as if
     * they were this version's would recover a ride with the wrong numbers in it.
     *
     * Built by rewriting a real header rather than by hand: a hand-written one
     * without the end marker is refused for being truncated, which would leave
     * the version guard untested and free to be deleted.
     */
    @Test
    fun `refuses a journal from another version`() {
        val header = TrackJournal.header("cycling_road", start)
        val newer = header.replaceFirst("\t${TrackJournal.VERSION}\t", "\t${TrackJournal.VERSION + 1}\t")
        val older = header.replaceFirst("\t${TrackJournal.VERSION}\t", "\t${TrackJournal.VERSION - 1}\t")

        assertNotEquals(header, newer)
        assertNull(TrackJournal.parse(sequenceOf(newer, TrackJournal.line(point(0), 1000L))))
        assertNull(TrackJournal.parse(sequenceOf(older, TrackJournal.line(point(0), 1000L))))
    }

    @Test
    fun `turns a recovered ride into a workout the encoder accepts`() {
        val recovered = assertNotNull(TrackJournal.parse(journal((0 until 5).map(::point), moving = 3500L)))

        val workout = recovered.toWorkout()

        assertEquals("cycling_road", workout.sportTypeId)
        assertEquals(start, workout.startedAtEpochMs)
        // Four seconds between the first and last of five one-second samples.
        assertEquals(4.0, workout.totalElapsedSeconds)
        assertEquals(3.5, workout.totalTimerSeconds)
        assertEquals(100.0, workout.totalDistanceMeters)
        assertEquals(5, workout.points.size)
    }

    /**
     * A clock that jumped, or a journal whose moving time outran its samples,
     * would otherwise build a workout the encoder refuses outright - and refusing
     * it is how the recovered ride would be lost for good.
     */
    @Test
    fun `never claims to have moved for longer than the ride lasted`() {
        val recovered = assertNotNull(TrackJournal.parse(journal((0 until 2).map(::point), moving = 999_000L)))

        val workout = recovered.toWorkout()

        assertTrue(workout.totalTimerSeconds <= workout.totalElapsedSeconds)
    }

    @Test
    fun `takes the distance from the last sample that carried one`() {
        val points = listOf(point(0, distance = 500.0), point(1, distance = null))

        val recovered = assertNotNull(TrackJournal.parse(journal(points)))

        assertEquals(500.0, recovered.toWorkout().totalDistanceMeters)
    }

    @Test
    fun `survives a ride that was interrupted before its first sample`() {
        val recovered = assertNotNull(TrackJournal.parse(sequenceOf(TrackJournal.header("running_road", start))))

        val workout = recovered.toWorkout()

        assertEquals(0.0, workout.totalElapsedSeconds)
        assertEquals(0.0, workout.totalDistanceMeters)
        assertTrue(workout.points.isEmpty())
    }

    @Test
    fun `an hour recorded every second survives the journal intact`() {
        // The shape the recorder actually writes now. It used to write a few
        // dozen points an hour, each stamped with the moment its batch was
        // processed rather than the moment it described - so a ride reached
        // Strava as under a minute of records. The count and the spacing are
        // what matter here: the values were never the problem.
        val written = (0 until 3600).map(::point)

        val recovered = assertNotNull(TrackJournal.parse(journal(written)))

        assertEquals(3600, recovered.points.size, "an hour at a point a second is three thousand six hundred")
        assertEquals(start, recovered.points.first().timestampEpochMs)
        assertEquals(start + 3599 * 1000L, recovered.points.last().timestampEpochMs)
        val gaps = recovered.points.zipWithNext { a, b -> b.timestampEpochMs - a.timestampEpochMs }
        assertTrue(gaps.all { it == 1000L }, "the points must stay a second apart rather than cluster")
    }
}
