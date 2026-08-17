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
    private val barometricThresholdMeters: Double = BAROMETRIC_THRESHOLD_METERS,
    private val satelliteThresholdMeters: Double = SATELLITE_THRESHOLD_METERS,
) {
    /**
     * Made on the first reading, with a threshold that suits whichever source
     * gave it.
     *
     * A barometer resolves a metre; a satellite fix is lucky to be within
     * fifteen, and its error wanders continuously. Counting climb from GNSS
     * altitude against a barometer's threshold is how a flat road totals a
     * mountain - and a total is the one figure that cannot self-correct, since
     * only the upward wobbles are added and none of the downward ones cancel
     * them.
     *
     * A watch has a barometer or it does not, so this is decided once.
     */
    private var climb: ClimbTotal? = null

    /** The height above the sea to show, or null before anything is known. */
    var altitudeMeters: Double? = null
        private set

    /** Climbed since this ride began, and nothing before it. */
    val ascentMeters: Double get() = climb?.ascentMeters ?: 0.0

    val descentMeters: Double get() = climb?.descentMeters ?: 0.0

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
        val counter =
            climb ?: ClimbTotal(
                if (barometricMeters != null) barometricThresholdMeters else satelliteThresholdMeters,
            ).also { climb = it }
        measuring = true
        counter.record(measured)
    }

    /** Forgets the ride. The next one starts from nothing, as it must. */
    fun clear() {
        fusion.clear()
        climb = null
        altitudeMeters = null
        measuring = false
    }

    private companion object {
        /** A barometer resolves about a metre; three is past its noise. */
        const val BAROMETRIC_THRESHOLD_METERS = 3.0

        /**
         * A satellite fix wanders by ten metres and more while standing still,
         * so a barometer's threshold applied to it invents climb by the hundred.
         */
        const val SATELLITE_THRESHOLD_METERS = 12.0
    }
}
