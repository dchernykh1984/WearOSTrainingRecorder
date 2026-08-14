package com.dchernykh.trainingrecorder.core.connector

import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import java.io.InputStream

/**
 * One value a connector needs before it can be used - a Strava client id, a
 * Garmin password. The app never ships credentials of its own: the rider enters
 * theirs once, and they stay encrypted on the phone.
 */
data class CredentialField(
    val key: String,
    val secret: Boolean = false,
    val required: Boolean = true,
) {
    init {
        require(key.isNotBlank()) { "a credential field needs a key" }
    }
}

/** A finished workout offered to a connector, streamed rather than held in memory. */
class WorkoutUpload(
    val workoutId: String,
    val sportTypeId: String,
    val fileName: String,
    val byteCount: Long,
    /**
     * What the ride is called on the service. Distinct from [fileName], which is
     * storage detail - a rider whose Strava feed reads
     * "workout-1723600000000.fit" has been shown the wrong string.
     *
     * Before [openStream] so that stays the trailing parameter, which is how
     * every caller passes it.
     */
    val activityName: String = fileName,
    val openStream: () -> InputStream,
) {
    init {
        require(workoutId.isNotBlank()) { "an upload needs a workout id" }
        require(fileName.isNotBlank()) { "an upload needs a file name" }
        require(byteCount >= 0) { "byte count cannot be negative" }
    }
}

/**
 * What came of an attempt.
 *
 * The split that matters is [Rejected] against [Retryable]: a rejected upload is
 * never tried again, because retrying a duplicate or a malformed file only burns
 * the rider's rate limit, while a retryable one is exactly what the queue exists
 * for.
 */
sealed interface UploadResult {
    data class Success(
        val remoteId: String? = null,
    ) : UploadResult

    data class Rejected(
        val reason: String,
    ) : UploadResult

    data class Retryable(
        val reason: String,
        val retryAfterSeconds: Int? = null,
    ) : UploadResult

    val state: UploadState
        get() =
            when (this) {
                is Success -> UploadState.UPLOADED
                is Rejected -> UploadState.FAILED
                is Retryable -> UploadState.PENDING
            }

    /**
     * Why the ride has not arrived, in the words the service or the network
     * used. Null on success, because a ride that is there needs no explaining.
     *
     * Deliberately untranslated: these are diagnostics, not prose - "session
     * expired", "rate limited", "UnknownHostException" - and a rider comparing
     * one against a search engine is better served by the string the service
     * actually said.
     */
    val failureReason: String?
        get() =
            when (this) {
                is Success -> null
                is Rejected -> reason
                is Retryable -> reason
            }
}

/**
 * A place finished workouts are sent.
 *
 * Adding a service means implementing this and adding it to a
 * [ConnectorRegistry] - nothing else in the app knows the difference between
 * Strava and Garmin.
 */
interface StorageConnector {
    /** Stable id, used as the key in [WorkoutSummary.uploads]. Never localized. */
    val id: String

    /** What the rider must supply. Empty means the connector needs nothing. */
    val credentialFields: List<CredentialField>

    /** True once every required credential is present and non-blank. */
    fun isConfigured(credentials: Map<String, String>): Boolean =
        credentialFields.filter { it.required }.all { credentials[it.key]?.isNotBlank() == true }

    suspend fun upload(
        upload: WorkoutUpload,
        credentials: Map<String, String>,
    ): UploadResult
}

/**
 * The connectors this build knows about, and which of them a given workout still
 * owes a copy to.
 */
class ConnectorRegistry(
    connectors: List<StorageConnector>,
) {
    val connectors: List<StorageConnector> = connectors.toList()

    init {
        val ids = connectors.map { it.id }
        require(ids.size == ids.toSet().size) { "connector ids must be unique, got $ids" }
    }

    val ids: List<String> get() = connectors.map { it.id }

    fun byId(id: String): StorageConnector? = connectors.firstOrNull { it.id == id }

    /** Connectors the rider has finished setting up. */
    fun configured(credentials: Map<String, Map<String, String>>): List<StorageConnector> =
        connectors.filter { it.isConfigured(credentials[it.id].orEmpty()) }

    /**
     * Connectors that should still be offered this workout: configured, and not
     * already holding it. A permanently rejected upload is not retried.
     */
    fun pendingFor(
        workout: WorkoutSummary,
        credentials: Map<String, Map<String, String>>,
    ): List<StorageConnector> =
        configured(credentials).filter {
            when (workout.uploads[it.id]) {
                UploadState.UPLOADED, UploadState.FAILED -> false
                UploadState.PENDING, null -> true
            }
        }
}
