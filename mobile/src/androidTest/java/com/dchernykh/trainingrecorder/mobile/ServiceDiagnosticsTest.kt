package com.dchernykh.trainingrecorder.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.mobile.connect.GarminAuthorization
import com.dchernykh.trainingrecorder.mobile.connect.StravaAuthorization
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SettingsPublisher
import com.dchernykh.trainingrecorder.mobile.ui.ServiceConnections
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** The original bug was at this boundary: the transport knew why, the screen did not. */
@RunWith(AndroidJUnit4::class)
class ServiceDiagnosticsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aGarminFailureReachesTheScreenAndTheNextAttemptClearsIt() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            var refused = true
            val connections =
                ServiceConnections(
                    PhoneSettingsStore(File(folder.root, "settings.json"), File(folder.root, "credentials.json")),
                    SettingsPublisher(context),
                    StravaAuthorization(context),
                    GarminAuthorization { _, _, _ ->
                        if (refused) {
                            GarminAuthorization.Response(429, """{"responseStatus":{"type":"CAPTCHA_REQUIRED"}}""")
                        } else {
                            GarminAuthorization.Response(200, """{"responseStatus":{"type":"MFA_REQUIRED"}}""")
                        }
                    },
                )
            connections.updateCredential(GarminProtocol.ID, GarminProtocol.LOGIN, "rider")
            connections.updateCredential(GarminProtocol.ID, GarminProtocol.PASSWORD, "password")
            connections.connect(GarminProtocol.ID)
            assertEquals(R.string.connect_failed, connections.statusFor(GarminProtocol.ID))
            assertEquals("SSO: HTTP 429; CAPTCHA_REQUIRED", connections.diagnosticFor(GarminProtocol.ID))
            refused = false
            connections.connect(GarminProtocol.ID)
            assertEquals(null, connections.diagnosticFor(GarminProtocol.ID))
            assertEquals(R.string.connect_code_sent, connections.statusFor(GarminProtocol.ID))
        }
}
