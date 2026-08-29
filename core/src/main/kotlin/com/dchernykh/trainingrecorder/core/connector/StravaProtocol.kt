package com.dchernykh.trainingrecorder.core.connector

import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

    /**
     * The narrowest set that does the job.
     *
     * `activity:write` creates an upload, `read` lists the rider's starred
     * segments, and `activity:read` is what lets a segment effort of theirs be
     * read back as the curve live segments race against.
     *
     * Deliberately not `read_all` or `activity:read_all`. Those would hand the
     * app every private segment and every private activity the rider owns, and
     * the only thing it would buy is live segments on climbs they have kept
     * private - which fall back to an even-paced comparison instead. A rider
     * cannot audit what an app does with a scope they granted, so the app asks
     * for less.
     */
    const val SCOPE = "activity:write,read,activity:read"

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

    /**
     * The form the token endpoint expects in exchange for an authorization code.
     *
     * The code is single-use and short-lived, so this runs on the phone the
     * moment the browser comes back - never on the watch, which may not see the
     * network for hours.
     */
    fun tokenRequestFields(
        clientId: String,
        clientSecret: String,
        code: String,
    ): Map<String, String> =
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "code" to code,
            "grant_type" to "authorization_code",
        )

    /**
     * The form that trades a refresh token for a fresh access token.
     *
     * Strava's access tokens last about six hours, so without this a rider who
     * connects in the morning finds the evening's ride rejected - and after ten
     * rejections the queue gives up and the workout is marked failed.
     */
    fun refreshRequestFields(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ): Map<String, String> =
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        )

    /**
     * Everything a token response carries that outlives the request.
     *
     * The refresh token is kept because Strava rotates it: the one that comes
     * back replaces the one that was sent, and dropping it strands the rider at
     * the next expiry with no way back but re-authorizing.
     */
    fun tokensFrom(body: String): Map<String, String> {
        val root =
            runCatching {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(body) as? JsonObject
            }.getOrNull() ?: return emptyMap()

        fun text(key: String): String? =
            (root[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

        return buildMap {
            text("access_token")?.let { put(ACCESS_TOKEN, it) }
            text("refresh_token")?.let { put(REFRESH_TOKEN, it) }
            text("expires_at")?.let { put(EXPIRES_AT, it) }
        }
    }

    /**
     * True when the stored token is past its life, or close enough that an
     * upload started now would finish after it. Missing expiry means unknown,
     * which is treated as still valid - a needless refresh costs a request the
     * rider's rate limit could spend better.
     */
    fun needsRefresh(
        credentials: Map<String, String>,
        nowEpochSeconds: Long,
    ): Boolean {
        // Nothing to refresh with: whatever the access token is, it is all there
        // will ever be, and spending a request to find that out helps nobody.
        if (credentials[REFRESH_TOKEN].isNullOrBlank()) return false
        if (credentials[ACCESS_TOKEN].isNullOrBlank()) return true
        val expiresAt = credentials[EXPIRES_AT]?.toLongOrNull()
        return expiresAt != null && nowEpochSeconds >= expiresAt - EXPIRY_MARGIN_SECONDS
    }

    /**
     * Says what recorded the ride, in the one place Strava will show it.
     *
     * Strava names the device from the manufacturer and product ids in the FIT
     * file, but only translates those numbers into a name through its own
     * device database, which a project gets into by arranging a mapping with
     * Strava directly. Ours writes 255 - the FIT "development" manufacturer -
     * because the alternative is borrowing some vendor's id and having the ride
     * show up as a Garmin Edge it was not recorded on.
     *
     * So the description, which is the only field on the upload endpoint that
     * carries free text. It is not the device line and does not pretend to be:
     * it reads as what it is, a note the recording app left on its own ride.
     *
     * Not translated, deliberately. It is a project name and a URL, it is read
     * by whoever follows the rider rather than by the rider, and a credit that
     * changes language with the watch's locale is a credit nobody can search
     * for.
     */
    const val CREDIT =
        "Recorded with WearOSTrainingRecorder\n" +
            "https://github.com/dchernykh1984/WearOSTrainingRecorder"

    /** Strava names the sport with `sport_type`, and models indoor as a flag. */
    fun uploadFields(
        sportTypeId: String,
        name: String,
    ): Map<String, String> {
        val sport = SportCatalogue.byId(sportTypeId)
        return buildMap {
            put("data_type", "fit")
            put("name", name)
            put("description", CREDIT)
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

    /** The credential keys, named once so both ends cannot drift apart. */
    const val ACCESS_TOKEN = "access_token"
    const val REFRESH_TOKEN = "refresh_token"
    const val EXPIRES_AT = "expires_at"
    const val CLIENT_ID = "client_id"
    const val CLIENT_SECRET = "client_secret"

    /** Refreshed this far before expiry, so a slow upload does not straddle it. */
    private const val EXPIRY_MARGIN_SECONDS = 600L

    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
    private const val UNAUTHORIZED = 401
    private const val TOO_MANY_REQUESTS = 429
    private val SUCCESS_RANGE = 200..299
    private val SERVER_ERROR_RANGE = 500..599
}
