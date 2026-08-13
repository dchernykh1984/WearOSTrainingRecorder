package com.dchernykh.trainingrecorder.wear.connector

import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.StorageConnector
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads a finished workout to Garmin Connect.
 *
 * Garmin has no open API for this, so the request carries the OAuth2 bearer the
 * phone obtained by signing in on the rider's behalf. If uploads start failing
 * at the transport layer rather than with a status code, the cause is most
 * likely the bot protection described in [GarminProtocol] - the Python client
 * gets past it by imitating a browser's TLS fingerprint, which this cannot do.
 */
class GarminConnector : StorageConnector {
    override val id: String = GarminProtocol.ID

    override val credentialFields: List<CredentialField> = GarminProtocol.credentialFields

    override suspend fun upload(
        upload: WorkoutUpload,
        credentials: Map<String, String>,
    ): UploadResult =
        withContext(Dispatchers.IO) {
            val bearer = credentials[BEARER.key].orEmpty()
            if (bearer.isBlank()) return@withContext UploadResult.Retryable("not signed in yet")
            val response =
                runCatching {
                    HttpUpload.post(
                        url = GarminProtocol.UPLOAD_URL,
                        parts = listOf(Part.File("file", upload.fileName, upload.openStream)),
                        headers =
                            mapOf(
                                "Authorization" to "Bearer $bearer",
                                // Garmin refuses an upload without one; the value
                                // itself is not checked, its absence is.
                                "NK" to "NT",
                            ),
                    )
                }.getOrElse { return@withContext UploadResult.Retryable("network: ${it.message}") }
            GarminProtocol.classify(response.statusCode)
        }

    private companion object {
        val BEARER = CredentialField(GarminProtocol.BEARER_TOKEN, secret = true)
    }
}
