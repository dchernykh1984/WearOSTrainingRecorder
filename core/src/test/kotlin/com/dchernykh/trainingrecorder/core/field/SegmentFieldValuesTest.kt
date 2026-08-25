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

    private companion object {
        const val NOW = 1_782_000_000_000L
    }
}
