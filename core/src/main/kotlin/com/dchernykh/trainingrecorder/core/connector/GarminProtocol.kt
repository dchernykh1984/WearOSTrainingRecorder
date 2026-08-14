package com.dchernykh.trainingrecorder.core.connector

/**
 * How the app uploads to Garmin Connect, and what its answers mean.
 *
 * Getting a token to upload with is [GarminSignIn]; this is what happens once
 * there is one.
 *
 * There is no official route to any of it. Garmin's Connect API is
 * partner-approval-only and, as of 2026, closed to new applicants, so both
 * halves reproduce what the Python `garminconnect` library does. One thing to
 * know before debugging a failure: that library depends on `curl-cffi` to
 * imitate a browser's TLS fingerprint, which implies bot protection reading the
 * handshake before it reads anything else. A plain client may therefore be
 * refused at the transport layer, with nothing in the response to explain why.
 */
object GarminProtocol {
    const val ID = "garmin"

    const val UPLOAD_URL = "https://connectapi.garmin.com/upload-service/upload/.fit"

    /**
     * The credential keys, named once so both ends cannot drift apart - the
     * phone writing one spelling and the watch expecting another is invisible
     * until every upload silently declines to start.
     */
    const val ACCESS_TOKEN = "access_token"
    const val REFRESH_TOKEN = "refresh_token"
    const val EXPIRES_AT = "expires_at"

    /**
     * Which of Garmin's client ids issued the token. Kept because a refresh has
     * to name the same one, and [GarminSignIn] discovers it by trying several.
     */
    const val TOKEN_CLIENT_ID = "token_client_id"

    /**
     * What the rider supplies: their own Garmin login and password.
     *
     * There is no client secret to leak here - the credentials are the rider's
     * own - but there is a password, which is why it never leaves the phone.
     * Only the tokens travel to the watch.
     */
    val credentialFields =
        listOf(
            CredentialField(LOGIN, secret = false),
            CredentialField(PASSWORD, secret = true),
        )

    /**
     * What an upload status means for the queue.
     *
     * Garmin answers a file it already holds with 409, which is a success in
     * every sense that matters: the ride is there, and retrying would only ask
     * again.
     */
    fun classify(statusCode: Int): UploadResult =
        when {
            statusCode in SUCCESS_RANGE -> UploadResult.Success()
            statusCode == CONFLICT -> UploadResult.Success()
            statusCode == UNAUTHORIZED || statusCode == FORBIDDEN -> UploadResult.Retryable("session expired")
            statusCode == TOO_MANY_REQUESTS -> UploadResult.Retryable("rate limited")
            statusCode in SERVER_ERROR_RANGE -> UploadResult.Retryable("garmin error $statusCode")
            else -> UploadResult.Rejected("garmin refused the upload: $statusCode")
        }

    /**
     * The headers the mobile API expects alongside the bearer.
     *
     * The token was issued to Garmin's own Android app, so the request that
     * carries it says the same thing about itself. A bearer that arrives under a
     * user agent the issuer never heard of is the sort of mismatch bot
     * protection exists to notice.
     */
    fun apiHeaders(accessToken: String): Map<String, String> =
        nativeHeaders() +
            mapOf(
                "Authorization" to "Bearer $accessToken",
                "Accept" to "application/json",
                // Kept from the version that uploaded with a hand-pasted token,
                // which recorded that Garmin refuses an upload without it and
                // does not check the value. The Python client omits it on this
                // path, so it may well be unnecessary now - but the two answers
                // cost nothing and cannot be told apart from here, and the one
                // that is wrong fails every upload the rider ever makes.
                "NK" to "NT",
            )

    /** Shared with the token exchange, which sends them without a bearer. */
    internal fun nativeHeaders(): Map<String, String> =
        mapOf(
            "User-Agent" to "GCM-Android-5.23",
            "X-Garmin-User-Agent" to
                "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; " +
                "Android/33; Dalvik/2.1.0",
            "X-Garmin-Paired-App-Version" to "10861",
            "X-Garmin-Client-Platform" to "Android",
            "X-App-Ver" to "10861",
            "X-Lang" to "en",
            "X-GCExperience" to "GC5",
            "Accept-Language" to "en-US,en;q=0.9",
        )

    const val LOGIN = "login"
    const val PASSWORD = "password"

    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val CONFLICT = 409
    private const val TOO_MANY_REQUESTS = 429
    private val SUCCESS_RANGE = 200..299
    private val SERVER_ERROR_RANGE = 500..599
}
