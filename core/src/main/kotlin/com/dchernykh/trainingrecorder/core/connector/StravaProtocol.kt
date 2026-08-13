package com.dchernykh.trainingrecorder.core.connector

import com.dchernykh.trainingrecorder.core.sport.SportCatalogue

/**
 * How the app talks to Strava, kept away from any HTTP client so the parts worth
 * testing - what is sent, and what a response means - can be tested at all.
 *
 * The app ships no credentials. Each rider registers their own Strava API
 * application and enters its client id and secret once, which keeps the developer
 * out of the blast radius entirely: a leaked shared secret lets anyone put your
 * application's name on a consent screen and harvest other people's tokens.
 */
object StravaProtocol {
    const val ID = "strava"

    const val AUTHORIZE_URL = "https://www.strava.com/oauth/authorize"
    const val TOKEN_URL = "https://www.strava.com/oauth/token"
    const val UPLOAD_URL = "https://www.strava.com/api/v3/uploads"

    /** `activity:write` is the narrowest scope that can create an upload. */
    const val SCOPE = "activity:write,read"

    val credentialFields =
        listOf(
            CredentialField("client_id"),
            CredentialField("client_secret", secret = true),
        )

    /**
     * A loopback redirect, per RFC 8252.
     *
     * Every rider registers their own application, so the callback cannot be
     * hardcoded to one domain - but `localhost` is the same string for everyone,
     * which keeps the setup instructions to a single line. It also beats a custom
     * scheme, which any other app on the device can claim and intercept.
     */
    fun redirectUri(port: Int): String {
        require(port in MIN_PORT..MAX_PORT) { "port must be $MIN_PORT..$MAX_PORT, got $port" }
        return "http://localhost:$port/exchange_token"
    }

    fun authorizeUrl(
        clientId: String,
        port: Int,
        state: String,
    ): String {
        require(clientId.isNotBlank()) { "a client id is required" }
        require(state.isNotBlank()) { "a state value is required to bind the callback to this request" }
        val redirect = encode(redirectUri(port))
        return "$AUTHORIZE_URL?client_id=${encode(clientId)}" +
            "&redirect_uri=$redirect" +
            "&response_type=code" +
            "&approval_prompt=auto" +
            "&scope=${encode(SCOPE)}" +
            "&state=${encode(state)}"
    }

    /**
     * Pulls the authorization code out of the loopback callback.
     *
     * The state must match the one sent, or the callback is not ours: without
     * that check any page the rider visits could hand us a code for someone
     * else's account.
     */
    fun codeFrom(
        callbackUrl: String,
        expectedState: String,
    ): String? {
        val query = callbackUrl.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return null
        val params =
            query
                .split('&')
                .mapNotNull {
                    val name = it.substringBefore('=', "")
                    val value = it.substringAfter('=', "")
                    if (name.isEmpty()) null else name to decode(value)
                }.toMap()
        if (params["state"] != expectedState) return null
        return params["code"]?.takeIf { it.isNotBlank() }
    }

    /** Strava names the sport with `sport_type`, and models indoor as a flag. */
    fun uploadFields(
        sportTypeId: String,
        name: String,
    ): Map<String, String> {
        val sport = SportCatalogue.byId(sportTypeId)
        return buildMap {
            put("data_type", "fit")
            put("name", name)
            sport?.let {
                put("sport_type", it.stravaSportType)
                if (it.stravaTrainer) put("trainer", "1")
                if (it.stravaCommute) put("commute", "1")
            }
        }
    }

    /**
     * What an HTTP status means for the queue.
     *
     * The split that matters: 4xx other than 429 is the rider's file or
     * credentials, and retrying only burns their rate limit; 429 and 5xx are the
     * service being busy or broken, which is exactly what backing off is for.
     */
    fun classify(
        statusCode: Int,
        body: String = "",
    ): UploadResult =
        when {
            statusCode in SUCCESS_RANGE -> UploadResult.Success()
            statusCode == TOO_MANY_REQUESTS -> UploadResult.Retryable("rate limited")
            statusCode in SERVER_ERROR_RANGE -> UploadResult.Retryable("strava error $statusCode")
            statusCode == UNAUTHORIZED -> UploadResult.Retryable("token expired")
            body.contains("duplicate", ignoreCase = true) -> UploadResult.Rejected("duplicate activity")
            else -> UploadResult.Rejected("strava refused the upload: $statusCode")
        }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decode(value: String): String = java.net.URLDecoder.decode(value, Charsets.UTF_8.name())

    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
    private const val UNAUTHORIZED = 401
    private const val TOO_MANY_REQUESTS = 429
    private val SUCCESS_RANGE = 200..299
    private val SERVER_ERROR_RANGE = 500..599
}
