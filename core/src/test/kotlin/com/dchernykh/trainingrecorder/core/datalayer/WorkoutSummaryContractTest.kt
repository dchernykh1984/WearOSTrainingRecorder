package com.dchernykh.trainingrecorder.core.datalayer

import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutSummaryContractTest {
    private val start = 1_770_000_000_000L

    private fun summary(
        index: Int = 0,
        uploads: Map<String, UploadState> = mapOf("strava" to UploadState.PENDING),
    ) = WorkoutSummary(
        id = "workout-$index",
        sportTypeId = "cycling_road",
        startedAtEpochMs = start + index * 1000L,
        durationSeconds = 3600,
        distanceMeters = 42_195.0,
        fileSizeBytes = 250_000,
        uploads = uploads,
    )

    @Test
    fun `carries everything the history screen shows`() {
        val decoded = assertNotNull(WorkoutSummaryContract.decode(WorkoutSummaryContract.encode(listOf(summary()))))

        assertEquals(1, decoded.size)
        val workout = decoded.single()
        assertEquals("workout-0", workout.id)
        assertEquals("cycling_road", workout.sportTypeId)
        assertEquals(start, workout.startedAtEpochMs)
        assertEquals(3600L, workout.durationSeconds)
        assertEquals(42_195.0, workout.distanceMeters)
        assertEquals(mapOf("strava" to UploadState.PENDING), workout.uploads)
    }

    @Test
    fun `sends the newest rides first`() {
        val payload = WorkoutSummaryContract.encode(listOf(summary(0), summary(5), summary(2)))

        val decoded = assertNotNull(WorkoutSummaryContract.decode(payload))

        assertEquals(listOf("workout-5", "workout-2", "workout-0"), decoded.map { it.id })
    }

    /**
     * A Data Layer item is capped at 100 KB, and a watch may hold two hundred
     * rides; going over the cap fails the put outright, which would leave the
     * phone showing nothing at all.
     */
    @Test
    fun `stops at the cap and keeps the most recent`() {
        val many = (0 until WorkoutSummaryContract.MAX_SUMMARIES + 20).map { summary(it) }

        val decoded = assertNotNull(WorkoutSummaryContract.decode(WorkoutSummaryContract.encode(many)))

        assertEquals(WorkoutSummaryContract.MAX_SUMMARIES, decoded.size)
        assertEquals("workout-${WorkoutSummaryContract.MAX_SUMMARIES + 19}", decoded.first().id)
    }

    @Test
    fun `refuses a payload it cannot read rather than emptying the history`() {
        assertNull(WorkoutSummaryContract.decode("not json at all"))
        assertNull(WorkoutSummaryContract.decode("{}"))
    }

    /**
     * The same rule the settings contract follows in the other direction: an
     * older phone paired with a newer watch keeps what it has rather than
     * guessing at a shape it does not know.
     */
    @Test
    fun `refuses a payload from a newer contract`() {
        val newer = """{"version":${WorkoutSummaryContract.VERSION + 1},"workouts":[]}"""

        assertNull(WorkoutSummaryContract.decode(newer))
    }

    @Test
    fun `drops an unreadable entry without losing the others`() {
        val payload =
            """
            {"version":1,"workouts":[
              {"sport":"cycling_road","startedAt":$start},
              {"id":"workout-1","sport":"cycling_road","startedAt":$start,"duration":60,"distance":10.0,"bytes":1}
            ]}
            """.trimIndent()

        val decoded = assertNotNull(WorkoutSummaryContract.decode(payload))

        assertEquals(listOf("workout-1"), decoded.map { it.id })
    }

    /**
     * The phone keys its list by id, and Compose throws on a repeated key - so a
     * duplicate arriving from a watch would take the history screen down rather
     * than show a row twice.
     */
    @Test
    fun `never hands the phone the same ride twice`() {
        val payload = WorkoutSummaryContract.encode(listOf(summary(1), summary(1)))

        val decoded = assertNotNull(WorkoutSummaryContract.decode(payload))

        assertEquals(1, decoded.size)
    }

    @Test
    fun `survives a ride with no upload state yet`() {
        val decoded =
            assertNotNull(
                WorkoutSummaryContract.decode(
                    WorkoutSummaryContract.encode(listOf(summary(uploads = emptyMap()))),
                ),
            )

        assertTrue(decoded.single().uploads.isEmpty())
    }

    @Test
    fun theReasonARideHasNotArrivedTravelsWithIt() {
        // The whole point of carrying it: the rider cannot read the watch's log,
        // so the phone's history is the only place an explanation can appear.
        val stuck =
            WorkoutSummary(
                id = "w1",
                sportTypeId = "cycling_road",
                startedAtEpochMs = 1_770_000_000_000L,
                durationSeconds = 3600,
                distanceMeters = 30_000.0,
                fileSizeBytes = 2048,
                uploads = mapOf("garmin" to UploadState.PENDING),
                uploadAttempts = mapOf("garmin" to 4),
                uploadReasons = mapOf("garmin" to "session expired"),
            )
        val decoded = WorkoutSummaryContract.decode(WorkoutSummaryContract.encode(listOf(stuck)))!!.single()
        assertEquals("session expired", decoded.uploadReasons["garmin"])
        assertEquals(4, decoded.uploadAttempts["garmin"])
    }

    @Test
    fun aPayloadFromAWatchThatPredatesTheReasonsIsStillReadable() {
        // Watch and phone update on their own schedules, so the older shape has
        // to keep working - as an unexplained ride rather than as no ride.
        val older =
            """
            {"version":1,"workouts":[{"id":"w1","sport":"cycling_road","startedAt":1770000000000,
            "duration":600,"distance":1000.0,"bytes":512,"uploads":{"garmin":"pending"}}]}
            """.trimIndent().replace("\n", "")
        val decoded = WorkoutSummaryContract.decode(older)!!.single()
        assertEquals(UploadState.PENDING, decoded.uploads["garmin"])
        assertTrue(decoded.uploadReasons.isEmpty(), "nothing to explain is not an error")
        assertTrue(decoded.uploadAttempts.isEmpty())
    }
}
