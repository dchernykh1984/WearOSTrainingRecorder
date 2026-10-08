package com.dchernykh.trainingrecorder.core.connector

import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary

/** How the last automatic sign-in ended, as it is written down between runs. */
enum class ReauthOutcome(
    val id: String,
) {
    /** It worked; fresh tokens went to the watch. */
    SIGNED_IN("signed_in"),

    /** Garmin wants a code only the rider can read. */
    NEEDS_CODE("needs_code"),

    /** The stored login and password are not accepted any more. */
    WRONG_CREDENTIALS("wrong_credentials"),

    /** Something else went wrong, which is usually the network. */
    FAILED("failed"),
    ;

    /**
     * True where trying again unattended cannot possibly help.
     *
     * Both of these need the rider: one to read a code out of their email, the
     * other to type a password that works. Retrying either on a timer would
     * hammer Garmin's sign-in with credentials it has already refused, which is
     * how an account gets locked.
     */
    val needsTheRider: Boolean get() = this == NEEDS_CODE || this == WRONG_CREDENTIALS

    companion object {
        fun byId(id: String?): ReauthOutcome? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Whether the phone should sign in to Garmin again on the rider's behalf.
 *
 * The watch refreshes its own Garmin token and retries a refusal with a fresh
 * one; [GarminProtocol.SESSION_EXPIRED] is what it reports when even that
 * fails, which means the refresh token itself is dead. Only a full sign-in
 * produces a new one, and a full sign-in needs the login and password - which
 * live on the phone and are deliberately never sent to the watch. So the watch
 * cannot fix this and the phone can, which is the whole reason this decision
 * exists over here.
 *
 * The rider has nothing to do with any of it. They connected Garmin once; a
 * token quietly expiring weeks later is not an event they should have to notice,
 * and until now it cost them every ride recorded afterwards.
 */
object GarminReauthorization {
    /**
     * Long enough that a Garmin outage cannot turn into a sign-in loop.
     *
     * Garmin's sign-in is an undocumented SSO exchange and is not gentle about
     * repetition. A dead refresh token stays dead, so there is no hurry: the
     * cost of waiting is that rides queue a few hours longer, and the cost of
     * not waiting is an account locked for suspicious activity.
     */
    const val RETRY_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** True when the watch is reporting a session only a sign-in can replace. */
    fun watchReportsDeadSession(workouts: List<WorkoutSummary>): Boolean =
        workouts.any {
            it.uploadReasons[GarminProtocol.ID] == GarminProtocol.SESSION_EXPIRED &&
                it.uploads[GarminProtocol.ID] != UploadState.UPLOADED
        }

    /**
     * Whether to go now.
     *
     * [lastOutcome] stops the attempts rather than spacing them out when only
     * the rider can help: a password Garmin has refused will be refused again in
     * six hours, and the point of stopping is that the rider is told once and
     * the app is not still trying behind them. Connecting by hand clears it.
     */
    @Suppress("ReturnCount")
    fun shouldSignIn(
        workouts: List<WorkoutSummary>,
        lastAttemptEpochMs: Long,
        lastOutcome: ReauthOutcome?,
        nowEpochMs: Long,
        hasCredentials: Boolean,
    ): Boolean {
        if (!hasCredentials) return false
        if (lastOutcome?.needsTheRider == true) return false
        if (!watchReportsDeadSession(workouts)) return false
        return nowEpochMs - lastAttemptEpochMs >= RETRY_INTERVAL_MS
    }
}
