package com.dchernykh.trainingrecorder.core.track

/**
 * The altitude to show and the climb to record, from whichever sources the watch
 * has.
 *
 * The two answers come from *different* readings on purpose, and putting them
 * together wrongly is what this class exists to prevent.
 *
 * The altitude on screen has to be a height above the sea, which a barometer
 * cannot know on its own - it is told by the first satellite fix and reminded
 * every hour, because weather moves the pressure.
 *
 * The climb must never see that correction. A ride recorded ascent equal to the
 * height of the ground it started on: the barometer began at its own raw reading,
 * the first fix moved the datum to eight hundred metres, and the eight hundred
 * metre step was counted as a climb the rider had not made. Every hourly
 * recalibration did the same thing again, more quietly.
 *
 * So the climb is measured from the reading that *measures change* - the
 * barometer's own value, or the fix's altitude on a watch with no barometer -
 * where a change of datum simply does not appear. The two differ by a constant,
 * and a constant cancels out of every difference, which is the whole point.
 */
class AltitudeTracker(
    private val fusion: AltitudeFusion = AltitudeFusion(),
    private val climb: ClimbTotal = ClimbTotal(),
) {
    /** The height above the sea to show, or null before anything is known. */
    var altitudeMeters: Double? = null
        private set

    /** Climbed since this ride began, and nothing before it. */
    val ascentMeters: Double get() = climb.ascentMeters

    val descentMeters: Double get() = climb.descentMeters

    /** True once some source has given a height, which is when the totals mean anything. */
    var measuring: Boolean = false
        private set

    fun record(
        barometricMeters: Double?,
        gnssMeters: Double?,
        nowEpochMs: Long,
    ) {
        altitudeMeters = fusion.altitude(barometricMeters, gnssMeters, nowEpochMs)
        // The raw barometer where there is one, and only then the fix. Never the
        // fused value: that one moves when the datum is corrected, and a datum
        // correction is not a hill.
        val measured = barometricMeters ?: gnssMeters ?: return
        measuring = true
        climb.record(measured)
    }

    /** Forgets the ride. The next one starts from nothing, as it must. */
    fun clear() {
        fusion.clear()
        climb.clear()
        altitudeMeters = null
        measuring = false
    }
}
