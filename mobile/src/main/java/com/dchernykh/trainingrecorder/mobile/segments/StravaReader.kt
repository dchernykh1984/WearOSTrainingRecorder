package com.dchernykh.trainingrecorder.mobile.segments

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What a request came back with, whatever it came back with. */
data class ApiResponse(
    val statusCode: Int,
    val body: String,
) {
    val ok: Boolean get() = statusCode in SUCCESS_RANGE

    /**
     * True when Strava is asking for a pause rather than refusing.
     *
     * Worth telling apart because the answer is different: a rate limit means
     * stop for now and the segments already fetched are still good, while a
     * refusal means this particular segment will never come and the rest of the
     * refresh should carry on without it.
     */
    val rateLimited: Boolean get() = statusCode == TOO_MANY_REQUESTS

    val unauthorized: Boolean get() = statusCode == UNAUTHORIZED

    private companion object {
        val SUCCESS_RANGE = 200..299
        const val TOO_MANY_REQUESTS = 429
        const val UNAUTHORIZED = 401
    }
}

/**
 * The one request shape reading segments needs: a GET with a bearer token.
 *
 * An interface so the part above it - which requests, in what order, and what
 * to do when one is refused - can be tested without a network, which is the
 * whole of what is worth testing there.
 */
fun interface StravaReader {
    fun get(
        url: String,
        accessToken: String,
    ): ApiResponse
}

/**
 * Written against [HttpURLConnection] like the rest of the app's networking,
 * for the same reason: this is two dozen lines against a dependency that would
 * have to be carried, locked and updated for the life of the project.
 */
class HttpStravaReader : StravaReader {
    override fun get(
        url: String,
        accessToken: String,
    ): ApiResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in SUCCESS) connection.inputStream else connection.errorStream
            ApiResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (error: IOException) {
            // A refresh that cannot reach the network is not a failure worth
            // reporting to the rider: it is a phone on a train. Nothing is
            // stored, the last sync time stays where it was, and the next
            // trigger tries again.
            ApiResponse(NO_NETWORK, error.message.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val SUCCESS = 200..299
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000

        /** Not a status Strava can send, which is what makes it usable as one. */
        const val NO_NETWORK = 0
    }
}
