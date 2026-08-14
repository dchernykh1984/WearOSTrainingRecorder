package com.dchernykh.trainingrecorder.wear.connector

import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.GarminSignIn
import com.dchernykh.trainingrecorder.core.connector.StorageConnector
import com.dchernykh.trainingrecorder.core.connector.UploadResult
import com.dchernykh.trainingrecorder.core.connector.WorkoutUpload
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
            val current = refreshed(credentials)
            val first = send(upload, current) ?: return@withContext UploadResult.Retryable("not signed in yet")
            // A rejection is the other way a token can be spent. The expiry says
            // when it should have run out, but a watch whose clock drifted, or a
            // session Garmin invalidated early, says nothing until it refuses -
            // and without this the queue would retry against a dead token until
            // it gave up and marked a perfectly good ride permanently failed.
            if (first != UNAUTHORIZED) return@withContext GarminProtocol.classify(first)
            val renewed = forceRefresh(current) ?: return@withContext GarminProtocol.classify(first)
            val second = send(upload, renewed) ?: return@withContext UploadResult.Retryable("not signed in yet")
            GarminProtocol.classify(second)
        }

    /** The status code, or null when there is no token to send. */
    private fun send(
        upload: WorkoutUpload,
        credentials: Map<String, String>,
    ): Int? {
        val token = credentials[GarminProtocol.ACCESS_TOKEN].orEmpty()
        if (token.isBlank()) return null
        return runCatching {
            HttpUpload
                .post(
                    url = GarminProtocol.UPLOAD_URL,
                    parts = listOf(Part.File("file", upload.fileName, upload.openStream)),
                    headers = GarminProtocol.apiHeaders(token),
                ).statusCode
        }.getOrElse { SERVICE_UNAVAILABLE }
    }

    /** A refresh asked for by a refusal rather than by the clock. */
    private fun forceRefresh(credentials: Map<String, String>): Map<String, String>? =
        exchangeRefreshToken(credentials)?.let { tokens -> (credentials + tokens).also(onTokensRefreshed) }

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
        if (!GarminSignIn.needsRefresh(credentials, System.currentTimeMillis() / MILLIS_PER_SECOND)) return null
        return exchangeRefreshToken(credentials)
    }

    /** Null when there is nothing to trade, or the trade was refused. */
    private fun exchangeRefreshToken(credentials: Map<String, String>): Map<String, String>? {
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        // The id the token was issued under, not the one this build would ask
        // for: Garmin refuses a refresh that names a different client.
        val clientId = credentials[GarminProtocol.TOKEN_CLIENT_ID].orEmpty()
        val refreshToken = credentials[GarminProtocol.REFRESH_TOKEN].orEmpty()
        if (clientId.isBlank() || refreshToken.isBlank()) return null
        return runCatching {
            HttpUpload.form(
                url = GarminSignIn.TOKEN_URL,
                fields = GarminSignIn.refreshFields(clientId, refreshToken),
                headers = GarminSignIn.tokenHeaders(clientId),
            )
        }.getOrNull()
            ?.let { GarminSignIn.tokensFrom(it, clientId, now) }
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        val ACCESS_TOKEN = CredentialField(GarminProtocol.ACCESS_TOKEN, secret = true)
        const val MILLIS_PER_SECOND = 1000L
        const val UNAUTHORIZED = 401

        /** What a network failure is reported as, so it classifies as retryable. */
        const val SERVICE_UNAVAILABLE = 503
    }
}
