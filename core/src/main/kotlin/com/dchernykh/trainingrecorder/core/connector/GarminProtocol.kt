package com.dchernykh.trainingrecorder.core.connector

/**
 * How the app talks to Garmin Connect.
 *
 * There is no official route. Garmin's Connect API is partner-approval-only and,
 * as of 2026, closed to new applicants, so this reproduces the sign-in the Python
 * `garminconnect` library performs: the SSO form yields a service ticket, the
 * ticket is exchanged for an OAuth1 token, and that is exchanged for an OAuth2
 * bearer used on the upload.
 *
 * Two things to know before debugging a failure here. The credentials are the
 * rider's own Garmin login and password - there is no client secret to leak, but
 * there is a password to keep encrypted. And the Python library depends on
 * `curl-cffi` specifically to imitate a browser's TLS fingerprint, which implies
 * bot protection that inspects the handshake before it ever reads a password. A
 * plain HTTP client may therefore be refused at the transport layer, with nothing
 * in the response to explain why.
 */
object GarminProtocol {
    const val ID = "garmin"

    const val SSO_URL = "https://sso.garmin.com/sso/signin"
    const val SSO_EMBED_URL = "https://sso.garmin.com/sso/embed"
    const val OAUTH1_PREVIEW_URL = "https://connectapi.garmin.com/oauth-service/oauth/preauthorized"
    const val OAUTH2_EXCHANGE_URL = "https://connectapi.garmin.com/oauth-service/oauth/exchange/user/2.0"
    const val UPLOAD_URL = "https://connectapi.garmin.com/upload-service/upload/.fit"

    val credentialFields =
        listOf(
            CredentialField("login"),
            CredentialField("password", secret = true),
        )

    /** Query the sign-in form is submitted with; Garmin rejects a bare POST. */
    fun signInQuery(): Map<String, String> =
        mapOf(
            "id" to "gauth-widget",
            "embedWidget" to "true",
            "gauthHost" to SSO_EMBED_URL,
            "service" to SSO_EMBED_URL,
            "source" to SSO_EMBED_URL,
            "redirectAfterAccountLoginUrl" to SSO_EMBED_URL,
            "redirectAfterAccountCreationUrl" to SSO_EMBED_URL,
        )

    /**
     * The service ticket, which the sign-in response embeds in a JavaScript
     * redirect rather than a header. Absent means the credentials were refused -
     * or that the request never reached the form at all.
     */
    fun ticketFrom(html: String): String? =
        TICKET
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }

    /** True when Garmin is asking for a second factor, which this app cannot answer. */
    fun needsMfa(html: String): Boolean = html.contains("MFA", ignoreCase = true) && html.contains("verificationCode")

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

    private val TICKET = Regex("""ticket=([^"'&\\]+)""")
    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val CONFLICT = 409
    private const val TOO_MANY_REQUESTS = 429
    private val SUCCESS_RANGE = 200..299
    private val SERVER_ERROR_RANGE = 500..599
}
