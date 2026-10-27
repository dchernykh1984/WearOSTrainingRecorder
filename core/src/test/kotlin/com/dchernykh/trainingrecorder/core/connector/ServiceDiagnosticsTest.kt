package com.dchernykh.trainingrecorder.core.connector

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException

class ServiceDiagnosticsTest {
    @Test
    fun garminAndStravaErrorCodesSurviveWithoutReturningTheResponseBody() {
        assertEquals(
            "HTTP 429; CAPTCHA_REQUIRED",
            ServiceDiagnostics.http(429, """{"responseStatus":{"type":"CAPTCHA_REQUIRED"},"password":"hidden"}"""),
        )
        assertEquals(
            "HTTP 400; invalid_grant; Application; client_secret; invalid",
            ServiceDiagnostics.http(
                400,
                """{"error":"invalid_grant","access_token":"hidden",
                    "errors":[{"resource":"Application","field":"client_secret","code":"invalid"}]}""",
            ),
        )
    }

    @Test
    fun htmlFreeTextAndNestedSecretsAreNeverEchoed() {
        assertEquals("HTTP 403", ServiceDiagnostics.http(403, "<html>ticket=secret</html>"))
        assertEquals("HTTP 502", ServiceDiagnostics.http(502, """{"error":"Bearer private-token"}"""))
        assertEquals("HTTP 400", ServiceDiagnostics.http(400, """{"error":{"token":"secret"}}"""))
        assertEquals("HTTP 503", ServiceDiagnostics.http(503, ""))
        assertEquals("HTTP 400", ServiceDiagnostics.http(400, """{"error":"null"}"""))
    }

    @Test
    fun aNetworkExceptionReportsItsKindWithoutEchoingAUrlContainingCredentials() {
        assertEquals("SocketTimeoutException", ServiceDiagnostics.network(SocketTimeoutException("https://secret")))
    }
}
