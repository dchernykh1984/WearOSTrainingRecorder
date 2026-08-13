package com.dchernykh.trainingrecorder.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Placeholder instrumented test. See the wear module's SmokeTest: both APKs must
 * keep reporting the same applicationId.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @Test
    fun appContextHasTheSharedApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.dchernykh.trainingrecorder", context.packageName)
    }
}
