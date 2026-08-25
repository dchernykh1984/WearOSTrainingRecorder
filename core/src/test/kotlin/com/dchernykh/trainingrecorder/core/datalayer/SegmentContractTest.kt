package com.dchernykh.trainingrecorder.core.datalayer

import com.dchernykh.trainingrecorder.core.segment.EffortPoint
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.core.segment.SegmentPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What survives the trip from the phone to the watch, and what it costs. */
class SegmentContractTest {
    private fun segment(
        points: Int = 100,
        reference: Int = 100,
        withAltitude: Boolean = true,
    ) = Segment(
        id = 229781L,
        name = "Hawk Hill",
        points =
            (0..points).map {
                SegmentPoint(
                    latitudeDeg = 37.833111 + it * 0.00009,
                    longitudeDeg = -122.483435 - it * 0.00007,
                    distanceMeters = it * 10.0,
                    altitudeMeters = if (withAltitude) 92.4 + it * 1.5 else null,
                )
            },
        reference = (0..reference).map { EffortPoint(it * 10.0, it * 2.1) },
    )

    @Test
    fun aSegmentComesBackTheSameSegment() {
        val original = segment()

        val decoded = assertNotNull(SegmentContract.decode(SegmentContract.encode(original)))

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.points.size, decoded.points.size)
        assertTrue(abs(decoded.distanceMeters - original.distanceMeters) < 0.1)
        assertEquals(original.referenceSeconds, decoded.referenceSeconds)
    }

    @Test
    fun theRoundingIsFinerThanAnyWatchKnowsWhereItIs() {
        val decoded = assertNotNull(SegmentContract.decode(SegmentContract.encode(segment())))

        val first = decoded.points.first()
        // Six decimal places of latitude is about ten centimetres.
        assertTrue(abs(first.latitudeDeg - 37.833111) < 0.0000005)
        assertTrue(abs(first.longitudeDeg - -122.483435) < 0.0000005)
    }

    @Test
    fun aSegmentWithNoHeightsTravelsAnyway() {
        val decoded = assertNotNull(SegmentContract.decode(SegmentContract.encode(segment(withAltitude = false))))

        assertNull(decoded.points.first().altitudeMeters)
        assertNull(decoded.ascentBetween(0.0, decoded.distanceMeters), "the climbing fields go quiet")
    }

    @Test
    fun aLongSegmentStaysInsideTheDataLayersItem() {
        // Four thousand points is a long descent recorded at one a second.
        val payload = SegmentContract.encode(segment(points = 4000, reference = 4000))

        assertTrue(
            payload.length < ITEM_LIMIT_BYTES,
            "a segment must fit an item on its own, this one is ${payload.length} bytes",
        )
        val decoded = assertNotNull(SegmentContract.decode(payload))
        assertTrue(decoded.points.size <= SegmentContract.MAX_POINTS)
        // Thinned, but still the same road: the last point is still the finish.
        assertTrue(abs(decoded.distanceMeters - 40_000.0) < 0.1)
    }

    @Test
    fun thinningKeepsBothEnds() {
        val thinned = SegmentContract.thin((1..1000).toList(), limit = 10)

        assertEquals(10, thinned.size)
        assertEquals(1, thinned.first())
        assertEquals(1000, thinned.last())
    }

    @Test
    fun aShortListIsLeftAlone() {
        assertEquals(listOf(1, 2, 3), SegmentContract.thin(listOf(1, 2, 3), limit = 600))
    }

    @Test
    fun pathsCarryTheIdBothWays() {
        assertEquals("/segment/229781", SegmentContract.path(229781L))
        assertEquals(229781L, SegmentContract.idFrom("/segment/229781"))
        assertNull(SegmentContract.idFrom("/settings"), "another path is not a segment")
        assertNull(SegmentContract.idFrom(null))
    }

    @Test
    fun anUnusablePayloadLeavesTheWatchWithWhatItHas() {
        assertNull(SegmentContract.decode(""))
        assertNull(SegmentContract.decode("{}"))
        assertNull(SegmentContract.decode("""{"v":99,"id":1,"name":"x","pts":[]}"""), "a newer shape")
        assertNull(SegmentContract.decode("""{"v":1,"id":1,"name":"x","pts":[[55.0,37.0,0.0,null]]}"""))
    }

    private companion object {
        /** The Data Layer refuses an item larger than this. */
        const val ITEM_LIMIT_BYTES = 100 * 1024
    }
}
