package com.dchernykh.trainingrecorder.core.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialContractTest {
    private val credentials =
        mapOf(
            "strava" to mapOf("client_id" to "12345", "access_token" to "a-secret-token"),
            "garmin" to mapOf("bearer" to "another-secret"),
        )

    @Test
    fun aRoundTripKeepsEveryFieldOfEveryConnector() {
        assertEquals(credentials, CredentialContract.decode(CredentialContract.encode(credentials)))
    }

    @Test
    fun credentialsTravelOnTheirOwnPathAwayFromTheSettings() {
        // The whole point of the split: a settings dump must not carry tokens.
        assertEquals("/credentials", CredentialContract.PATH)
        assertFalse(CredentialContract.PATH == "/settings")
    }

    @Test
    fun anUnreadablePayloadIsNullRatherThanEmpty() {
        // Empty would look like "the rider connected nothing" and overwrite
        // working credentials; null lets the caller keep what it has.
        listOf("", "not json", "[]", "null").forEach {
            assertNull(CredentialContract.decode(it), "expected null for: $it")
        }
    }

    @Test
    fun anEmptyObjectMeansNoConnectorsRatherThanAFailure() {
        assertEquals(emptyMap(), CredentialContract.decode("{}"))
    }

    @Test
    fun nonStringValuesAreDroppedRatherThanCoerced() {
        // A number where a token belongs is a bug at the other end; sending it
        // as "12345" would produce a confusing 401 instead of an obvious gap.
        val decoded = CredentialContract.decode("""{"strava": {"client_id": 12345, "token": "x"}}""")
        assertEquals(mapOf("strava" to mapOf("token" to "x")), decoded)
    }

    @Test
    fun aConnectorWhoseEntryIsNotAnObjectIsSkipped() {
        val decoded = CredentialContract.decode("""{"strava": "nope", "garmin": {"bearer": "b"}}""")
        assertEquals(mapOf("garmin" to mapOf("bearer" to "b")), decoded)
    }

    @Test
    fun theRedactedFormShowsWhichKeysExistAndNeverTheirValues() {
        val redacted = CredentialContract.redact(credentials)
        assertEquals("strava=[access_token,client_id], garmin=[bearer]", redacted)
        assertFalse(redacted.contains("a-secret-token"))
        assertFalse(redacted.contains("another-secret"))
    }

    @Test
    fun anEncodedPayloadIsPlainJsonTheWatchCanRead() {
        val payload = CredentialContract.encode(mapOf("strava" to mapOf("token" to "t")))
        assertTrue(payload.startsWith("{") && payload.endsWith("}"))
        assertEquals(mapOf("strava" to mapOf("token" to "t")), CredentialContract.decode(payload))
    }
}
