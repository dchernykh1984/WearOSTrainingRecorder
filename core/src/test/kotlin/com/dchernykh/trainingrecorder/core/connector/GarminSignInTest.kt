package com.dchernykh.trainingrecorder.core.connector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GarminSignInTest {
    private val now = 1_770_000_000L

    private fun field(
        body: String,
        key: String,
    ): String? = ((Json.parseToJsonElement(body) as JsonObject)[key] as? JsonPrimitive)?.content

    @Test
    fun `asks for the ticket the exchange will present`() {
        // A ticket is bound to the service it was issued for, so the two calls
        // have to name the same one or the exchange is refused.
        assertEquals(GarminSignIn.SERVICE_URL, GarminSignIn.signInQuery()["service"])
        assertEquals(GarminSignIn.SERVICE_URL, GarminSignIn.exchangeFields("id", "ST-1")["service_url"])
    }

    @Test
    fun `sends the credentials as json the mobile endpoint accepts`() {
        val body = GarminSignIn.signInBody("rider@example.com", "hunter2")

        assertEquals("rider@example.com", field(body, "username"))
        assertEquals("hunter2", field(body, "password"))
        assertEquals("true", field(body, "rememberMe"))
        assertEquals("", field(body, "captchaToken"))
        assertEquals("application/json", GarminSignIn.signInHeaders()["Content-Type"])
    }

    @Test
    fun `reads the service ticket out of a successful sign-in`() {
        val body = """{"responseStatus":{"type":"SUCCESSFUL"},"serviceTicketId":"ST-12345-abc"}"""

        val outcome = assertIs<SignInOutcome.Ticket>(GarminSignIn.outcomeFrom(body))

        assertEquals("ST-12345-abc", outcome.serviceTicketId)
    }

    @Test
    fun `reports a second factor with the method garmin used`() {
        val body =
            """{"responseStatus":{"type":"MFA_REQUIRED"},"customerMfaInfo":{"mfaLastMethodUsed":"sms"}}"""

        val outcome = assertIs<SignInOutcome.MfaRequired>(GarminSignIn.outcomeFrom(body))

        assertEquals("sms", outcome.method)
    }

    @Test
    fun `falls back to email when garmin does not say how it challenged`() {
        val outcome =
            assertIs<SignInOutcome.MfaRequired>(
                GarminSignIn.outcomeFrom("""{"responseStatus":{"type":"MFA_REQUIRED"}}"""),
            )

        assertEquals("email", outcome.method)
    }

    @Test
    fun `tells a wrong password apart from a refusal`() {
        val wrong = GarminSignIn.outcomeFrom("""{"responseStatus":{"type":"INVALID_USERNAME_PASSWORD"}}""")
        val captcha = GarminSignIn.outcomeFrom("""{"responseStatus":{"type":"CAPTCHA_REQUIRED"}}""")

        assertIs<SignInOutcome.InvalidCredentials>(wrong)
        assertIs<SignInOutcome.Refused>(captcha)
    }

    /**
     * What a Cloudflare challenge looks like. Reporting it as a wrong password
     * would send the rider off to reset a password that is perfectly fine.
     */
    @Test
    fun `treats a page that is not json as a refusal rather than a bad password`() {
        val outcome = GarminSignIn.outcomeFrom("<html><body>Just a moment...</body></html>")

        assertIs<SignInOutcome.Refused>(outcome)
    }

    @Test
    fun `refuses a success that carries no ticket`() {
        assertIs<SignInOutcome.Refused>(GarminSignIn.outcomeFrom("""{"responseStatus":{"type":"SUCCESSFUL"}}"""))
    }

    @Test
    fun `echoes the code and the method back when verifying a second factor`() {
        val body = GarminSignIn.mfaBody("123456", "sms")

        assertEquals("123456", field(body, "mfaVerificationCode"))
        assertEquals("sms", field(body, "mfaMethod"))
        assertEquals("true", field(body, "rememberMyBrowser"))
    }

    @Test
    fun `sends the client id in the form and in the basic header`() {
        val headers = GarminSignIn.tokenHeaders("GARMIN_TEST_DI")

        assertEquals("GARMIN_TEST_DI", GarminSignIn.exchangeFields("GARMIN_TEST_DI", "ST-1")["client_id"])
        assertEquals(
            "Basic " + Base64.getEncoder().encodeToString("GARMIN_TEST_DI:".toByteArray()),
            headers["Authorization"],
        )
        assertEquals("application/x-www-form-urlencoded", headers["Content-Type"])
        // The token call is the Android app's, so it carries the app's own
        // identity - the sign-in before it is the iOS endpoint's.
        assertEquals("GCM-Android-5.23", headers["User-Agent"])
    }

    @Test
    fun `turns a lifetime into the moment the token expires`() {
        val body = """{"access_token":"opaque","refresh_token":"r1","expires_in":3600}"""

        val tokens = GarminSignIn.tokensFrom(body, "GARMIN_TEST_DI", now)

        assertEquals("opaque", tokens[GarminProtocol.ACCESS_TOKEN])
        assertEquals("r1", tokens[GarminProtocol.REFRESH_TOKEN])
        assertEquals((now + 3600).toString(), tokens[GarminProtocol.EXPIRES_AT])
        assertEquals("GARMIN_TEST_DI", tokens[GarminProtocol.TOKEN_CLIENT_ID])
    }

    /**
     * Garmin issues under an id of its own choosing, and a refresh that names
     * the one we asked for instead of the one we got is refused - which would
     * strand the rider at the first expiry.
     */
    @Test
    fun `remembers the client id the token was actually issued under`() {
        val payload =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"client_id":"GARMIN_ISSUED_DI"}""".toByteArray())
        val jwt = "header.$payload.signature"

        val tokens = GarminSignIn.tokensFrom("""{"access_token":"$jwt"}""", "GARMIN_ASKED_DI", now)

        assertEquals("GARMIN_ISSUED_DI", tokens[GarminProtocol.TOKEN_CLIENT_ID])
    }

    @Test
    fun `keeps the id it asked for when the token says nothing`() {
        val tokens = GarminSignIn.tokensFrom("""{"access_token":"opaque"}""", "GARMIN_ASKED_DI", now)

        assertEquals("GARMIN_ASKED_DI", tokens[GarminProtocol.TOKEN_CLIENT_ID])
    }

    @Test
    fun `reads nothing out of an answer with no token in it`() {
        assertTrue(GarminSignIn.tokensFrom("""{"error":"invalid_grant"}""", "id", now).isEmpty())
        assertTrue(GarminSignIn.tokensFrom("not json", "id", now).isEmpty())
    }

    @Test
    fun `refreshes only when there is something to refresh with and a reason to`() {
        val fresh =
            mapOf(
                GarminProtocol.ACCESS_TOKEN to "a",
                GarminProtocol.REFRESH_TOKEN to "r",
                GarminProtocol.EXPIRES_AT to (now + 3600).toString(),
            )

        assertFalse(GarminSignIn.needsRefresh(fresh, now))
        assertTrue(GarminSignIn.needsRefresh(fresh + (GarminProtocol.EXPIRES_AT to now.toString()), now))
        // Nothing to trade: spending a request to discover that helps nobody.
        assertFalse(GarminSignIn.needsRefresh(mapOf(GarminProtocol.ACCESS_TOKEN to "a"), now))
        // Never had a token, but can get one.
        assertTrue(GarminSignIn.needsRefresh(mapOf(GarminProtocol.REFRESH_TOKEN to "r"), now))
    }

    @Test
    fun `refreshes before the token expires rather than after`() {
        val about =
            mapOf(
                GarminProtocol.ACCESS_TOKEN to "a",
                GarminProtocol.REFRESH_TOKEN to "r",
                GarminProtocol.EXPIRES_AT to (now + 60).toString(),
            )

        assertTrue(GarminSignIn.needsRefresh(about, now))
    }

    @Test
    fun `asks for a refresh in the shape the service expects`() {
        val fields = GarminSignIn.refreshFields("GARMIN_TEST_DI", "r1")

        assertEquals("refresh_token", fields["grant_type"])
        assertEquals("r1", fields["refresh_token"])
        assertEquals("GARMIN_TEST_DI", fields["client_id"])
    }

    @Test
    fun `carries more than one client id so a retired one is not the end`() {
        assertTrue(GarminSignIn.CLIENT_IDS.size > 1)
        assertEquals(GarminSignIn.CLIENT_IDS, GarminSignIn.CLIENT_IDS.distinct())
    }
}
