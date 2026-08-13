package com.dchernykh.trainingrecorder.wear

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Placeholder instrumented test. It keeps the androidTest source set wired up so
 * the emulator gate has something to run, and it fails loudly if the watch and
 * phone APKs ever stop sharing one applicationId - the condition Google Play uses
 * to pair them into a single app.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @Test
    fun appContextHasTheSharedApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.dchernykh.trainingrecorder", context.packageName)
    }
}
