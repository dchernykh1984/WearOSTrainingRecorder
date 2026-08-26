package com.dchernykh.trainingrecorder.mobile.segments

import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Trades a refresh token for a working access token.
 *
 * Strava's access tokens last about six hours and the phone only talks to
 * Strava when the rider is not looking, so by the time a refresh runs the token
 * from the morning is usually dead. The rotated refresh token that comes back
 * replaces the one that was sent - dropping it strands the rider at the next
 * expiry with nothing to renew from.
 */
class TokenRefresher {
    fun refresh(credentials: Map<String, String>): Map<String, String>? {
        val clientId = credentials[StravaProtocol.CLIENT_ID].orEmpty()
        val clientSecret = credentials[StravaProtocol.CLIENT_SECRET].orEmpty()
        val refreshToken = credentials[StravaProtocol.REFRESH_TOKEN].orEmpty()
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) return null
        val fields = StravaProtocol.refreshRequestFields(clientId, clientSecret, refreshToken)
        val body = fields.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val response = runCatching { post(body) }.getOrElse { return null }
        return StravaProtocol.tokensFrom(response).takeIf { it[StravaProtocol.ACCESS_TOKEN] != null }
    }

    @Throws(IOException::class)
    private fun post(body: String): String {
        val connection = URL(StravaProtocol.TOKEN_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            OutputStreamWriter(connection.outputStream).use { it.write(body) }
            val stream = if (connection.responseCode in SUCCESS) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        val SUCCESS = 200..299
    }
}
