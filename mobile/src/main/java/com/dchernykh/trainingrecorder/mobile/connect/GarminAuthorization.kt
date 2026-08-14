package com.dchernykh.trainingrecorder.mobile.connect

import com.dchernykh.trainingrecorder.core.connector.GarminSignIn
import com.dchernykh.trainingrecorder.core.connector.SignInOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** How a Garmin sign-in ended, in terms the setup screen can act on. */
sealed interface GarminAuthResult {
    /** Access token, refresh token, expiry and the client id they were issued under. */
    data class Authorized(
        val tokens: Map<String, String>,
    ) : GarminAuthResult

    /** Garmin sent a code; [method] is how, so the screen can say where to look. */
    data class NeedsCode(
        val method: String,
    ) : GarminAuthResult

    /** The login or the password is wrong, which is worth saying in those words. */
    data object WrongCredentials : GarminAuthResult

    /** Anything else: a captcha, a rate limit, a network that was not there. */
    data class Failed(
        val reason: String,
    ) : GarminAuthResult
}

/**
 * Signs in to Garmin Connect with the rider's own login and password.
 *
 * On the phone, never the watch. The service ticket in the middle of this is
 * single-use and expires in seconds, so it has to be spent immediately - a watch
 * that might not see a network until the evening is the wrong place to hold one.
 * Only the resulting tokens travel over the Data Layer.
 *
 * What is sent and what an answer means lives in [GarminSignIn], where it is
 * unit-tested; this is the transport, the cookie the two-step MFA exchange needs
 * to keep, and the loop over Garmin's client ids.
 *
 * Expect this to be the fragile part of the app. The Python library this is
 * ported from imitates a browser's TLS fingerprint on purpose, which says
 * Garmin inspects the handshake before it reads a password. If sign-ins fail
 * with a page that is not JSON, that is what happened, and there is nothing in
 * the response that will say so.
 */
class GarminAuthorization {
    /**
     * The sign-in and its second factor are two requests that have to look like
     * one session, so whatever Garmin set on the first is sent back on the
     * second. Held on the instance because the code arrives by SMS or email
     * minutes later, with a screen in between.
     */
    private var cookies: String = ""
    private var mfaMethod: String = "email"

    suspend fun signIn(
        login: String,
        password: String,
    ): GarminAuthResult =
        withContext(Dispatchers.IO) {
            cookies = ""
            val response =
                runCatching {
                    postJson(
                        url = GarminSignIn.SIGN_IN_URL,
                        query = GarminSignIn.signInQuery(),
                        headers = GarminSignIn.signInHeaders(),
                        body = GarminSignIn.signInBody(login, password),
                    )
                }.getOrElse { return@withContext GarminAuthResult.Failed("network: ${it.message}") }
            interpret(response.body)
        }

    /**
     * Answers the second factor.
     *
     * Sent to the same endpoint family with the same session cookie, and answers
     * in the same shape as the sign-in - so the outcome goes through exactly the
     * same reading.
     */
    suspend fun submitCode(code: String): GarminAuthResult =
        withContext(Dispatchers.IO) {
            val response =
                runCatching {
                    postJson(
                        url = GarminSignIn.MFA_VERIFY_URL,
                        query = GarminSignIn.signInQuery(),
                        headers = GarminSignIn.signInHeaders(),
                        body = GarminSignIn.mfaBody(code, mfaMethod),
                    )
                }.getOrElse { return@withContext GarminAuthResult.Failed("network: ${it.message}") }
            interpret(response.body)
        }

    private fun interpret(body: String): GarminAuthResult =
        when (val outcome = GarminSignIn.outcomeFrom(body)) {
            is SignInOutcome.Ticket -> exchange(outcome.serviceTicketId)
            is SignInOutcome.MfaRequired -> {
                mfaMethod = outcome.method
                GarminAuthResult.NeedsCode(outcome.method)
            }

            is SignInOutcome.InvalidCredentials -> GarminAuthResult.WrongCredentials
            is SignInOutcome.Refused -> GarminAuthResult.Failed(outcome.reason)
        }

    /**
     * Trades the ticket for tokens, trying each client id in turn.
     *
     * Garmin retires these as its own apps are updated and refuses a retired
     * one outright, so the first that answers wins. The ticket survives a
     * refusal - it is consumed by the exchange that succeeds, not by one that is
     * turned away at the door.
     */
    private fun exchange(serviceTicketId: String): GarminAuthResult {
        GarminSignIn.CLIENT_IDS.forEach { clientId ->
            val body =
                runCatching {
                    postForm(
                        url = GarminSignIn.TOKEN_URL,
                        headers = GarminSignIn.tokenHeaders(clientId),
                        fields = GarminSignIn.exchangeFields(clientId, serviceTicketId),
                    ).body
                }.getOrNull() ?: return@forEach
            val tokens = GarminSignIn.tokensFrom(body, clientId, System.currentTimeMillis() / MILLIS_PER_SECOND)
            if (tokens.isNotEmpty()) return GarminAuthResult.Authorized(tokens)
        }
        return GarminAuthResult.Failed("garmin would not issue a token")
    }

    private data class Response(
        val statusCode: Int,
        val body: String,
    )

    private fun postJson(
        url: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        body: String,
    ): Response = post(withQuery(url, query), headers, body.toByteArray())

    private fun postForm(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
    ): Response {
        val encoded = fields.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return post(url, headers, encoded.toByteArray())
    }

    private fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            // Bounded, so a service that accepts the connection and then says
            // nothing ends as a failure the rider can retry rather than a screen
            // that says "connecting" until they force-quit.
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (cookies.isNotEmpty()) connection.setRequestProperty("Cookie", cookies)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            rememberCookies(connection)
            val stream = if (status in SUCCESS_RANGE) connection.inputStream else connection.errorStream
            Response(status, stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Keeps the session Garmin hands out at sign-in.
     *
     * Only the name and value: the attributes describe a browser's storage
     * rules, and sending them back is neither expected nor meaningful here.
     */
    private fun rememberCookies(connection: HttpURLConnection) {
        val received =
            connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values
                .flatten()
                .map { it.substringBefore(';') }
        if (received.isNotEmpty()) cookies = received.joinToString("; ")
    }

    private fun withQuery(
        url: String,
        query: Map<String, String>,
    ): String = url + "?" + query.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MILLIS_PER_SECOND = 1000L
        val SUCCESS_RANGE = 200..299
    }
}
