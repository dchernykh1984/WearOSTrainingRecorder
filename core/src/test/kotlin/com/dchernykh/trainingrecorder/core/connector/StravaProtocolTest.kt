package com.dchernykh.trainingrecorder.core.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StravaProtocolTest {
    @Test
    fun theAppAsksForItsOwnCredentialsRatherThanShippingAny() {
        assertEquals(listOf("client_id", "client_secret"), StravaProtocol.credentialFields.map { it.key })
        assertTrue(StravaProtocol.credentialFields.single { it.key == "client_secret" }.secret)
    }

    @Test
    fun theRedirectIsALoopbackSoEveryRiderRegistersTheSameCallback() {
        assertEquals("http://localhost:8080/exchange_token", StravaProtocol.redirectUri(8080))
    }

    @Test
    fun anImpossiblePortIsRejected() {
        assertFailsWith<IllegalArgumentException> { StravaProtocol.redirectUri(80) }
        assertFailsWith<IllegalArgumentException> { StravaProtocol.redirectUri(70_000) }
    }

    @Test
    fun theAuthorizeUrlCarriesEverythingStravaNeeds() {
        val url = StravaProtocol.authorizeUrl(clientId = "12345", port = 8080, state = "abc")
        assertTrue(url.startsWith(StravaProtocol.AUTHORIZE_URL))
        assertTrue(url.contains("client_id=12345"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=abc"))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fexchange_token"))
        assertTrue(url.contains("scope=activity%3Awrite%2Cread"))
    }

    @Test
    fun anEmptyClientIdOrStateIsRejected() {
        assertFailsWith<IllegalArgumentException> { StravaProtocol.authorizeUrl("", 8080, "abc") }
        assertFailsWith<IllegalArgumentException> { StravaProtocol.authorizeUrl("1", 8080, " ") }
    }

    @Test
    fun theCodeIsReadBackFromTheCallback() {
        val callback = "http://localhost:8080/exchange_token?state=abc&code=xyz789&scope=activity:write"
        assertEquals("xyz789", StravaProtocol.codeFrom(callback, expectedState = "abc"))
    }

    @Test
    fun aCallbackWithTheWrongStateIsRefused() {
        val callback = "http://localhost:8080/exchange_token?state=someone_else&code=xyz789"
        assertNull(
            StravaProtocol.codeFrom(callback, expectedState = "abc"),
            "a mismatched state means the callback is not ours",
        )
    }

    @Test
    fun aCallbackWithoutACodeYieldsNothing() {
        assertNull(StravaProtocol.codeFrom("http://localhost:8080/exchange_token?state=abc", "abc"))
        assertNull(StravaProtocol.codeFrom("http://localhost:8080/exchange_token", "abc"))
        assertNull(StravaProtocol.codeFrom("http://localhost:8080/exchange_token?state=abc&code=", "abc"))
    }

    @Test
    fun anEncodedCallbackValueIsDecoded() {
        val callback = "http://localhost:8080/exchange_token?state=a%2Bb&code=xyz"
        assertEquals("xyz", StravaProtocol.codeFrom(callback, expectedState = "a+b"))
    }

    @Test
    fun theUploadNamesTheSportStravaUnderstands() {
        val fields = StravaProtocol.uploadFields("cycling_gravel", "Morning Ride")
        assertEquals("fit", fields["data_type"])
        assertEquals("GravelRide", fields["sport_type"])
        assertEquals("Morning Ride", fields["name"])
        assertNull(fields["trainer"])
    }

    @Test
    fun indoorAndCommuteBecomeFlagsBecauseThatIsHowStravaModelsThem() {
        assertEquals("1", StravaProtocol.uploadFields("cycling_indoor", "Trainer")["trainer"])
        assertEquals("Ride", StravaProtocol.uploadFields("cycling_indoor", "Trainer")["sport_type"])
        assertEquals("1", StravaProtocol.uploadFields("cycling_commute", "To work")["commute"])
    }

    @Test
    fun anUnknownSportStillProducesAnUploadableRequest() {
        val fields = StravaProtocol.uploadFields("no_such_sport", "Something")
        assertEquals("fit", fields["data_type"])
        assertNull(fields["sport_type"], "better to let Strava guess than to send a made-up type")
    }

    @Test
    fun successStatusesEndTheQueueEntry() {
        assertTrue(StravaProtocol.classify(200) is UploadResult.Success)
        assertTrue(StravaProtocol.classify(201) is UploadResult.Success)
    }

    @Test
    fun rateLimitsAndServerErrorsAreWorthRetrying() {
        assertTrue(StravaProtocol.classify(429) is UploadResult.Retryable)
        assertTrue(StravaProtocol.classify(500) is UploadResult.Retryable)
        assertTrue(StravaProtocol.classify(503) is UploadResult.Retryable)
    }

    @Test
    fun anExpiredTokenIsRetryableBecauseItCanBeRefreshed() {
        assertTrue(StravaProtocol.classify(401) is UploadResult.Retryable)
    }

    @Test
    fun aDuplicateOrRefusedFileIsNeverRetried() {
        assertTrue(StravaProtocol.classify(400, "duplicate of activity 123") is UploadResult.Rejected)
        assertTrue(StravaProtocol.classify(400, "malformed") is UploadResult.Rejected)
        assertTrue(StravaProtocol.classify(403) is UploadResult.Rejected)
    }
}
