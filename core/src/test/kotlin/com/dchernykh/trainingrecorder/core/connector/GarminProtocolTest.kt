package com.dchernykh.trainingrecorder.core.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarminProtocolTest {
    @Test
    fun theTokenIsWhatIsAskedForBecauseTheSignInIsNotImplemented() {
        // Asking for a login the app cannot exchange for a token would leave the
        // rider with a connector that looks configured and never uploads.
        assertEquals(listOf(GarminProtocol.BEARER_TOKEN), GarminProtocol.credentialFields.map { it.key })
        assertTrue(GarminProtocol.credentialFields.single().secret)
    }

    @Test
    fun theRidersOwnLoginIsWhatTheSignInWouldAskForNotAnApplicationSecret() {
        assertEquals(listOf("login", "password"), GarminProtocol.signInFields.map { it.key })
        assertTrue(GarminProtocol.signInFields.single { it.key == "password" }.secret)
    }

    @Test
    fun bothEndsNameTheTokenTheSameWay() {
        // The phone writes this key and the watch reads it; two spellings is a
        // failure with no symptom other than uploads that never begin.
        assertEquals("bearer_token", GarminProtocol.BEARER_TOKEN)
    }

    @Test
    fun theSignInQueryCarriesTheWidgetParametersGarminInsistsOn() {
        val query = GarminProtocol.signInQuery()
        assertEquals("gauth-widget", query["id"])
        assertEquals("true", query["embedWidget"])
        assertEquals(GarminProtocol.SSO_EMBED_URL, query["service"])
        assertEquals(GarminProtocol.SSO_EMBED_URL, query["gauthHost"])
    }

    @Test
    fun theServiceTicketIsPulledOutOfTheRedirectScript() {
        val ticket = "ST-012345-abcXYZ-cas"
        val html = """<script>response_url = "https://connect.garmin.com/modern?ticket=$ticket";</script>"""
        assertEquals(ticket, GarminProtocol.ticketFrom(html))
    }

    @Test
    fun aTicketIsReadOutOfAnEscapedRedirectToo() {
        val html = """response_url = "https:\/\/connect.garmin.com\/modern?ticket=ST-99-zz-cas\\";"""
        assertEquals("ST-99-zz-cas", GarminProtocol.ticketFrom(html))
    }

    @Test
    fun aRefusedSignInHasNoTicket() {
        assertNull(GarminProtocol.ticketFrom("<html>Invalid username or password</html>"))
        assertNull(GarminProtocol.ticketFrom(""))
        assertNull(GarminProtocol.ticketFrom("ticket="))
    }

    @Test
    fun aSecondFactorPromptIsRecognisedRatherThanReadAsAWrongPassword() {
        val html = """<form>Enter your MFA code<input name="verificationCode"></form>"""
        assertTrue(GarminProtocol.needsMfa(html))
        assertFalse(GarminProtocol.needsMfa("<html>Invalid username or password</html>"))
    }

    @Test
    fun anAcceptedUploadEndsTheQueueEntry() {
        assertTrue(GarminProtocol.classify(200) is UploadResult.Success)
        assertTrue(GarminProtocol.classify(201) is UploadResult.Success)
    }

    @Test
    fun anActivityGarminAlreadyHoldsCountsAsDone() {
        assertTrue(
            GarminProtocol.classify(409) is UploadResult.Success,
            "the ride is there; asking again would change nothing",
        )
    }

    @Test
    fun anExpiredSessionIsRetryableBecauseSigningInAgainFixesIt() {
        assertTrue(GarminProtocol.classify(401) is UploadResult.Retryable)
        assertTrue(GarminProtocol.classify(403) is UploadResult.Retryable)
    }

    @Test
    fun rateLimitsAndServerErrorsBackOff() {
        assertTrue(GarminProtocol.classify(429) is UploadResult.Retryable)
        assertTrue(GarminProtocol.classify(500) is UploadResult.Retryable)
        assertTrue(GarminProtocol.classify(502) is UploadResult.Retryable)
    }

    @Test
    fun anythingElseIsPermanent() {
        assertTrue(GarminProtocol.classify(400) is UploadResult.Rejected)
        assertTrue(GarminProtocol.classify(415) is UploadResult.Rejected)
    }
}
