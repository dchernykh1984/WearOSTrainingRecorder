package com.dchernykh.trainingrecorder.core.connector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

/** How a sign-in attempt ended, in terms the setup screen can act on. */
sealed interface SignInOutcome {
    /**
     * Garmin accepted the credentials and issued a service ticket, which is
     * single-use and short-lived - it is traded for tokens immediately.
     */
    data class Ticket(
        val serviceTicketId: String,
    ) : SignInOutcome

    /**
     * A second factor is needed. [method] is how Garmin says it sent the code,
     * and has to be echoed back with the code itself.
     */
    data class MfaRequired(
        val method: String,
    ) : SignInOutcome

    /** The login or the password is wrong. Retrying the same pair will not help. */
    data object InvalidCredentials : SignInOutcome

    /**
     * Garmin answered, but not with a decision about the credentials - a
     * captcha, a rate limit, or a shape this does not recognise. Distinct from
     * [InvalidCredentials] because the rider's password is probably fine and
     * telling them otherwise sends them off to reset it for nothing.
     */
    data class Refused(
        val reason: String,
    ) : SignInOutcome
}

/**
 * The Garmin sign-in, ported from the Python `garminconnect` library.
 *
 * Three steps. The mobile SSO endpoint takes the rider's login and password and
 * answers with a service ticket; the ticket is exchanged at Garmin's DI OAuth2
 * service for an access token and a refresh token; the access token uploads.
 *
 * The mobile flow rather than the SSO web widget the earlier notes described.
 * The widget means posting an HTML form, scraping a ticket out of a JavaScript
 * redirect and carrying a CSRF token and cookies between two requests; the
 * mobile endpoint is a JSON request with a JSON answer. Both exist in the Python
 * library, which tries the mobile one first for the same reason.
 *
 * Everything here is a pure function over strings: what to send, and what an
 * answer means. That is deliberate - it is the half that can be tested on a
 * machine with no network and no Garmin account, which is the only half that
 * could have been verified while this was written.
 */
object GarminSignIn {
    private const val SSO = "https://sso.garmin.com"

    const val SIGN_IN_URL = "$SSO/mobile/api/login"
    const val MFA_VERIFY_URL = "$SSO/mobile/api/mfa/verifyCode"
    const val TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token"

    /**
     * The service the ticket is issued for. It has to be identical in the
     * sign-in and in the exchange - a ticket is bound to the service it was
     * asked for, and a mismatch is refused with nothing that says so.
     */
    const val SERVICE_URL = "https://mobile.integration.garmin.com/gcm/ios"

    private const val SSO_CLIENT_ID = "GCM_IOS_DARK"

    private const val GRANT_TYPE_SERVICE_TICKET =
        "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"

    /**
     * Tried in order until one answers.
     *
     * Garmin retires these as its apps are updated, and an id that has been
     * retired is refused outright. Carrying the list is what stops a rider being
     * locked out by a rotation the app knows nothing about; the one that worked
     * is remembered, because a refresh has to name the same id.
     */
    val CLIENT_IDS =
        listOf(
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI",
            "GARMIN_CONNECT_MOBILE_IOS_DI",
        )

    private val json = Json { ignoreUnknownKeys = true }

    /** Query the sign-in is posted with; Garmin refuses a bare POST. */
    fun signInQuery(): Map<String, String> =
        mapOf(
            "clientId" to SSO_CLIENT_ID,
            "locale" to "en-US",
            "service" to SERVICE_URL,
        )

    /**
     * Headers for the sign-in itself.
     *
     * An iPhone user agent, because the endpoint being asked is the one the iOS
     * app uses. The native Android headers come later, with the token exchange.
     */
    fun signInHeaders(): Map<String, String> =
        mapOf(
            "User-Agent" to
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148",
            "Accept" to "application/json, text/plain, */*",
            "Content-Type" to "application/json",
            "Origin" to SSO,
        )

    fun signInBody(
        login: String,
        password: String,
    ): String =
        buildJsonObject {
            put("username", login)
            put("password", password)
            put("rememberMe", true)
            // Sent empty. The field is required; a value only exists when Garmin
            // has decided to challenge, which this app cannot answer anyway.
            put("captchaToken", "")
        }.toString()

    /** The second-factor code, echoed back with the method Garmin said it used. */
    fun mfaBody(
        code: String,
        method: String,
    ): String =
        buildJsonObject {
            put("mfaMethod", method)
            put("mfaVerificationCode", code)
            // So a rider is not challenged again on every ride's worth of
            // token refreshes.
            put("rememberMyBrowser", true)
            put("mfaSetup", false)
        }.toString()

