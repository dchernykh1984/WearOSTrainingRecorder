package com.dchernykh.trainingrecorder.mobile.settings

import com.dchernykh.trainingrecorder.core.connector.ReauthOutcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReauthDiagnosticsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = PhoneSettingsStore(File(folder.root, "settings.json"), File(folder.root, "credentials.json"))

    @Test
    fun theAutomaticFailureSurvivesReopeningAndManualRecoveryClearsIt() {
        store().writeReauth(123L, ReauthOutcome.FAILED, "SSO: HTTP 429")
        assertEquals(123L to ReauthOutcome.FAILED, store().readReauth())
        assertEquals("SSO: HTTP 429", store().readReauthDiagnostic())
        store().clearReauth()
        assertEquals(null, store().readReauthDiagnostic())
    }

    @Test
    fun theOldTwoFieldRecordIsStillReadable() {
        File(folder.root, "garmin-reauth.txt").writeText("123 needs_code")
        assertEquals(123L to ReauthOutcome.NEEDS_CODE, store().readReauth())
        assertEquals(null, store().readReauthDiagnostic())
    }
}
