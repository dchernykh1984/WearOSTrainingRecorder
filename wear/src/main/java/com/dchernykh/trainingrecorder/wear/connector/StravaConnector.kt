package com.dchernykh.trainingrecorder.wear.connector

import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.ServiceDiagnostics
import com.dchernykh.trainingrecorder.core.connector.StorageConnector
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
import com.dchernykh.trainingrecorder.core.connector.withDiagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends a finished workout to Strava.
 *
 * Everything about the shape of the request lives in [StravaProtocol] where it
 * is unit-tested; this class is the transport and the token handling.
 *
 * Access tokens last about six hours, so this refreshes before uploading rather
 * than discovering the expiry as a 401. Left to fail, an evening ride would be
 * retried ten times against a token that cannot come back, and then recorded as
 * permanently failed - which the retention policy is entitled to delete.
 */
class StravaConnector(
    private val postRequest: (String, List<Part>, Map<String, String>) -> HttpUpload.Response = HttpUpload::post,
    private val formRequest: (
        String,
        Map<String, String>,
        Map<String, String>,
    ) -> HttpUpload.Response = HttpUpload::form,
    /**
     * Called with the credentials to keep whenever a refresh produces new ones.
     * Strava rotates the refresh token, so the answer has to be written down or
     * the next expiry has nothing left to trade.
     */
    private val onTokensRefreshed: (Map<String, String>) -> Unit = {},
) : StorageConnector {
    override val id: String = StravaProtocol.ID

    /**
     * The token alone. The client id and secret are needed to refresh it, but a
     * rider who has authorized has all three - and requiring them here would
     * make a connector configured only by a pasted token look unusable.
     */
    override val credentialFields: List<CredentialField> = listOf(ACCESS_TOKEN)

    override suspend fun upload(
        upload: WorkoutUpload,
        credentials: Map<String, String>,
    ): UploadResult =
        withContext(Dispatchers.IO) {
            var refreshDiagnostic: String? = null
            val current = refreshed(credentials) { refreshDiagnostic = it }
            val token = current[StravaProtocol.ACCESS_TOKEN].orEmpty()
            if (token.isBlank()) return@withContext UploadResult.Retryable("not authorized yet")
            val fields = StravaProtocol.uploadFields(upload.sportTypeId, upload.activityName)
            val parts =
                fields.map { (name, value) -> Part.Text(name, value) } +
                    Part.File("file", upload.fileName, upload.openStream)
            val response =
                runCatching {
                    postRequest(
                        StravaProtocol.UPLOAD_URL,
                        parts,
                        mapOf("Authorization" to "Bearer $token"),
                    )
                }.getOrElse {
                    return@withContext UploadResult
                        .Retryable(
                            ServiceDiagnostics.network(it),
                        ).withDiagnostic(refreshDiagnostic)
                }
            StravaProtocol
                .classify(response.statusCode, response.body)
                .withDiagnostic(ServiceDiagnostics.http(response.statusCode, response.body))
                .withDiagnostic(refreshDiagnostic)
        }

    /**
     * The credentials to upload with, refreshing first if the token is spent.
     *
     * Returns what it was given when a refresh is not needed or does not work -
     * the upload still has a chance to succeed. If it fails too, the history
     * includes both answers rather than hiding the failed refresh.
     */
    private fun refreshed(
        credentials: Map<String, String>,
        onFailure: (String) -> Unit,
    ): Map<String, String> =
        newTokens(credentials, onFailure)?.let { tokens ->
            (credentials + tokens).also(onTokensRefreshed)
        } ?: credentials

    /** Null whenever the refresh is unnecessary, impossible, or unsuccessful. */
    @Suppress("ReturnCount")
    private fun newTokens(
        credentials: Map<String, String>,
        onFailure: (String) -> Unit,
    ): Map<String, String>? {
        if (!StravaProtocol.needsRefresh(credentials, System.currentTimeMillis() / MILLIS_PER_SECOND)) return null
        val fields =
            StravaProtocol.refreshRequestFields(
                clientId = credentials[StravaProtocol.CLIENT_ID].orEmpty(),
                clientSecret = credentials[StravaProtocol.CLIENT_SECRET].orEmpty(),
                refreshToken = credentials[StravaProtocol.REFRESH_TOKEN].orEmpty(),
            )
        if (fields.values.any { it.isBlank() }) return null
        val response =
            runCatching { formRequest(StravaProtocol.TOKEN_URL, fields, emptyMap()) }.getOrElse {
                onFailure("OAuth: ${ServiceDiagnostics.network(it)}")
                return null
            }
        val tokens = StravaProtocol.tokensFrom(response.body)
        if (response.statusCode !in 200..299 || tokens.isEmpty()) {
            onFailure("OAuth: ${ServiceDiagnostics.http(response.statusCode, response.body)}")
            return null
        }
        return tokens
    }

    private companion object {
        val ACCESS_TOKEN = CredentialField(StravaProtocol.ACCESS_TOKEN, secret = true)
        const val MILLIS_PER_SECOND = 1000L
    }
}
