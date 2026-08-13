package com.dchernykh.trainingrecorder.core.sport

/**
 * Top-level discipline. Screen configuration inherits along
 * default -> [Discipline] -> [SportType], so this is also the middle tier of
 * that hierarchy.
 */
enum class Discipline(
    val id: String,
) {
    CYCLING("cycling"),
    RUNNING("running"),
    SWIMMING("swimming"),
    XC_SKIING("xc_skiing"),
    ;

    companion object {
        fun byId(id: String): Discipline? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One concrete activity the rider picks before starting.
 *
 * The three target platforms disagree about what a sport is, so each mapping is
 * recorded explicitly rather than derived:
 *
 * - [stravaSportType] is a value of Strava's `SportType` enum. Strava models
 *   "indoor" and "commute" as boolean flags on a plain `Ride`, not as sport
 *   types, hence [stravaTrainer] and [stravaCommute].
 * - [garminTypeKey] is Garmin Connect's `typeKey`. Null means Garmin has no
 *   matching type at all - roller skiing is the real case.
 * - [healthServicesExerciseType] is the *name* of the constant in
 *   `androidx.health.services.client.data.ExerciseType`. It is a string because
 *   this module deliberately has no Android dependency; the watch module
 *   resolves it.
 *
 * [stravaExact] and [garminExact] are false when the mapping is a deliberate
 * fallback, so the phone can warn that an upload will land under a broader type
 * than the rider chose.
 */
data class SportType(
    val id: String,
    val discipline: Discipline,
    val stravaSportType: String,
    val garminTypeKey: String?,
    val healthServicesExerciseType: String,
    val stravaTrainer: Boolean = false,
    val stravaCommute: Boolean = false,
    val stravaExact: Boolean = true,
    val garminExact: Boolean = true,
)

/**
 * The sports the app offers, grouped by discipline. Kept deliberately short -
 * a long list is worse than a good default on a watch screen.
 */
object SportCatalogue {
    val cycling: List<SportType> =
        listOf(
            SportType("cycling_road", Discipline.CYCLING, "Ride", "road_biking", "BIKING"),
            SportType("cycling_gravel", Discipline.CYCLING, "GravelRide", "gravel_cycling", "BIKING"),
            SportType("cycling_mtb", Discipline.CYCLING, "MountainBikeRide", "mountain_biking", "MOUNTAIN_BIKING"),
            SportType(
                id = "cycling_cyclocross",
                discipline = Discipline.CYCLING,
                stravaSportType = "GravelRide",
                garminTypeKey = "cyclocross",
                healthServicesExerciseType = "MOUNTAIN_BIKING",
                stravaExact = false,
            ),
            SportType(
                id = "cycling_indoor",
                discipline = Discipline.CYCLING,
                stravaSportType = "Ride",
                garminTypeKey = "indoor_cycling",
                healthServicesExerciseType = "BIKING_STATIONARY",
                stravaTrainer = true,
            ),
            SportType("cycling_virtual", Discipline.CYCLING, "VirtualRide", "virtual_ride", "BIKING_STATIONARY"),
            SportType(
                id = "cycling_commute",
                discipline = Discipline.CYCLING,
                stravaSportType = "Ride",
                garminTypeKey = "road_biking",
                healthServicesExerciseType = "BIKING",
                stravaCommute = true,
                garminExact = false,
            ),
            SportType("cycling_ebike", Discipline.CYCLING, "EBikeRide", "e_bike_fitness", "BIKING"),
            SportType(
                "cycling_ebike_mtb",
                Discipline.CYCLING,
                "EMountainBikeRide",
                "e_bike_mountain",
                "MOUNTAIN_BIKING",
            ),
            SportType(
                id = "cycling_track",
                discipline = Discipline.CYCLING,
                stravaSportType = "Ride",
                garminTypeKey = "track_cycling",
                healthServicesExerciseType = "BIKING_STATIONARY",
                stravaExact = false,
            ),
        )

    val running: List<SportType> =
        listOf(
            SportType("run_road", Discipline.RUNNING, "Run", "street_running", "RUNNING"),
            SportType("run_trail", Discipline.RUNNING, "TrailRun", "trail_running", "RUNNING"),
            SportType(
                id = "run_treadmill",
                discipline = Discipline.RUNNING,
                stravaSportType = "Run",
                garminTypeKey = "treadmill_running",
                healthServicesExerciseType = "RUNNING_TREADMILL",
                stravaTrainer = true,
            ),
            SportType(
                id = "run_track",
                discipline = Discipline.RUNNING,
                stravaSportType = "Run",
                garminTypeKey = "track_running",
                healthServicesExerciseType = "RUNNING",
                stravaExact = false,
            ),
            SportType("run_virtual", Discipline.RUNNING, "VirtualRun", "virtual_run", "RUNNING_TREADMILL"),
            SportType(
                id = "run_indoor",
                discipline = Discipline.RUNNING,
                stravaSportType = "Run",
                garminTypeKey = "indoor_running",
                healthServicesExerciseType = "RUNNING_TREADMILL",
                stravaExact = false,
            ),
            SportType(
                id = "run_ultra",
                discipline = Discipline.RUNNING,
                stravaSportType = "Run",
                garminTypeKey = "ultra_run",
                healthServicesExerciseType = "RUNNING",
                stravaExact = false,
            ),
            SportType("walk", Discipline.RUNNING, "Walk", "walking", "WALKING"),
            SportType("hike", Discipline.RUNNING, "Hike", "hiking", "HIKING"),
            SportType(
                id = "ruck",
                discipline = Discipline.RUNNING,
                stravaSportType = "Hike",
                garminTypeKey = "rucking",
                healthServicesExerciseType = "BACKPACKING",
                stravaExact = false,
            ),
        )

    val swimming: List<SportType> =
        listOf(
            SportType(
                id = "swim_pool",
                discipline = Discipline.SWIMMING,
                stravaSportType = "Swim",
                garminTypeKey = "lap_swimming",
                healthServicesExerciseType = "SWIMMING_POOL",
                stravaExact = false,
            ),
            SportType(
                id = "swim_open_water",
                discipline = Discipline.SWIMMING,
                stravaSportType = "Swim",
                garminTypeKey = "open_water_swimming",
                healthServicesExerciseType = "SWIMMING_OPEN_WATER",
                stravaExact = false,
            ),
            SportType("swim_generic", Discipline.SWIMMING, "Swim", "swimming", "SWIMMING_POOL"),
        )

    val xcSkiing: List<SportType> =
        listOf(
            SportType(
                id = "xc_classic",
                discipline = Discipline.XC_SKIING,
                stravaSportType = "NordicSki",
                garminTypeKey = "cross_country_skiing_ws",
                healthServicesExerciseType = "CROSS_COUNTRY_SKIING",
                stravaExact = false,
            ),
            SportType(
                id = "xc_skate",
                discipline = Discipline.XC_SKIING,
                stravaSportType = "NordicSki",
                garminTypeKey = "skate_skiing_ws",
                healthServicesExerciseType = "CROSS_COUNTRY_SKIING",
                stravaExact = false,
            ),
            SportType(
                "xc_generic",
                Discipline.XC_SKIING,
                "NordicSki",
                "cross_country_skiing_ws",
                "CROSS_COUNTRY_SKIING",
            ),
            SportType("xc_backcountry", Discipline.XC_SKIING, "BackcountrySki", "backcountry_skiing", "SKIING"),
            SportType(
                id = "xc_rollerski_classic",
                discipline = Discipline.XC_SKIING,
                stravaSportType = "RollerSki",
                garminTypeKey = null,
                healthServicesExerciseType = "ROLLER_SKATING",
                garminExact = false,
            ),
            SportType(
                id = "xc_rollerski_skate",
                discipline = Discipline.XC_SKIING,
                stravaSportType = "RollerSki",
                garminTypeKey = null,
                healthServicesExerciseType = "ROLLER_SKATING",
                garminExact = false,
            ),
            SportType("snowshoe", Discipline.XC_SKIING, "Snowshoe", "snow_shoe_ws", "SNOWSHOEING"),
            SportType("alpine_ski", Discipline.XC_SKIING, "AlpineSki", "resort_skiing", "ALPINE_SKIING"),
        )

    val all: List<SportType> = cycling + running + swimming + xcSkiing

    /** The sport preselected the very first time, before any history exists. */
    val default: SportType = requireNotNull(byId("cycling_road"))

    fun byId(id: String): SportType? = all.firstOrNull { it.id == id }

    fun forDiscipline(discipline: Discipline): List<SportType> = all.filter { it.discipline == discipline }
}
