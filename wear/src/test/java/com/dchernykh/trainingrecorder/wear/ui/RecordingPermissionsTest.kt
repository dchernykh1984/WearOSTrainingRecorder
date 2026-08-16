package com.dchernykh.trainingrecorder.wear.ui

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app asks for, at every release where the answer changes.
 *
 * These rules are not a formality. Missing background location does not fail
 * loudly: the watch simply stops handing over positions once the app leaves the
 * screen, which on a ride is almost immediately. The timer runs, the satellite
 * indicator stays green because availability is still reported, and the distance
 * never moves again. A rider covered two hundred metres and the ride recorded
 * ten - and nothing anywhere said why.
 */
class RecordingPermissionsTest {
    @Test
    fun backgroundLocationIsAskedForFromTheReleaseThatIntroducedIt() {
        assertTrue(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION in
                RecordingPermissions.backgroundFor(Build.VERSION_CODES.Q),
        )
        assertTrue(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION in
                RecordingPermissions.backgroundFor(RecordingPermissions.API_36),
        )
    }

    @Test
    fun backgroundPermissionsAreNeverBundledWithTheForegroundRequest() {
        // The platform drops a background request that arrives alongside
        // anything else, and drops it silently - so a bundled one leaves the
        // rider believing they granted something they did not.
        listOf(30, 33, RecordingPermissions.API_36).forEach { sdk ->
            val required = RecordingPermissions.requiredFor(sdk)
            RecordingPermissions.backgroundFor(sdk).forEach {
                assertFalse("$it must not be in the foreground batch on API $sdk", it in required)
            }
        }
    }

    @Test
    fun theyAreAskedForOneAtATimeAndInOrder() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        val all = RecordingPermissions.backgroundFor(sdk)
        assertEquals(all.first(), RecordingPermissions.nextBackground(emptySet(), sdk))
        assertEquals(all[1], RecordingPermissions.nextBackground(setOf(all.first()), sdk))
        assertNull(RecordingPermissions.nextBackground(all.toSet(), sdk))
    }

    @Test
    fun theWatchsOwnSensorNeedsBackgroundAccessOnlyWhereThatPermissionExists() {
        // Below 33 there is no such permission, and above 35 the granular health
        // permissions replace it. Asking anywhere else leaves it permanently
        // ungranted and the prompt reappearing on every launch.
        assertFalse(
            RecordingPermissions.BODY_SENSORS_BACKGROUND in
                RecordingPermissions.backgroundFor(Build.VERSION_CODES.R),
        )
        assertTrue(
            RecordingPermissions.BODY_SENSORS_BACKGROUND in
                RecordingPermissions.backgroundFor(Build.VERSION_CODES.TIRAMISU),
        )
        assertFalse(
            RecordingPermissions.BODY_SENSORS_BACKGROUND in
                RecordingPermissions.backgroundFor(RecordingPermissions.API_36),
        )
    }

    @Test
    fun theForegroundLocationPairIsAlwaysAskedForTogether() {
        // Asking for the fine permission alone is ignored outright from API 31,
        // which leaves the app with no location rather than an approximate one.
        listOf(30, 31, 33, RecordingPermissions.API_36).forEach { sdk ->
            val required = RecordingPermissions.requiredFor(sdk)
            assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in required)
            assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in required)
        }
    }

    @Test
    fun nothingIsAskedForTwice() {
        listOf(30, 31, 33, RecordingPermissions.API_36).forEach { sdk ->
            val asked = RecordingPermissions.requiredFor(sdk) + RecordingPermissions.backgroundFor(sdk)
            assertEquals("a permission asked twice on API $sdk", asked.size, asked.toSet().size)
        }
    }
}
