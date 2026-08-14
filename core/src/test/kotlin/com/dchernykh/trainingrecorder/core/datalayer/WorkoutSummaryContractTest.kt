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
}
