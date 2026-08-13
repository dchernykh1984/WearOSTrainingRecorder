package com.dchernykh.trainingrecorder.wear.connector

import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.StorageConnector
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads a finished workout to Strava.
 *
 * Everything decidable - the URL, the fields, what a status code means - lives in
 * [StravaProtocol] where it is unit-tested; this class is the transport and the
 * credentials, and nothing else.
 */
class StravaConnector : StorageConnector {
    override val id: String = StravaProtocol.ID

    override val credentialFields: List<CredentialField> = StravaProtocol.credentialFields + ACCESS_TOKEN

    override suspend fun upload(
        upload: WorkoutUpload,
        credentials: Map<String, String>,
    ): UploadResult =
        withContext(Dispatchers.IO) {
            val token = credentials[ACCESS_TOKEN.key].orEmpty()
            if (token.isBlank()) return@withContext UploadResult.Retryable("not authorized yet")
            val fields = StravaProtocol.uploadFields(upload.sportTypeId, upload.fileName)
            val parts =
                fields.map { (name, value) -> Part.Text(name, value) } +
                    Part.File("file", upload.fileName, upload.openStream)
            val response =
                runCatching {
                    HttpUpload.post(
                        url = StravaProtocol.UPLOAD_URL,
                        parts = parts,
                        headers = mapOf("Authorization" to "Bearer $token"),
                    )
                }.getOrElse { return@withContext UploadResult.Retryable("network: ${it.message}") }
            StravaProtocol.classify(response.statusCode, response.body)
        }

    private companion object {
        /**
         * Held alongside the client credentials rather than derived from them:
         * the phone runs the authorization and hands the watch the token, so the
         * watch never sees the rider's Strava password or a refresh flow.
         */
        val ACCESS_TOKEN = CredentialField("access_token", secret = true)
    }
}
