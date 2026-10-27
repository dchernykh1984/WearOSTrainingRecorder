package com.dchernykh.trainingrecorder.mobile.connect

import com.dchernykh.trainingrecorder.core.connector.GarminSignIn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class GarminAuthorizationTest {
    @Test
    fun aRefusedSignInKeepsTheHttpStatusAndGarminCode() =
        runBlocking {
            val auth =
                GarminAuthorization { _, _, _ ->
                    GarminAuthorization.Response(429, """{"responseStatus":{"type":"CAPTCHA_REQUIRED"}}""")
                }
            val result = auth.signIn("rider", "password") as GarminAuthResult.Failed
            assertEquals("SSO: HTTP 429; CAPTCHA_REQUIRED", result.reason)
        }

    @Test
    fun aFailedMfaRequestKeepsItsHttpStatus() =
        runBlocking {
            val auth = GarminAuthorization { _, _, _ -> GarminAuthorization.Response(403, "<html>blocked</html>") }
            assertTrue((auth.submitCode("123456") as GarminAuthResult.Failed).reason.contains("HTTP 403"))
        }

    @Test
    fun everyRefusedTokenExchangeRemainsAvailableForDiagnosis() =
        runBlocking {
            val auth =
                GarminAuthorization { url, _, _ ->
                    if (url.startsWith(GarminSignIn.SIGN_IN_URL)) {
                        GarminAuthorization.Response(
                            200,
                            """{"responseStatus":{"type":"SUCCESSFUL"},"serviceTicketId":"secret"}""",
                        )
                    } else {
                        GarminAuthorization.Response(400, """{"error":"invalid_client"}""")
                    }
                }
            val result = auth.signIn("rider", "password") as GarminAuthResult.Failed
            GarminSignIn.CLIENT_IDS.forEach { assertTrue(result.reason.contains("$it: HTTP 400; invalid_client")) }
            assertTrue(!result.reason.contains("secret"))
        }

    @Test
    fun anOfflinePhoneReportsTheNetworkFailureKind() =
        runBlocking {
            val auth = GarminAuthorization { _, _, _ -> throw SocketTimeoutException("private URL") }
            assertEquals(
                "SSO: SocketTimeoutException",
                (auth.signIn("rider", "password") as GarminAuthResult.Failed).reason,
            )
        }

    @Test
    fun anHtmlChallengeWithHttp200ExplainsWhyTheSignInStillFailed() =
        runBlocking {
            val auth = GarminAuthorization { _, _, _ -> GarminAuthorization.Response(200, "<html>challenge</html>") }
            assertEquals(
                "SSO: HTTP 200; RESPONSE_NOT_JSON",
                (auth.signIn("rider", "password") as GarminAuthResult.Failed).reason,
            )
        }
}
