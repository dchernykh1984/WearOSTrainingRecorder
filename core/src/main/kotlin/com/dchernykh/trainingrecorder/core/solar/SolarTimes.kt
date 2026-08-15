package com.dchernykh.trainingrecorder.core.solar

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** When the sun comes up and goes down where the rider is, as instants. */
data class SolarEvents(
    val sunriseEpochMs: Long,
    val sunsetEpochMs: Long,
) {
    init {
        require(sunsetEpochMs >= sunriseEpochMs) { "the sun sets after it rises" }
    }
}

/**
 * Sunrise and sunset from a position and a date, computed rather than looked up.
 *
 * Nothing is asked of the system or the network. A rider on a ridge at dusk
 * wants to know how long the light lasts, and that is a question a watch with a
 * clock and a fix can answer on its own - which is the only kind of answer worth
 * having when there is no signal, which is exactly where the question gets
 * asked.
 *
 * This is the standard sunrise equation: mean solar anomaly, the equation of the
 * centre, ecliptic longitude, declination, and the hour angle at which the sun's
 * centre reaches 0.833° below the horizon - half a degree for the disc and a
 * third for refraction, which is why sunrise is a moment before geometry says
 * it should be. Good to about a minute, which is a great deal better than the
 * light changes.
 *
 * Checked against places where the answer is known: Moscow at both solstices and
 * London at the equinox agree to the minute. The longitude sign in particular is
 * pinned by tests, since a place near Greenwich cannot tell the two conventions
 * apart and every one of them looks right until you go east.
 */
object SolarTimes {
    /**
     * Null where the sun does not rise or set that day.
     *
     * Above the Arctic circle in June there is no sunrise, and no honest time to
     * print for one - the field shows nothing, which is the truth.
     */
    fun at(
        latitudeDeg: Double,
        longitudeDeg: Double,
        atEpochMs: Long,
    ): SolarEvents? {
        require(abs(latitudeDeg) <= MAX_LATITUDE) { "latitude must be within +/-90, got $latitudeDeg" }
        require(abs(longitudeDeg) <= MAX_LONGITUDE) { "longitude must be within +/-180, got $longitudeDeg" }

        val julian = atEpochMs / MILLIS_PER_DAY + UNIX_EPOCH_AS_JULIAN
        val longitudeAsDays = longitudeDeg / DEGREES_PER_DAY
        // Which solar day to answer for: the one whose noon is nearest to now
        // *where the rider is standing*, which is why the longitude is in the
        // rounding as well as after it. Rounded against UTC noon instead, the
        // day changed at midnight in Greenwich and therefore at eleven in the
        // morning in Sydney and three in the afternoon in Anchorage - so for a
        // good part of every day the watch reported yesterday's sunrise.
        // Rounded rather than truncated for the same reason: it is the nearest
        // noon that matters, not the last one.
        val day = (julian - J2000 + LEAP_SECOND_FUDGE + longitudeAsDays).roundToLong()
        // And off again here, because solar time runs ahead of UTC to the east.
        // This sign is the one thing that a test near Greenwich cannot catch.
        val meanSolarDay = day - longitudeAsDays

        val anomaly = degrees(MEAN_ANOMALY_AT_EPOCH + MEAN_ANOMALY_PER_DAY * meanSolarDay)
        val centre =
            EQUATION_C1 * sin(radians(anomaly)) +
                EQUATION_C2 * sin(radians(2 * anomaly)) +
                EQUATION_C3 * sin(radians(3 * anomaly))
        val eclipticLongitude = degrees(anomaly + centre + ARGUMENT_OF_PERIHELION)

        val transit =
            J2000 + meanSolarDay +
                TRANSIT_ANOMALY_TERM * sin(radians(anomaly)) -
                TRANSIT_ECLIPTIC_TERM * sin(radians(2 * eclipticLongitude))

        val declinationSin = sin(radians(eclipticLongitude)) * sin(radians(OBLIQUITY_DEG))
        val declination = asin(declinationSin)
        val latitude = radians(latitudeDeg)
        val hourAngleCos =
            (sin(radians(HORIZON_DEG)) - sin(latitude) * declinationSin) /
                (cos(latitude) * cos(declination))
        // Outside +/-1 the sun never reaches the horizon that day: polar day one
        // way, polar night the other. There is no time to print for either.
        //
        // NaN is checked as well as the range, because it fails the range test:
        // exactly at a pole the divisor is a rounding error away from zero, and
        // 0/0 would sail past `> 1` and come out as a pair of times in 1970.
        if (hourAngleCos.isNaN() || abs(hourAngleCos) > 1) return null
        val hourAngleFraction = toDegrees(acos(hourAngleCos)) / DEGREES_PER_DAY

        return SolarEvents(
            sunriseEpochMs = epochMsOf(transit - hourAngleFraction),
            sunsetEpochMs = epochMsOf(transit + hourAngleFraction),
        )
    }

    private fun epochMsOf(julian: Double): Long = ((julian - UNIX_EPOCH_AS_JULIAN) * MILLIS_PER_DAY).roundToLong()

    /** Normalised to [0, 360), which the trigonometry below assumes. */
    private fun degrees(value: Double): Double {
        val wrapped = value % DEGREES_PER_DAY
        return if (wrapped < 0) wrapped + DEGREES_PER_DAY else wrapped
    }

    private fun radians(degrees: Double): Double = degrees * PI_OVER_180

    private fun toDegrees(radians: Double): Double = radians / PI_OVER_180

    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val UNIX_EPOCH_AS_JULIAN = 2_440_587.5
    private const val J2000 = 2_451_545.0
    private const val DEGREES_PER_DAY = 360.0
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0
    private const val PI_OVER_180 = Math.PI / 180.0

    /** Keeps the rounding to the right side of midnight; it is not a correction. */
    private const val LEAP_SECOND_FUDGE = 0.0008

    private const val MEAN_ANOMALY_AT_EPOCH = 357.5291
    private const val MEAN_ANOMALY_PER_DAY = 0.98560028
    private const val EQUATION_C1 = 1.9148
    private const val EQUATION_C2 = 0.0200
    private const val EQUATION_C3 = 0.0003

    /** 180 degrees to face the sun, plus the earth's argument of perihelion. */
    private const val ARGUMENT_OF_PERIHELION = 180.0 + 102.9372

    private const val TRANSIT_ANOMALY_TERM = 0.0053
    private const val TRANSIT_ECLIPTIC_TERM = 0.0069

    /** The tilt of the earth's axis. */
    private const val OBLIQUITY_DEG = 23.4397

    /**
     * Where the sun's centre is when the rider calls it sunrise: half a degree
     * below the horizon for the disc's own radius, and a third of a degree again
     * for the atmosphere bending the light around the curve.
     */
    private const val HORIZON_DEG = -0.833
}
