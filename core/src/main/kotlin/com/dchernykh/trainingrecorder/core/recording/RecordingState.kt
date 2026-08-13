package com.dchernykh.trainingrecorder.core.recording

/**
 * Where a recording is in its life.
 *
 * [PREPARING] is the window after the rider taps start and before anything is
 * written: the watch is looking for a GNSS fix and reconnecting the sensors it
 * has seen before. It exists as a state of its own because a rider who starts
 * moving during it must not lose those seconds.
 */
enum class RecordingPhase(
    val id: String,
) {
    IDLE("idle"),
    PREPARING("preparing"),
    RECORDING("recording"),
    PAUSED("paused"),
    FINISHED("finished"),
    ;

    val isActive: Boolean get() = this == RECORDING || this == PAUSED

    companion object {
        fun byId(id: String): RecordingPhase? = entries.firstOrNull { it.id == id }
    }
}

/** What the rider can do next, which is what the control overlay draws. */
enum class RecordingAction(
    val id: String,
) {
    START("start"),
    PAUSE("pause"),
    RESUME("resume"),
    FINISH("finish"),
    DISCARD("discard"),
    ;

    /**
     * Finishing is the one action that must survive a pocket brush, so it is
     * bound to a long press rather than a tap.
     */
    val requiresLongPress: Boolean get() = this == FINISH || this == DISCARD

    companion object {
        fun byId(id: String): RecordingAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The recording as a state machine, with elapsed and moving time kept apart.
 *
 * Time is passed in rather than read from a clock so the whole thing is testable
 * without waiting: the caller supplies "now" at each transition.
 */
data class RecordingState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val sportTypeId: String? = null,
    val startedAtEpochMs: Long? = null,
    val lastTransitionAtEpochMs: Long? = null,
    val accumulatedMovingMs: Long = 0,
) {
    val availableActions: List<RecordingAction>
        get() =
            when (phase) {
                RecordingPhase.IDLE, RecordingPhase.FINISHED -> listOf(RecordingAction.START)
                RecordingPhase.PREPARING -> listOf(RecordingAction.DISCARD)
                RecordingPhase.RECORDING -> listOf(RecordingAction.PAUSE, RecordingAction.FINISH)
                RecordingPhase.PAUSED -> listOf(RecordingAction.RESUME, RecordingAction.FINISH)
            }

    fun prepare(
        sportTypeId: String,
        nowEpochMs: Long,
    ): RecordingState {
        check(!phase.isActive) { "cannot start while a recording is $phase" }
        return RecordingState(
            phase = RecordingPhase.PREPARING,
            sportTypeId = sportTypeId,
            startedAtEpochMs = nowEpochMs,
            lastTransitionAtEpochMs = nowEpochMs,
            accumulatedMovingMs = 0,
        )
    }

    fun begin(nowEpochMs: Long): RecordingState {
        check(phase == RecordingPhase.PREPARING) { "a recording begins from PREPARING, not $phase" }
        return copy(phase = RecordingPhase.RECORDING, lastTransitionAtEpochMs = nowEpochMs)
    }

    fun pause(nowEpochMs: Long): RecordingState {
        check(phase == RecordingPhase.RECORDING) { "only a running recording can pause, not $phase" }
        return copy(
            phase = RecordingPhase.PAUSED,
            accumulatedMovingMs = movingMillisAt(nowEpochMs),
            lastTransitionAtEpochMs = nowEpochMs,
        )
    }

    fun resume(nowEpochMs: Long): RecordingState {
        check(phase == RecordingPhase.PAUSED) { "only a paused recording can resume, not $phase" }
        return copy(phase = RecordingPhase.RECORDING, lastTransitionAtEpochMs = nowEpochMs)
    }

    fun finish(nowEpochMs: Long): RecordingState {
        check(phase.isActive) { "only an active recording can finish, not $phase" }
        return copy(
            phase = RecordingPhase.FINISHED,
            accumulatedMovingMs = movingMillisAt(nowEpochMs),
            lastTransitionAtEpochMs = nowEpochMs,
        )
    }

    fun discard(): RecordingState = RecordingState()

    /**
     * Wall-clock time since the rider tapped start, pauses included.
     *
     * Frozen once the recording is finished: it is what the FIT session reports
     * as total elapsed time, and a value that keeps growing after the ride would
     * put an ever-lengthening workout into the file.
     */
    fun elapsedMillisAt(nowEpochMs: Long): Long {
        val start = startedAtEpochMs ?: return 0
        val end = if (phase == RecordingPhase.FINISHED) lastTransitionAtEpochMs ?: nowEpochMs else nowEpochMs
        return (end - start).coerceAtLeast(0)
    }

    /** Time the recording was actually running, which is what a session reports. */
    fun movingMillisAt(nowEpochMs: Long): Long {
        if (phase != RecordingPhase.RECORDING) return accumulatedMovingMs
        val since = lastTransitionAtEpochMs ?: return accumulatedMovingMs
        return accumulatedMovingMs + (nowEpochMs - since).coerceAtLeast(0)
    }
}
