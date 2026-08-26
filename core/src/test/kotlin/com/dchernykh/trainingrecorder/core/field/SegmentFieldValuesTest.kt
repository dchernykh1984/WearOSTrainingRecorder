package com.dchernykh.trainingrecorder.core.field

import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.segment.EffortPoint
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.core.segment.SegmentPoint
import com.dchernykh.trainingrecorder.core.segment.SegmentState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the segment fields actually read on the watch.
 *
 * The sign is the part worth pinning down. A rider glancing at the screen mid
 * effort reads it before the number, and a field that says "+8" when they are
 * eight seconds *down* is worse than a field that says nothing at all.
 */
class SegmentFieldValuesTest {
    private val hill =
        Segment(
            id = 1L,
            name = "Ostrich Hill",
            points =
                (0..20).map {
                    SegmentPoint(55.0 + it * 0.0001, 37.0, it * 50.0, altitudeMeters = 100.0 + it * 2.5)
                },
            // Their best: a thousand metres in two and a half minutes.
            reference = (0..150).map { EffortPoint(it * (1000.0 / 150.0), it.toDouble()) },
        )

    /** Up fifty metres over the first half, down thirty over the second. */
    private val rolling =
        Segment(
            id = 2L,
            name = "Rolling",
            points =
                (0..20).map {
                    val metres = it * 50.0
                    val height = if (it <= 10) 100.0 + it * 5.0 else 150.0 - (it - 10) * 3.0
                    SegmentPoint(55.0 + it * 0.0001, 37.0, metres, altitudeMeters = height)
                },
            reference = (0..150).map { EffortPoint(it * (1000.0 / 150.0), it.toDouble()) },
        )

    private fun values(segment: SegmentState?) =
        FieldValues.snapshot(
            state = RecordingState(),
            nowEpochMs = NOW,
            segment = segment,
        )

    @Test
    fun withNoSegmentInSightEveryFieldIsEmpty() {
        val values = values(null)

        FieldCatalogue.segment.forEach {
            assertEquals(FieldCatalogue.EMPTY_VALUE, values[it.id], "${it.id} should be empty")
        }
    }

