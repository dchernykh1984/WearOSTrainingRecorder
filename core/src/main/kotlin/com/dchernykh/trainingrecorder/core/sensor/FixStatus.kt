package com.dchernykh.trainingrecorder.core.sensor

/**
 * How the ride's position is doing, in the three states worth telling a rider
 * apart.
 *
 * Three rather than two because the middle one is the interesting one: a watch
 * that has just been asked for a fix spends the first minute or two acquiring,
 * and a rider who sees only "no" during it either waits for something that has
 * already started or sets off believing the ride is not being tracked. The
 * platform reports that state, so there is no need to guess at it.
 *
 * [ACQUIRING] also covers a fix that is being held by something weaker than the
 * watch's own receiver - a position relayed from the phone, say - since what it
 * means to the rider is the same: the ride is being tracked, but not yet by the
 * thing that will draw the line.
 */
enum class FixStatus {
    /** No position at all: no receiver, no permission, or nothing found. */
    NONE,

    /** Looking, or holding something less than a proper fix. */
    ACQUIRING,

    /** A fix the ride can be drawn from. */
    ACQUIRED,
}
