package com.dchernykh.trainingrecorder.mobile.connect

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/** How the authorization ended, in terms the setup screen can show. */
sealed interface AuthorizationResult {
    data class Authorized(
        /** Access token, refresh token and expiry, keyed as the protocol names them. */
        val tokens: Map<String, String>,
    ) : AuthorizationResult

    data class Failed(
        val reason: String,
    ) : AuthorizationResult
}

/**
 * Turns the rider's own Strava application into an access token the watch can
 * upload with.
 *
 * The redirect is a loopback socket, per RFC 8252. A custom scheme would be
 * shorter to write but any other app on the phone can register the same one and
 * intercept the code; `localhost` cannot be claimed by anyone else, and it is
 * also the same single line of setup for every rider, since each registers their
 * own application and no callback domain can be hardcoded.
 *
 * Only the phone ever does this. The code is single-use and expires in minutes,
 * so a watch that might not see the network until the evening is the wrong place
 * to hold one.
 */
class StravaAuthorization(
    private val context: Context,
) {
    /**
     * Opens the browser and waits for the redirect.
     *
     * Suspends until the rider approves or denies, so the caller shows a spinner
     * rather than guessing when to come back.
     */
    suspend fun authorize(
        clientId: String,
        clientSecret: String,
    ): AuthorizationResult =
        withContext(Dispatchers.IO) {
            // Bound before the browser opens: the authorize URL has to carry the
            // port, and asking the OS for a free one avoids colliding with
            // whatever else on the phone happens to be listening.
            val redirect =
                LoopbackRedirect.open()
                    ?: return@withContext AuthorizationResult.Failed("could not open a local port")
            redirect.use {
                // Bound to the request so a redirect the rider did not start -
                // an old link, another app - cannot inject a code of its own.
                val state = Random.nextLong().toString(RADIX)
                val opened = openBrowser(StravaProtocol.authorizeUrl(clientId, it.port, state))
                if (!opened) return@withContext AuthorizationResult.Failed("no browser to open")
                val callback =
                    it.awaitTarget(TIMEOUT_MS)
                        ?: return@withContext AuthorizationResult.Failed("the browser did not return")
                val code =
                    StravaProtocol.codeFrom(callback, state)
                        ?: return@withContext AuthorizationResult.Failed("authorization was declined")
                exchange(clientId, clientSecret, code)
            }
        }

    private fun openBrowser(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun exchange(
        clientId: String,
        clientSecret: String,
        code: String,
    ): AuthorizationResult {
        val fields = StravaProtocol.tokenRequestFields(clientId, clientSecret, code)
        val body =
            fields.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        val response =
            runCatching {
                val connection = URL(StravaProtocol.TOKEN_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                // Bounded, so a stalled network ends as a failure the rider can
                // retry rather than a screen that says "connecting" forever.
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.use { it.write(body) }
            }.getOrElse { return AuthorizationResult.Failed("the token exchange failed: ${it.message}") }
        val tokens = StravaProtocol.tokensFrom(response)
        if (tokens[StravaProtocol.ACCESS_TOKEN].isNullOrBlank()) {
            return AuthorizationResult.Failed("Strava did not return a token")
        }
        return AuthorizationResult.Authorized(tokens)
    }

    private fun HttpURLConnection.use(write: (OutputStreamWriter) -> Unit): String =
        try {
            OutputStreamWriter(outputStream).use(write)
            val stream = if (responseCode in SUCCESS_RANGE) inputStream else errorStream
            stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        } finally {
            disconnect()
        }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        /** Long enough for a rider to find their password, short enough to give up. */
        const val TIMEOUT_MS = 300_000L

        const val CONNECT_TIMEOUT_MS = 15_000

        const val READ_TIMEOUT_MS = 10_000

        const val RADIX = 36
        val SUCCESS_RANGE = 200..299
    }
}