    @Test
    fun approachingASegmentShowsItsNameAndHowFarThereIsToGo() {
        val values = values(SegmentState(hill, toStartMeters = 420.0))

        assertEquals("Ostrich Hill", values["segment_name"])
        assertEquals("420 m", values["segment_to_start"])
        assertEquals("2:30", values["segment_best"], "the time to beat is worth seeing on the way in")
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["segment_time"], "the effort has not begun")
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["segment_ahead"])
    }

    @Test
    fun aheadOfTheBestEffortReadsAsAPlus() {
        // Half way in 70 s, where their best took 75.
        val values =
            values(SegmentState(hill, riding = true, coveredMeters = 500.0, elapsedSeconds = 70.0))

        assertEquals("+0:05", values["segment_ahead"])
        assertEquals("1:10", values["segment_time"])
        assertEquals("500 m", values["segment_remaining"])
    }

    @Test
    fun behindTheBestEffortReadsAsAMinus() {
        val values =
            values(SegmentState(hill, riding = true, coveredMeters = 500.0, elapsedSeconds = 83.0))

        assertEquals("-0:08", values["segment_ahead"])
    }

    @Test
    fun levelWithTheirBestReadsAsLevelRatherThanAsASign() {
        // Half way in exactly the 75 s their best took. A sign here would
        // flicker between plus and minus over a difference of milliseconds.
        val values =
            values(SegmentState(hill, riding = true, coveredMeters = 500.0, elapsedSeconds = 75.0))

        assertEquals("0:00", values["segment_ahead"])
        assertEquals("0 m", values["segment_ahead_distance"])
    }

    @Test
    fun theGapIsAlsoShownAsGroundUpTheRoad() {
        // At 70 s their best had covered 466.7 m; the rider is on 500.
        val values =
            values(SegmentState(hill, riding = true, coveredMeters = 500.0, elapsedSeconds = 70.0))

        assertEquals("+33 m", values["segment_ahead_distance"])
    }

    @Test
    fun whatIsLeftToClimbIsWhatIsStillAbove() {
        val values =
            values(SegmentState(hill, riding = true, coveredMeters = 500.0, elapsedSeconds = 70.0))

        // Half of a fifty-metre climb, at a steady five percent.
        assertEquals("25 m", values["segment_ascent_left"])
        assertEquals("+5%", values["segment_grade_left"])
    }

    @Test
    fun aSegmentNeverRiddenBeforeStillTimesTheEffort() {
        val fresh = hill.copy(reference = emptyList())
        val values =
            values(SegmentState(fresh, riding = true, coveredMeters = 500.0, elapsedSeconds = 70.0))

        assertEquals("1:10", values["segment_time"])
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["segment_ahead"], "nobody to be ahead of")
        assertEquals(FieldCatalogue.EMPTY_VALUE, values["segment_best"])
    }

    @Test
    fun theFinishingTimeStopsCounting() {
        val values =
            values(SegmentState(hill, finished = true, coveredMeters = 1000.0, elapsedSeconds = 148.0))

        assertEquals("2:28", values["segment_time"])
        assertEquals("+0:02", values["segment_ahead"], "two seconds off their best")
        assertEquals("0 m", values["segment_remaining"])
    }

    @Test
    fun theWayToTheSegmentIsNoWayAtAllWhileRidingIt() {
        val values = values(SegmentState(hill, riding = true, coveredMeters = 300.0, elapsedSeconds = 45.0))

        assertEquals("0 m", values["segment_to_start"], "the nearest segment is the one underneath")
        assertEquals("300 m", values["segment_covered"])
    }

    @Test
    fun climbingAndDescendingBothCountAndBothAddUp() {
        // Three quarters of the way: all fifty metres of the climb, and fifteen
        // of the thirty metres of descent.
        val values =
            values(SegmentState(rolling, riding = true, coveredMeters = 750.0, elapsedSeconds = 110.0))

        assertEquals("50 m", values["segment_ascent"])
        assertEquals("15 m", values["segment_descent"])
        assertEquals("0 m", values["segment_ascent_left"], "the top is behind them")
        assertEquals("15 m", values["segment_descent_left"])
    }

    @Test
    fun theTimeLeftIsTheirOwnPaceAppliedToWhatTheyHaveNotRidden() {
        // Half the segment in 60 s where their best took 75: twenty percent up,
        // so the remaining 75 s of the reference should come out around 60.
        val values =
            values(SegmentState(hill, riding = true, coveredMeters = 500.0, elapsedSeconds = 60.0))

        assertEquals("1:00", values["segment_time_left"])
    }

    @Test
    fun theProjectedFinishSitsBesideTheBestForComparison() {
        // Twenty percent up at half way: 2:30 becomes about 2:00.
        val values =
            values(SegmentState(hill, riding = true, coveredMeters = 500.0, elapsedSeconds = 60.0))

        assertEquals("2:00", values["segment_projected"])
        assertEquals("2:30", values["segment_best"])
    }

    @Test
    fun onceFinishedTheProjectionIsSimplyTheTime() {
        val values =
            values(SegmentState(hill, finished = true, coveredMeters = 1000.0, elapsedSeconds = 148.0))

        assertEquals("2:28", values["segment_projected"], "nothing left to predict")
    }

    @Test
    fun withNoBestToScaleByThereIsNoEstimate() {
        val fresh = hill.copy(reference = emptyList())
        val values =
            values(SegmentState(fresh, riding = true, coveredMeters = 500.0, elapsedSeconds = 60.0))

        assertEquals(FieldCatalogue.EMPTY_VALUE, values["segment_time_left"])
    }

    @Test
    fun atTheStartLineThereIsNothingToScaleAndSoNoEstimate() {
        val values = values(SegmentState(hill, riding = true, coveredMeters = 0.0, elapsedSeconds = 0.0))

        assertEquals(FieldCatalogue.EMPTY_VALUE, values["segment_time_left"], "one fix predicts nothing")
        assertEquals("0:00", values["segment_time"])
    }

    private companion object {
        const val NOW = 1_782_000_000_000L
    }
}
