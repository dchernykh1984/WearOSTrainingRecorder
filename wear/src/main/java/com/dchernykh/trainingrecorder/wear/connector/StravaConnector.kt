package com.dchernykh.trainingrecorder.wear.connector

import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.StorageConnector
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
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
            val current = refreshed(credentials)
            val token = current[StravaProtocol.ACCESS_TOKEN].orEmpty()
            if (token.isBlank()) return@withContext UploadResult.Retryable("not authorized yet")
            val fields = StravaProtocol.uploadFields(upload.sportTypeId, upload.activityName)
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

    /**
     * The credentials to upload with, refreshing first if the token is spent.
     *
     * Returns what it was given when a refresh is not needed or does not work -
     * the upload then fails on its own terms, which is a clearer answer than a
     * refresh error the rider cannot act on.
     */
    private fun refreshed(credentials: Map<String, String>): Map<String, String> =
        newTokens(credentials)?.let { tokens ->
            (credentials + tokens).also(onTokensRefreshed)
        } ?: credentials

    /** Null whenever the refresh is unnecessary, impossible, or unsuccessful. */
    private fun newTokens(credentials: Map<String, String>): Map<String, String>? {
        if (!StravaProtocol.needsRefresh(credentials, System.currentTimeMillis() / MILLIS_PER_SECOND)) return null
        val fields =
            StravaProtocol.refreshRequestFields(
                clientId = credentials[StravaProtocol.CLIENT_ID].orEmpty(),
                clientSecret = credentials[StravaProtocol.CLIENT_SECRET].orEmpty(),
                refreshToken = credentials[StravaProtocol.REFRESH_TOKEN].orEmpty(),
            )
        if (fields.values.any { it.isBlank() }) return null
        return runCatching { HttpUpload.form(StravaProtocol.TOKEN_URL, fields) }
            .getOrNull()
            ?.let { StravaProtocol.tokensFrom(it) }
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        val ACCESS_TOKEN = CredentialField(StravaProtocol.ACCESS_TOKEN, secret = true)
        const val MILLIS_PER_SECOND = 1000L
    }
}