    /**
     * What the sign-in - or the MFA verification, which answers in the same
     * shape - came back with.
     *
     * An unparsable body is [SignInOutcome.Refused] rather than a failure to
     * report: it is what a Cloudflare challenge page looks like, and the rider
     * needs to be told something other than "wrong password".
     */
    fun outcomeFrom(body: String): SignInOutcome {
        val root =
            runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: return SignInOutcome.Refused("garmin did not answer with json")
        val type = text(root["responseStatus"] as? JsonObject, "type")
        return when (type) {
            "SUCCESSFUL" ->
                text(root, "serviceTicketId")
                    ?.let { SignInOutcome.Ticket(it) }
                    ?: SignInOutcome.Refused("no service ticket in a successful sign-in")

            "MFA_REQUIRED" ->
                SignInOutcome.MfaRequired(
                    text(root["customerMfaInfo"] as? JsonObject, "mfaLastMethodUsed") ?: "email",
                )

            "INVALID_USERNAME_PASSWORD" -> SignInOutcome.InvalidCredentials
            else -> SignInOutcome.Refused(type ?: "garmin refused the sign-in")
        }
    }

    /**
     * The form that trades a service ticket for tokens.
     *
     * The grant type is a URL rather than a word, which is unusual but is what
     * the service expects.
     */
    fun exchangeFields(
        clientId: String,
        serviceTicketId: String,
    ): Map<String, String> =
        mapOf(
            "client_id" to clientId,
            "service_ticket" to serviceTicketId,
            "grant_type" to GRANT_TYPE_SERVICE_TICKET,
            "service_url" to SERVICE_URL,
        )

    /** The form that trades a refresh token for a fresh access token. */
    fun refreshFields(
        clientId: String,
        refreshToken: String,
    ): Map<String, String> =
        mapOf(
            "grant_type" to "refresh_token",
            "client_id" to clientId,
            "refresh_token" to refreshToken,
        )

    /**
     * Headers for both token calls.
     *
     * The client id is sent twice - once as HTTP Basic with an empty password,
     * once in the form. That is what the service expects; sending only one of
     * them is refused.
     */
    fun tokenHeaders(clientId: String): Map<String, String> =
        GarminProtocol.nativeHeaders() +
            mapOf(
                "Authorization" to basicAuth(clientId),
                "Accept" to "application/json,text/html;q=0.9,*/*;q=0.8",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cache-Control" to "no-cache",
            )

    private fun basicAuth(clientId: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$clientId:".toByteArray())

    /**
     * Everything a token response carries that outlives the request.
     *
     * Expiry is stored as the moment it happens rather than the lifetime it came
     * as: a duration is only meaningful next to the instant it was measured
     * from, and that instant is gone by the time anything reads it back.
     *
     * The client id is taken from inside the token when it is there, because
     * Garmin sometimes issues under a different one than was asked for, and a
     * refresh naming the id we asked for rather than the one we got is refused.
     */
    fun tokensFrom(
        body: String,
        requestedClientId: String,
        nowEpochSeconds: Long,
    ): Map<String, String> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyMap()
        val access = text(root, "access_token") ?: return emptyMap()
        return buildMap {
            put(GarminProtocol.ACCESS_TOKEN, access)
            text(root, "refresh_token")?.let { put(GarminProtocol.REFRESH_TOKEN, it) }
            text(root, "expires_in")?.toLongOrNull()?.let {
                put(GarminProtocol.EXPIRES_AT, (nowEpochSeconds + it).toString())
            }
            put(GarminProtocol.TOKEN_CLIENT_ID, clientIdWithin(access) ?: requestedClientId)
        }
    }

    /**
     * True when the stored token is spent, or close enough that an upload
     * started now would finish after it.
     *
     * Missing expiry is treated as still valid: Garmin's tokens are short, but
     * refreshing on a guess spends a request against protection that counts
     * them.
     */
    fun needsRefresh(
        credentials: Map<String, String>,
        nowEpochSeconds: Long,
    ): Boolean {
        if (credentials[GarminProtocol.REFRESH_TOKEN].isNullOrBlank()) return false
        if (credentials[GarminProtocol.ACCESS_TOKEN].isNullOrBlank()) return true
        val expiresAt = credentials[GarminProtocol.EXPIRES_AT]?.toLongOrNull()
        return expiresAt != null && nowEpochSeconds >= expiresAt - EXPIRY_MARGIN_SECONDS
    }

    /**
     * The `client_id` claim inside a JWT, or null if the token is not one.
     *
     * No signature check: this is not being trusted, it is being read back to
     * find out which id the issuer used.
     */
    private fun clientIdWithin(token: String): String? {
        val payload = token.split(".").getOrNull(1) ?: return null
        val padded = payload + "=".repeat((PAD - payload.length % PAD) % PAD)
        return runCatching {
            val decoded = String(Base64.getUrlDecoder().decode(padded))
            text(json.parseToJsonElement(decoded) as? JsonObject, "client_id")
        }.getOrNull()
    }

    private fun text(
        node: JsonObject?,
        key: String,
    ): String? = (node?.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    /** Refreshed this far before expiry, so a slow upload does not straddle it. */
    private const val EXPIRY_MARGIN_SECONDS = 300L
    private const val PAD = 4
}
