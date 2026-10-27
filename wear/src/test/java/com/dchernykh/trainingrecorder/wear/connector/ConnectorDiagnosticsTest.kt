package com.dchernykh.trainingrecorder.wear.connector

import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.GarminSignIn
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.UnknownHostException

class ConnectorDiagnosticsTest {
    private val upload = WorkoutUpload("ride", "cycling_road", "ride.fit", 0) { ByteArrayInputStream(byteArrayOf()) }

    @Test
    fun aGarminNetworkFailureIsNotInventedAsAnHttp503() =
        runBlocking {
            val connector = GarminConnector(postRequest = { _, _, _ -> throw UnknownHostException("private URL") })
            val result = connector.upload(upload, mapOf(GarminProtocol.ACCESS_TOKEN to "token"))
            assertEquals(UploadResult.Retryable("UnknownHostException"), result)
        }

    @Test
    fun garminKeepsBothTheUploadRefusalAndTheFailedRefresh() =
        runBlocking {
            val connector =
                GarminConnector(
                    postRequest = { _, _, _ -> HttpUpload.Response(401, "") },
                    formRequest = { _, _, _ -> HttpUpload.Response(400, """{"error":"invalid_grant"}""") },
                )
            val result =
                connector.upload(
                    upload,
                    mapOf(
                        GarminProtocol.ACCESS_TOKEN to "token",
                        GarminProtocol.REFRESH_TOKEN to "refresh",
                        GarminProtocol.TOKEN_CLIENT_ID to GarminSignIn.CLIENT_IDS.first(),
                    ),
                )
            assertEquals("session expired; HTTP 401; OAuth: HTTP 400; invalid_grant", result.failureReason)
        }

    @Test
    fun stravaKeepsTheServiceErrorCodeWhenAnUploadIsRejected() =
        runBlocking {
            val connector =
                StravaConnector(
                    postRequest = { _, _, _ -> HttpUpload.Response(400, """{"errors":[{"code":"invalid"}]}""") },
                )
            val result = connector.upload(upload, mapOf(StravaProtocol.ACCESS_TOKEN to "token"))
            assertTrue(result is UploadResult.Rejected)
            assertEquals("strava refused the upload: 400; HTTP 400; invalid", result.failureReason)
        }

    @Test
    fun aSuccessfulUploadDoesNotShowAnEarlierRefreshFailure() =
        runBlocking {
            val connector =
                StravaConnector(
                    postRequest = { _, _, _ -> HttpUpload.Response(201, "") },
                    formRequest = { _, _, _ -> HttpUpload.Response(503, "") },
                )
            val result =
                connector.upload(
                    upload,
                    mapOf(
                        StravaProtocol.ACCESS_TOKEN to "token",
                        StravaProtocol.REFRESH_TOKEN to "refresh",
                        StravaProtocol.CLIENT_ID to "client",
                        StravaProtocol.CLIENT_SECRET to "secret",
                        StravaProtocol.EXPIRES_AT to "1",
                    ),
                )
            assertTrue(result is UploadResult.Success)
            assertEquals(null, result.failureReason)
        }
}
