package com.dchernykh.trainingrecorder.core.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingStateTest {
    private val t0 = 1_770_000_000_000L

    private fun running() = RecordingState().prepare("cycling_road", t0).begin(t0 + 5_000)

    @Test
    fun aFreshStateIsIdleAndOffersOnlyStart() {
        val state = RecordingState()
        assertEquals(RecordingPhase.IDLE, state.phase)
        assertEquals(listOf(RecordingAction.START), state.availableActions)
        assertNull(state.sportTypeId)
    }

    @Test
    fun preparingRemembersTheSportAndTheMomentStartWasTapped() {
        val state = RecordingState().prepare("run_trail", t0)
        assertEquals(RecordingPhase.PREPARING, state.phase)
        assertEquals("run_trail", state.sportTypeId)
        assertEquals(t0, state.startedAtEpochMs)
        assertEquals(listOf(RecordingAction.DISCARD), state.availableActions)
    }

    @Test
    fun elapsedTimeRunsFromTheTapNotFromTheGpsFix() {
        // The seconds spent finding satellites belong to the ride.
        val state = RecordingState().prepare("cycling_road", t0)
        assertEquals(9_000, state.elapsedMillisAt(t0 + 9_000))
    }

    @Test
    fun movingTimeStartsOnlyOnceRecordingBegins() {
        val preparing = RecordingState().prepare("cycling_road", t0)
        assertEquals(0, preparing.movingMillisAt(t0 + 9_000))
        val recording = preparing.begin(t0 + 9_000)
        assertEquals(1_000, recording.movingMillisAt(t0 + 10_000))
    }

    @Test
    fun pausingFreezesMovingTimeButNotElapsedTime() {
        val paused = running().pause(t0 + 65_000)
        assertEquals(60_000, paused.movingMillisAt(t0 + 65_000))
        assertEquals(60_000, paused.movingMillisAt(t0 + 300_000), "moving time must not tick while paused")
        assertEquals(300_000, paused.elapsedMillisAt(t0 + 300_000), "elapsed time keeps running")
    }

    @Test
    fun resumingContinuesFromWhereItStopped() {
        val resumed = running().pause(t0 + 65_000).resume(t0 + 125_000)
        assertEquals(RecordingPhase.RECORDING, resumed.phase)
        assertEquals(70_000, resumed.movingMillisAt(t0 + 135_000), "60s before the pause plus 10s after")
    }

    @Test
    fun severalPauseCyclesAccumulateCorrectly() {
        val state =
            running()
                .pause(t0 + 65_000)
                .resume(t0 + 125_000)
                .pause(t0 + 155_000)
                .resume(t0 + 200_000)
        assertEquals(100_000, state.movingMillisAt(t0 + 210_000))
    }

    @Test
    fun finishingFreezesElapsedTimeToo() {
        // Elapsed time is what the FIT session reports; if it kept running the
        // recorded workout would grow for as long as the app stayed open.
        val finished = running().finish(t0 + 65_000)
        assertEquals(65_000, finished.elapsedMillisAt(t0 + 65_000))
        assertEquals(65_000, finished.elapsedMillisAt(t0 + 999_000))
    }

    @Test
    fun finishingFreezesMovingTime() {
        val finished = running().finish(t0 + 65_000)
        assertEquals(RecordingPhase.FINISHED, finished.phase)
        assertEquals(60_000, finished.movingMillisAt(t0 + 999_000))
    }

    @Test
    fun aPausedRecordingCanBeFinishedDirectly() {
        val finished = running().pause(t0 + 65_000).finish(t0 + 300_000)
        assertEquals(RecordingPhase.FINISHED, finished.phase)
        assertEquals(60_000, finished.movingMillisAt(t0 + 300_000))
    }

    @Test
    fun discardingReturnsToACleanIdleState() {
        assertEquals(RecordingState(), running().discard())
    }

    @Test
    fun finishingIsAlwaysBehindALongPress() {
        assertTrue(RecordingAction.FINISH.requiresLongPress)
        assertTrue(RecordingAction.DISCARD.requiresLongPress)
        assertFalse(RecordingAction.START.requiresLongPress)
        assertFalse(RecordingAction.PAUSE.requiresLongPress)
        assertFalse(RecordingAction.RESUME.requiresLongPress)
    }

    @Test
    fun theControlsOfferedMatchThePhase() {
        assertEquals(
            listOf(RecordingAction.PAUSE, RecordingAction.FINISH, RecordingAction.DISCARD),
            running().availableActions,
        )
        assertEquals(
            listOf(RecordingAction.RESUME, RecordingAction.FINISH, RecordingAction.DISCARD),
            running().pause(t0 + 1).availableActions,
        )
        assertEquals(listOf(RecordingAction.START), running().finish(t0 + 1).availableActions)
    }

    @Test
    fun aRideCanBeThrownAwayAtAnyPointWhileItRuns() {
        // A rider who sets off by mistake, or turns back after a mile, should
        // not have to save the ride in order to be rid of it.
        listOf(running(), running().pause(t0 + 1)).forEach {
            assertTrue(RecordingAction.DISCARD in it.availableActions, "no way out of ${it.phase}")
        }
    }

    @Test
    fun throwingARideAwayNeedsDeliberateIntent() {
        // Both of the controls that lose a ride are held rather than tapped -
        // a pocket brush must not be able to end one.
        assertTrue(RecordingAction.DISCARD.requiresLongPress)
        assertTrue(RecordingAction.FINISH.requiresLongPress)
        assertFalse(RecordingAction.PAUSE.requiresLongPress)
    }

    @Test
    fun impossibleTransitionsAreRefused() {
        assertFailsWith<IllegalStateException> { running().prepare("run_road", t0) }
        assertFailsWith<IllegalStateException> { RecordingState().begin(t0) }
        assertFailsWith<IllegalStateException> { RecordingState().pause(t0) }
        assertFailsWith<IllegalStateException> { running().resume(t0) }
        assertFailsWith<IllegalStateException> { RecordingState().finish(t0) }
    }

    @Test
    fun aClockThatGoesBackwardsNeverYieldsNegativeTime() {
        assertEquals(0, running().elapsedMillisAt(t0 - 10_000))
        assertEquals(0, running().movingMillisAt(t0))
    }

    @Test
    fun onlyRunningAndPausedCountAsActive() {
        assertTrue(RecordingPhase.RECORDING.isActive)
        assertTrue(RecordingPhase.PAUSED.isActive)
        assertFalse(RecordingPhase.IDLE.isActive)
        assertFalse(RecordingPhase.PREPARING.isActive)
        assertFalse(RecordingPhase.FINISHED.isActive)
    }

    @Test
    fun enumsRoundTripThroughTheirIds() {
        RecordingPhase.entries.forEach { assertEquals(it, RecordingPhase.byId(it.id)) }
        RecordingAction.entries.forEach { assertEquals(it, RecordingAction.byId(it.id)) }
        assertNull(RecordingPhase.byId("nope"))
        assertNull(RecordingAction.byId("nope"))
    }
}
