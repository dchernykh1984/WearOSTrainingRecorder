package com.dchernykh.trainingrecorder.core.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GarminProtocolTest {
    @Test
    fun theRidersOwnLoginIsWhatIsAskedForNotAnApplicationSecret() {
        // There is no client secret to ask for: the credentials are the rider's
        // own Garmin account, which is why the password never leaves the phone.
        assertEquals(listOf("login", "password"), GarminProtocol.credentialFields.map { it.key })
        assertTrue(GarminProtocol.credentialFields.single { it.key == "password" }.secret)
    }

    @Test
    fun bothEndsNameTheTokensTheSameWay() {
        // The phone writes these keys and the watch reads them; two spellings is
        // a failure with no symptom other than uploads that never begin.
        assertEquals("access_token", GarminProtocol.ACCESS_TOKEN)
        assertEquals("refresh_token", GarminProtocol.REFRESH_TOKEN)
        assertEquals("expires_at", GarminProtocol.EXPIRES_AT)
    }

    @Test
    fun theUploadCarriesTheBearerUnderTheIdentityItWasIssuedTo() {
        val headers = GarminProtocol.apiHeaders("token-value")

        assertEquals("Bearer token-value", headers["Authorization"])
        // A token issued to Garmin's Android app arriving under some other user
        // agent is exactly the mismatch bot protection exists to notice.
        assertEquals("GCM-Android-5.23", headers["User-Agent"])
        assertTrue(headers.containsKey("X-Garmin-User-Agent"))
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
