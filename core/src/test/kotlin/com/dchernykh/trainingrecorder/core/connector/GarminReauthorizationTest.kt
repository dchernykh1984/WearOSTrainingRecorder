package com.dchernykh.trainingrecorder.core.connector

import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the phone signs in to Garmin again without being asked.
 *
 * The case this exists for: a refresh token quietly dies weeks after the rider
 * connected Garmin, the watch refreshes, is refused, gives up after ten tries,
 * and every ride recorded afterwards piles up refused. None of that is
 * something the rider did, and until now all of it was theirs to notice.
 */
class GarminReauthorizationTest {
    private val now = 1_782_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun ride(
        reason: String? = GarminProtocol.SESSION_EXPIRED,
        state: UploadState = UploadState.FAILED,
    ) = WorkoutSummary(
        id = "ride-1",
        sportTypeId = "cycling_road",
        startedAtEpochMs = now - hour,
        durationSeconds = 3_600,
        distanceMeters = 30_000.0,
        fileSizeBytes = 200_000,
        uploads = mapOf(GarminProtocol.ID to state),
        uploadAttempts = mapOf(GarminProtocol.ID to 10),
        uploadReasons = reason?.let { mapOf(GarminProtocol.ID to it) }.orEmpty(),
    )

    private fun shouldSignIn(
        workouts: List<WorkoutSummary> = listOf(ride()),
        lastAttemptEpochMs: Long = 0,
        lastOutcome: ReauthOutcome? = null,
        hasCredentials: Boolean = true,
    ) = GarminReauthorization.shouldSignIn(
        workouts = workouts,
        lastAttemptEpochMs = lastAttemptEpochMs,
        lastOutcome = lastOutcome,
        nowEpochMs = now,
        hasCredentials = hasCredentials,
    )

    @Test
    fun aDeadSessionIsSignedInAgain() {
        assertTrue(shouldSignIn())
    }

    @Test
    fun theWordingIsTheOneTheWatchActuallyWrites() {
        // Both sides read the same constant. A literal copied into the phone is
        // how this link breaks the first time the watch's wording improves.
        assertTrue(GarminReauthorization.watchReportsDeadSession(listOf(ride(reason = "session expired"))))
    }

    @Test
    fun anotherKindOfFailureIsLeftAlone() {
        // Signing in again fixes an expired session and nothing else. A file
        // Garmin will not parse is refused just as firmly by a fresh token.
        assertFalse(shouldSignIn(listOf(ride(reason = "garmin refused the upload: 400"))))
    }

    @Test
    fun aRideThatGotThroughIsNotEvidenceOfAnything() {
        // The reason is cleared on success, but a stale one surviving beside an
        // accepted ride must not drag the phone into a pointless sign-in.
        assertFalse(shouldSignIn(listOf(ride(state = UploadState.UPLOADED))))
    }

    @Test
    fun withNothingStuckThereIsNothingToFix() {
        assertFalse(shouldSignIn(emptyList()))
    }

    @Test
    fun aRiderWhoNeverConnectedGarminIsLeftAlone() {
        assertFalse(shouldSignIn(hasCredentials = false), "there is no password to sign in with")
    }

    @Test
    fun twoAttemptsAnHourApartAreOneAttempt() {
        assertFalse(
            shouldSignIn(lastAttemptEpochMs = now - hour),
            "a dead refresh token stays dead, and garmin's sign-in is not gentle about repetition",
        )
        assertTrue(shouldSignIn(lastAttemptEpochMs = now - 7 * hour))
    }

    @Test
    fun aCodeOnlyTheRiderCanReadStopsTheAttempts() {
        assertFalse(
            shouldSignIn(lastAttemptEpochMs = now - 7 * hour, lastOutcome = ReauthOutcome.NEEDS_CODE),
            "retrying on a timer would never produce the code, and the rider is already being told",
        )
    }

    @Test
    fun aPasswordGarminRefusedStopsTheAttempts() {
        assertFalse(
            shouldSignIn(lastAttemptEpochMs = now - 7 * hour, lastOutcome = ReauthOutcome.WRONG_CREDENTIALS),
            "the same password refused every six hours is how an account gets locked",
        )
    }

    @Test
    fun aNetworkFailureIsWorthAnotherGo() {
        assertTrue(
            shouldSignIn(lastAttemptEpochMs = now - 7 * hour, lastOutcome = ReauthOutcome.FAILED),
            "nothing about a phone on a train says the credentials are wrong",
        )
    }

    @Test
    fun havingSignedInOnceDoesNotStopItHappeningAgain() {
        // Tokens expire more than once in a project's life.
        assertTrue(shouldSignIn(lastAttemptEpochMs = now - 7 * hour, lastOutcome = ReauthOutcome.SIGNED_IN))
    }

    @Test
    fun onlyTheTwoOutcomesTheRiderOwnsStopTheLoop() {
        assertTrue(ReauthOutcome.NEEDS_CODE.needsTheRider)
        assertTrue(ReauthOutcome.WRONG_CREDENTIALS.needsTheRider)
        assertFalse(ReauthOutcome.FAILED.needsTheRider)
        assertFalse(ReauthOutcome.SIGNED_IN.needsTheRider)
    }

    @Test
    fun anOutcomeSurvivesBeingWrittenDownAndReadBack() {
        ReauthOutcome.entries.forEach { assertTrue(ReauthOutcome.byId(it.id) == it) }
        assertTrue(ReauthOutcome.byId("no_such_outcome") == null)
        assertTrue(ReauthOutcome.byId(null) == null)
    }
}
