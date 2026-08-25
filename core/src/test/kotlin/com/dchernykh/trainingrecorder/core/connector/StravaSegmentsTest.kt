package com.dchernykh.trainingrecorder.core.connector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shapes Strava actually sends, held against what the watch needs.
 *
 * Written from the response bodies in Strava's own reference rather than from
 * what would be convenient here: a parser tested against its own idea of the
 * format is a parser tested against nothing.
 */
class StravaSegmentsTest {
    private val starredBody =
        """
        [
          {
            "id": 229781,
            "name": "Hawk Hill",
            "distance": 2684.82,
            "average_grade": 5.7,
            "start_latlng": [37.8331119, -122.4834356],
            "end_latlng": [37.8280451, -122.4981185],
            "athlete_pr_effort": {
              "id": 999999,
              "elapsed_time": 553,
              "pr_elapsed_time": 553,
              "pr_activity_id": 4823400794
            }
          },
          {
            "id": 632535,
            "name": "Ranch Ascent",
            "distance": 1200.0,
            "start_latlng": [37.8, -122.5]
          }
        ]
        """.trimIndent()

    private val streamsBody =
        """
        {
          "latlng": { "data": [[37.83, -122.48], [37.831, -122.481], [37.832, -122.482]] },
          "distance": { "data": [0.0, 120.5, 260.9] },
          "altitude": { "data": [92.4, 104.0, 118.2] }
        }
        """.trimIndent()

    @Test
    fun theStarredListComesBackWithWhatTheWatchNeeds() {
        val segments = StravaSegments.starredFrom(starredBody)

        assertEquals(2, segments.size)
        val hawkHill = segments.first()
        assertEquals(229781L, hawkHill.id)
        assertEquals("Hawk Hill", hawkHill.name)
        assertTrue(abs(hawkHill.distanceMeters - 2684.82) < 0.01)
        assertTrue(abs(hawkHill.startLatitudeDeg - 37.8331119) < 1e-7)
        assertEquals(553.0, hawkHill.bestSeconds)
        assertEquals(999999L, hawkHill.bestEffortId)
    }

    @Test
    fun aSegmentNeverRiddenHasNoBestAndIsStillOffered() {
        val ranch = StravaSegments.starredFrom(starredBody)[1]

        assertNull(ranch.bestSeconds, "never ridden it")
        assertEquals("Ranch Ascent", ranch.name, "which is no reason not to time it")
    }

    @Test
    fun theLineIsReadFromTheThreeStreamsTogether() {
        val line = StravaSegments.lineFrom(streamsBody)

        assertEquals(3, line.size)
        assertEquals(0.0, line.first().distanceMeters)
        assertEquals(260.9, line.last().distanceMeters)
        assertEquals(118.2, line.last().altitudeMeters)
    }

    @Test
    fun aSegmentWithNoAltitudeStreamStillHasALine() {
        val body =
            """
            { "latlng": { "data": [[37.83, -122.48], [37.831, -122.481]] }, "distance": { "data": [0.0, 120.5] } }
            """.trimIndent()

        val line = StravaSegments.lineFrom(body)

        assertEquals(2, line.size)
        assertNull(line.first().altitudeMeters, "the climbing fields go quiet, the rest works")
    }

    @Test
    fun anEffortBecomesACurveOfDistanceAgainstTime() {
        val body =
            """
            { "distance": { "data": [0.0, 8.2, 16.9] }, "time": { "data": [0, 1, 2] } }
            """.trimIndent()

        val curve = StravaSegments.effortCurveFrom(body)

        assertEquals(3, curve.size)
        assertEquals(16.9, curve.last().distanceMeters)
        assertEquals(2.0, curve.last().elapsedSeconds)
    }

    @Test
    fun nonsenseParsesToNothingRatherThanThrowing() {
        // A rate-limit page, a proxy's error, a truncated body: all of these
        // arrive as a 200 often enough, and none of them should take the phone
        // down on a Sunday morning.
        assertTrue(StravaSegments.starredFrom("<html>rate limited</html>").isEmpty())
        assertTrue(StravaSegments.lineFrom("").isEmpty())
        assertTrue(StravaSegments.effortCurveFrom("{}").isEmpty())
        assertNull(StravaSegments.segmentFrom("null"))
    }

    @Test
    fun aSegmentIsAssembledFromItsListingAndItsLine() {
        val starred = StravaSegments.starredFrom(starredBody).first()
        val line = StravaSegments.lineFrom(streamsBody)

        val segment = assertNotNull(StravaSegments.segment(starred, line))

        assertEquals("Hawk Hill", segment.name)
        assertEquals(260.9, segment.distanceMeters)
        assertEquals(553.0, segment.referenceSeconds, "an even pace stands in for the effort's own curve")
    }

    @Test
    fun theEffortsOwnCurveIsPreferredToAnEvenPace() {
        val starred = StravaSegments.starredFrom(starredBody).first()
        val line = StravaSegments.lineFrom(streamsBody)
        val body =
            """
            { "distance": { "data": [0, 130, 260.9] }, "time": { "data": [0, 300, 553] } }
            """.trimIndent()
        val curve = StravaSegments.effortCurveFrom(body)

        val segment = assertNotNull(StravaSegments.segment(starred, line, curve))

        // Half way took 300 s in the real effort, not the 275 an even pace says.
        assertEquals(300.0, segment.referenceSecondsAt(130.0))
    }

    @Test
    fun aLineTooShortToFollowIsNoSegmentAtAll() {
        val starred = StravaSegments.starredFrom(starredBody).first()

        assertNull(StravaSegments.segment(starred, emptyList()))
    }

    @Test
    fun theUrlsAreTheOnesStravaDocuments() {
        assertEquals("https://www.strava.com/api/v3/segments/starred?page=1&per_page=30", StravaSegments.starredUrl())
        assertEquals("https://www.strava.com/api/v3/segments/229781", StravaSegments.segmentUrl(229781))
        assertTrue(StravaSegments.streamsUrl(229781).endsWith("keys=latlng,distance,altitude&key_by_type=true"))
        assertTrue(StravaSegments.effortStreamsUrl(999999).contains("/segment_efforts/999999/streams"))
    }
}
