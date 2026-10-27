package com.dchernykh.trainingrecorder.wear.connector

import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.GarminSignIn
import com.dchernykh.trainingrecorder.core.connector.ServiceDiagnostics
import com.dchernykh.trainingrecorder.core.connector.StorageConnector
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
import com.dchernykh.trainingrecorder.core.connector.withDiagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads a finished workout to Garmin Connect.
 *
 * The phone signs in and sends the tokens over; this spends them. Garmin's
 * access tokens are short - shorter than Strava's - so this refreshes before
 * uploading rather than discovering the expiry as a 401. Left to fail, an
 * evening ride would be retried until the queue gave up and marked it
 * permanently failed, which the retention policy is then entitled to delete.
 *
 * If uploads start failing at the transport layer rather than with a status
 * code, the cause is most likely the bot protection described in
 * [com.dchernykh.trainingrecorder.core.connector.GarminSignIn] - the Python
 * client gets past it by imitating a browser's TLS fingerprint, which this
 * cannot do.
 */
class GarminConnector(
    private val postRequest: (String, List<Part>, Map<String, String>) -> HttpUpload.Response = HttpUpload::post,
    private val formRequest: (
        String,
        Map<String, String>,
        Map<String, String>,
    ) -> HttpUpload.Response = HttpUpload::form,
    /**
     * Called with the credentials to keep whenever a refresh produces new ones.
     * Garmin rotates the refresh token, so the answer has to be written down or
     * the next expiry has nothing left to trade.
     */
    private val onTokensRefreshed: (Map<String, String>) -> Unit = {},
) : StorageConnector {
    override val id: String = GarminProtocol.ID

    /**
     * The access token alone. The rider's login and password are what they type
     * on the phone, but they never come here - the watch uploads with a token,
     * and requiring a password would make a connector that is perfectly usable
     * look unconfigured.
     */
    override val credentialFields: List<CredentialField> = listOf(ACCESS_TOKEN)

    override suspend fun upload(
        upload: WorkoutUpload,
        credentials: Map<String, String>,
    ): UploadResult =
        withContext(Dispatchers.IO) {
            var refreshDiagnostic: String? = null
            val current = refreshed(credentials) { refreshDiagnostic = it }
            val first =
                runCatching { send(upload, current) }.getOrElse {
                    return@withContext UploadResult
                        .Retryable(
                            ServiceDiagnostics.network(it),
                        ).withDiagnostic(refreshDiagnostic)
                } ?: return@withContext UploadResult.Retryable("not signed in yet").withDiagnostic(refreshDiagnostic)
            // A rejection is the other way a token can be spent. The expiry says
            // when it should have run out, but a watch whose clock drifted, or a
            // session Garmin invalidated early, says nothing until it refuses -
            // and without this the queue would retry against a dead token until
            // it gave up and marked a perfectly good ride permanently failed.
            if (first.statusCode != UNAUTHORIZED) return@withContext classify(first).withDiagnostic(refreshDiagnostic)
            val renewed =
                forceRefresh(current) { refreshDiagnostic = it }
                    ?: return@withContext classify(first).withDiagnostic(refreshDiagnostic)
            val second =
                runCatching { send(upload, renewed) }.getOrElse {
                    return@withContext UploadResult
                        .Retryable(
                            ServiceDiagnostics.network(it),
                        ).withDiagnostic(refreshDiagnostic)
                } ?: return@withContext UploadResult.Retryable("not signed in yet")
            classify(second).withDiagnostic(refreshDiagnostic)
        }

    /** The complete response, or null when there is no token to send. */
    private fun send(
        upload: WorkoutUpload,
        credentials: Map<String, String>,
    ): HttpUpload.Response? {
        val token = credentials[GarminProtocol.ACCESS_TOKEN].orEmpty()
        if (token.isBlank()) return null
        return postRequest(
            GarminProtocol.UPLOAD_URL,
            listOf(Part.File("file", upload.fileName, upload.openStream)),
            GarminProtocol.apiHeaders(token),
        )
    }

    /** A refresh asked for by a refusal rather than by the clock. */
    private fun forceRefresh(
        credentials: Map<String, String>,
        onFailure: (String) -> Unit,
    ): Map<String, String>? =
        exchangeRefreshToken(credentials, onFailure)?.let { tokens -> (credentials + tokens).also(onTokensRefreshed) }

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
    private fun newTokens(
        credentials: Map<String, String>,
        onFailure: (String) -> Unit,
    ): Map<String, String>? {
        if (!GarminSignIn.needsRefresh(credentials, System.currentTimeMillis() / MILLIS_PER_SECOND)) return null
        return exchangeRefreshToken(credentials, onFailure)
    }

    /** Null when there is nothing to trade, or the trade was refused. */
    @Suppress("ReturnCount")
    private fun exchangeRefreshToken(
        credentials: Map<String, String>,
        onFailure: (String) -> Unit,
    ): Map<String, String>? {
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        // The id the token was issued under, not the one this build would ask
        // for: Garmin refuses a refresh that names a different client.
        val clientId = credentials[GarminProtocol.TOKEN_CLIENT_ID].orEmpty()
        val refreshToken = credentials[GarminProtocol.REFRESH_TOKEN].orEmpty()
        if (clientId.isBlank() || refreshToken.isBlank()) return null
        val response =
            runCatching {
                formRequest(
                    GarminSignIn.TOKEN_URL,
                    GarminSignIn.refreshFields(clientId, refreshToken),
                    GarminSignIn.tokenHeaders(clientId),
                )
            }.getOrElse {
                onFailure("OAuth: ${ServiceDiagnostics.network(it)}")
                return null
            }
        val tokens = GarminSignIn.tokensFrom(response.body, clientId, now)
        if (response.statusCode !in 200..299 || tokens.isEmpty()) {
            onFailure("OAuth: ${ServiceDiagnostics.http(response.statusCode, response.body)}")
            return null
        }
        return tokens
    }

    private fun classify(response: HttpUpload.Response): UploadResult =
        GarminProtocol
            .classify(response.statusCode)
            .withDiagnostic(ServiceDiagnostics.http(response.statusCode, response.body))

    private companion object {
        val ACCESS_TOKEN = CredentialField(GarminProtocol.ACCESS_TOKEN, secret = true)
        const val MILLIS_PER_SECOND = 1000L
        const val UNAUTHORIZED = 401
    }
}
