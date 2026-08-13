package com.dchernykh.trainingrecorder.core.connector

import com.dchernykh.trainingrecorder.core.workout.UploadState
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageConnectorTest {
    private class FakeConnector(
        override val id: String,
        override val credentialFields: List<CredentialField> = emptyList(),
        private val result: UploadResult = UploadResult.Success(),
    ) : StorageConnector {
        var uploaded: WorkoutUpload? = null

        override suspend fun upload(
            upload: WorkoutUpload,
            credentials: Map<String, String>,
        ): UploadResult {
            uploaded = upload
            return result
        }
    }

    private val strava =
        FakeConnector(
            "strava",
            listOf(CredentialField("client_id"), CredentialField("client_secret", secret = true)),
        )
    private val garmin =
        FakeConnector("garmin", listOf(CredentialField("login"), CredentialField("password", secret = true)))
    private val registry = ConnectorRegistry(listOf(strava, garmin))

    private fun workout(uploads: Map<String, UploadState> = emptyMap()) =
        WorkoutSummary(
            id = "w1",
            sportTypeId = "cycling_road",
            startedAtEpochMs = 1,
            durationSeconds = 60,
            distanceMeters = 100.0,
            fileSizeBytes = 1024,
            uploads = uploads,
        )

    private val bothConfigured =
        mapOf(
            "strava" to mapOf("client_id" to "1", "client_secret" to "s"),
            "garmin" to mapOf("login" to "me", "password" to "p"),
        )

    @Test
    fun aConnectorIsConfiguredOnlyWhenEveryRequiredFieldIsFilled() {
        assertFalse(strava.isConfigured(emptyMap()))
        assertFalse(strava.isConfigured(mapOf("client_id" to "1")))
        assertFalse(strava.isConfigured(mapOf("client_id" to "1", "client_secret" to " ")))
        assertTrue(strava.isConfigured(mapOf("client_id" to "1", "client_secret" to "s")))
    }

    @Test
    fun optionalFieldsDoNotBlockConfiguration() {
        val connector = FakeConnector("x", listOf(CredentialField("note", required = false)))
        assertTrue(connector.isConfigured(emptyMap()))
    }

    @Test
    fun aConnectorWithoutCredentialsIsAlwaysConfigured() {
        assertTrue(FakeConnector("local").isConfigured(emptyMap()))
    }

    @Test
    fun theRegistryFindsConnectorsById() {
        assertEquals(strava, registry.byId("strava"))
        assertNull(registry.byId("nope"))
        assertEquals(listOf("strava", "garmin"), registry.ids)
    }

    @Test
    fun duplicateConnectorIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> { ConnectorRegistry(listOf(strava, FakeConnector("strava"))) }
    }

    @Test
    fun onlyConfiguredConnectorsAreOffered() {
        assertEquals(emptyList(), registry.configured(emptyMap()))
        val partial = mapOf("strava" to mapOf("client_id" to "1", "client_secret" to "s"))
        assertEquals(listOf(strava), registry.configured(partial))
        assertEquals(listOf(strava, garmin), registry.configured(bothConfigured))
    }

    @Test
    fun aWorkoutIsOfferedToEveryConfiguredConnectorThatLacksIt() {
        assertEquals(listOf(strava, garmin), registry.pendingFor(workout(), bothConfigured))
    }

    @Test
    fun anAlreadyUploadedWorkoutIsNotOfferedAgain() {
        val done = workout(mapOf("strava" to UploadState.UPLOADED))
        assertEquals(listOf(garmin), registry.pendingFor(done, bothConfigured))
    }

    @Test
    fun aPermanentlyRejectedUploadIsNotRetried() {
        val rejected = workout(mapOf("strava" to UploadState.FAILED))
        assertEquals(listOf(garmin), registry.pendingFor(rejected, bothConfigured))
    }

    @Test
    fun aPendingUploadIsOfferedAgain() {
        val pending = workout(mapOf("strava" to UploadState.PENDING))
        assertEquals(listOf(strava, garmin), registry.pendingFor(pending, bothConfigured))
    }

    @Test
    fun resultsMapOntoTheStatesTheRetentionPolicyReadsBack() {
        assertEquals(UploadState.UPLOADED, UploadResult.Success("42").state)
        assertEquals(UploadState.FAILED, UploadResult.Rejected("duplicate").state)
        assertEquals(UploadState.PENDING, UploadResult.Retryable("no network").state)
        assertEquals(30, UploadResult.Retryable("rate limited", 30).retryAfterSeconds)
    }

    @Test
    fun anUploadStreamsItsFileRatherThanHoldingIt() {
        val bytes = "FIT".toByteArray()
        val upload =
            WorkoutUpload("w1", "cycling_road", "w1.fit", bytes.size.toLong()) { ByteArrayInputStream(bytes) }
        assertEquals(3, upload.byteCount)
        assertEquals("FIT", upload.openStream().readBytes().decodeToString())
        assertEquals("FIT", upload.openStream().readBytes().decodeToString(), "the stream can be opened again")
    }

    @Test
    fun degenerateUploadsAndFieldsAreRejected() {
        assertFailsWith<IllegalArgumentException> { CredentialField("") }
        assertFailsWith<IllegalArgumentException> {
            WorkoutUpload("", "s", "f.fit", 1) { ByteArrayInputStream(ByteArray(0)) }
        }
        assertFailsWith<IllegalArgumentException> {
            WorkoutUpload("w", "s", "", 1) { ByteArrayInputStream(ByteArray(0)) }
        }
        assertFailsWith<IllegalArgumentException> {
            WorkoutUpload("w", "s", "f.fit", -1) { ByteArrayInputStream(ByteArray(0)) }
        }
    }
}
